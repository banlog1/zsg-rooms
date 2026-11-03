package zsgrooms.modid.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import zsgrooms.modid.replay.ReplayLocalSoundCapture;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ReplayLocalMiningSoundMixin {
    @ModifyArgs(method = "updateBlockBreakingProgress", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sound/PositionedSoundInstance;<init>(Lnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FFLnet/minecraft/util/math/BlockPos;)V"))
    private void zsgRooms$localMiningSound(Args args) {
        // Observe raw multipliers; SoundInstance getters require a resolved sound and include its variant multipliers.
        ReplayLocalSoundCapture.mining(args.get(0), args.get(1), args.get(2), args.get(3), args.get(4));
    }
}
