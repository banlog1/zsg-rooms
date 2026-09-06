package zsgrooms.modid.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.FireBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.WoodLightingStandardization;

import java.util.Random;

@Mixin(FireBlock.class)
public abstract class FireSpreadMixin {
    @Shadow
    protected abstract int getBurnChance(WorldView world, BlockPos pos);

    @Shadow
    protected abstract void trySpreadingFire(World world, BlockPos pos, int spreadFactor, Random random, int age);

    @Inject(method = "scheduledTick", at = @At("HEAD"), cancellable = true)
    private void zsgRooms$useLocalFireSchedule(BlockState state, ServerWorld world, BlockPos pos,
                                              Random random, CallbackInfo ci) {
        if (WoodLightingStandardization.suppressScheduledFire(world, pos)) {
            ci.cancel();
        }
    }

    @Redirect(method = "onBlockAdded", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/FireBlock;method_26155(Ljava/util/Random;)I"))
    private int zsgRooms$initialFireDelay(Random random, BlockState state, World world, BlockPos pos,
                                         BlockState oldState, boolean notify) {
        return WoodLightingStandardization.initialFireDelay(world, pos, random);
    }

    @Redirect(method = "scheduledTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/FireBlock;method_26155(Ljava/util/Random;)I"))
    private int zsgRooms$nextFireDelay(Random random, BlockState state, ServerWorld world, BlockPos pos, Random tickRandom) {
        return WoodLightingStandardization.fireDelay(world, pos, random);
    }

    @Redirect(method = "scheduledTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/FireBlock;trySpreadingFire(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;ILjava/util/Random;I)V"), require = 6)
    private void zsgRooms$independentBurnTarget(FireBlock fire, World world, BlockPos target, int factor, Random random, int age) {
        this.trySpreadingFire(world, target, factor, WoodLightingStandardization.burnRandom(random, target), age);
    }

    @Redirect(method = "scheduledTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/FireBlock;getBurnChance(Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;)I"))
    private int zsgRooms$trackAirTarget(FireBlock fire, WorldView world, BlockPos pos) {
        WoodLightingStandardization.airTarget(pos);
        return this.getBurnChance(world, pos);
    }

    @Redirect(method = "scheduledTick", at = @At(value = "INVOKE", target = "Ljava/util/Random;nextInt(I)I"), require = 4)
    private int zsgRooms$independentAirRoll(Random random, int bound) {
        return WoodLightingStandardization.fireInt(random, bound);
    }
}
