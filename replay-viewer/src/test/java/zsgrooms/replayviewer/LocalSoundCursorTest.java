// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalSoundCursorTest {
    @Test void normalForwardPlaybackUsesOriginalTimesAndDoesNotRepeatOnRepeatedFrames() throws Exception {
        try (LocalSoundTrack track = LocalSoundTrackTest.read(LocalSoundTrackTest.normal())) {
            LocalSoundCursor cursor = new LocalSoundCursor(); List<Integer> heard = new ArrayList<>();
            cursor.frame(track, 0, 0, 42, false, false, e -> heard.add(e.time));
            assertTrue(cursor.covers(1, 42)); assertFalse(cursor.covers(1, 7));
            cursor.frame(track, 49, 0, 42, false, false, e -> heard.add(e.time)); assertTrue(heard.isEmpty());
            cursor.frame(track, 100, 0, 42, false, false, e -> heard.add(e.time));
            cursor.frame(track, 100, 0, 42, false, false, e -> heard.add(e.time));
            cursor.frame(track, 150, 0, 42, false, false, e -> heard.add(e.time));
            assertEquals(java.util.Arrays.asList(50, 100, 150), heard);
        }
    }

    @Test void pauseNeverEmitsAndResumeStartsAtPausedPositionWithoutSkippingNextSounds() throws Exception {
        try (LocalSoundTrack track = LocalSoundTrackTest.read(LocalSoundTrackTest.normal())) {
            LocalSoundCursor cursor = new LocalSoundCursor(); List<Integer> heard = new ArrayList<>();
            cursor.frame(track, 0, 0, 42, false, false, e -> heard.add(e.time));
            for (int i = 0; i < 5; i++) cursor.frame(track, 25, 0, 42, true, false, e -> heard.add(e.time));
            assertTrue(heard.isEmpty());
            cursor.frame(track, 50, 0, 42, false, false, e -> heard.add(e.time));
            assertEquals(java.util.Arrays.asList(50), heard);
        }
    }

    @Test void forwardBackwardAndQuickCatchupSeeksAreStrictlySilent() throws Exception {
        try (LocalSoundTrack track = LocalSoundTrackTest.read(LocalSoundTrackTest.normal())) {
            LocalSoundCursor cursor = new LocalSoundCursor(); List<Integer> heard = new ArrayList<>();
            cursor.frame(track, 0, 0, 42, false, false, e -> heard.add(e.time));
            cursor.frame(track, 125, 0, 42, false, true, e -> heard.add(e.time));
            assertFalse(cursor.covers(1, 42));
            cursor.frame(track, 130, 0, 42, false, false, e -> heard.add(e.time));
            assertTrue(heard.isEmpty());
            cursor.frame(track, 150, 0, 42, false, false, e -> heard.add(e.time));
            assertEquals(java.util.Arrays.asList(150), heard); heard.clear();
            cursor.frame(track, 25, 0, 42, false, false, e -> heard.add(e.time));
            assertTrue(heard.isEmpty());
            cursor.reset(); cursor.frame(track, 100, 0, 42, false, false, e -> heard.add(e.time));
            assertTrue(heard.isEmpty());
            cursor.frame(track, 150, 0, 42, false, false, e -> heard.add(e.time));
            assertEquals(java.util.Arrays.asList(150), heard);
        }
    }

    @Test void oldAbsentClosedUnsupportedAndWrongIdentityTracksKeepReconstruction() throws Exception {
        LocalSoundCursor cursor = new LocalSoundCursor();
        cursor.frame(null, 0, 0, 42, false, false, e -> fail()); assertFalse(cursor.covers(1, 42));
        try (LocalSoundTrack track = LocalSoundTrackTest.read(new LocalSoundTrackTest.Fixture(1).context(0, 0, 42, 1).end(100))) {
            cursor.frame(track, 10, 0, 42, false, false, e -> fail());
            assertTrue(cursor.covers(1, 42)); assertFalse(cursor.covers(2, 42)); assertFalse(cursor.covers(1, 99));
            cursor.frame(track, 20, 1, 42, false, false, e -> fail()); assertFalse(cursor.covers(1, 42));
            track.close(); cursor.frame(track, 30, 0, 42, false, false, e -> fail()); assertFalse(cursor.covers(1, 42));
        }
    }

    @Test void gapAndWorldChangeNeverReplayAudioFromPreviousWorldEvenWithReusedEntityId() throws Exception {
        try (LocalSoundTrack track = LocalSoundTrackTest.read(new LocalSoundTrackTest.Fixture(127)
                .context(0, 0, 42, 127).event(50, 0, 42, 1).context(75, -1, -1, 0)
                .context(100, 1, 42, 127).event(120, 1, 42, 2).end(1000))) {
            LocalSoundCursor cursor = new LocalSoundCursor(); List<Integer> heard = new ArrayList<>();
            cursor.frame(track, 0, 0, 42, false, false, e -> heard.add(e.time));
            cursor.frame(track, 80, 0, 42, false, true, e -> heard.add(e.time));
            assertFalse(cursor.covers(1, 42));
            cursor.frame(track, 100, 1, 42, false, false, e -> heard.add(e.time));
            cursor.frame(track, 125, 1, 42, false, false, e -> heard.add(e.time));
            assertEquals(java.util.Arrays.asList(120), heard);
        }
    }

    @Test void longForwardJumpNeverDumpsQueuedHistoricalSounds() throws Exception {
        try (LocalSoundTrack track = LocalSoundTrackTest.read(LocalSoundTrackTest.normal())) {
            LocalSoundCursor cursor = new LocalSoundCursor();
            cursor.frame(track, 0, 0, 42, false, false, e -> fail());
            cursor.frame(track, 900, 0, 42, false, false, e -> fail());
        }
    }
}
