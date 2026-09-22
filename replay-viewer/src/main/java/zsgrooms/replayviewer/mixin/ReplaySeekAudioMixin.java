// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import com.replaymod.replay.ReplayHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.replayviewer.ReplayAudio;

@Mixin(value = ReplayHandler.class, remap = false)
public abstract class ReplaySeekAudioMixin {
    @Inject(method = {"doJump", "setQuickMode"}, at = @At("HEAD"))
    private void zsgViewer$seekAudio(CallbackInfo ci) { ReplayAudio.beginSeek(); }
}
