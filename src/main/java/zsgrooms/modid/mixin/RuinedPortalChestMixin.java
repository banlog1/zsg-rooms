package zsgrooms.modid.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.RuinedPortalChestRepair;

@Mixin(ChestBlockEntity.class)
public abstract class RuinedPortalChestMixin {
    @Inject(method = "fromTag", at = @At("TAIL"))
    private void zsgRooms$captureRuinedPortalLoot(BlockState state, CompoundTag tag, CallbackInfo ci) {
        RuinedPortalChestRepair.capture((ChestBlockEntity) (Object) this, state, tag);
    }
}
