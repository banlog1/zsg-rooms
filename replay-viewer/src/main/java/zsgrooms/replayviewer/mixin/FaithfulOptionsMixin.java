// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import net.minecraft.client.options.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.replayviewer.ReplayViewer;

@Mixin(GameOptions.class)
public abstract class FaithfulOptionsMixin {
    @Inject(method = "write", at = @At("HEAD"))
    private void zsgViewer$saveRealOptions(CallbackInfo ci) {
        ReplayViewer.releaseFaithfulOptions();
    }
}
