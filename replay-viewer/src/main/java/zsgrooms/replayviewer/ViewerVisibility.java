// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

final class ViewerVisibility {
    private long lastActivity;
    private boolean previousMouseVisible;
    private double x = Double.NaN;
    private double y = Double.NaN;

    ViewerVisibility(long now) {
        lastActivity = now;
    }

    void touch(long now) {
        lastActivity = now;
    }

    boolean update(long now, boolean autoHide, boolean mouseVisible, double mouseX, double mouseY,
                   boolean interacting, boolean paused) {
        if (mouseVisible && (!previousMouseVisible || mouseX != x || mouseY != y) || interacting || paused) {
            lastActivity = now;
        }
        previousMouseVisible = mouseVisible;
        x = mouseX;
        y = mouseY;
        return !autoHide || now - lastActivity < 3000000000L;
    }
}
