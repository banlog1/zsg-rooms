// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.text.Style;
import zsgrooms.replayviewer.ReplayViewer;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void zsgViewer$hideChat(MatrixStack matrices, int ticks, CallbackInfo ci) {
        if (ReplayViewer.shouldHideChat()) ci.cancel();
    }

    @Inject(method = "getText", at = @At("HEAD"), cancellable = true)
    private void zsgViewer$hideChatHitTargets(double x, double y, CallbackInfoReturnable<Style> cir) {
        if (ReplayViewer.shouldHideChat()) cir.setReturnValue(null);
    }
}
