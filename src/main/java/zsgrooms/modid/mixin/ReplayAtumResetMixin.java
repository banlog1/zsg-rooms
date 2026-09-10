package zsgrooms.modid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.replay.ReplayPrototype;

@Pseudo
@Mixin(targets = "me.voidxwalker.autoreset.Atum", remap = false)
public abstract class ReplayAtumResetMixin {
    @Shadow private static boolean shouldReset;

    @Inject(method = "scheduleReset", at = @At("RETURN"), remap = false)
    private static void zsgRooms$continueReplayOnReset(CallbackInfo ci) {
        if (shouldReset) ReplayPrototype.beginReset();
    }

    @Inject(method = "stopRunning", at = @At("HEAD"), remap = false)
    private static void zsgRooms$cancelReplayReset(CallbackInfo ci) {
        ReplayPrototype.cancelReset();
    }
}
