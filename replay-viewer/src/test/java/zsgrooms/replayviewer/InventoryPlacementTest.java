// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventoryPlacementTest {
    @Test void inventoryFitsBetweenTimersAndHudAtSupportedGuiSizes() {
        for (int width : new int[]{320, 427, 640, 960}) {
            for (int height : new int[]{240, 360, 540}) {
                InventoryPlacement box = new InventoryPlacement(width, height, 58);
                assertEquals(width / 2F, box.x + 88 * box.scale, 0.001);
                assertTrue(box.scale > 0 && box.scale <= 1);
                assertTrue(box.y >= 34);
                assertTrue(box.y + 166 * box.scale <= height - 58);
            }
        }
    }
    @Test void normalViewportUsesVanillaInventorySizeAndCenter() {
        InventoryPlacement box = new InventoryPlacement(640, 360, 58);
        assertEquals(1, box.scale);
        assertEquals(232, box.x);
        assertEquals(97, box.y);
    }
    @Test void transportStaysAboveTheSurvivalHudWithoutMovingOtherCameraModes() {
        for (int width : new int[]{320, 427, 640, 960}) {
            ViewerLayout bar = new ViewerLayout(width);
            assertEquals(240 - bar.height - 8, bar.barY(240, 0));
            assertTrue(bar.barY(240, 58) + bar.height <= 240 - 58);
        }
    }
}
