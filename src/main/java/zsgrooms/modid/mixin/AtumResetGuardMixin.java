package zsgrooms.modid.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.ZsgRooms;

@Pseudo
@Mixin(targets = "me.voidxwalker.autoreset.Atum", remap = false)
public abstract class AtumResetGuardMixin {
    @Inject(method = "scheduleReset", at = @At("HEAD"), cancellable = true, remap = false)
    private static void zsgRooms$blockUnrequestedRoomReset(CallbackInfo ci) {
        if (ZsgRooms.hasManagedRoom()) {
            ci.cancel();
        }
    }
}
