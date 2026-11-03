package zsgrooms.modid.replay;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ReplayLocalSoundTrackTest {
    @TempDir Path temp;

    @Test void writesVersionedContextEventsAndRequiredCompletionWithExactValues() throws Exception {
        ReplayLocalSoundTrack track = ReplayLocalSoundTrack.create(temp);
        Path spool;
        try {
            assertTrue(track.context(0, -1, -1, false));
            assertTrue(track.context(10, 0, 42, true));
            assertTrue(track.context(11, 0, 42, true));
            assertTrue(sound(track, 20, 0, 42, 1));
            assertTrue(sound(track, 20, 0, 42, 2));
            assertTrue(track.context(30, -1, -1, false));
            assertTrue(track.context(40, 1, 7, true));
            assertTrue(sound(track, 40, 1, 7, 8));
            track.finish(1000);
            spool = track.completedFile();
            assertNotNull(spool);
            DataInputStream data = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(spool)));
            assertEquals(0x5A534E44, data.readInt()); assertEquals(1, data.readInt()); assertEquals(127, data.readInt());
            context(data, 0, -1, -1, 0); context(data, 10, 0, 42, 127);
            event(data, 20, 0, 42, 1); event(data, 20, 0, 42, 2);
            context(data, 30, -1, -1, 0); context(data, 40, 1, 7, 127);
            event(data, 40, 1, 7, 8);
            assertEquals(0, data.readUnsignedByte()); assertEquals(1000, data.readInt()); assertEquals(-1, data.read());
            assertEquals("complete", track.status()); assertEquals(1000, track.lastTimestamp());
            assertFalse(track.accepting()); assertFalse(sound(track, 1001, 1, 7, 1));
            track.finish(1000);
        } finally { track.close(); }
        assertFalse(Files.exists(spool));
    }

    @Test void noEventsIsStillAnExplicitlyCompletedOptionalTrack() throws Exception {
        ReplayLocalSoundTrack track = ReplayLocalSoundTrack.create(temp);
        try {
            track.finish(500);
            assertEquals(17, Files.size(track.completedFile()));
            assertTrue(track.statusJson().contains("\"events\":0"));
        } finally { track.close(); }
    }

    @Test void invalidContextOrSupportedEventMakesOnlyOptionalTrackUnavailable() throws Exception {
        for (int mode = 0; mode < 7; mode++) {
            ReplayLocalSoundTrack track = ReplayLocalSoundTrack.create(temp);
            ReplayBuffer packets = new ReplayBuffer();
            try {
                assertTrue(track.context(10, 2, 42, true));
                switch (mode) {
                    case 0: assertFalse(track.context(9, -1, -1, false)); break;
                    case 1: assertFalse(track.context(20, 1, 42, true)); break;
                    case 2: assertFalse(sound(track, 20, 3, 42, 1)); break;
                    case 3: assertFalse(sound(track, 20, 2, 99, 1)); break;
                    case 4: assertFalse(sound(track, 20, 2, 42, 3)); break;
                    case 5: assertFalse(track.offer(20, 2, 42, 1, 7, "bad:id:two", 1, 1, 0, 64, 0)); break;
                    case 6: assertFalse(track.offer(20, 2, 42, 1, 7, "minecraft:block.stone.step", Float.NaN, 1, 0, 64, 0)); break;
                    default: fail();
                }
                track.finish(100);
                assertNull(track.completedFile()); assertEquals("failed", track.status());
                assertTrue(packets.isOpen());
                ReplayBuffer.Record packet = packets.reserve(0, 1, 1);
                assertNotNull(packet); assertTrue(packets.commit(packet, 100));
            } finally { track.close(); packets.discardQueued(); }
        }
    }

    @Test void pressureIsBoundedAndNeverPublishesIncompleteAuthority() throws Exception {
        BlockingSink sink = new BlockingSink();
        ReplayLocalSoundTrack track = new ReplayLocalSoundTrack(temp.resolve("pressure.bin"), sink, 80, 100000, 5000);
        try {
            assertTrue(sink.entered.await(5, TimeUnit.SECONDS));
            assertTrue(track.context(0, 0, 42, true));
            assertFalse(sound(track, 1, 0, 42, 1));
            assertFalse(track.accepting());
            assertTrue(track.statusJson().contains("QueueOverflow"));
            sink.release.countDown(); track.finish(100);
            assertNull(track.completedFile()); assertEquals("failed", track.status());
        } finally { sink.release.countDown(); track.close(); }
    }

    @Test void byteLimitDisablesSidecarAndDoesNotAffectPacketRecorder() throws Exception {
        ReplayLocalSoundTrack track = new ReplayLocalSoundTrack(temp.resolve("cap.bin"), new ByteArrayOutputStream(), 4096, 34, 5000);
        try {
            assertTrue(track.context(0, 0, 42, true));
            assertFalse(sound(track, 1, 0, 42, 1));
            track.finish(100);
            assertNull(track.completedFile()); assertTrue(track.statusJson().contains("SizeLimit"));
        } finally { track.close(); }
    }

    @Test void failedDiskAndStalledShutdownCannotPublishPartialFiles() throws Exception {
        ReplayLocalSoundTrack broken = new ReplayLocalSoundTrack(temp.resolve("broken.bin"), new OutputStream() {
            @Override public void write(int ignored) throws IOException { throw new IOException("disk full"); }
        }, 4096, 100000, 5000);
        try { broken.finish(100); assertNull(broken.completedFile()); assertEquals("failed", broken.status()); }
        finally { broken.close(); }
        BlockingSink sink = new BlockingSink();
        ReplayLocalSoundTrack stalled = new ReplayLocalSoundTrack(temp.resolve("stalled.bin"), sink, 4096, 100000, 25);
        try {
            assertTrue(sink.entered.await(5, TimeUnit.SECONDS));
            assertTimeout(Duration.ofSeconds(1), () -> stalled.finish(100));
            assertNull(stalled.completedFile()); assertTrue(stalled.statusJson().contains("SpoolTimeout"));
        } finally { sink.release.countDown(); stalled.close(); }
    }

    @Test void disabledCaptureDoesNotDereferenceMinecraftSoundOrPlayer() {
        ReplayLocalSoundCapture.player(null, null, null, 1, 1);
        ReplayLocalSoundCapture.world(null, null, null, 1, 1, 0, 0, 0, false);
        ReplayLocalSoundCapture.mining(null, null, 1, 1, null);
        ReplayLocalSoundCapture.worldEvent(null, null, 2001, null, 0);
    }

    @Test void preprocessingFailuresDisableOnlyTheOptionalSoundTrack() throws Exception {
        Runnable[] hooks = {
                () -> ReplayLocalSoundCapture.player(null, null, null, 1, 1),
                () -> ReplayLocalSoundCapture.world(null, null, null, 1, 1, 0, 0, 0, false),
                () -> ReplayLocalSoundCapture.mining(null, null, 1, 1, null),
                () -> ReplayLocalSoundCapture.worldEvent(null, null, 2001, null, 0)
        };
        for (Runnable hook : hooks) {
            ReplayLocalSoundTrack track = ReplayLocalSoundTrack.create(temp);
            ReplayBuffer packets = new ReplayBuffer();
            try {
                ReplayLocalSoundCapture.attach(track, System.nanoTime(), () -> 0, () -> true);
                assertDoesNotThrow(hook::run);
                assertEquals("failed", track.status());
                assertFalse(track.accepting());
                assertDoesNotThrow(hook::run); // The failed hook detached itself.
                track.finish(100);
                assertNull(track.completedFile());
                assertTrue(packets.isOpen());
            } finally { ReplayLocalSoundCapture.detach(track); track.close(); packets.discardQueued(); }
        }
    }

    @Test void classificationExcludesNetworkWorldAudioUiCraftingAndUnprovenSources() {
        for (String id : new String[] {"minecraft:block.chest.open", "minecraft:entity.generic.explode",
                "minecraft:ui.button.click", "minecraft:item.craft", "minecraft:entity.piglin.ambient", "mod:block.stone.step"})
            assertEquals(0, ReplayLocalSoundCapture.playerChannel(id));
        assertEquals(1, ReplayLocalSoundCapture.playerChannel("minecraft:block.stone.step"));
        assertEquals(8, ReplayLocalSoundCapture.playerChannel("minecraft:item.bucket.fill_lava"));
        assertEquals(16, ReplayLocalSoundCapture.playerChannel("minecraft:entity.generic.eat"));
        assertEquals(32, ReplayLocalSoundCapture.playerChannel("minecraft:entity.player.hurt_on_fire"));
        assertEquals(64, ReplayLocalSoundCapture.playerChannel("minecraft:entity.player.big_fall"));
        assertEquals(64, ReplayLocalSoundCapture.playerChannel("minecraft:block.stone.fall"));
    }

    @Test void identitiesAndLongSessionTimeAreNotTiedToDimensionNamesOrWallClock() throws Exception {
        ReplayLocalSoundTrack track = ReplayLocalSoundTrack.create(temp);
        try {
            long time = 15L * 3600000;
            assertTrue(track.context(time, 10, 42, true));
            assertTrue(sound(track, time, 10, 42, 1));
            assertTrue(track.context(time + 1, -1, -1, false));
            assertTrue(track.context(time + 2, 11, 42, true));
            assertTrue(sound(track, time + 3, 11, 42, 1));
            track.finish(time + 100);
            assertNotNull(track.completedFile());
        } finally { track.close(); }
    }

    private static boolean sound(ReplayLocalSoundTrack track, long time, int world, int entity, int channel) {
        return track.offer(time, world, entity, channel, 7, "minecraft:block.stone.step", 0.15F, 1.25F, 12.125, 64, -90.75);
    }
    private static void context(DataInputStream data, int time, int world, int entity, int mask) throws Exception {
        assertEquals(1, data.readUnsignedByte()); assertEquals(time, data.readInt());
        assertEquals(world, data.readInt()); assertEquals(entity, data.readInt()); assertEquals(mask, data.readInt());
    }
    private static void event(DataInputStream data, int time, int world, int entity, int channel) throws Exception {
        assertEquals(2, data.readUnsignedByte()); assertEquals(time, data.readInt());
        assertEquals(world, data.readInt()); assertEquals(entity, data.readInt());
        assertEquals(channel, data.readUnsignedByte()); assertEquals(7, data.readUnsignedByte());
        assertEquals(0.15F, data.readFloat()); assertEquals(1.25F, data.readFloat());
        assertEquals(12.125, data.readDouble()); assertEquals(64, data.readDouble()); assertEquals(-90.75, data.readDouble());
        assertEquals("minecraft:block.stone.step", data.readUTF());
    }
    private static final class BlockingSink extends OutputStream {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        @Override public void write(int ignored) throws IOException {
            entered.countDown();
            boolean interrupted = false;
            for (;;) {
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("test timeout"); break; }
                catch (InterruptedException error) { interrupted = true; }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
