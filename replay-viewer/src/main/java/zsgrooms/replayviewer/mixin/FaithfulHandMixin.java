// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zsgrooms.replayviewer.ReplayViewer;

@Mixin(GameRenderer.class)
public abstract class FaithfulHandMixin {
    @Redirect(method = "renderHand", at = @At(value = "FIELD", target = "Lnet/minecraft/client/options/GameOptions;hudHidden:Z"))
    private boolean zsgViewer$recordedHandVisibility(GameOptions options) {
        return options.hudHidden || ReplayViewer.faithfulHudHidden();
    }
}
