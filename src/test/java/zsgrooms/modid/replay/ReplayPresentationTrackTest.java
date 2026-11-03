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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ReplayPresentationTrackTest {
    @TempDir Path directory;

    @Test void completeStatesPreserveTheVersionedEnvelopeAndTimestampRange() throws Exception {
        ReplayPresentationTrack track = ReplayPresentationTrack.create(directory);
        Path spool;
        try {
            byte[] first = payload(1), second = payload(17);
            assertTrue(track.offer(0, 0, 7, first));
            assertTrue(track.offer(Integer.MAX_VALUE, 3, 9, second));
            track.finish();
            spool = track.completedFile();
            ByteBuffer expected = ByteBuffer.allocate(8 + 32 + first.length + second.length);
            expected.putInt(0x5A505253).putInt(1);
            expected.putInt(0).putInt(0).putInt(7).putInt(first.length).put(first);
            expected.putInt(Integer.MAX_VALUE).putInt(3).putInt(9).putInt(second.length).put(second);
            assertArrayEquals(expected.array(), Files.readAllBytes(spool));
            assertEquals(Integer.MAX_VALUE, track.lastTimestamp());
            assertEquals("complete", track.status());
            assertEquals(2, status(track).get("frames").getAsInt());
            assertEquals(60, status(track).get("maxHz").getAsInt());
            assertFalse(track.accepting());
        } finally { track.close(); }
        assertFalse(Files.exists(spool));
        assertNull(track.completedFile());
    }

    @Test void invalidInputAndOversizedOptionalFrameDoNotClosePacketRecording() throws Exception {
        ReplayPresentationTrack track = ReplayPresentationTrack.create(directory);
        ReplayBuffer packets = new ReplayBuffer();
        try {
            assertFalse(track.offer(-1, 0, 1, payload(1)));
            assertFalse(track.offer(0, -2, 1, payload(1)));
            assertFalse(track.offer(0, 0, 1, null));
            assertTrue(track.offer(0, 0, 1, payload(1)));
            assertFalse(track.offer(0, 0, 1, payload(1)));
            assertFalse(track.offer(1, 0, 1, new byte[ReplayPresentationTrack.MAX_FRAME + 1]));
            assertTrue(track.accepting());
            track.finish();
            assertEquals(1, status(track).get("gaps").getAsInt());
            assertTrue(packets.isOpen());
            ReplayBuffer.Record packet = packets.reserve(0, 1, 1);
            assertNotNull(packet); assertTrue(packets.commit(packet, 2));
        } finally { track.close(); packets.discardQueued(); }
    }

    @Test void pressureStaysBoundedWritesOneGapAndRecoversInOrder() throws Exception {
        BlockingSink sink = new BlockingSink();
        ReplayPresentationTrack track = new ReplayPresentationTrack(directory.resolve("pressure"), sink, 200, 2, 100000);
        try {
            assertTrue(sink.entered.await(5, TimeUnit.SECONDS));
            assertTrue(track.offer(0, 0, 1, payload(1)));
            assertTrue(track.offer(16, 0, 1, payload(2)));
            for (int time = 32; time < 10000; time += 16) assertFalse(track.offer(time, 0, 1, payload(3)));
            assertTrue(status(track).get("peakQueuedBytes").getAsInt() <= 200);
            assertEquals(2, status(track).get("peakQueuedFrames").getAsInt());
            sink.release.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (track.lastTimestamp() < 32 && System.nanoTime() < deadline) Thread.sleep(1);
            assertEquals(32, track.lastTimestamp());
            assertTrue(track.offer(10000, 0, 1, payload(4)));
            track.finish();
            assertEquals(java.util.Arrays.asList(0, 16, 32, 10000), times(sink.bytes.toByteArray()));
            assertEquals(1, status(track).get("gaps").getAsInt());
            assertEquals("gaps", track.status());
        } finally { sink.release.countDown(); track.close(); }
    }

    @Test void capReservesTerminalGapAndPublishesOnlyACompleteFile() throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        byte[] payload = payload(1);
        int cap = 8 + 16 + payload.length + 20;
        ReplayPresentationTrack track = new ReplayPresentationTrack(directory.resolve("cap"), sink, 1024, 8, cap);
        try {
            assertTrue(track.offer(0, 0, 7, payload));
            assertFalse(track.offer(16, 0, 7, payload));
            track.finish();
            assertEquals(cap, sink.size());
            assertEquals("capped", track.status());
            assertEquals(java.util.Arrays.asList(0, 16), times(sink.toByteArray()));
            assertEquals(1, status(track).get("gaps").getAsInt());
        } finally { track.close(); }
    }

    @Test void emptyTrackHasAValidHeaderAndDiskFailuresStayOptional() throws Exception {
        ReplayPresentationTrack empty = ReplayPresentationTrack.create(directory);
        try { empty.finish(); assertEquals(8, Files.size(empty.completedFile())); }
        finally { empty.close(); }
        ReplayPresentationTrack failed = new ReplayPresentationTrack(directory.resolve("failure"), new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("disk full"); }
        }, 1024, 8, 10000);
        try {
            failed.finish(); assertEquals("failed", failed.status());
            assertNull(failed.completedFile()); assertFalse(failed.accepting());
        } finally { failed.close(); }
    }

    private static JsonObject status(ReplayPresentationTrack track) {
        return new JsonParser().parse(track.statusJson()).getAsJsonObject();
    }
    private static byte[] payload(int value) { return ByteBuffer.allocate(8).putInt(1).putInt(value).array(); }
    private static List<Integer> times(byte[] bytes) {
        ByteBuffer data = ByteBuffer.wrap(bytes);
        assertEquals(ReplayPresentationTrack.MAGIC, data.getInt()); assertEquals(1, data.getInt());
        List<Integer> times = new ArrayList<>();
        while (data.hasRemaining()) {
            times.add(data.getInt()); data.getInt(); data.getInt(); int length = data.getInt(); data.position(data.position() + length);
        }
        return times;
    }
    private static final class BlockingSink extends OutputStream {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        boolean first = true;
        @Override public void write(int value) throws IOException {
            if (first) {
                first = false; entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("test timeout"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
            }
            bytes.write(value);
        }
    }
}
