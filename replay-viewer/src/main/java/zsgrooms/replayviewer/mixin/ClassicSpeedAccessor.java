// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import com.replaymod.replay.camera.ClassicCameraController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ClassicCameraController.class, remap = false)
public interface ClassicSpeedAccessor {
    @Accessor("MAX_SPEED") double zsgViewer$getSpeed();
    @Invoker("setCameraMaximumSpeed") void zsgViewer$setSpeed(double speed);
}
