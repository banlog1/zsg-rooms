// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

final class ViewerVisibility {
    private long lastActivity;
    private boolean previousMouseVisible;
    private double x = Double.NaN;
    private double y = Double.NaN;
    private boolean manuallyHidden;

    ViewerVisibility(long now) {
        lastActivity = now;
    }

    void touch(long now) {
        lastActivity = now;
    }

    boolean manuallyHidden() { return manuallyHidden; }

    void setManuallyHidden(boolean hidden, long now) {
        manuallyHidden = hidden;
        touch(now);
    }

    void toggle(long now) { setManuallyHidden(!manuallyHidden, now); }

    boolean update(long now, boolean autoHide, boolean mouseVisible, double mouseX, double mouseY,
                   boolean interacting, boolean paused) {
        if (mouseVisible && (!previousMouseVisible || mouseX != x || mouseY != y) || interacting || paused) {
            lastActivity = now;
        }
        previousMouseVisible = mouseVisible;
        x = mouseX;
        y = mouseY;
        return !manuallyHidden && (!autoHide || now - lastActivity < 3000000000L);
    }
}
