// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

/** Vanilla 1.16.1 slot positions, independent of live screen handlers or inventories. */
final class RecordedScreenLayout {
    static final int INVENTORY = 1, CRAFTING = 2, CHEST = 3;
    final int kind, rows, height;
    final int[] x, y;

    private RecordedScreenLayout(int kind, int slots, int rows) {
        this.kind = kind;
        this.rows = rows;
        height = kind == CHEST ? 114 + rows * 18 : 166;
        x = new int[slots]; y = new int[slots];
    }

    static RecordedScreenLayout create(int kind, int slots) {
        if (kind == INVENTORY && slots == 46) {
            RecordedScreenLayout layout = new RecordedScreenLayout(kind, slots, 0);
            layout.slot(0, 154, 28);
            for (int i = 0; i < 4; i++) {
                layout.slot(i + 1, 98 + i % 2 * 18, 18 + i / 2 * 18);
                layout.slot(i + 5, 8, 8 + i * 18);
            }
            layout.player(9, 84);
            layout.slot(45, 77, 62);
            return layout;
        }
        if (kind == CRAFTING && slots == 46) {
            RecordedScreenLayout layout = new RecordedScreenLayout(kind, slots, 0);
            layout.slot(0, 124, 35);
            for (int i = 0; i < 9; i++) layout.slot(i + 1, 30 + i % 3 * 18, 17 + i / 3 * 18);
            layout.player(10, 84);
            return layout;
        }
        int rows = (slots - 36) / 9;
        if (kind == CHEST && rows >= 1 && rows <= 6 && slots == rows * 9 + 36) {
            RecordedScreenLayout layout = new RecordedScreenLayout(kind, slots, rows);
            for (int i = 0; i < rows * 9; i++) layout.slot(i, 8 + i % 9 * 18, 18 + i / 9 * 18);
            layout.player(rows * 9, rows * 18 + 31);
            return layout;
        }
        return null;
    }

    private void player(int start, int top) {
        for (int i = 0; i < 27; i++) slot(start + i, 8 + i % 9 * 18, top + i / 9 * 18);
        for (int i = 0; i < 9; i++) slot(start + 27 + i, 8 + i * 18, top + 58);
    }
    private void slot(int index, int px, int py) { x[index] = px; y[index] = py; }
}
