package zsgrooms.modid.replay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class ReplayCameraTrackTest {
    @TempDir Path temp;

    @Test void exactBigEndianContractPreservesRawAnglesBoundariesAndAllRates() throws Exception {
        for (int hz : new int[] { 60, 120, 240 }) {
            ReplayCameraTrack track = ReplayCameraTrack.create(temp, hz);
            Path spool;
            try {
                assertEquals(-1, track.lastTimestamp());
                assertNull(track.completedFile());
                float yaw = Float.intBitsToFloat(0x47C35001), pitch = Float.intBitsToFloat(0xBEAAAAAB);
                assertTrue(track.offer(0, -1, -1, 0, 0, -0.0f));
                assertTrue(track.offer(8, 2, 42, ReplayCameraTrack.AVAILABLE, yaw, pitch));
                assertTrue(track.offer(9, 2, 42, ReplayCameraTrack.GAP, 0, 0));
                assertTrue(track.offer(10, 3, 7, ReplayCameraTrack.AVAILABLE, -721.125f, 89.5f));
                track.finish();
                spool = track.completedFile();
                assertNotNull(spool);
                ByteBuffer expected = ByteBuffer.allocate(12 + 4 * 24).putInt(0x5A43414D).putInt(1).putInt(hz);
                expected.putInt(0).putInt(-1).putInt(-1).putInt(0).putFloat(0).putFloat(-0.0f);
                expected.putInt(8).putInt(2).putInt(42).putInt(1).putFloat(yaw).putFloat(pitch);
                expected.putInt(9).putInt(2).putInt(42).putInt(2).putFloat(0).putFloat(0);
                expected.putInt(10).putInt(3).putInt(7).putInt(1).putFloat(-721.125f).putFloat(89.5f);
                assertArrayEquals(expected.array(), Files.readAllBytes(spool));
                assertEquals(10, track.lastTimestamp());
                assertEquals("gaps", track.status());
                assertEquals(4, status(track).get("samples").getAsInt());
                assertEquals(hz, status(track).get("hz").getAsInt());
                assertFalse(track.accepting());
                assertFalse(track.offer(11, 0, 1, 1, 0, 0));
                track.finish();
            } finally { track.close(); }
            assertNull(track.completedFile());
            assertFalse(Files.exists(spool));
        }
    }

    @Test void invalidSamplesDoNotConsumeTimeOrDisableRecording() throws Exception {
        ReplayCameraTrack track = ReplayCameraTrack.create(temp, 60);
        try {
            assertFalse(track.offer(-1, -1, -1, 0, 0, 0));
            assertFalse(track.offer((long) Integer.MAX_VALUE + 1, 0, 1, 1, 0, 0));
            assertTrue(track.offer(0, -1, -1, 0, 0, 0));
            assertFalse(track.offer(0, 0, 1, 1, 0, 0));
            assertFalse(track.offer(1, -2, 1, 1, 0, 0));
            assertFalse(track.offer(1, 0, -2, 1, 0, 0));
            for (int flags : new int[] { -1, 3, 4, Integer.MAX_VALUE })
                assertFalse(track.offer(1, 0, 1, flags, 0, 0));
            for (float angle : new float[] { Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY }) {
                assertFalse(track.offer(1, 0, 1, 1, angle, 0));
                assertFalse(track.offer(1, 0, 1, 0, 0, angle));
            }
            assertTrue(track.accepting());
            assertTrue(track.offer(1, 0, 1, 1, Float.MAX_VALUE, -90));
            assertTrue(track.offer(Integer.MAX_VALUE, 0, 1, 1, 1, 2));
            track.finish();
            assertEquals(Integer.MAX_VALUE, track.lastTimestamp());
            assertEquals(3, records(Files.readAllBytes(track.completedFile())).length);
            assertEquals("complete", track.status());
            assertEquals(0, status(track).get("gaps").getAsLong());
            assertEquals(15, status(track).get("rejectedSamples").getAsLong());
        } finally { track.close(); }
    }

    @Test void availableSamplesRequireContextAndValidPitchWithoutConsumingRejectedTimes() throws Exception {
        for (int hz : new int[] { 60, 120, 240 }) {
            ReplayCameraTrack track = ReplayCameraTrack.create(temp, hz);
            try {
                assertTrue(track.offer(0, -1, -1, 0, 0, 91));
                assertFalse(track.offer(1, -1, 0, 1, 0, 0));
                assertFalse(track.offer(1, 0, -1, 1, 0, 0));
                assertFalse(track.offer(1, -1, -1, 1, 0, 0));
                for (float pitch : new float[] { -91, 91, Math.nextDown(-90.0f), Math.nextUp(90.0f),
                        -Float.MAX_VALUE, Float.MAX_VALUE })
                    assertFalse(track.offer(1, 0, 0, 1, 0, pitch));
                assertTrue(track.accepting());
                assertTrue(track.offer(1, 0, 0, 1, Float.MAX_VALUE, -90));
                assertTrue(track.offer(2, Integer.MAX_VALUE, Integer.MAX_VALUE, 1, -Float.MAX_VALUE, 90));
                assertTrue(track.offer(3, -1, -1, 2, 0, -91));
                track.finish();
                int[][] records = records(Files.readAllBytes(track.completedFile()));
                assertArrayEquals(new int[] { 0, 1, 2, 3 }, times(records));
                assertEquals(Float.floatToRawIntBits(-90), records[1][5]);
                assertEquals(Float.floatToRawIntBits(90), records[2][5]);
                assertEquals(9, status(track).get("rejectedSamples").getAsInt());
                assertEquals(0, status(track).get("droppedSamples").getAsInt());
            } finally { track.close(); }
        }
    }

    @Test void backpressureCoalescesDropsIntoAnOrderedGapAndThenRecovers() throws Exception {
        BlockingSink sink = new BlockingSink(temp.resolve("pressure.bin"), true);
        ReplayCameraTrack track = new ReplayCameraTrack(temp.resolve("pressure.bin"), sink, 240, 2, 100000, 5000);
        try {
            assertTrue(sink.entered.await(5, TimeUnit.SECONDS));
            assertTrue(track.offer(0, 0, 1, 1, 1, 2));
            assertTrue(track.offer(8, 0, 1, 1, 3, 4));
            assertFalse(track.offer(16, 1, 2, 1, 5, 6));
            assertFalse(track.offer(24, 2, 3, 1, 7, 8));
            assertFalse(track.offer(20, 0, 1, 1, 0, 0));
            assertEquals(-1, track.lastTimestamp());
            assertTrue(track.accepting());
            sink.release.countDown();
            await(() -> track.lastTimestamp() == 16);
            assertTrue(track.offer(32, 3, 4, 1, 9, 10));
            track.finish();
            int[][] records = records(Files.readAllBytes(track.completedFile()));
            assertEquals(4, records.length);
            assertArrayEquals(new int[] { 0, 8, 16, 32 }, times(records));
            assertArrayEquals(new int[] { 16, 1, 2, 2, 0, 0 }, records[2]);
            assertEquals(32, track.lastTimestamp());
            JsonObject coverage = status(track);
            assertEquals("gaps", track.status());
            assertEquals(1, coverage.get("gaps").getAsLong());
            assertEquals(2, coverage.get("droppedSamples").getAsLong());
            assertEquals(2, coverage.get("peakQueuedSamples").getAsInt());
            assertEquals(48, coverage.get("peakQueuedBytes").getAsInt());
        } finally { sink.release.countDown(); track.close(); }
    }

    @Test void sustainedPressureUsesFixedStorageAndFinishFlushesPendingGap() throws Exception {
        BlockingSink sink = new BlockingSink(temp.resolve("bounded.bin"), true);
        ReplayCameraTrack track = new ReplayCameraTrack(temp.resolve("bounded.bin"), sink, 240, 4096, 1000000, 5000);
        try {
            assertTrue(sink.entered.await(5, TimeUnit.SECONDS));
            assertTimeout(Duration.ofSeconds(2), () -> {
                for (int time = 0; time < 100000; time++)
                    assertEquals(time < 4096, track.offer(time, 0, 1, 1, time, 0));
            });
            assertTrue(track.accepting());
            assertEquals(4096, status(track).get("peakQueuedSamples").getAsInt());
            assertEquals(95904, status(track).get("droppedSamples").getAsInt());
            sink.release.countDown();
            track.finish();
            int[][] records = records(Files.readAllBytes(track.completedFile()));
            assertEquals(4097, records.length);
            assertArrayEquals(new int[] { 4096, 0, 1, 2, 0, 0 }, records[4096]);
        } finally { sink.release.countDown(); track.close(); }
    }

    @Test void ringWrapsRepeatedlyAndLongRecordingTimestampsStayBounded() throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        ReplayCameraTrack track = new ReplayCameraTrack(temp.resolve("wrap.bin"), sink, 60, 3, 100000, 5000);
        try {
            for (int hour = 0; hour <= 72; hour++) {
                int time = hour * 3600000;
                assertTrue(track.offer(time, hour % 3, hour, 1, hour + 0.125f, -hour));
                await(() -> track.lastTimestamp() == time);
            }
            track.finish();
            int[][] records = records(sink.toByteArray());
            assertEquals(73, records.length);
            for (int hour = 0; hour <= 72; hour++) {
                assertEquals(hour * 3600000, records[hour][0]);
                assertEquals(Float.floatToRawIntBits(hour + 0.125f), records[hour][4]);
            }
            assertEquals("complete", track.status());
            assertTrue(status(track).get("peakQueuedSamples").getAsInt() <= 3);
        } finally { track.close(); }
    }

    @Test void capReservesTerminalGapAndDoesNotDisableNormalPackets() throws Exception {
        Path spool = temp.resolve("cap.bin");
        long cap = 12 + 3 * 24;
        ReplayCameraTrack track = new ReplayCameraTrack(spool, Files.newOutputStream(spool), 60, 4, cap, 5000);
        ReplayBuffer packets = new ReplayBuffer();
        try {
            assertTrue(track.offer(0, 0, 1, 1, 1, 2));
            assertTrue(track.offer(16, 0, 1, 1, 3, 4));
            assertFalse(track.offer(32, 0, 1, 1, 5, 6));
            assertFalse(track.accepting());
            assertFalse(track.offer(48, 0, 1, 1, 7, 8));
            track.finish();
            assertEquals("capped", track.status());
            assertEquals(cap, Files.size(track.completedFile()));
            assertEquals(cap, status(track).get("bytes").getAsLong());
            int[][] records = records(Files.readAllBytes(track.completedFile()));
            assertArrayEquals(new int[] { 32, 0, 1, 2, 0, 0 }, records[2]);
            assertEquals(32, track.lastTimestamp());
            assertTrue(packets.isOpen());
            ReplayBuffer.Record packet = packets.reserve(0, 1, 1);
            assertNotNull(packet);
            assertTrue(packets.commit(packet, 48));
        } finally { track.close(); packets.discardQueued(); }
        assertFalse(Files.exists(spool));
    }

    @Test void minimumCapStillProducesAValidTerminalGap() throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        ReplayCameraTrack track = new ReplayCameraTrack(temp.resolve("minimum.bin"), sink, 60, 1, 36, 5000);
        try {
            assertFalse(track.offer(0, -1, -1, 0, 0, 0));
            track.finish();
            assertEquals("capped", track.status());
            assertEquals(36, sink.size());
            assertArrayEquals(new int[] { 0, -1, -1, 2, 0, 0 }, records(sink.toByteArray())[0]);
        } finally { track.close(); }
    }

    @Test void capDuringPressureDoesNotDoubleReserveThePendingGap() throws Exception {
        BlockingSink sink = new BlockingSink(temp.resolve("pressure-cap.bin"), true);
        ReplayCameraTrack track = new ReplayCameraTrack(temp.resolve("pressure-cap.bin"), sink, 120, 1, 84, 5000);
        try {
            assertTrue(sink.entered.await(5, TimeUnit.SECONDS));
            assertTrue(track.offer(0, 0, 1, 1, 0, 0));
            assertFalse(track.offer(8, 0, 1, 1, 0, 0));
            assertFalse(track.offer(16, 0, 1, 1, 0, 0));
            assertFalse(track.accepting());
            sink.release.countDown();
            track.finish();
            assertEquals("capped", track.status());
            assertEquals(60, Files.size(track.completedFile()));
            assertArrayEquals(new int[] { 0, 8 }, times(records(Files.readAllBytes(track.completedFile()))));
            assertEquals(1, status(track).get("gaps").getAsInt());
        } finally { sink.release.countDown(); track.close(); }
    }

    @Test void diskOrFlushFailureNeverPublishesATruncatedSpool() throws Exception {
        for (boolean failOnClose : new boolean[] { false, true }) {
            Path spool = Files.createFile(temp.resolve("failure-" + failOnClose + ".bin"));
            ReplayCameraTrack track = new ReplayCameraTrack(spool, new OutputStream() {
                @Override public void write(int value) throws IOException {
                    if (!failOnClose) throw new IOException("full disk");
                }
                @Override public void close() throws IOException { throw new IOException("flush failure"); }
            }, 60, 2, 100000, 5000);
            try {
                track.offer(0, 0, 1, 1, 0, 0);
                track.finish();
                assertEquals("failed", track.status());
                assertEquals("IOException", status(track).get("failure").getAsString());
                assertNull(track.completedFile());
                assertFalse(track.accepting());
            } finally { track.close(); }
            assertFalse(Files.exists(spool));
        }
    }

    @Test void shutdownIsBoundedEvenWhenSinkIgnoresInterruptionAndCleanupIsDeferred() throws Exception {
        Path spool = temp.resolve("stalled.bin");
        BlockingSink sink = new BlockingSink(spool, false);
        ReplayCameraTrack track = new ReplayCameraTrack(spool, sink, 120, 2, 100000, 25);
        try {
            assertTrue(sink.entered.await(5, TimeUnit.SECONDS));
            assertTrue(track.offer(0, 0, 1, 1, 0, 0));
            assertTimeout(Duration.ofSeconds(1), track::finish);
            assertEquals("failed", track.status());
            assertEquals("SpoolTimeout", status(track).get("failure").getAsString());
            assertEquals(-1, track.lastTimestamp());
            assertNull(track.completedFile());
            assertFalse(track.accepting());
            assertTimeout(Duration.ofSeconds(1), track::close);
            assertTrue(Files.exists(spool));
        } finally {
            track.close();
            sink.release.countDown();
            assertTrue(sink.closed.await(5, TimeUnit.SECONDS));
            await(() -> !Files.exists(spool));
        }
    }

    @Test void closeWithoutFinishDeletesSpoolAndPreservesNeighborFiles() throws Exception {
        Path neighbor = Files.write(temp.resolve("keep.bin"), new byte[] { 1, 2, 3 });
        ReplayCameraTrack track = ReplayCameraTrack.create(temp, 60);
        track.offer(0, 0, 1, 1, 0, 0);
        track.close();
        await(() -> {
            try (java.util.stream.Stream<Path> files = Files.list(temp)) { return files.count() == 1; }
            catch (IOException error) { throw new AssertionError(error); }
        });
        assertArrayEquals(new byte[] { 1, 2, 3 }, Files.readAllBytes(neighbor));
        assertNull(track.completedFile());
        track.close();
    }

    @Test void emptyTrackAndUnavailableStatusAreExplicitAndInvalidRatesLeaveNoSpools() throws Exception {
        for (int hz : new int[] { 0, 20, 59, 61, 119, 121, 239, 241, 480, -1 })
            assertThrows(IllegalArgumentException.class, () -> ReplayCameraTrack.create(temp, hz));
        try (java.util.stream.Stream<Path> files = Files.list(temp)) { assertEquals(0, files.count()); }
        ReplayCameraTrack track = ReplayCameraTrack.create(temp, 240);
        try {
            track.finish();
            assertEquals(12, Files.size(track.completedFile()));
            assertEquals(-1, track.lastTimestamp());
            assertEquals("complete", track.status());
        } finally { track.close(); }
        JsonObject unsupported = new JsonParser().parse(ReplayCameraTrack.unavailableStatus("old-writer", false, 240)).getAsJsonObject();
        assertTrue(unsupported.get("requested").getAsBoolean());
        assertFalse(unsupported.get("supported").getAsBoolean());
        assertEquals(240, unsupported.get("hz").getAsInt());
        assertEquals("old-writer", unsupported.get("status").getAsString());
        JsonObject standard = new JsonParser().parse(ReplayCameraTrack.unavailableStatus("standard", true, 0)).getAsJsonObject();
        assertFalse(standard.get("requested").getAsBoolean());
        assertEquals(0, standard.get("hz").getAsInt());
        assertThrows(IllegalArgumentException.class, () -> ReplayCameraTrack.unavailableStatus("invalid", true, 241));
    }

    private static JsonObject status(ReplayCameraTrack track) {
        return new JsonParser().parse(track.statusJson()).getAsJsonObject();
    }

    private static int[][] records(byte[] bytes) {
        ByteBuffer input = ByteBuffer.wrap(bytes);
        assertEquals(0x5A43414D, input.getInt());
        assertEquals(1, input.getInt());
        int hz = input.getInt();
        assertTrue(hz == 60 || hz == 120 || hz == 240);
        assertEquals(0, input.remaining() % 24);
        int[][] result = new int[input.remaining() / 24][6];
        int last = -1;
        for (int[] record : result) {
            for (int field = 0; field < 6; field++) record[field] = input.getInt();
            assertTrue(record[0] > last);
            last = record[0];
        }
        return result;
    }

    private static int[] times(int[][] records) {
        int[] result = new int[records.length];
        for (int i = 0; i < records.length; i++) result[i] = records[i][0];
        return result;
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(1);
        assertTrue(condition.getAsBoolean(), "Timed out waiting for camera spool");
    }

    private static final class BlockingSink extends OutputStream {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), closed = new CountDownLatch(1);
        private final OutputStream delegate;
        private final boolean interruptible;
        private boolean first = true;
        BlockingSink(Path path, boolean interruptible) throws IOException {
            delegate = Files.newOutputStream(path);
            this.interruptible = interruptible;
        }
        @Override public void write(int value) throws IOException {
            if (first) {
                first = false;
                entered.countDown();
                boolean interrupted = false;
                for (;;) {
                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("test sink timeout");
                        break;
                    } catch (InterruptedException error) {
                        if (interruptible) { Thread.currentThread().interrupt(); throw new IOException(error); }
                        interrupted = true;
                    }
                }
                if (interrupted) Thread.currentThread().interrupt();
            }
            delegate.write(value);
        }
        @Override public void close() throws IOException {
            try { delegate.close(); } finally { closed.countDown(); }
        }
    }
}
