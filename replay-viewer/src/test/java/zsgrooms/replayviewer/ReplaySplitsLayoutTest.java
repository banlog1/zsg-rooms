// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplaySplitsLayoutTest {
    @Test void tableAndFooterFitEverySupportedGuiSize() {
        for (int width : new int[] {320, 360, 427, 640, 854, 1280, 1920}) {
            for (int height : new int[] {240, 270, 360, 480, 720, 1080}) {
                ReplaySplitsLayout layout = new ReplaySplitsLayout(width, height);
                assertTrue(layout.columnWidth >= 112);
                assertTrue(layout.left >= 12);
                assertTrue(layout.left + layout.width <= width - 12);
                assertTrue(layout.columnX(layout.columns) <= layout.left + layout.width);
                assertTrue(layout.rowY(Milestones.Kind.values().length) < layout.footerY - 13);
                assertTrue(layout.rowHeight >= MilestoneGlyphs.SIZE);
                assertTrue(layout.left + 76 < width / 2 - 48);
                assertTrue(width / 2 + 48 < layout.left + layout.width - 64);
                assertTrue(layout.footerY + 20 <= height);
            }
        }
    }

    @Test void pagingAlwaysProvidesAValidPage() {
        ReplaySplitsLayout small = new ReplaySplitsLayout(320, 240);
        assertEquals(1, small.columns);
        assertEquals(1, small.pages(0));
        assertEquals(4, small.pages(4));
        ReplaySplitsLayout wide = new ReplaySplitsLayout(1280, 720);
        assertEquals(6, wide.columns);
        assertEquals(2, wide.pages(7));
    }

    @Test void smallGroupsUseTheAvailableSpaceWithoutEmptyColumns() {
        ReplaySplitsLayout single = new ReplaySplitsLayout(1280, 720, 1);
        assertEquals(1, single.columns);
        assertEquals(single.width, single.labelWidth + single.columnWidth);
        ReplaySplitsLayout pair = new ReplaySplitsLayout(640, 360, 2);
        assertEquals(2, pair.columns);
        assertEquals(pair.width, pair.labelWidth + pair.columnWidth * 2);
        assertEquals(1, new ReplaySplitsLayout(320, 240, 0).columns);
    }
}
