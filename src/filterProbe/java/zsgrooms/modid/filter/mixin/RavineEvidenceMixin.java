package zsgrooms.modid.filter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.gen.carver.RavineCarver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import zsgrooms.modid.filter.RavineEvidence;
import java.util.Random;

@Mixin(RavineCarver.class)
public abstract class RavineEvidenceMixin {
    // The second nextInt is the per-step nextInt(4), after vanilla updates the path and radii.
    @WrapOperation(method = "carveRavine", at = @At(value = "INVOKE", target = "Ljava/util/Random;nextInt(I)I", ordinal = 1))
    private int zsgRooms$middle(Random random, int bound, Operation<Integer> original,
                               @Local(argsOnly = true, ordinal = 0) double x,
                               @Local(argsOnly = true, ordinal = 1) double y,
                               @Local(argsOnly = true, ordinal = 2) double z,
                               @Local(ordinal = 5) double verticalRadius,
                               @Local(argsOnly = true, ordinal = 4) int length,
                               @Local(ordinal = 5) int step) {
        if (step == length / 2) RavineEvidence.middle(x, y, z, verticalRadius);
        return original.call(random, bound);
    }
}
