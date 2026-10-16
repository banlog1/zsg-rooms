// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

/** Stable table geometry in GUI pixels, including Minecraft's 320 x 240 minimum. */
final class ReplaySplitsLayout {
    final int left, width, labelWidth, columns, columnWidth, rowHeight, footerY;
    final int firstRow = 88;

    ReplaySplitsLayout(int screenWidth, int screenHeight) {
        this(screenWidth, screenHeight, Integer.MAX_VALUE);
    }

    ReplaySplitsLayout(int screenWidth, int screenHeight, int recordings) {
        width = Math.max(1, Math.min(960, screenWidth - 24));
        left = (screenWidth - width) / 2;
        labelWidth = width < 400 ? 128 : 148;
        columns = Math.max(1, Math.min(recordings, Math.min(6, (width - labelWidth) / 112)));
        columnWidth = Math.max(1, (width - labelWidth) / columns);
        rowHeight = Math.max(16, Math.min(26, (screenHeight - 144) / Milestones.Kind.values().length));
        footerY = screenHeight - 32;
    }

    int columnX(int index) { return left + labelWidth + index * columnWidth; }
    int rowY(int index) { return firstRow + index * rowHeight; }
    int pages(int count) { return Math.max(1, (count + columns - 1) / columns); }
}
