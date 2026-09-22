// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.replayviewer.ReplayAudio;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ReplayFallSoundMixin {
    @Inject(method = "onEntityPosition", at = @At("TAIL"))
    private void zsgViewer$absoluteMovement(EntityPositionS2CPacket packet, CallbackInfo ci) {
        if (!ReplayAudio.enabledForMovement()) return;
        Entity entity = MinecraftClient.getInstance().world.getEntityById(packet.getId());
        ReplayAudio.movement(entity, packet.getX(), packet.getY(), packet.getZ(), packet.isOnGround());
    }

    @Inject(method = "onEntityUpdate", at = @At("TAIL"))
    private void zsgViewer$relativeMovement(EntityS2CPacket packet, CallbackInfo ci) {
        if (!ReplayAudio.enabledForMovement()) return;
        Entity entity = packet.getEntity(MinecraftClient.getInstance().world);
        if (entity != null) ReplayAudio.movement(entity, entity.trackedX / 4096.0,
                entity.trackedY / 4096.0, entity.trackedZ / 4096.0, packet.isOnGround());
    }
}
