package zsgrooms.modid.filter.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.gen.feature.LakeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zsgrooms.modid.filter.GenerationEvidence;

@Mixin(LakeFeature.class)
public abstract class LakeEvidenceMixin {
    @Redirect(method = "generate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/ServerWorldAccess;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"))
    private boolean zsgRooms$recordLava(ServerWorldAccess world, BlockPos pos, BlockState state, int flags) {
        boolean placed = world.setBlockState(pos, state, flags);
        if (placed && state.isOf(Blocks.LAVA) && state.getFluidState().isStill()) {
            if (world instanceof net.minecraft.world.ChunkRegion) {
                GenerationEvidence.naturalLava(((net.minecraft.world.ChunkRegion) world).getWorld(), pos);
            } else if (world instanceof net.minecraft.server.world.ServerWorld) {
                GenerationEvidence.naturalLava((net.minecraft.server.world.ServerWorld) world, pos);
            }
        }
        return placed;
    }
}
