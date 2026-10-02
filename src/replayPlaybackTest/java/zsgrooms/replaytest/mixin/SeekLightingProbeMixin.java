package zsgrooms.replaytest.mixin;

import com.replaymod.replay.FullReplaySender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.replaytest.SeekLightingProbe;

/** Development-only hooks. Never loaded by a release mod. */
@Mixin(value = FullReplaySender.class, remap = false)
public abstract class SeekLightingProbeMixin {
    @Inject(method = "sendPacketsTill", at = @At("HEAD"))
    private void begin(int time, CallbackInfo ci) { SeekLightingProbe.begin(); }

    @Inject(method = "sendPacketsTill", at = @At("RETURN"))
    private void end(int time, CallbackInfo ci) { SeekLightingProbe.end(); }

    @Inject(method = "lambda$channelRead$0", at = @At("HEAD"), cancellable = true)
    private void light(CallbackInfo ci) { if (SeekLightingProbe.light()) ci.cancel(); }

    @Inject(method = "lambda$channelRead$0", at = @At("RETURN"))
    private void lit(CallbackInfo ci) { SeekLightingProbe.lit(); }
}
