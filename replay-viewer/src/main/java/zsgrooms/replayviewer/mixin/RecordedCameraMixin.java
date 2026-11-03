// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.replayviewer.ReplayViewer;

@Mixin(Camera.class)
public abstract class RecordedCameraMixin {
    @Unique private final float[] zsgViewer$cameraRotation = new float[2];
    @Unique private boolean zsgViewer$cameraAvailable;

    @Inject(method = "update", at = @At("HEAD"))
    private void zsgViewer$snapshot(BlockView area, Entity focused, boolean thirdPerson, boolean inverseView,
                                     float delta, CallbackInfo ci) {
        zsgViewer$cameraAvailable = !thirdPerson && !inverseView
                && ReplayViewer.recordedCameraRotation(focused, zsgViewer$cameraRotation);
    }

    @ModifyArg(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V", ordinal = 0), index = 0)
    private float zsgViewer$recordedYaw(float yaw) {
        return zsgViewer$cameraAvailable ? zsgViewer$cameraRotation[0] : yaw;
    }

    @ModifyArg(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V", ordinal = 0), index = 1)
    private float zsgViewer$recordedPitch(float pitch) {
        return zsgViewer$cameraAvailable ? zsgViewer$cameraRotation[1] : pitch;
    }
}
