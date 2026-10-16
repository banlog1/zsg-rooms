package zsgrooms.modid.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.mob.PiglinBrain;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import zsgrooms.modid.replay.ReplayPrototype;

import java.util.List;

@Mixin(PiglinBrain.class)
public abstract class ReplayPiglinBarterMixin {
    @WrapOperation(method = "consumeOffHandItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/mob/PiglinBrain;getBarteredItem(Lnet/minecraft/entity/mob/PiglinEntity;)Ljava/util/List;"))
    private static List<ItemStack> zsgRooms$completedBarter(PiglinEntity piglin, Operation<List<ItemStack>> original) {
        // Observe the actual return, including the cancellable standardized/boosted HEAD hook.
        List<ItemStack> result = original.call(piglin);
        ReplayPrototype.completedPiglinBarter(piglin, result);
        return result;
    }
}
