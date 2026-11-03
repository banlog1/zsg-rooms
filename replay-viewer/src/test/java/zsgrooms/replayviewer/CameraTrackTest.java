// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CameraTrackTest {
    @Test void allRatesKeepFullPrecisionAndInterpolateWithinATick() throws Exception {
        for (int hz : new int[]{60, 120, 240}) {
            try (CameraTrack track = read(hz, frame(0, 1.125F, -20.25F), frame(16, 9.375F, 30.5F))) {
                assertEquals(hz, track.hz);
                assertEquals(2, track.frameCount());
                assertRotation(track, 0, 1.125F, -20.25F);
                assertRotation(track, 4, 3.1875F, -7.5625F);
                assertRotation(track, 8, 5.25F, 5.125F);
                assertRotation(track, 16, 9.375F, 30.5F);
            }
        }
    }

    @Test void yawTakesTheShortestArcAndPitchRemainsBounded() throws Exception {
        try (CameraTrack track = read(120, frame(0, 179, -90), frame(16, -179, 90), frame(32, 179, -90))) {
            assertRotation(track, 8, 180, 0);
            assertRotation(track, 24, -180, 0);
            assertRotation(track, 16, -179, 90);
        }
        try (CameraTrack track = read(60, frame(0, 359, 10), frame(16, 1, 20))) {
            assertRotation(track, 8, 360, 15);
        }
    }

    @Test void neverExtrapolatesBeforeAfterOrOutsideCoverage() throws Exception {
        try (CameraTrack track = read(120, frame(20, 0, 0), frame(36, 16, 16))) {
            float[] result = {77, 88};
            for (double time : new double[]{-1, 0, 19.99, 36.01, 100, Double.NaN, Double.POSITIVE_INFINITY}) {
                assertFalse(track.sample(time, 0, 7, 0, 1000, result));
                assertArrayEquals(new float[]{77, 88}, result);
            }
            assertRotation(track, 20, 0, 0);
            assertRotation(track, 36, 16, 16);
            assertFalse(track.sample(28, 0, 7, 21, 1000, result));
            assertFalse(track.sample(28, 0, 7, 0, 36, result));
            assertFalse(track.sample(36, 0, 7, 0, 36, result));
        }
    }

    @Test void missingSamplesAndExplicitGapsDoNotBridge() throws Exception {
        for (int gapFlags : new int[]{0, CameraTrack.GAP}) {
            try (CameraTrack track = read(120, frame(0, 0, 0),
                    frame(16, 0, 7, gapFlags, 0, 0), frame(32, 80, 10), frame(48, 96, 20))) {
                float[] result = new float[2];
                assertRotation(track, 0, 0, 0);
                for (int time : new int[]{1, 15, 16, 17, 31}) assertFalse(track.sample(time, 0, 7, 0, 1000, result));
                assertRotation(track, 32, 80, 10);
                assertRotation(track, 40, 88, 15);
            }
        }
    }

    @Test void maximumGapIsOneHundredMillisInclusive() throws Exception {
        try (CameraTrack track = read(60, frame(0, 0, 0), frame(100, 100, 20), frame(201, 150, 40))) {
            assertRotation(track, 50, 50, 10);
            assertFalse(track.sample(150, 0, 7, 0, 1000, new float[2]));
            assertRotation(track, 201, 150, 40);
        }
    }

    @Test void worldAndEntityBoundariesCannotReuseAnOldPose() throws Exception {
        try (CameraTrack track = read(120, frame(0, 0, 0), frame(16, 16, 0),
                frame(32, 1, 7, CameraTrack.AVAILABLE, 32, 0),
                frame(48, 1, 8, CameraTrack.AVAILABLE, 48, 0))) {
            float[] result = new float[2];
            assertFalse(track.sample(24, 0, 7, 0, 1000, result));
            assertFalse(track.sample(24, 1, 7, 0, 1000, result));
            assertTrue(track.sample(32, 1, 7, 32, 1000, result));
            assertEquals(32F, result[0]);
            assertFalse(track.sample(40, 1, 7, 32, 1000, result));
            assertFalse(track.sample(48, 1, 7, 32, 1000, result));
            assertTrue(track.sample(48, 1, 8, 32, 1000, result));
            assertRotation(track, 8, 8, 0);
        }
    }

    @Test void equalTimestampsAreRejectedIncludingBoundaryMarkers() {
        assertThrows(IOException.class, () -> read(120, frame(0, 0, 0), frame(0, 10, 20)));
        assertThrows(IOException.class, () -> read(60, frame(16, 16, 0), frame(16, -1, -1, CameraTrack.GAP, 0, 0)));
    }

    @Test void forwardBackwardAndRandomSeeksCrossDiskPagesWithoutHistory() throws Exception {
        Frame[] frames = new Frame[4099];
        for (int i = 0; i < frames.length; i++) frames[i] = frame(i * 8, i % 120, i % 80);
        try (CameraTrack track = read(120, frames)) {
            java.util.Random random = new java.util.Random(9271);
            for (int pass = 0; pass < 3; pass++) {
                for (int i = 0; i < frames.length - 1; i++) {
                    int slot = pass == 0 ? i : pass == 1 ? frames.length - 2 - i : random.nextInt(frames.length - 1);
                    Frame a = frames[slot], b = frames[slot + 1];
                    assertRotation(track, a.time + 2, a.yaw + (b.yaw - a.yaw) * .25F,
                            a.pitch + (b.pitch - a.pitch) * .25F);
                    assertRotation(track, a.time, a.yaw, a.pitch);
                }
            }
        }
    }

    @Test void closedTrackDropsCachedResultsAndDeletesItsSpool() throws Exception {
        CameraTrack track = read(60, frame(0, 0, 0), frame(16, 16, 16));
        java.lang.reflect.Field field = CameraTrack.class.getDeclaredField("path");
        field.setAccessible(true);
        Path path = (Path) field.get(track);
        assertTrue(Files.exists(path));
        assertRotation(track, 8, 8, 8);
        track.close();
        track.close();
        assertFalse(Files.exists(path));
        assertFalse(track.sample(8, 0, 7, 0, 1000, new float[2]));
    }

    @Test void malformedHeadersSamplesAndTruncationFailClosed() throws Exception {
        byte[] valid = bytes(120, frame(0, 0, 0), frame(16, 16, 16));
        for (int length = 0; length < valid.length; length++) {
            if (length >= 12 && (length - 12) % CameraTrack.RECORD_BYTES == 0) continue;
            byte[] truncated = java.util.Arrays.copyOf(valid, length);
            assertThrows(IOException.class, () -> CameraTrack.read(new ByteArrayInputStream(truncated), 1000));
        }
        for (int hz : new int[]{0, 20, 61, 480}) {
            assertThrows(IOException.class, () -> read(hz, frame(0, 0, 0)));
        }
        for (Frame frame : new Frame[]{frame(-1, 0, 0), frame(100001, 0, 0),
                frame(0, -1, 7, 1, 0, 0), frame(0, 0, -1, 1, 0, 0), frame(0, 0, 7, 4, 0, 0),
                frame(0, 0, 7, 3, 0, 0), frame(0, -1, -1, 0, Float.NaN, 0),
                frame(0, -1, -1, CameraTrack.GAP, 0, Float.NaN),
                frame(0, 0, 7, 1, Float.NaN, 0), frame(0, 0, 7, 1, Float.POSITIVE_INFINITY, 0),
                frame(0, 0, 7, 1, 0, Float.NaN), frame(0, 0, 7, 1, 0, 90.01F), frame(0, 0, 7, 1, 0, -90.01F)}) {
            assertThrows(IOException.class, () -> read(120, frame));
        }
        assertThrows(IOException.class, () -> read(120, frame(16, 0, 0), frame(0, 0, 0)));
        for (int offset : new int[]{0, 4}) {
            byte[] invalid = valid.clone();
            invalid[offset] ^= 1;
            assertThrows(IOException.class, () -> CameraTrack.read(new ByteArrayInputStream(invalid), 1000));
        }
    }

    @Test void emptyAndUnavailableTracksAreOrdinaryFallbacks() throws Exception {
        try (CameraTrack track = read(60)) {
            assertEquals(0, track.frameCount());
            assertFalse(track.sample(0, 0, 7, 0, 1000, new float[2]));
        }
        try (CameraTrack track = read(120, frame(0, -1, -1, 0, 0, 0))) {
            assertFalse(track.sample(0, 0, 7, 0, 1000, new float[2]));
        }
    }

    private static void assertRotation(CameraTrack track, double time, float yaw, float pitch) {
        float[] rotation = new float[2];
        assertTrue(track.sample(time, 0, 7, 0, 100000, rotation), "Missing sample at " + time);
        assertEquals(yaw, rotation[0], .00001F);
        assertEquals(pitch, rotation[1], .00001F);
    }

    private static CameraTrack read(int hz, Frame... frames) throws IOException {
        return CameraTrack.read(new ByteArrayInputStream(bytes(hz, frames)), 100000);
    }

    private static byte[] bytes(int hz, Frame... frames) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(CameraTrack.MAGIC); out.writeInt(1); out.writeInt(hz);
        for (Frame frame : frames) {
            out.writeInt(frame.time); out.writeInt(frame.world); out.writeInt(frame.entity); out.writeInt(frame.flags);
            out.writeFloat(frame.yaw); out.writeFloat(frame.pitch);
        }
        return bytes.toByteArray();
    }

    private static Frame frame(int time, float yaw, float pitch) { return frame(time, 0, 7, CameraTrack.AVAILABLE, yaw, pitch); }
    private static Frame frame(int time, int world, int entity, int flags, float yaw, float pitch) {
        return new Frame(time, world, entity, flags, yaw, pitch);
    }

    private static final class Frame {
        final int time, world, entity, flags;
        final float yaw, pitch;
        Frame(int time, int world, int entity, int flags, float yaw, float pitch) {
            this.time = time; this.world = world; this.entity = entity; this.flags = flags;
            this.yaw = yaw; this.pitch = pitch;
        }
    }
}
