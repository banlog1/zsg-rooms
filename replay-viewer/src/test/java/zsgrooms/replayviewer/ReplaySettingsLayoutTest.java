// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplaySettingsLayoutTest {
    @Test void controlsFitMinimumAndLargerGuiSizes() {
        for (int[] size : new int[][]{{320, 240}, {426, 240}, {640, 360}, {854, 480}, {1920, 1080}}) {
            ReplaySettingsLayout layout = new ReplaySettingsLayout(size[0], size[1]);
            assertTrue(layout.left >= 16);
            assertTrue(layout.left + layout.width <= size[0] - 16);
            assertTrue(layout.top >= 12);
            assertTrue(layout.doneY() + ReplaySettingsLayout.CONTROL_HEIGHT <= size[1] - 12);
            assertTrue(layout.tabsY() >= layout.top + 9);
            assertTrue(layout.tabsY() + 20 <= layout.headingY(0));
            for (int row = 0; row < 5; row++) {
                assertTrue(layout.rowY(row) + 20 <= layout.statusY());
                if (row > 0) assertTrue(layout.rowY(row - 1) + 20 <= layout.rowY(row));
            }
            assertTrue(layout.statusY() + 9 < layout.doneY());
        }
    }

    @Test void tabsAndTrailActionsHaveStableSeparatedHitTargets() {
        ReplaySettingsLayout layout = new ReplaySettingsLayout(320, 240);
        for (int index = 0; index < 3; index++) {
            assertTrue(layout.tabWidth(index) >= 88);
            assertTrue(layout.tabLeft(index) >= layout.left);
            assertTrue(layout.tabLeft(index) + layout.tabWidth(index) <= layout.left + layout.width);
            if (index < 2) assertEquals(4, layout.tabLeft(index + 1) - layout.tabLeft(index) - layout.tabWidth(index));
        }
        assertEquals(layout.left + layout.width, layout.customizeX() + ReplaySettingsLayout.CUSTOMIZE_WIDTH);
        assertEquals(4, layout.customizeX() - layout.left - layout.trailWidth());
        assertTrue(layout.trailWidth() >= 196);
        assertEquals(76, ReplaySettingsLayout.CUSTOMIZE_WIDTH);
    }

    @Test void headingsDoNotOverlapPreviousGroupControls() {
        ReplaySettingsLayout layout = new ReplaySettingsLayout(320, 240);
        assertTrue(layout.headingY(3) >= layout.rowY(1) + 20);
        assertTrue(layout.headingY(4) >= layout.rowY(2) + 20);
        assertTrue(layout.headingY(3) + 9 < layout.rowY(3));
        assertTrue(layout.headingY(4) + 9 < layout.rowY(4));
    }
}
