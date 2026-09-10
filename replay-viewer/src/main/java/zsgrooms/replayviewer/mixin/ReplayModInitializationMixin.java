// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import com.replaymod.core.ReplayMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.replayviewer.ReplayViewer;

@Mixin(value = ReplayMod.class, remap = false)
abstract class ReplayModInitializationMixin {
    @Inject(method = "initModules", at = @At("RETURN"))
    private void zsgViewer$registerShortcuts(CallbackInfo ci) {
        ReplayViewer.registerShortcuts(((ReplayMod) (Object) this).getKeyBindingRegistry());
    }
}
