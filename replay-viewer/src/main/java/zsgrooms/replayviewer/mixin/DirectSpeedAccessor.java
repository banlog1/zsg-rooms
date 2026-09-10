// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import com.replaymod.replay.camera.VanillaCameraController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = VanillaCameraController.class, remap = false)
public interface DirectSpeedAccessor {
    @Accessor("speed") int zsgViewer$getSpeed();
    @Accessor("speed") void zsgViewer$setSpeed(int speed);
}
