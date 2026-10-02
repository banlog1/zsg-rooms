// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import com.replaymod.replay.FullReplaySender;
import com.replaymod.replay.ReplayHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zsgrooms.replayviewer.SeekLighting;
import zsgrooms.replayviewer.SeekLightingAccess;

@Mixin(value = ReplayHandler.class, remap = false)
public abstract class SeekLightingHandlerMixin implements SeekLightingAccess {
    @Unique private boolean zsgViewer$fastSeeking;

    public boolean zsgViewer$fastSeeking() { return zsgViewer$fastSeeking; }
    public void zsgViewer$fastSeeking(boolean enabled) { zsgViewer$fastSeeking = enabled; }

    @Redirect(method = "doJump", at = @At(value = "INVOKE",
            target = "Lcom/replaymod/replay/FullReplaySender;sendPacketsTill(I)V"), require = 2)
    private void zsgViewer$seek(FullReplaySender sender, int time) {
        SeekLighting.send(sender, time, zsgViewer$fastSeeking);
    }
}
