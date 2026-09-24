// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import com.replaymod.replay.camera.CameraEntity;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.replayviewer.ReplayViewer;

@Mixin(value = CameraEntity.class, remap = false)
public abstract class DroneClickMixin {
    @Inject(method = "handleInputEvents", at = @At("HEAD"), cancellable = true)
    private void zsgViewer$reserveOrbitClick(CallbackInfo ci) {
        if (ReplayViewer.inspectChest()) { ci.cancel(); return; }
        if (!ReplayViewer.droneActive()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        // Avoid ReplayMod's click-to-spectate stealing an orbit drag or queuing it for another mode.
        while (client.options.keyAttack.wasPressed()) { }
        while (client.options.keyUse.wasPressed()) { }
        ci.cancel();
    }
}
