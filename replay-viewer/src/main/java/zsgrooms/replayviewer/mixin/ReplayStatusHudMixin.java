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
public abstract class ReplayStatusHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void zsgViewer$status(MatrixStack matrices, float delta, CallbackInfo ci) {
        ReplayViewer.renderStatus(matrices);
    }
}
