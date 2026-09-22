// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.replayviewer.ReplayAudio;

@Mixin(OtherClientPlayerEntity.class)
public abstract class ReplayPlayerSoundsMixin {
    @Inject(method = "tickMovement", at = @At("TAIL"))
    private void zsgViewer$playerSounds(CallbackInfo ci) {
        ReplayAudio.tick((PlayerEntity) (Object) this);
    }
}
