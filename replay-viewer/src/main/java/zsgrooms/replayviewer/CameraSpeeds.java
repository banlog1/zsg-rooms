// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.replay.camera.CameraController;
import zsgrooms.replayviewer.mixin.ClassicSpeedAccessor;
import zsgrooms.replayviewer.mixin.DirectSpeedAccessor;

final class CameraSpeeds {
    private Integer direct;
    private Double classic;

    void copyFrom(CameraSpeeds other) { direct = other.direct; classic = other.classic; }

    void remember(CameraController controller) {
        if (controller instanceof DirectSpeedAccessor) direct = ((DirectSpeedAccessor) controller).zsgViewer$getSpeed();
        if (controller instanceof ClassicSpeedAccessor) classic = ((ClassicSpeedAccessor) controller).zsgViewer$getSpeed();
    }

    void restore(CameraController controller) {
        if (direct != null && controller instanceof DirectSpeedAccessor) ((DirectSpeedAccessor) controller).zsgViewer$setSpeed(direct);
        if (classic != null && controller instanceof ClassicSpeedAccessor) ((ClassicSpeedAccessor) controller).zsgViewer$setSpeed(classic);
    }
}
