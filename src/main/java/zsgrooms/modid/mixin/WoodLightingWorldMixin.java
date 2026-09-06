package zsgrooms.modid.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.WoodLightingStandardization;

@Mixin(ServerWorld.class)
public abstract class WoodLightingWorldMixin {
    // Vanilla returns early for changes that do not affect a point of interest.
    @Inject(method = "onBlockChanged", at = @At("RETURN"))
    private void zsgRooms$trackWoodLighting(BlockPos pos, BlockState oldState, BlockState state, CallbackInfo ci) {
        WoodLightingStandardization.onBlockChanged((ServerWorld) (Object) this, pos, oldState, state);
    }
}
