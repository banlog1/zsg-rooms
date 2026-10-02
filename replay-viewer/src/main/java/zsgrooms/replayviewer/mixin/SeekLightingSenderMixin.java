// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import com.replaymod.replay.FullReplaySender;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.replayviewer.SeekLighting;

@Mixin(value = FullReplaySender.class, remap = false)
public abstract class SeekLightingSenderMixin {
    @Inject(method = "lambda$channelRead$0", at = @At("HEAD"), cancellable = true)
    private void zsgViewer$defer(CallbackInfo ci) { if (SeekLighting.defer()) ci.cancel(); }

    @Inject(method = "processPacketSync", at = @At("HEAD"))
    private void zsgViewer$barrier(Packet<?> packet, CallbackInfo ci) { SeekLighting.beforePacket(packet); }
}
