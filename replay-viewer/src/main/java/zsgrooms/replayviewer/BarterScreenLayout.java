// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

final class BarterScreenLayout {
    final int left, width, footer, rows;
    static final int LIST_TOP = 120;
    static final int ROW_HEIGHT = 20;

    BarterScreenLayout(int screenWidth, int screenHeight) {
        width = Math.min(520, screenWidth - 24);
        left = (screenWidth - width) / 2;
        footer = screenHeight - 28;
        rows = Math.max(1, (footer - 8 - LIST_TOP) / ROW_HEIGHT);
    }

    int rowY(int row) { return LIST_TOP + ROW_HEIGHT * row; }
    int pages(int size) { return Math.max(1, (size + rows - 1) / rows); }
}
