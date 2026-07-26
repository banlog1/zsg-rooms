package zsgrooms.modid.mixin;

import net.minecraft.entity.EyeOfEnderEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zsgrooms.modid.RngStandardization;

import java.util.Random;

@Mixin(EyeOfEnderEntity.class)
public abstract class EyeOfEnderEntityMixin {
    @Redirect(
            method = "moveTowards",
            at = @At(value = "INVOKE", target = "Ljava/util/Random;nextInt(I)I")
    )
    private int zsgRooms$standardizeBreakRoll(Random vanillaRandom, int bound) {
        EyeOfEnderEntity eye = (EyeOfEnderEntity) (Object) this;
        if (bound == 5 && RngStandardization.isEnabled() && eye.world instanceof ServerWorld) {
            return RngStandardization.nextEyeBreakRandom((ServerWorld) eye.world).nextInt(bound);
        }
        return vanillaRandom.nextInt(bound);
    }
}
