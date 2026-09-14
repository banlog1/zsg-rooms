package zsgrooms.modid.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zsgrooms.modid.SharedNetherEntry;

@Mixin(ServerPlayerEntity.class)
public abstract class SharedNetherEntryMixin {
    @Inject(method = "changeDimension", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/PortalForcer;usePortal(Lnet/minecraft/entity/Entity;F)Z",
            ordinal = 0))
    private void zsgRooms$sharedFirstEntry(ServerWorld destination, CallbackInfoReturnable<Entity> ci) {
        SharedNetherEntry.beginTransfer((ServerPlayerEntity) (Object) this, destination);
    }

    @Inject(method = "changeDimension", at = @At("RETURN"))
    private void zsgRooms$completeFirstEntry(ServerWorld destination, CallbackInfoReturnable<Entity> ci) {
        SharedNetherEntry.finishTransfer((ServerPlayerEntity) (Object) this, ci.getReturnValue());
    }
}
