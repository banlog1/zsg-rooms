// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion;
import com.replaymod.replaystudio.protocol.PacketType;
import com.replaymod.replaystudio.protocol.PacketTypeRegistry;
import com.replaymod.replaystudio.protocol.Packet;
import com.replaymod.replaystudio.protocol.packets.PacketJoinGame;
import com.replaymod.replaystudio.protocol.registry.DimensionType;
import com.replaymod.replaystudio.protocol.registry.Registries;
import com.replaymod.lib.com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class TrailIndexTest {
    @TempDir Path directory;
    private final UUID player = new UUID(1, 2), other = new UUID(3, 4);
    private final List<TrailIndex> indexes = new ArrayList<>();

    @AfterEach void stopWorkersBeforeRemovingTemporaryDirectory() throws Exception {
        for (TrailIndex index : indexes) index.close();
        java.lang.reflect.Field field = TrailIndex.class.getDeclaredField("worker");
        field.setAccessible(true);
        for (TrailIndex index : indexes) {
            Thread worker = (Thread) field.get(index);
            if (worker != null) {
                worker.join(5000);
                assertFalse(worker.isAlive(), "Trail worker did not cancel");
            }
            assertEquals("Trail history closed", index.status);
        }
        assertEquals(0, scratches());
    }

    @Test void scansLazilyWithEmptySelectionAndSupportsOldArchivesWithoutRoomsMetadata() throws Exception {
        Path replay = replay(packets(), 736);
        byte[] original = Files.readAllBytes(replay);
        try (TrailIndex index = index(replay)) {
            assertEquals("Trail history not requested", index.status);
            assertEquals(0, scratches());
            assertNull(index.snapshot(1000, Collections.emptySet(), 1000));
            index.request(1000, Collections.emptySet(), 1000);
            TrailIndex.Snapshot empty = await(index, 1000, Collections.emptySet(), 1000);
            assertTrue(empty.paths.isEmpty());
            Set<UUID> selected = Collections.singleton(player);
            assertNull(index.snapshot(1000, selected, 1000));
            index.request(1000, selected, 1000);
            TrailIndex.Snapshot snapshot = await(index, 1000, selected, 1000);
            assertEquals(2, snapshot.paths.get(player).get(snapshot.paths.get(player).size() - 1).x);
            assertFalse(snapshot.paths.get(player).stream().filter(p -> p.time == 100).findFirst().get().breakBefore,
                    "Undo ReplayStudio's JoinGame camera ID rewrite before classifying local movement");
            assertSame(snapshot, index.snapshot(1000, selected, 1000));
            assertThrows(UnsupportedOperationException.class, snapshot.paths::clear);
            assertThrows(UnsupportedOperationException.class, () -> snapshot.paths.get(player).clear());
            assertEquals("", index.status);
        }
        waitFor(() -> scratches() == 0);
        assertArrayEquals(original, Files.readAllBytes(replay));
        Path moved = directory.resolve("moved.mcpr");
        Files.move(replay, moved);
    }

    @Test void lookaheadCacheReusesRevisionButNeverLeaksFuturePositionsAndSupportsBackwardSeek() throws Exception {
        try (TrailIndex index = index(replay(packets(), 736))) {
            Set<UUID> selected = Collections.singleton(player);
            index.request(1000, selected, 1000);
            TrailIndex.Snapshot first = await(index, 1000, selected, 1000);
            assertTrue(first.paths.get(player).stream().allMatch(p -> p.time <= 1000 && p.x <= 2));
            TrailIndex.Snapshot later = index.snapshot(1500, selected, 1000);
            assertNotNull(later);
            assertEquals(first.revision, later.revision);
            assertNotSame(first, later);
            assertTrue(later.paths.get(player).stream().allMatch(p -> p.time <= 1500 && p.x <= 2));
            assertNotNull(index.snapshot(800, selected, 500));
            assertNull(index.snapshot(20_000, selected, 1000));
            index.request(20_000, selected, 30_000);
            TrailIndex.Snapshot sought = await(index, 20_000, selected, 30_000);
            assertTrue(sought.revision > first.revision);
            assertTrue(sought.paths.get(player).stream().anyMatch(p -> p.time == 10 && p.x == 1));
            assertNull(index.snapshot(2000, Collections.singleton(other), 1000));
        }
        waitFor(() -> scratches() == 0);
    }

    @Test void absentUuidIsUnknownAndChangingSelectionDoesNotReuseWrongHistory() throws Exception {
        try (TrailIndex index = index(replay(packets(), 736))) {
            Set<UUID> selected = new HashSet<>(); selected.add(player); selected.add(other);
            index.request(1000, selected, 1000);
            selected.clear();
            Set<UUID> both = new HashSet<>(); both.add(player); both.add(other);
            TrailIndex.Snapshot snapshot = await(index, 1000, both, 1000);
            assertTrue(snapshot.paths.containsKey(player));
            assertFalse(snapshot.paths.containsKey(other));
            assertNotNull(index.snapshot(1000, Collections.singleton(player), 1000));
        }
    }

    @Test void identicalPendingRequestsReuseTheDefensiveSelectionCopy() throws Exception {
        try (TrailIndex index = index(replay(packets(), 736))) {
            java.lang.reflect.Field field = TrailIndex.class.getDeclaredField("pending");
            field.setAccessible(true);
            Set<UUID> selected = new HashSet<>(Collections.singleton(player));
            Set<UUID> both = new HashSet<>(selected); both.add(other);
            synchronized (index) {
                // Hold publication/dequeue while checking coalescing, regardless of scan speed.
                index.request(1000, selected, Integer.MAX_VALUE);
                Object pending = field.get(index);
                assertNotNull(pending);
                selected.clear();
                index.request(1000, Collections.singleton(player), 30_000);
                assertSame(pending, field.get(index));
                index.request(1001, Collections.singleton(player), 30_000);
                assertNotSame(pending, field.get(index));
                pending = field.get(index);
                index.request(1001, both, 30_000);
                assertNotSame(pending, field.get(index));
                pending = field.get(index);
                index.request(1001, both, 2000);
                assertNotSame(pending, field.get(index));
            }
            TrailIndex.Snapshot result = await(index, 1001, both, 2000);
            assertTrue(result.paths.containsKey(player));
            assertFalse(result.paths.containsKey(other));
        }
    }

    @Test void unsupportedProtocolAndCorruptStreamDiscardScratchAndNeverPublishPartialData() throws Exception {
        for (boolean protocol : new boolean[]{true, false}) {
            byte[] packets = packets();
            if (!protocol) {
                byte[] truncated = new byte[packets.length + 3];
                System.arraycopy(packets, 0, truncated, 0, packets.length);
                packets = truncated;
            }
            try (TrailIndex index = index(replay(packets, protocol ? 754 : 736))) {
                index.request(1000, Collections.singleton(player), 1000);
                waitFor(() -> index.status.startsWith("Trail history unavailable"));
                assertNull(index.snapshot(1000, Collections.singleton(player), 1000));
                assertEquals(0, scratches());
            }
        }
    }

    @Test void closeCancelsPendingScanAndDeletesScratchWithoutReopeningWork() throws Exception {
        TrailIndex index = index(replay(packets(), 736));
        index.request(1000, Collections.singleton(player), 1000);
        index.close();
        waitFor(() -> scratches() == 0);
        index.request(1000, Collections.singleton(player), 1000);
        assertNull(index.snapshot(1000, Collections.singleton(player), 1000));
        assertEquals("Trail history closed", index.status);
    }

    @Test void selectionIsBoundedAndDurationClampsWithoutStartingInvalidWork() throws Exception {
        try (TrailIndex index = index(replay(packets(), 736))) {
            Set<UUID> tooMany = new HashSet<>();
            for (int i = 0; i <= TrailIndex.MAX_SELECTED; i++) tooMany.add(new UUID(0, i));
            index.request(1000, tooMany, 1000);
            assertNull(index.snapshot(1000, tooMany, 1000));
            assertEquals(0, scratches());
            Set<UUID> selected = Collections.singleton(player);
            index.request(1000, selected, Integer.MAX_VALUE);
            assertNotNull(await(index, 1000, selected, 30_000));
            assertNotNull(index.snapshot(1000, selected, Integer.MAX_VALUE));
            assertNotNull(index.snapshot(1000, selected, -1));
            assertNull(index.snapshot(-1, selected, 1000));
        }
    }

    private TrailIndex.Snapshot await(TrailIndex index, int time, Set<UUID> entities, int duration) throws Exception {
        waitFor(() -> index.snapshot(time, entities, duration) != null || index.status.startsWith("Trail history unavailable"));
        TrailIndex.Snapshot result = index.snapshot(time, entities, duration);
        assertNotNull(result, () -> "Trail snapshot unavailable: " + index.status);
        return result;
    }

    private TrailIndex index(Path replay) {
        TrailIndex index = new TrailIndex(replay, directory);
        indexes.add(index);
        return index;
    }

    private long scratches() {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(p -> p.getFileName().toString().startsWith("zsg-trails-")).count();
        } catch (IOException e) { throw new AssertionError(e); }
    }

    private void waitFor(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + 5_000_000_000L;
        boolean ready;
        while (!(ready = condition.getAsBoolean()) && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(ready, () -> "Timed out waiting for trail worker: " + indexes.stream()
                .map(index -> index.status).collect(java.util.stream.Collectors.joining("; ")));
    }

    private byte[] packets() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        frame(bytes, 0, State.LOGIN, PacketType.LoginSuccess, b -> { b.writeUuid(player); b.writeString("Runner"); });
        PacketJoinGame join = new PacketJoinGame();
        join.entityId = 42; join.gameMode = 0; join.prevGameMode = -1;
        join.dimensions = Collections.singletonList("minecraft:overworld");
        join.registries = new Registries(new CompoundTag());
        join.dimensionType = new DimensionType("minecraft:overworld");
        join.dimension = "minecraft:overworld"; join.maxPlayers = 1; join.viewDistance = 10;
        join.respawnScreen = true;
        Packet packet = join.write(PacketTypeRegistry.get(ProtocolVersion.v1_16_1, State.PLAY));
        try { frame(bytes, 0, State.PLAY, PacketType.JoinGame, b -> b.writeBytes(packet.getBuf().duplicate())); }
        finally { packet.release(); }
        frame(bytes, 10, State.PLAY, PacketType.SpawnPlayer, b -> TrailPacketTrackerTest.spawnBytes(b, 42, player, 1, true));
        frame(bytes, 100, State.PLAY, PacketType.EntityTeleport, b -> {
            b.writeVarInt(42); b.writeDouble(2); b.writeDouble(64); b.writeDouble(0); b.writeZero(3);
        });
        frame(bytes, 2000, State.PLAY, PacketType.EntityTeleport, b -> {
            b.writeVarInt(42); b.writeDouble(3); b.writeDouble(64); b.writeDouble(0); b.writeZero(3);
        });
        return bytes.toByteArray();
    }

    private static void frame(ByteArrayOutputStream target, int time, State state, PacketType type,
                              Consumer<PacketByteBuf> payload) throws IOException {
        PacketByteBuf bytes = new PacketByteBuf(Unpooled.buffer());
        try {
            bytes.writeVarInt(PacketTypeRegistry.get(ProtocolVersion.v1_16_1, state).getId(type));
            payload.accept(bytes);
            byte[] data = new byte[bytes.readableBytes()]; bytes.readBytes(data);
            DataOutputStream output = new DataOutputStream(target);
            output.writeInt(time); output.writeInt(data.length); output.write(data);
        } finally { bytes.release(); }
    }

    private Path replay(byte[] packets, int protocol) throws IOException {
        Path path = directory.resolve(UUID.randomUUID() + ".mcpr");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            entry(zip, "recording.tmcpr", packets);
            entry(zip, "metaData.json", ("{\"protocol\":" + protocol + ",\"fileFormatVersion\":14,\"fileFormat\":\"MCPR\",\"duration\":100000}")
                    .getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private static void entry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); zip.write(bytes); zip.closeEntry();
    }
}
