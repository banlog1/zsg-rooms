// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

/** Full sender time is already continuous; Quick sender time is updated only on client ticks. */
final class CameraPlaybackClock {
    static double time(int timestamp, boolean quick, boolean paused, double speed, long now, long quickUpdate) {
        if (!quick || paused) return timestamp;
        if (!Double.isFinite(speed) || speed <= 0 || quickUpdate <= 0 || now < quickUpdate) return Double.NaN;
        double elapsed = ((double) now - quickUpdate) * speed;
        return elapsed <= CameraTrack.MAX_GAP ? timestamp + elapsed : Double.NaN;
    }
}
