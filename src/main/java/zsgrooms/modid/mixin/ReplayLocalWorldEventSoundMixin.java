package zsgrooms.modid.mixin;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.replay.ReplayLocalSoundCapture;

@Mixin(ClientWorld.class)
public abstract class ReplayLocalWorldEventSoundMixin {
    @Inject(method = "syncWorldEvent(Lnet/minecraft/entity/player/PlayerEntity;ILnet/minecraft/util/math/BlockPos;I)V", at = @At("HEAD"))
    private void zsgRooms$localBreakSound(PlayerEntity player, int event, BlockPos pos, int data, CallbackInfo ci) {
        ReplayLocalSoundCapture.worldEvent((ClientWorld) (Object) this, player, event, pos, data);
    }
}
