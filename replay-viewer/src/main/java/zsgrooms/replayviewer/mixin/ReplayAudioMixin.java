// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.client.sound.TickableSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.replayviewer.ReplayAudio;

@Mixin(SoundSystem.class)
public abstract class ReplayAudioMixin {
    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void zsgViewer$mute(SoundInstance sound, CallbackInfo ci) {
        if (ReplayAudio.muted()) ci.cancel();
    }

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;I)V", at = @At("HEAD"), cancellable = true)
    private void zsgViewer$muteDelayed(SoundInstance sound, int delay, CallbackInfo ci) {
        if (ReplayAudio.muted()) ci.cancel();
    }

    @Inject(method = "playNextTick", at = @At("HEAD"), cancellable = true)
    private void zsgViewer$muteNextTick(TickableSoundInstance sound, CallbackInfo ci) {
        if (ReplayAudio.muted()) ci.cancel();
    }
}
