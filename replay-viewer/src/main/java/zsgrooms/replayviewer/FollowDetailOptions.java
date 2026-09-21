// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

final class FollowDetailOptions {
    boolean alwaysInventory;
    boolean inventoryOpen;
    boolean inventoryDismissed;
    boolean inventoryVisible() { return !inventoryDismissed && (alwaysInventory || inventoryOpen); }
    void toggleInventory() {
        if (inventoryVisible()) closeInventory();
        else { inventoryDismissed = false; inventoryOpen = true; }
    }
    void closeInventory() { inventoryOpen = false; inventoryDismissed = true; }
    void copyFrom(FollowDetailOptions other) {
        alwaysInventory = other.alwaysInventory;
        inventoryOpen = other.inventoryOpen;
        inventoryDismissed = other.inventoryDismissed;
    }
}
