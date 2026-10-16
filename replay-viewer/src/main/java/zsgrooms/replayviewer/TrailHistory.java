// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import java.util.ArrayDeque;
import java.util.Deque;

/** Bounded replay-time samples, never joined across a seek or teleport. */
final class TrailHistory {
    static final int DURATION = 10000;
    static final int INTERVAL = 100;
    final Deque<TrailPoint> points = new ArrayDeque<>();
    private boolean indexed;

    void add(int time, double x, double y, double z) {
        add(time, x, y, z, DURATION);
    }

    void add(int time, double x, double y, double z, int duration) {
        duration = Math.max(2000, Math.min(30000, duration));
        TrailPoint last = points.peekLast();
        if (last != null) {
            double dx = x - last.x, dy = y - last.y, dz = z - last.z;
            if (time < last.time || (long) time - last.time > 1000 || dx * dx + dy * dy + dz * dz > 4096) {
                points.clear();
                indexed = false;
            } else if (time - last.time < INTERVAL) return;
        }
        while (!points.isEmpty() && (long) time - points.peekFirst().time > duration) points.removeFirst();
        points.addLast(new TrailPoint(time, x, y, z, false));
        while (points.size() > (indexed ? 2048 : duration / INTERVAL + 1)) points.removeFirst();
    }

    /** Historical packets already carry discontinuities; do not infer gaps from stationary time. */
    void replace(Iterable<TrailPoint> samples, int time, int duration) {
        points.clear();
        indexed = true;
        int from = Math.max(0, time - Math.max(2000, Math.min(30000, duration)));
        TrailPoint previous = null;
        for (TrailPoint sample : samples) {
            if (sample.time > time) break;
            if (sample.time < from) { previous = sample; continue; }
            if (points.isEmpty() && previous != null && !sample.breakBefore && sample.time > previous.time) {
                double fraction = (from - previous.time) / (double) (sample.time - previous.time);
                points.addLast(new TrailPoint(from, previous.x + (sample.x - previous.x) * fraction,
                        previous.y + (sample.y - previous.y) * fraction,
                        previous.z + (sample.z - previous.z) * fraction, true));
            }
            points.addLast(sample);
            previous = sample;
            while (points.size() > 2048) points.removeFirst();
        }
    }

}
