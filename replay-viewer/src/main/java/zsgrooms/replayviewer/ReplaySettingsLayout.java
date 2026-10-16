// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

/** Fixed native control sizes, including at the minimum 320 x 240 GUI scale. */
final class ReplaySettingsLayout {
    static final int HEIGHT = 216;
    static final int CONTROL_HEIGHT = 20;
    static final int GAP = 4;
    static final int CUSTOMIZE_WIDTH = 76;
    final int left, top, width;

    ReplaySettingsLayout(int screenWidth, int screenHeight) {
        width = Math.min(280, screenWidth - 32);
        left = (screenWidth - width) / 2;
        top = Math.max(12, (screenHeight - HEIGHT) / 2);
    }

    int tabsY() { return top + 18; }
    int tabLeft(int index) { return left + index * (width + GAP) / 3; }
    int tabWidth(int index) { return tabLeft(index + 1) - tabLeft(index) - GAP; }
    int rowY(int index) { return top + 60 + index * 24; }
    int headingY(int row) { return rowY(row) - 12; }
    int customizeX() { return left + width - CUSTOMIZE_WIDTH; }
    int trailWidth() { return width - CUSTOMIZE_WIDTH - GAP; }
    int statusY() { return top + 180; }
    int doneY() { return top + HEIGHT - CONTROL_HEIGHT; }
}
