package zsgrooms.modid.replay;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import static org.junit.jupiter.api.Assertions.*;

class ReplayHudTrackTest {
    @Test void reusedScratchMatchesBaselineAcrossChangesHeartbeatsAndResets() throws Exception {
        ReplayHudTrack baseline = new ReplayHudTrack(), optimized = new ReplayHudTrack();
        io.netty.buffer.ByteBuf buffer = ReplayEncodingBuffers.acquireHud();
        java.util.Random random = new java.util.Random(20260921L);
        byte[] payload = new byte[100];
        try {
            for (int time = 0; time < 300000; time += 50) {
                int world = time / 30000, entity = time % 30000 < 500 ? -1 : world + 1;
                if (time % 1700 == 0) random.nextBytes(payload);
                byte[] state = entity == -1 ? new byte[0] : payload.clone();
                baseline.add(time, world, entity, state);
                buffer.clear();
                buffer.writeInt(123); // A nonzero reader index must not become part of the frame.
                buffer.writeBytes(state);
                buffer.readerIndex(4);
                byte[] owned = new byte[buffer.readableBytes()];
                buffer.getBytes(buffer.readerIndex(), owned);
                optimized.add(time, world, entity, owned);
                assertEquals(4, buffer.readerIndex());
                buffer.setZero(0, buffer.writerIndex()); // Stored frames must own their data.
                assertEquals(baseline.due(time + 1, world, entity), optimized.due(time + 1, world, entity));
            }
            assertArrayEquals(baseline.finish(250000), optimized.finish(250000));
        } finally { buffer.release(); }
    }

    @Test void changedStatesAreFiveHzAndUnchangedStatesHaveOneSecondHeartbeats() throws Exception {
        ReplayHudTrack track = new ReplayHudTrack();
        track.add(0, 0, 5, new byte[]{1});
        assertFalse(track.due(199, 0, 5));
        track.add(200, 0, 5, new byte[]{1});
        assertFalse(track.due(201, 0, 5));
        track.add(1000, 0, 5, new byte[]{1});
        track.add(1200, 0, 5, new byte[]{2});
        DataInputStream data = data(track.finish(2000));
        assertEquals(0, frame(data, 0, 5, new byte[]{1}));
        assertEquals(1000, frame(data, 0, 5, new byte[]{1}));
        assertEquals(1200, frame(data, 0, 5, new byte[]{2}));
        assertEquals(0, data.available());
        assertFalse(track.due(3000, 0, 5));
    }

    @Test void resetAndUnavailableStatesBypassNormalRateLimitAndFinishClampsDuration() throws Exception {
        ReplayHudTrack track = new ReplayHudTrack();
        track.add(50, 0, 1, new byte[]{1});
        track.add(51, 0, -1, new byte[0]);
        track.add(52, 1, 1, new byte[]{2});
        track.add(53, 2, 1, new byte[]{3});
        DataInputStream data = data(track.finish(52));
        assertEquals(50, frame(data, 0, 1, new byte[]{1}));
        assertEquals(51, frame(data, 0, -1, new byte[0]));
        assertEquals(52, frame(data, 1, 1, new byte[]{2}));
        assertEquals(0, data.available());
    }

    @Test void captureBudgetStopsOnlyOptionalTrack() throws Exception {
        ReplayHudTrack track = new ReplayHudTrack();
        track.add(0, 0, 1, new byte[]{1});
        track.add(200, 0, 1, new byte[ReplayHudTrack.MAX_FRAME + 1]);
        assertFalse(track.due(1000, 0, 1));
        assertEquals(25, track.finish(1000).length);
    }

    private static DataInputStream data(byte[] bytes) throws Exception {
        DataInputStream data = new DataInputStream(new ByteArrayInputStream(bytes));
        assertEquals(0x5A485544, data.readInt());
        assertEquals(1, data.readInt());
        return data;
    }
    private static int frame(DataInputStream data, int world, int entity, byte[] payload) throws Exception {
        int time = data.readInt();
        assertEquals(world, data.readInt());
        assertEquals(entity, data.readInt());
        assertEquals(payload.length, data.readInt());
        byte[] actual = new byte[payload.length];
        data.readFully(actual);
        assertArrayEquals(payload, actual);
        return time;
    }
}
