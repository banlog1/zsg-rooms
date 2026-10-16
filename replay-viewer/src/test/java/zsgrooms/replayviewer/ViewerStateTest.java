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
            assertEquals(80, layout.height);
        }
        assertFalse(new ViewerLayout(854).narrow);
    }

    @Test void seeksClampToRecordingIncludingIntegerOverflow() {
        assertEquals(0, ViewerLayout.seekTarget(1000, -5000, 30000));
        assertEquals(30000, ViewerLayout.seekTarget(29000, 5000, 30000));
        assertEquals(Integer.MAX_VALUE, ViewerLayout.seekTarget(Integer.MAX_VALUE - 1, 5000, Integer.MAX_VALUE));
    }

    @Test void recordedScreensReserveTheVisibleToolbarWithoutChangingHiddenLayout() {
        for (int width : new int[]{320, 640, 960}) for (int height : new int[]{240, 360, 540}) {
            ViewerLayout layout = new ViewerLayout(width);
            for (int hudInset : new int[]{0, 58, 70}) {
                assertEquals(hudInset, layout.recordedScreenInset(height, hudInset, false));
                int inset = layout.recordedScreenInset(height, hudInset, true);
                assertEquals(layout.barY(height, hudInset) - 4, height - inset);
                assertTrue(inset > hudInset);
                assertTrue(inset < height);
            }
        }
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

    @Test void explicitHidingWinsOverPauseHoverAndDisabledAutoHide() {
        ViewerVisibility state = new ViewerVisibility(0);
        state.setManuallyHidden(true, 1000000000L);
        assertTrue(state.manuallyHidden());
        assertFalse(state.update(2000000000L, true, true, 100, 100, true, true));
        assertFalse(state.update(3000000000L, false, true, 200, 100, true, true));
        state.touch(4000000000L);
        assertFalse(state.update(4500000000L, false, true, 200, 100, true, true));
        state.toggle(5000000000L);
        assertFalse(state.manuallyHidden());
        assertTrue(state.update(5000000001L, true, false, 0, 0, false, false));
        assertFalse(state.update(9000000000L, true, false, 0, 0, false, false));
    }

    @Test void utilityIconsAndCameraControlsFitSupportedGuiWidths() {
        for (int width : new int[] {320, 360, 427, 480, 495, 496, 512, 535, 536, 854, 1280}) {
            ViewerLayout layout = new ViewerLayout(width);
            assertTrue(layout.cameraWidth > 0);
            assertTrue(layout.cameraX + layout.cameraWidth + 8 <= layout.utilityX, "Camera overlaps tools at " + width);
            assertEquals(layout.width, layout.utilityX + 116);
            assertTrue(layout.textWidth >= 100);
            assertTrue(142 + layout.textWidth <= (layout.narrow ? layout.width : layout.cameraX));
            assertEquals(layout.height, layout.timelineY + 16);
            assertEquals(layout.timelineY, layout.milestoneY + 16);
            int top = layout.barY(480, 65);
            assertTrue(top + layout.height <= 480 - 8 - 65);
        }
    }
}
