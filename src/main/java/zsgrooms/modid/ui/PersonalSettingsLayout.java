package zsgrooms.modid.ui;

/** Shared bounds in Minecraft GUI pixels. */
final class PersonalSettingsLayout {
    final boolean sidebar, stacked;
    final int left, width, contentX, contentWidth, top, bottom, rowHeight, capacity;

    PersonalSettingsLayout(int screenWidth, int screenHeight) {
        width = Math.min(720, screenWidth - 24);
        left = (screenWidth - width) / 2;
        sidebar = screenWidth >= 500 && screenHeight >= 220;
        contentX = left + (sidebar ? 120 : 0);
        contentWidth = width - (sidebar ? 120 : 0) - 14;
        stacked = contentWidth < 330;
        rowHeight = stacked ? 44 : 32;
        top = sidebar ? 58 : 80;
        bottom = screenHeight - 36;
        capacity = Math.max(1, (bottom - top) / rowHeight);
    }

    int maxOffset(int rows) { return Math.max(0, rows - capacity); }
    int clampOffset(int offset, int rows) { return Math.max(0, Math.min(maxOffset(rows), offset)); }
    int controlX() { return stacked ? contentX : contentX + contentWidth / 2; }
    int controlWidth() { return stacked ? contentWidth : contentWidth - contentWidth / 2; }
    int labelWidth() { return stacked ? contentWidth : contentWidth / 2 - 8; }
}
