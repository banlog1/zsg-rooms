package zsgrooms.modid.mixin;

import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.phase.AbstractPhase;
import net.minecraft.entity.boss.dragon.phase.HoldingPatternPhase;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zsgrooms.modid.RngStandardization;

import java.util.Random;

@Mixin(HoldingPatternPhase.class)
public abstract class HoldingPatternPhaseMixin extends AbstractPhase {
    protected HoldingPatternPhaseMixin(EnderDragonEntity dragon) {
        super(dragon);
    }

    @Redirect(
            method = "method_6841",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Random;nextInt(I)I",
                    ordinal = 0
            )
    )
    private int zsgRooms$standardizePerchRoll(Random vanillaRandom, int bound) {
        if (dragon.world instanceof ServerWorld) {
            return RngStandardization.nextDragonPerchRoll(
                    vanillaRandom,
                    bound,
                    dragon.age,
                    ((ServerWorld) dragon.world).getSeed());
        }
        return vanillaRandom.nextInt(bound);
    }
}
