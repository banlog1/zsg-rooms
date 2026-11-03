// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraPlaybackClockTest {
    @Test void fullSenderIsAlreadyAContinuousClock() {
        assertEquals(1023, CameraPlaybackClock.time(1023, false, false, 2, 5000, 4980));
        assertEquals(1040, CameraPlaybackClock.time(1040, false, false, 2, 5010, 4980));
    }

    @Test void quickClockAdvancesWithinATickAtPlaybackSpeed() {
        for (double speed : new double[]{.25, .5, 1, 2, 4}) {
            assertEquals(1000, CameraPlaybackClock.time(1000, true, false, speed, 5000, 5000));
            assertEquals(1000 + 4 * speed, CameraPlaybackClock.time(1000, true, false, speed, 5004, 5000));
            assertEquals(1000 + 8 * speed, CameraPlaybackClock.time(1000, true, false, speed, 5008, 5000));
        }
    }

    @Test void pauseAndSeekHaveNoWallClockHistory() {
        for (boolean quick : new boolean[]{false, true}) {
            assertEquals(1234, CameraPlaybackClock.time(1234, quick, true, 2, 5000, 4000));
            assertEquals(1234, CameraPlaybackClock.time(1234, quick, true, 2, 9000, 4000));
            assertEquals(200, CameraPlaybackClock.time(200, quick, true, 2, 9000, 4000));
            assertEquals(60000, CameraPlaybackClock.time(60000, quick, true, 2, 9000, 4000));
        }
        assertEquals(200, CameraPlaybackClock.time(200, true, false, 2, 9000, 9000));
        assertEquals(204, CameraPlaybackClock.time(200, true, false, 2, 9002, 9000));
    }

    @Test void missingStaleOrBackwardsQuickAnchorsFallBack() {
        assertTrue(Double.isNaN(CameraPlaybackClock.time(1000, true, false, 1, 5000, 0)));
        assertTrue(Double.isNaN(CameraPlaybackClock.time(1000, true, false, 1, 5000, 5001)));
        assertTrue(Double.isNaN(CameraPlaybackClock.time(1000, true, false, 1, 5101, 5000)));
        assertTrue(Double.isNaN(CameraPlaybackClock.time(1000, true, false, 2, 5051, 5000)));
        assertEquals(1100, CameraPlaybackClock.time(1000, true, false, 2, 5050, 5000));
        for (double speed : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertTrue(Double.isNaN(CameraPlaybackClock.time(1000, true, false, speed, 5000, 5000)));
        }
    }
}
