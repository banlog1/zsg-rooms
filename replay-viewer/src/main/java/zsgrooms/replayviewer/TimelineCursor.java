// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

/** Shares ReplayMod's four-pixel track inset and truncated pixel mapping. */
final class TimelineCursor {
    interface Rects { void draw(int x, int y, int width, int height, int color); }
    private TimelineCursor() { }

    static int center(int width, int length, double zoom, int offset, int time) {
        double span = Math.max(1, (int) (length * zoom));
        double fraction = Math.max(0, Math.min(1, (time - (double) offset) / span));
        return 4 + (int) (Math.max(0, width - 8) * fraction);
    }

    static void draw(int center, int height, Rects rects) {
        for (int row = 0; row < 3; row++) {
            int radius = 3 - row;
            rects.draw(center - radius, row + 1, radius * 2 + 1, 1, 0xDD000000);
            rects.draw(center - radius + 1, row + 1, radius * 2 - 1, 1, 0xFFFFFFFF);
        }
        rects.draw(center - 1, 4, 3, Math.max(1, height - 5), 0xDD000000);
        rects.draw(center, 4, 1, Math.max(1, height - 5), 0xFFFFFFFF);
    }
}
