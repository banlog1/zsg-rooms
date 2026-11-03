package zsgrooms.modid.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.replay.ReplayLocalSoundCapture;

@Mixin(ClientPlayerEntity.class)
public abstract class ReplayLocalPlayerSoundMixin {
    @Inject(method = "playSound(Lnet/minecraft/sound/SoundEvent;FF)V", at = @At("HEAD"))
    private void zsgRooms$localPlayerSound(SoundEvent sound, float volume, float pitch, CallbackInfo ci) {
        ReplayLocalSoundCapture.player((PlayerEntity) (Object) this, sound, SoundCategory.PLAYERS, volume, pitch);
    }

    @Inject(method = "playSound(Lnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FF)V", at = @At("HEAD"))
    private void zsgRooms$categorizedPlayerSound(SoundEvent sound, SoundCategory category, float volume, float pitch, CallbackInfo ci) {
        ReplayLocalSoundCapture.player((PlayerEntity) (Object) this, sound, category, volume, pitch);
    }
}
