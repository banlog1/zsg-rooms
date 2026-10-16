// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class DetailedTrackTest {
    @Test void renderDistanceReadsOnlyValidAvailableHeaders() {
        for (int distance : new int[]{2, 8, 16, 32, 128})
            assertEquals(distance, distanceFrame(1, distance).renderDistance);
        for (int distance : new int[]{Integer.MIN_VALUE, -1, 0, 1, 129, Integer.MAX_VALUE})
            assertEquals(-1, distanceFrame(1, distance).renderDistance);
        for (int flags : new int[]{0, 2, 3, 4, 5, 6, 7, 255})
            assertEquals(-1, distanceFrame(flags, 16).renderDistance);
        for (int size = 0; size < 5; size++)
            assertEquals(-1, new DetailedTrack.Frame(0, 0, 1, new byte[size]).renderDistance);
    }

    @Test void renderDistanceTracksForwardAndBackwardSeeksAndGaps() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(DetailedTrack.MAGIC); out.writeInt(1);
        for (int i = 0; i < 3; i++) {
            byte[] payload = distanceFrame(i == 2 ? 0 : 1, i == 0 ? 8 : 24).payload;
            out.writeInt(i * 500); out.writeInt(0); out.writeInt(1);
            out.writeInt(payload.length); out.write(payload);
        }
        try (DetailedTrack track = DetailedTrack.read(new ByteArrayInputStream(bytes.toByteArray()), 3000)) {
            assertEquals(8, track.at(499, 0, 1, 0).renderDistance);
            assertEquals(24, track.at(500, 0, 1, 0).renderDistance);
            assertEquals(-1, track.at(1000, 0, 1, 0).renderDistance);
            assertEquals(8, track.at(0, 0, 1, 0).renderDistance);
            assertNull(track.at(0, 1, 1, 0));
            assertNull(track.at(0, 0, 2, 0));
            assertNull(track.at(2600, 0, 1, 0));
        }
    }

    private static DetailedTrack.Frame distanceFrame(int flags, int distance) {
        byte[] payload = java.nio.ByteBuffer.allocate(5).put((byte) flags).putInt(distance).array();
        return new DetailedTrack.Frame(0, 0, 1, payload);
    }

    @Test void pastOnlyFreshAndSameWorldEntity() throws Exception {
        try (DetailedTrack track = read(new int[][]{{100, 0, 4, 1}, {500, 0, 4, 0}, {1000, 1, 4, 1}})) {
            assertNull(track.at(0, 0, 4, 0));
            assertEquals(100, track.at(400, 0, 4, 0).time);
            assertNull(track.at(500, 0, 4, 0));
            assertNull(track.at(1001, 0, 4, 0));
            assertNull(track.at(1001, 1, 5, 0));
            assertNull(track.at(1001, 1, 4, 1001));
            assertEquals(1000, track.at(1001, 1, 4, 1000).time);
            assertNull(track.at(2501, 1, 4, 1000));
            assertEquals(100, track.at(101, 0, 4, 0).time);
        }
    }

    @Test void manyHoursStaySeekableAcrossDiskIndexPages() throws Exception {
        int[][] frames = new int[90000][4];
        for (int i = 0; i < frames.length; i++) frames[i] = new int[]{i * 1000, 0, 7, 1};
        try (DetailedTrack track = read(frames)) {
            assertEquals(90000, track.frameCount());
            for (int i : new int[]{89999, 300, 0, 16385, 255, 256, 75000}) {
                assertEquals(i * 1000, track.at(i * 1000 + 100, 0, 7, 0).time);
            }
        }
    }

    @Test void malformedTruncatedAndTooLargeFramesFailClosed() {
        assertThrows(IOException.class, () -> read(new int[][]{{2,0,1,1}, {1,0,1,1}}));
        assertThrows(IOException.class, () -> read(new int[][]{{1,0,1,1}, {1,0,1,1}}));
        assertThrows(IOException.class, () -> read(new int[][]{{1,0,1,65537}}));
        assertThrows(IOException.class, () -> DetailedTrack.read(new ByteArrayInputStream(new byte[8]), 1000));
        assertThrows(IOException.class, () -> DetailedTrack.read(new ByteArrayInputStream(bytes(new int[][]{{1,0,1,1}}, false)), 1000));
    }

    @Test void closedTrackCannotReturnCachedState() throws Exception {
        DetailedTrack track = read(new int[][]{{1,0,1,1}});
        assertNotNull(track.at(1, 0, 1, 0));
        track.close();
        assertNull(track.at(1, 0, 1, 0));
        track.close();
    }

    private static DetailedTrack read(int[][] frames) throws IOException {
        return DetailedTrack.read(new ByteArrayInputStream(bytes(frames, true)), Integer.MAX_VALUE);
    }
    private static byte[] bytes(int[][] frames, boolean payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(DetailedTrack.MAGIC); out.writeInt(1);
        for (int[] frame : frames) {
            for (int field : frame) out.writeInt(field);
            if (payload) out.write(new byte[frame[3]]);
        }
        return bytes.toByteArray();
    }
}
