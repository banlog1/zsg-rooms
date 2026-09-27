// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zsgrooms.replayviewer.ReplayViewer;

@Mixin(Entity.class)
public abstract class MobHighlightColorMixin {
    @Inject(method = "getTeamColorValue", at = @At("RETURN"), cancellable = true)
    private void zsgViewer$color(CallbackInfoReturnable<Integer> cir) {
        int color = ReplayViewer.mobHighlightColor((Entity) (Object) this);
        if (color >= 0) cir.setReturnValue(color);
    }
}
