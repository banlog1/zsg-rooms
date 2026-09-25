package zsgrooms.modid.ui;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class FilterPickerLayoutTest {
    @Test
    void everyExistingChoiceAppearsOnce() {
        assertEquals(20, FilterCatalog.ENTRIES.size());
        HashSet<String> ids = new HashSet<String>();
        FilterCatalog.ENTRIES.forEach(entry -> assertTrue(ids.add(entry.id)));
        assertEquals(6, FilterCatalog.entries(FilterCatalog.Group.ROOMS).size());
        assertEquals(11, FilterCatalog.entries(FilterCatalog.Group.EXISTING).size());
        assertEquals(3, FilterCatalog.entries(FilterCatalog.Group.OTHER).size());
        assertEquals("rpseedbank", FilterCatalog.find("rpseedbank").id);
        assertEquals(FilterCatalog.Group.OTHER, FilterCatalog.find("manual").group);
    }

    @Test
    void supportedGuiSizesKeepCardsBetweenHeaderAndFooter() {
        for (int width : new int[] {320, 427, 640, 854, 1280, 1920}) {
            for (int height : new int[] {180, 240, 360, 480, 720, 1080}) {
                for (boolean gallery : new boolean[] {false, true}) {
                    FilterPickerLayout layout = new FilterPickerLayout(width, height, gallery);
                    int right = layout.left + layout.columns * layout.cellWidth + (layout.columns - 1) * FilterPickerLayout.GAP;
                    int bottom = FilterPickerLayout.TOP + layout.rows * layout.cellHeight + (layout.rows - 1) * FilterPickerLayout.GAP;
                    assertTrue(layout.left >= 0 && right <= width);
                    assertTrue(bottom <= height - 38, width + "x" + height);
                    assertTrue(layout.cellWidth >= 140);
                    for (FilterCatalog.Group group : FilterCatalog.Group.values()) {
                        int count = FilterCatalog.entries(group).size();
                        assertTrue(layout.pages(count) * layout.pageSize() >= count);
                    }
                }
            }
        }
    }

    @Test
    void shortScreensFallBackToCompactRows() {
        assertFalse(new FilterPickerLayout(320, 180, true).gallery);
        assertTrue(new FilterPickerLayout(320, 240, true).gallery);
    }
}
