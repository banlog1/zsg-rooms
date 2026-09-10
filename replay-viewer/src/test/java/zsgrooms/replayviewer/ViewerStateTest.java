// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ViewerStateTest {
    @Test void narrowLayoutsReserveAnExtraControlRow() {
        for (int width : new int[] {320, 360, 427}) {
            ViewerLayout layout = new ViewerLayout(width);
            assertTrue(layout.narrow);
            assertEquals(24, layout.cameraY);
            assertEquals(width - 16, layout.width);
            assertEquals(122, layout.height);
        }
        assertFalse(new ViewerLayout(854).narrow);
    }

    @Test void seeksClampToRecordingIncludingIntegerOverflow() {
        assertEquals(0, ViewerLayout.seekTarget(1000, -5000, 30000));
        assertEquals(30000, ViewerLayout.seekTarget(29000, 5000, 30000));
        assertEquals(Integer.MAX_VALUE, ViewerLayout.seekTarget(Integer.MAX_VALUE - 1, 5000, Integer.MAX_VALUE));
    }

    @Test void idleHidesButHoverPauseAndCursorRevealKeepControlsVisible() {
        ViewerVisibility state = new ViewerVisibility(0);
        assertFalse(state.update(4000000000L, true, false, 0, 0, false, false));
        assertTrue(state.update(5000000000L, true, true, 0, 0, false, false));
        assertFalse(state.update(9000000000L, true, true, 0, 0, false, false));
        assertTrue(state.update(10000000000L, true, true, 0, 0, true, false));
        assertTrue(state.update(15000000000L, true, false, 0, 0, false, true));
        assertTrue(state.update(20000000000L, false, false, 0, 0, false, false));
    }

    @Test void freecamMouseMotionDoesNotKeepTheBarVisible() {
        ViewerVisibility state = new ViewerVisibility(0);
        assertFalse(state.update(4000000000L, true, false, 100, 200, false, false));
        state.touch(5000000000L);
        assertTrue(state.update(6000000000L, true, false, 100, 200, false, false));
    }
}
