// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import com.replaymod.replay.ReplayHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.replayviewer.ReplayViewer;

@Mixin(value = ReplayHandler.class, remap = false)
public abstract class RecordedCameraSeekMixin {
    @Inject(method = {"doJump", "setQuickMode", "restartedReplay"}, at = @At("HEAD"))
    private void zsgViewer$beginCameraSeek(CallbackInfo ci) {
        ReplayViewer.cameraDiscontinuity((ReplayHandler) (Object) this, true);
    }

    @Inject(method = {"doJump", "setQuickMode", "restartedReplay"}, at = @At("RETURN"))
    private void zsgViewer$endCameraSeek(CallbackInfo ci) {
        ReplayViewer.cameraDiscontinuity((ReplayHandler) (Object) this, false);
    }
}
