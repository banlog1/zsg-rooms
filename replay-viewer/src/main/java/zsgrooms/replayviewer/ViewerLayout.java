// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

/** GUI-scaled dimensions, independent of the playback/rendering classes. */
final class ViewerLayout {
    final int width;
    final boolean narrow;
    final int height;
    final int cameraY;
    final int cameraWidth;
    final int cameraX;
    final int utilityY;
    final int milestoneY;
    final int timelineY;
    final int utilityX;
    final int textWidth;

    ViewerLayout(int screenWidth) {
        width = Math.max(1, screenWidth - 16);
        narrow = width < 520;
        height = narrow ? 80 : 56;
        cameraY = narrow ? 24 : 0;
        cameraX = narrow ? 0 : Math.max(242, width - 258);
        cameraWidth = 128;
        utilityX = width - 116;
        utilityY = cameraY;
        textWidth = narrow ? width - 142 : cameraX - 142;
        milestoneY = narrow ? 48 : 24;
        timelineY = milestoneY + 16;
    }

    static int seekTarget(int current, int delta, int duration) {
        return (int) Math.max(0L, Math.min(Math.max(0, duration), (long) current + delta));
    }

    int barY(int screenHeight, int bottomInset) {
        return Math.max(0, screenHeight - height - 8 - bottomInset);
    }

    int recordedScreenInset(int screenHeight, int hudInset, boolean controlsShown) {
        return controlsShown ? screenHeight - barY(screenHeight, hudInset) + 4 : hudInset;
    }

    static String time(int milliseconds) {
        int seconds = Math.max(0, milliseconds) / 1000;
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }
}
