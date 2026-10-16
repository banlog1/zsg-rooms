// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.replayviewer.ReplayViewer;

@Mixin(InGameHud.class)
public abstract class FaithfulHudMixin {
    @Redirect(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/options/GameOptions;hudHidden:Z"))
    private boolean zsgViewer$recordedHudVisibility(GameOptions options) {
        return options.hudHidden || ReplayViewer.faithfulHudHidden();
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void zsgViewer$recordedAttackIndicator(MatrixStack matrices, CallbackInfo ci) {
        if (ReplayViewer.faithfulActive()) ci.cancel();
    }
}
