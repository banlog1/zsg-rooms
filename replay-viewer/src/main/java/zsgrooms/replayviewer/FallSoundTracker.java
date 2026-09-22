// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

/** Matches packet movement to damage evidence, never camera motion or interpolated positions. */
final class FallSoundTracker {
    private double x, y, z, distance, landedDistance;
    double landedX, landedY, landedZ;
    private int last = -1, hurt = -1, landed = -1;
    private boolean grounded, airborne;

    void reset() { last = hurt = landed = -1; distance = landedDistance = 0; airborne = false; }

    static int damage(float distance, int jumpBoost, boolean bed, boolean cushioned) {
        if (bed) distance *= 0.5F;
        return (int) Math.ceil((distance - 3.0F - jumpBoost) * (cushioned ? 0.2F : 1.0F));
    }

    double move(int time, double x, double y, double z, boolean onGround, boolean eligible) {
        double dx = x - this.x, dy = y - this.y, dz = z - this.z;
        boolean continuous = last >= 0 && time >= last && (grounded || time - last <= 500)
                && dx * dx + dy * dy + dz * dz <= 64;
        if (!eligible || !continuous) reset();
        if (eligible && continuous) {
            if (!onGround) {
                if (grounded) airborne = true;
                if (airborne && dy < 0) distance -= dy;
            } else {
                if (airborne && distance > 3) {
                    landedDistance = distance;
                    landed = time;
                    landedX = x; landedY = y; landedZ = z;
                }
                distance = 0;
                airborne = false;
            }
        }
        this.x = x; this.y = y; this.z = z;
        grounded = eligible && onGround;
        last = time;
        return match(time);
    }

    double hurt(int time) {
        if (last < 0 || time < last || time - last > 500) { reset(); return 0; }
        hurt = time;
        return match(time);
    }

    private double match(int time) {
        if (landed < 0 || hurt < 0 || time < landed || time < hurt
                || time - landed > 250 || time - hurt > 250) return 0;
        double result = landedDistance;
        landed = hurt = -1;
        landedDistance = 0;
        return result;
    }
}
