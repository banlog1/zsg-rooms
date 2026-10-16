// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecordedScreenLayoutTest {
    @Test void inventoryAndCraftingKeepVanillaSlots() {
        RecordedScreenLayout inventory = RecordedScreenLayout.create(1, 46);
        assertEquals(154, inventory.x[0]);
        assertEquals(28, inventory.y[0]);
        assertEquals(77, inventory.x[45]);
        assertEquals(142, inventory.y[36]);
        RecordedScreenLayout crafting = RecordedScreenLayout.create(2, 46);
        assertEquals(124, crafting.x[0]);
        assertEquals(35, crafting.y[0]);
        assertEquals(84, crafting.y[10]);
        assertEquals(142, crafting.y[37]);
    }
    @Test void chestRowsFitTheirWholeInventory() {
        for (int rows = 1; rows <= 6; rows++) {
            RecordedScreenLayout layout = RecordedScreenLayout.create(3, 36 + 9 * rows);
            for (int i = 0; i < layout.x.length; i++) {
                assertTrue(layout.x[i] >= 0 && layout.x[i] + 16 <= 176);
                assertTrue(layout.y[i] >= 0 && layout.y[i] + 16 <= layout.height);
            }
            assertEquals(114 + rows * 18, layout.height);
        }
        assertNull(RecordedScreenLayout.create(3, 44));
        assertNull(RecordedScreenLayout.create(3, 99));
        assertNull(RecordedScreenLayout.create(1, 45));
        assertNull(RecordedScreenLayout.create(4, 46));
    }
}
