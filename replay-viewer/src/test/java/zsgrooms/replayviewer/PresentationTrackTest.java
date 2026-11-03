// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PresentationTrackTest {
    @Test void completeFramesResolveDirectlyAfterForwardBackwardAndRandomSeeks() throws Exception {
        Frame[] frames = new Frame[1100];
        for (int i = 0; i < frames.length; i++) frames[i] = frame(i * 16, 0, 7, payload(70 + i % 40));
        try (PresentationTrack track = read(frames)) {
            assertEquals(frames.length, track.frameCount());
            java.util.Random random = new java.util.Random(33);
            for (int pass = 0; pass < 3; pass++) {
                for (int i = 0; i < frames.length - 1; i++) {
                    int slot = pass == 0 ? i : pass == 1 ? frames.length - 2 - i : random.nextInt(frames.length - 1);
                    PresentationTrack.Pair pair = track.at(frames[slot].time + 4, 0, 7, 0, 100000);
                    assertNotNull(pair);
                    assertEquals(frames[slot].time, pair.before.time);
                    assertArrayEquals(frames[slot].payload, pair.before.payload);
                    assertArrayEquals(frames[slot + 1].payload, pair.after.payload);
                    assertEquals(.25, pair.fraction);
                }
            }
        }
    }

    @Test void fullStateCacheIsReusedInsideASampleInterval() throws Exception {
        try (PresentationTrack track = read(frame(0, 0, 7, payload(70)), frame(16, 0, 7, payload(80)))) {
            PresentationTrack.Pair pair = track.at(1, 0, 7, 0, 100000);
            PresentationTrack.Frame before = pair.before, after = pair.after;
            for (int i = 2; i < 16; i++) {
                assertSame(pair, track.at(i, 0, 7, 0, 100000));
                assertSame(before, pair.before); assertSame(after, pair.after);
            }
        }
    }

    @Test void gapsIdentityBoundariesAndIntervalEdgesNeverBlend() throws Exception {
        byte[] gap = ByteBuffer.allocate(4).putInt(PresentationTrack.GAP).array();
        try (PresentationTrack track = read(frame(0, 0, 7, payload(70)), frame(16, 0, 7, gap),
                frame(32, 0, 7, payload(80)), frame(48, 1, 7, payload(90)), frame(64, 1, 8, payload(100)))) {
            assertNotNull(track.at(0, 0, 7, 0, 100000));
            for (int time : new int[]{1, 15, 16, 17, 31, 40}) assertNull(track.at(time, 0, 7, 0, 100000));
            assertNotNull(track.at(32, 0, 7, 32, 100000));
            assertNull(track.at(32, 0, 7, 33, 100000));
            assertNull(track.at(48, 1, 7, 0, 48));
            assertNull(track.at(56, 1, 7, 0, 100000));
            assertNotNull(track.at(64, 1, 8, 64, 100000));
        }
    }

    @Test void doesNotExtrapolateAcrossMissingTimeOrOutsideTrack() throws Exception {
        try (PresentationTrack track = read(frame(20, 0, 7, payload(70)), frame(120, 0, 7, payload(80)),
                frame(221, 0, 7, payload(90)))) {
            assertNotNull(track.at(70, 0, 7, 0, 100000));
            for (double time : new double[]{-1, 19.99, 150, 221.01, Double.NaN, Double.POSITIVE_INFINITY})
                assertNull(track.at(time, 0, 7, 0, 100000));
            assertNotNull(track.at(221, 0, 7, 0, 100000));
        }
    }

    @Test void validatesFlagsFovEveryScalarAndFrameEnvelope() throws Exception {
        for (int flags : new int[]{-1, 0, 3, 2048}) {
            byte[] invalid = payload(70); ByteBuffer.wrap(invalid).putInt(flags);
            assertThrows(IOException.class, () -> read(frame(0, 0, 7, invalid)));
        }
        for (double fov : new double[]{0, -1, 360, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IOException.class, () -> read(frame(0, 0, 7, payload(fov))));
        for (int offset = 20; offset < 80; offset += 4) {
            byte[] invalid = payload(70); ByteBuffer.wrap(invalid).putFloat(offset, Float.NaN);
            assertThrows(IOException.class, () -> read(frame(0, 0, 7, invalid)));
        }
        for (Frame invalid : new Frame[]{frame(-1, 0, 7, payload(70)), frame(100001, 0, 7, payload(70)),
                frame(0, -1, 7, payload(70)), frame(0, 0, -1, payload(70)), frame(0, 0, 7, new byte[3]),
                frame(0, 0, 7, new byte[65537])}) assertThrows(IOException.class, () -> read(invalid));
        assertThrows(IOException.class, () -> read(frame(0, 0, 7, payload(70)), frame(0, 0, 7, payload(80))));
        byte[] valid = bytes(frame(0, 0, 7, payload(70)));
        for (int length = 0; length < valid.length; length++) {
            if (length == 8) continue;
            byte[] truncated = Arrays.copyOf(valid, length);
            assertThrows(IOException.class, () -> PresentationTrack.read(new ByteArrayInputStream(truncated), 100000));
        }
    }

    @Test void emptyAndClosedTracksAreOrdinaryFallbacksAndSpoolsAreDeleted() throws Exception {
        try (PresentationTrack empty = read()) { assertEquals(0, empty.frameCount()); assertNull(empty.at(0, 0, 7, 0, 100)); }
        PresentationTrack track = read(frame(0, 0, 7, payload(70)));
        java.lang.reflect.Field data = PresentationTrack.class.getDeclaredField("dataPath"), index = PresentationTrack.class.getDeclaredField("indexPath");
        data.setAccessible(true); index.setAccessible(true);
        Path path = (Path) data.get(track), indexPath = (Path) index.get(track);
        assertTrue(Files.exists(path)); assertTrue(Files.exists(indexPath));
        track.close(); track.close();
        assertFalse(Files.exists(path)); assertFalse(Files.exists(indexPath));
        assertNull(track.at(0, 0, 7, 0, 100));
    }

    static byte[] payload(double fov) {
        ByteBuffer bytes = ByteBuffer.allocate(83);
        bytes.putInt(PresentationTrack.AVAILABLE | PresentationTrack.BOB | PresentationTrack.HANDS
                | PresentationTrack.SHOW_MAIN | PresentationTrack.SHOW_OFF);
        bytes.putDouble(fov).putDouble(70);
        bytes.putFloat(1).putFloat(0).putFloat(0).putFloat(1).putFloat(0).putFloat(0);
        bytes.putFloat(-.25F).putFloat(.1F).putFloat(20).putFloat(1).putFloat(2);
        bytes.putFloat(.25F).putFloat(.3F).putFloat(.4F).putFloat(30.75F);
        bytes.put((byte) 0).put((byte) 0).put((byte) 0);
        return bytes.array();
    }
    private static PresentationTrack read(Frame... frames) throws IOException {
        return PresentationTrack.read(new ByteArrayInputStream(bytes(frames)), 100000);
    }
    private static byte[] bytes(Frame... frames) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(PresentationTrack.MAGIC); out.writeInt(1);
        for (Frame frame : frames) {
            out.writeInt(frame.time); out.writeInt(frame.world); out.writeInt(frame.entity); out.writeInt(frame.payload.length); out.write(frame.payload);
        }
        return bytes.toByteArray();
    }
    private static Frame frame(int time, int world, int entity, byte[] payload) { return new Frame(time, world, entity, payload); }
    private static final class Frame {
        final int time, world, entity; final byte[] payload;
        Frame(int time, int world, int entity, byte[] payload) { this.time = time; this.world = world; this.entity = entity; this.payload = payload; }
    }
}
