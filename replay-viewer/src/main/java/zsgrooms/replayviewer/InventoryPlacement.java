// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

/** Fits the vanilla inventory between the timer overlay and the fixed survival HUD. */
final class InventoryPlacement {
    final float x, y, scale;
    InventoryPlacement(int width, int height, int bottomInset) {
        scale = Math.min(1, Math.min((width - 16) / 176F, Math.max(1, height - bottomInset - 36) / 166F));
        x = (width - 176 * scale) / 2;
        y = Math.min((height - 166 * scale) / 2, height - bottomInset - 166 * scale);
    }
}
