package zsgrooms.modid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zsgrooms.modid.SpeedRunIgtBridge;

/** Apply before a new/reloaded timer can observe an End exit or advancement. */
@Pseudo
@Mixin(targets = "com.redlimerl.speedrunigt.timer.InGameTimer", remap = false)
public abstract class RoomTimerCategoryMixin {
    @Inject(method = {"start", "reset"}, at = @At("RETURN"), remap = false)
    private static void zsgRooms$selectCategory(CallbackInfo ci) {
        SpeedRunIgtBridge.syncActiveCategory();
    }

    @Inject(method = "load", at = @At("RETURN"), remap = false)
    private static void zsgRooms$restoreCategory(CallbackInfoReturnable<Boolean> cir) {
        SpeedRunIgtBridge.syncActiveCategory();
    }
}
