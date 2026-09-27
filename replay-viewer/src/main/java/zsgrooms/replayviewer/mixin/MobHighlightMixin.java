// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zsgrooms.replayviewer.ReplayViewer;

@Mixin(MinecraftClient.class)
public abstract class MobHighlightMixin {
    @Inject(method = "method_27022", at = @At("RETURN"), cancellable = true)
    private void zsgViewer$outline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (ReplayViewer.mobHighlightColor(entity) >= 0) cir.setReturnValue(true);
    }
}
