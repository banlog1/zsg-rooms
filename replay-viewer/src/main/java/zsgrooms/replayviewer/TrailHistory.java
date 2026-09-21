// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import java.util.ArrayDeque;
import java.util.Deque;

/** Bounded replay-time samples, never joined across a seek or teleport. */
final class TrailHistory {
    static final int DURATION = 10000;
    static final int INTERVAL = 100;
    final Deque<Point> points = new ArrayDeque<>();

    void add(int time, double x, double y, double z) {
        add(time, x, y, z, DURATION);
    }

    void add(int time, double x, double y, double z, int duration) {
        duration = Math.max(2000, Math.min(30000, duration));
        Point last = points.peekLast();
        if (last != null) {
            double dx = x - last.x, dy = y - last.y, dz = z - last.z;
            if (time < last.time || (long) time - last.time > 1000 || dx * dx + dy * dy + dz * dz > 4096) {
                points.clear();
            } else if (time - last.time < INTERVAL) return;
        }
        while (!points.isEmpty() && (long) time - points.peekFirst().time > duration) points.removeFirst();
        points.addLast(new Point(time, x, y, z));
        while (points.size() > duration / INTERVAL + 1) points.removeFirst();
    }

    static final class Point {
        final int time;
        final double x, y, z;
        Point(int time, double x, double y, double z) {
            this.time = time; this.x = x; this.y = y; this.z = z;
        }
    }
}
