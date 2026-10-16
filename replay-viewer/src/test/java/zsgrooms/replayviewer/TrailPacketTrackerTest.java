// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.replaystudio.PacketData;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion;
import com.replaymod.replaystudio.protocol.Packet;
import com.replaymod.replaystudio.protocol.PacketType;
import com.replaymod.replaystudio.protocol.PacketTypeRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class TrailPacketTrackerTest {
    @TempDir Path directory;
    private final UUID player = UUID.randomUUID(), mob = UUID.randomUUID();

    @Test void playerAbsoluteTickSamplesStayContinuousButRealCorrectionsAndLargeJumpsBreak() throws Exception {
        try (TrailDiskStore store = new TrailDiskStore(directory)) {
            TrailPacketTracker tracker = new TrailPacketTracker(store);
            deliver(tracker, 0, PacketType.JoinGame, b -> b.writeInt(42));
            spawn(tracker, 10, 42, player, 0, true);
            teleport(tracker, 20, 42, 1);
            teleport(tracker, 30, 42, 2);
            deliver(tracker, 40, PacketType.PlayerPositionRotation, b -> {
                b.writeDouble(1); b.writeDouble(0); b.writeDouble(0);
                b.writeFloat(0); b.writeFloat(0); b.writeByte(7); b.writeVarInt(1);
            });
            teleport(tracker, 50, 42, 3);
            teleport(tracker, 60, 42, 100);
            store.finish();
            List<TrailPoint> points = path(store, player, 60);
            assertFalse(at(points, 20).breakBefore);
            assertFalse(at(points, 30).breakBefore);
            assertEquals(3, at(points, 40).x);
            assertTrue(at(points, 40).breakBefore);
            assertTrue(at(points, 60).breakBefore);
        }
    }

    @Test void relativePositionsUseQuantizedMinecraftCoordinatesNotFloatingPointSpawnOrigin() throws Exception {
        try (TrailDiskStore store = new TrailDiskStore(directory)) {
            TrailPacketTracker tracker = new TrailPacketTracker(store);
            spawn(tracker, 0, 5, mob, -1.234, false);
            relative(tracker, 100, 5, (short) 1, false);
            relative(tracker, 200, 5, (short) 4096, true);
            teleport(tracker, 300, 5, 9.1234);
            relative(tracker, 400, 5, (short) -1, false);
            store.finish();
            List<TrailPoint> points = path(store, mob, 400);
            assertEquals((Math.floor(-1.234 * 4096) + 1) / 4096, at(points, 100).x);
            assertEquals((Math.floor(-1.234 * 4096) + 4097) / 4096, at(points, 200).x);
            assertFalse(at(points, 200).breakBefore);
            assertTrue(at(points, 300).breakBefore);
            assertEquals((Math.floor(9.1234 * 4096) - 1) / 4096, at(points, 400).x);
        }
    }

    @Test void idReuseDestroyAndUuidReappearanceCannotJoinTrails() throws Exception {
        try (TrailDiskStore store = new TrailDiskStore(directory)) {
            TrailPacketTracker tracker = new TrailPacketTracker(store);
            spawn(tracker, 10, 5, mob, 1, false);
            spawn(tracker, 20, 5, player, 2, true);
            relative(tracker, 30, 5, (short) 4096, false);
            deliver(tracker, 40, PacketType.DestroyEntities, b -> { b.writeVarInt(1); b.writeVarInt(5); });
            relative(tracker, 50, 5, (short) 4096, false);
            spawn(tracker, 60, 9, player, 9, true);
            store.finish();
            List<TrailPoint> old = path(store, mob, 100);
            assertTrue(old.stream().allMatch(p -> p.x == 1 && p.time <= 20));
            List<TrailPoint> current = path(store, player, 100);
            assertEquals(3, at(current, 30).x);
            assertTrue(at(current, 60).breakBefore);
            assertTrue(current.stream().noneMatch(p -> p.time == 50));
        }
    }

    @Test void objectReusingMobIdInvalidatesTheOldMob() throws Exception {
        try (TrailDiskStore store = new TrailDiskStore(directory)) {
            TrailPacketTracker tracker = new TrailPacketTracker(store);
            spawn(tracker, 10, 5, mob, 1, false);
            deliver(tracker, 20, PacketType.SpawnObject, b -> b.writeVarInt(5));
            relative(tracker, 30, 5, (short) 4096, false);
            store.finish();
            assertTrue(path(store, mob, 100).stream().allMatch(p -> p.x == 1 && p.time <= 20));
        }
    }

    @Test void respawnAndJoinClearTrackedIdsAndSeparateWorldEpochs() throws Exception {
        try (TrailDiskStore store = new TrailDiskStore(directory)) {
            TrailPacketTracker tracker = new TrailPacketTracker(store);
            deliver(tracker, 0, PacketType.JoinGame, b -> b.writeInt(42));
            spawn(tracker, 10, 42, player, 1, true);
            deliver(tracker, 20, PacketType.Respawn, b -> {});
            relative(tracker, 30, 42, (short) 4096, false);
            spawn(tracker, 40, 42, player, 2, true);
            deliver(tracker, 50, PacketType.JoinGame, b -> b.writeInt(99));
            spawn(tracker, 60, 99, player, 3, true);
            teleport(tracker, 70, 99, 4);
            store.finish();
            assertTrue(path(store, player, 19).stream().allMatch(p -> p.x == 1));
            assertFalse(store.window(0, 100, Collections.singleton(player), () -> false)
                    .snapshot(35, Collections.singleton(player), 35).containsKey(player));
            List<TrailPoint> points = path(store, player, 100);
            assertTrue(points.stream().allMatch(p -> p.time >= 60));
            assertFalse(at(points, 70).breakBefore);
        }
    }

    @Test void duplicateUuidAtNewIdEndsPriorIdAndIgnoresItsLaterMovement() throws Exception {
        try (TrailDiskStore store = new TrailDiskStore(directory)) {
            TrailPacketTracker tracker = new TrailPacketTracker(store);
            spawn(tracker, 10, 5, mob, 1, false);
            spawn(tracker, 20, 6, mob, 2, false);
            relative(tracker, 30, 5, (short) 4096, false);
            relative(tracker, 40, 6, (short) 4096, false);
            store.finish();
            List<TrailPoint> points = path(store, mob, 50);
            assertTrue(points.stream().noneMatch(p -> p.time == 30));
            assertTrue(at(points, 20).breakBefore || points.stream().anyMatch(p -> p.time == 20 && p.breakBefore));
            assertEquals(3, at(points, 40).x);
        }
    }

    @Test void malformedCoordinatesAndTruncatedPacketsFailWithoutTakingPacketOwnership() throws Exception {
        try (TrailDiskStore store = new TrailDiskStore(directory)) {
            TrailPacketTracker tracker = new TrailPacketTracker(store);
            assertThrows(IOException.class, () -> spawn(tracker, 0, 5, mob, Double.NaN, false));
            assertThrows(IOException.class, () -> deliver(tracker, 1, PacketType.EntityTeleport, b -> b.writeVarInt(5)));
            assertThrows(IOException.class, () -> deliver(tracker, 2, PacketType.DestroyEntities, b -> b.writeVarInt(Integer.MAX_VALUE)));
        }
    }

    @Test void unrelatedPacketAndLoginPhaseAreIgnored() throws Exception {
        try (TrailDiskStore store = new TrailDiskStore(directory)) {
            TrailPacketTracker tracker = new TrailPacketTracker(store);
            deliver(tracker, 1, PacketType.Chat, b -> b.writeByte(255));
            ByteBuf buf = Unpooled.wrappedBuffer(new byte[]{(byte) 255});
            PacketData data = new PacketData(1, new Packet(PacketTypeRegistry.get(ProtocolVersion.v1_16_1, State.LOGIN), 99, buf));
            try { tracker.packet(data); } finally { data.release(); }
            assertEquals(0, buf.refCnt());
            store.finish();
            assertNull(path(store, mob, 100));
        }
    }

    static void spawn(TrailPacketTracker tracker, int time, int id, UUID uuid, double x, boolean player) throws IOException {
        deliver(tracker, time, player ? PacketType.SpawnPlayer : PacketType.SpawnMob, b -> spawnBytes(b, id, uuid, x, player));
    }

    static void spawnBytes(PacketByteBuf b, int id, UUID uuid, double x, boolean player) {
        b.writeVarInt(id); b.writeUuid(uuid);
        if (!player) b.writeVarInt(0); // Numeric type is irrelevant: MobSpawn is exclusively LivingEntity in 1.16.1.
        b.writeDouble(x); b.writeDouble(64); b.writeDouble(0);
        b.writeZero(player ? 2 : 9);
    }

    static void teleport(TrailPacketTracker tracker, int time, int id, double x) throws IOException {
        deliver(tracker, time, PacketType.EntityTeleport, b -> {
            b.writeVarInt(id); b.writeDouble(x); b.writeDouble(64); b.writeDouble(0); b.writeZero(3);
        });
    }

    static void relative(TrailPacketTracker tracker, int time, int id, short dx, boolean rotate) throws IOException {
        deliver(tracker, time, rotate ? PacketType.EntityPositionRotation : PacketType.EntityPosition, b -> {
            b.writeVarInt(id); b.writeShort(dx); b.writeShort(0); b.writeShort(0); b.writeZero(rotate ? 3 : 1);
        });
    }

    static void deliver(TrailPacketTracker tracker, int time, PacketType type, Consumer<PacketByteBuf> writer) throws IOException {
        PacketByteBuf bytes = new PacketByteBuf(Unpooled.buffer());
        bytes.writeByte(123); writer.accept(bytes); bytes.readerIndex(1);
        PacketData data = new PacketData(time, new Packet(PacketTypeRegistry.get(ProtocolVersion.v1_16_1, State.PLAY),
                TrailPacketTracker.packetId(type), bytes));
        try {
            tracker.packet(data);
        } finally {
            assertEquals(1, bytes.refCnt()); assertEquals(1, bytes.readerIndex());
            data.release(); assertEquals(0, bytes.refCnt());
        }
    }

    private static List<TrailPoint> path(TrailDiskStore store, UUID uuid, int time) throws IOException {
        return store.window(0, time + 100, Collections.singleton(uuid), () -> false)
                .snapshot(time, Collections.singleton(uuid), time).get(uuid);
    }

    private static TrailPoint at(List<TrailPoint> points, int time) {
        return points.stream().filter(p -> p.time == time).findFirst().orElseThrow(AssertionError::new);
    }
}
