package zsgrooms.modid.mixin;

import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zsgrooms.modid.benchmark.DragonPerchHeadlessBenchmark;

@Mixin(EnderDragonFight.class)
public abstract class EnderDragonFightBenchmarkMixin {
    @Inject(method = "createDragon", at = @At("RETURN"))
    private void zsgRooms$captureBenchmarkDragon(
            CallbackInfoReturnable<EnderDragonEntity> cir
    ) {
        DragonPerchHeadlessBenchmark.onNaturalDragonCreated(cir.getReturnValue());
    }
}
