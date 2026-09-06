package zsgrooms.modid.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.LavaFluid;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zsgrooms.modid.WoodLightingStandardization;

import java.util.Random;

@Mixin(LavaFluid.class)
public abstract class LavaIgnitionMixin {
    @Redirect(method = "onRandomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)Z"), require = 2)
    private boolean zsgRooms$useLocalIgnitionSchedule(World receiver, BlockPos target, BlockState fire,
            World world, BlockPos source, FluidState fluid, Random random) {
        // Still evaluate the ordinary attempt to consume its original world RNG draws.
        return WoodLightingStandardization.suppressNaturalLava(world, source)
                || receiver.setBlockState(target, fire);
    }
}
