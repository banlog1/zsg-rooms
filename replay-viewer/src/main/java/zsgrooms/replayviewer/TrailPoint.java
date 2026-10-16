// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

/** An immutable packet-position sample. Time is absolute replay milliseconds; coordinates are entity feet. */
public final class TrailPoint {
    public final int time;
    public final double x, y, z;
    public final boolean breakBefore;

    public TrailPoint(int time, double x, double y, double z, boolean breakBefore) {
        this.time = time;
        this.x = x; this.y = y; this.z = z;
        this.breakBefore = breakBefore;
    }
}
