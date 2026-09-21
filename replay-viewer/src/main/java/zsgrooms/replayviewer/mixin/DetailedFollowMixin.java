// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.replayviewer.ReplayViewer;

@Mixin(InGameHud.class)
public abstract class DetailedFollowMixin {
    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void zsgViewer$hotbar(float delta, MatrixStack matrices, CallbackInfo ci) {
        if (ReplayViewer.detailedActive()) ci.cancel();
    }
    @Inject(method = {"renderStatusBars", "renderStatusEffectOverlay"}, at = @At("HEAD"), cancellable = true)
    private void zsgViewer$bars(MatrixStack matrices, CallbackInfo ci) {
        if (ReplayViewer.detailedActive()) ci.cancel();
    }
    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void zsgViewer$experience(MatrixStack matrices, int x, CallbackInfo ci) {
        if (ReplayViewer.detailedActive()) ci.cancel();
    }
}
