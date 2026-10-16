// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

final class MobHighlights extends MobSelection {
    TrailStyle.Color color = TrailStyle.Color.CYAN;
    void copyFrom(MobHighlights other) {
        if (other == this) return;
        super.copyFrom(other);
        color = other.color;
    }
}
