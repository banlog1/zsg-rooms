package zsgrooms.modid.replay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ReplayDetailedTrackTest {
    @TempDir Path temp;

    @Test void diskSpoolPreservesFullFramesAndRejectsDuplicateOrBackwardsTimes() throws Exception {
        Path spool = temp.resolve("detail.bin");
        ReplayDetailedTrack track = new ReplayDetailedTrack(spool, 4096, 32, 100000);
        try {
            byte[] state = ReplayDetailedTrack.unavailable(ReplayDetailedTrack.LOADING);
            assertTrue(track.offer(0, -1, -1, state));
            assertFalse(track.offer(0, -1, -1, state));
            assertFalse(track.offer(-1, -1, -1, state));
            assertTrue(track.offer(50, 1, 5, state));
            assertFalse(track.offer(49, 1, 5, state));
            track.finish();
            assertEquals(spool, track.completedFile());
            List<Frame> frames = frames(Files.readAllBytes(spool));
            assertEquals(2, frames.size());
            assertEquals(0, frames.get(0).time);
            assertEquals(50, frames.get(1).time);
            assertEquals(1, frames.get(1).world);
            assertEquals(5, frames.get(1).entity);
            assertArrayEquals(state, frames.get(1).payload);
            assertEquals("complete", track.status());
            assertFalse(track.accepting());
        } finally { track.close(); }
        assertFalse(Files.exists(spool));
    }

    @Test void capReservesATerminalGapAndNeverDisablesThePacketBuffer() throws Exception {
        byte[] state = ReplayDetailedTrack.unavailable(0);
        long cap = 8 + 3 * (16 + state.length);
        Path spool = temp.resolve("capped.bin");
        ReplayDetailedTrack track = new ReplayDetailedTrack(spool, 4096, 32, cap);
        ReplayBuffer packets = new ReplayBuffer();
        try {
            assertTrue(track.offer(0, 0, 1, state));
            assertTrue(track.offer(50, 0, 1, state));
            assertFalse(track.offer(100, 0, 1, state));
            assertFalse(track.accepting());
            assertFalse(track.offer(200, 0, 1, state));
            track.finish();
            assertEquals("capped", track.status());
            assertEquals(cap, Files.size(spool));
            List<Frame> frames = frames(Files.readAllBytes(spool));
            assertEquals(3, frames.size());
            assertEquals(100, frames.get(2).time);
            assertEquals(ReplayDetailedTrack.GAP, frames.get(2).payload[0]);
            assertTrue(packets.isOpen());
            ReplayBuffer.Record packet = packets.reserve(0, 1, 1);
            assertNotNull(packet);
            assertTrue(packets.commit(packet, 200));
        } finally { track.close(); packets.discardQueued(); }
    }

    @Test void queuePressureIsBoundedProducesAGapAndRecoversInTimestampOrder() throws Exception {
        BlockingSink sink = new BlockingSink();
        ReplayDetailedTrack track = new ReplayDetailedTrack(temp.resolve("bounded.bin"), sink, 4096, 2, 100000);
        try {
            assertTrue(sink.entered.await(5, TimeUnit.SECONDS));
            byte[] state = ReplayDetailedTrack.unavailable(0);
            assertTrue(track.offer(0, 0, 1, state));
            assertTrue(track.offer(50, 0, 1, state));
            assertFalse(track.offer(100, 0, 1, state));
            assertFalse(track.offer(150, 0, 1, state));
            assertTrue(track.accepting());
            sink.release.countDown();
            awaitFrames(track, 3);
            assertTrue(track.offer(200, 0, 1, state));
            track.finish();
            List<Frame> frames = frames(sink.bytes.toByteArray());
            assertEquals(4, frames.size());
            assertEquals(0, frames.get(0).time);
            assertEquals(50, frames.get(1).time);
            assertEquals(100, frames.get(2).time);
            assertEquals(ReplayDetailedTrack.GAP, frames.get(2).payload[0]);
            assertEquals(200, frames.get(3).time);
            JsonObject status = status(track);
            assertEquals("gaps", status.get("status").getAsString());
            assertEquals(1, status.get("gaps").getAsInt());
            assertTrue(status.get("peakQueuedFrames").getAsInt() <= 2);
            assertTrue(status.get("peakQueuedBytes").getAsInt() <= 4096);
        } finally { sink.release.countDown(); track.close(); }
    }

    @Test void multiDayTimestampsStayValidWithoutSessionStateRetainedInHeap() throws Exception {
        Path spool = temp.resolve("long.bin");
        ReplayDetailedTrack track = new ReplayDetailedTrack(spool, 4096, 32, 100000);
        try {
            byte[] state = ReplayDetailedTrack.unavailable(0);
            for (int hour = 0; hour <= 72; hour++) {
                assertTrue(track.offer(hour * 3600000L, 0, 1, state));
                awaitFrames(track, hour + 1);
            }
            track.finish();
            List<Frame> frames = frames(Files.readAllBytes(spool));
            assertEquals(73, frames.size());
            assertEquals(72 * 3600000, frames.get(72).time);
            assertEquals("complete", track.status());
            assertTrue(status(track).get("peakQueuedFrames").getAsInt() <= 32);
        } finally { track.close(); }
    }

    @Test void diskFailureReportsUnavailableWithoutPublishingATruncatedFile() throws Exception {
        ReplayDetailedTrack track = new ReplayDetailedTrack(temp.resolve("failed.bin"), new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("test full disk"); }
        }, 4096, 32, 100000);
        try {
            track.offer(0, 0, 1, ReplayDetailedTrack.unavailable(0));
            track.finish();
            assertNull(track.completedFile());
            assertFalse(track.accepting());
            assertEquals("failed", track.status());
            assertEquals("IOException", status(track).get("failure").getAsString());
        } finally { track.close(); }
    }

    @Test void unavailablePayloadMatchesTheConsumerContractExactly() throws Exception {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(ReplayDetailedTrack.unavailable(6)));
        assertEquals(6, input.readUnsignedByte());
        assertEquals(0, input.readInt()); // Render distance.
        assertEquals(0, input.readUnsignedByte());
        assertFalse(input.readBoolean());
        assertFalse(input.readBoolean());
        assertEquals(0, input.readFloat());
        assertEquals(0, input.readFloat());
        assertEquals(0, input.readUnsignedByte());
        assertFalse(input.readBoolean());
        assertEquals(0, input.readInt()); // HUD length.
        assertEquals(0, input.readUnsignedByte());
        assertEquals(0, input.readInt());
        assertEquals(-1, input.readInt());
        assertEquals(0, input.readUnsignedByte()); // Empty string VarInt length.
        assertEquals(0, input.readUnsignedShort());
        assertFalse(input.readBoolean()); // Empty ItemStack.
        assertEquals(-1, input.read());
    }

    private static void awaitFrames(ReplayDetailedTrack track, int count) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (status(track).get("frames").getAsInt() < count && System.nanoTime() < deadline) Thread.sleep(1);
        assertEquals(count, status(track).get("frames").getAsInt());
    }

    private static JsonObject status(ReplayDetailedTrack track) {
        return new JsonParser().parse(track.statusJson()).getAsJsonObject();
    }

    private static List<Frame> frames(byte[] bytes) throws Exception {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
        assertEquals(ReplayDetailedTrack.MAGIC, input.readInt());
        assertEquals(1, input.readInt());
        List<Frame> frames = new ArrayList<>();
        while (input.available() > 0) {
            int time = input.readInt(), world = input.readInt(), entity = input.readInt(), length = input.readInt();
            byte[] payload = new byte[length];
            input.readFully(payload);
            if (!frames.isEmpty()) assertTrue(time > frames.get(frames.size() - 1).time);
            frames.add(new Frame(time, world, entity, payload));
        }
        return frames;
    }

    private static final class Frame {
        final int time, world, entity;
        final byte[] payload;
        Frame(int time, int world, int entity, byte[] payload) {
            this.time = time; this.world = world; this.entity = entity; this.payload = payload;
        }
    }

    private static final class BlockingSink extends OutputStream {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        boolean first = true;
        @Override public void write(int value) throws IOException {
            if (first) {
                first = false;
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("test sink timeout");
                } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
            }
            bytes.write(value);
        }
    }
}
