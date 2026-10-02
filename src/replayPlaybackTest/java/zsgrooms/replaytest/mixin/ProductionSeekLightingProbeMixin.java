package zsgrooms.replaytest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zsgrooms.replaytest.SeekLightingProbe;

@Pseudo
@Mixin(targets = "zsgrooms.replayviewer.SeekLighting", remap = false)
public abstract class ProductionSeekLightingProbeMixin {
    @Inject(method = "defer", at = @At("RETURN"))
    private static void deferred(CallbackInfoReturnable<Boolean> ci) {
        SeekLightingProbe.productionDeferred(ci.getReturnValue());
    }
    @Inject(method = "drain", at = @At("HEAD"))
    private static void begin(CallbackInfo ci) { SeekLightingProbe.productionDrainStart(); }
    @Inject(method = "drain", at = @At("RETURN"))
    private static void end(CallbackInfo ci) { SeekLightingProbe.productionDrainEnd(); }
}
