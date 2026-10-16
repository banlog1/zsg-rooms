// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BarterScreenLayoutTest {
    @Test void rowsAndFooterFitSmallAndWideGuiSizes() {
        for (int[] size : new int[][] {{320, 240}, {427, 240}, {640, 360}, {1280, 720}}) {
            BarterScreenLayout layout = new BarterScreenLayout(size[0], size[1]);
            assertTrue(layout.left >= 12);
            assertTrue(layout.left + layout.width <= size[0] - 12);
            assertTrue(layout.rows >= 4);
            assertTrue(layout.rowY(layout.rows) <= layout.footer - 8);
            assertTrue(layout.footer + 20 <= size[1] - 8);
            assertTrue(layout.width - 86 >= 200);
        }
    }

    @Test void paginationIncludesTheLastPartialPage() {
        BarterScreenLayout layout = new BarterScreenLayout(320, 240);
        assertEquals(1, layout.pages(0));
        assertEquals(1, layout.pages(layout.rows));
        assertEquals(2, layout.pages(layout.rows + 1));
    }
}
