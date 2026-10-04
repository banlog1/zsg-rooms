package zsgrooms.modid.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PersonalSettingsLayoutTest {
    @Test void boundsKeepControlsAboveFooterAtSupportedGuiSizes() {
        for (int width : new int[] {320, 360, 480, 499, 500, 640, 960, 1920}) {
            for (int height : new int[] {180, 200, 219, 220, 240, 360, 540, 1080}) {
                PersonalSettingsLayout l = new PersonalSettingsLayout(width, height);
                assertTrue(l.contentX >= 0);
                assertTrue(l.controlX() + l.controlWidth() <= width - 12);
                assertTrue(l.top + l.capacity * l.rowHeight <= l.bottom);
                assertTrue(l.controlWidth() >= 150);
                if (l.sidebar) assertTrue(58 + 4 * 26 + 20 < height - 32);
                for (int rows : new int[] {0, 1, 4, 6, 13}) {
                    assertEquals(0, l.clampOffset(-10, rows));
                    assertEquals(l.maxOffset(rows), l.clampOffset(999, rows));
                    assertTrue(l.maxOffset(rows) + l.capacity >= rows);
                }
            }
        }
    }

    @Test void narrowScreensUseCategorySelectorAndStackedLabels() {
        assertFalse(new PersonalSettingsLayout(320, 180).sidebar);
        assertTrue(new PersonalSettingsLayout(320, 180).stacked);
        assertFalse(new PersonalSettingsLayout(960, 180).sidebar);
        assertTrue(new PersonalSettingsLayout(640, 360).sidebar);
        assertFalse(new PersonalSettingsLayout(640, 360).stacked);
    }
}
