// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

final class PlayerSoundCadence {
    private double x, y, z, distance;
    private boolean initialized;

    void reset() { initialized = false; distance = 0; }

    boolean step(double x, double y, double z, boolean walking) {
        double dx = x - this.x, dy = y - this.y, dz = z - this.z;
        boolean continuous = initialized && dx * dx + dy * dy + dz * dz <= 9;
        this.x = x; this.y = y; this.z = z; initialized = true;
        if (!continuous || !walking) { distance = 0; return false; }
        distance += Math.sqrt(dx * dx + dz * dz) * 0.6;
        if (distance < 1) return false;
        distance %= 1;
        return true;
    }

}
