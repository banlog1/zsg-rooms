// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import net.minecraft.util.math.Vec3d;
import java.util.List;

final class PiglinClusters {
    private PiglinClusters() {}

    static int largest(List<Vec3d> positions) {
        int largest = 0;
        for (Vec3d center : positions) {
            int count = 0;
            for (Vec3d position : positions) {
                if (center.squaredDistanceTo(position) <= 1.0) count++;
            }
            largest = Math.max(largest, count);
        }
        return largest;
    }
}
