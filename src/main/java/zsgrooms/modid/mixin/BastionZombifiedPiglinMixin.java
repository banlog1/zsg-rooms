package zsgrooms.modid.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zsgrooms.modid.BastionZombifiedPiglinControl;

@Mixin(ServerWorld.class)
public abstract class BastionZombifiedPiglinMixin {
    @Inject(method = {"addEntity", "loadEntity"}, at = @At("HEAD"), cancellable = true)
    private void zsgRooms$removeZombifiedPiglinsInsideBastions(Entity entity,
            CallbackInfoReturnable<Boolean> cir) {
        if (BastionZombifiedPiglinControl.shouldReject((ServerWorld) (Object) this, entity)) {
            cir.setReturnValue(false);
        }
    }
}
