// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

/** GUI-scaled dimensions, independent of the playback/rendering classes. */
final class ViewerLayout {
    final int width;
    final boolean narrow;
    final int height;
    final int cameraY;

    ViewerLayout(int screenWidth) {
        width = Math.max(1, screenWidth - 16);
        narrow = width < 480;
        height = narrow ? 122 : 98;
        cameraY = narrow ? 24 : 0;
    }

    static int seekTarget(int current, int delta, int duration) {
        return (int) Math.max(0L, Math.min(Math.max(0, duration), (long) current + delta));
    }

    static String time(int milliseconds) {
        int seconds = Math.max(0, milliseconds) / 1000;
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }
}
