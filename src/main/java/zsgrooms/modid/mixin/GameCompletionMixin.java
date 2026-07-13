package zsgrooms.modid.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.ZsgRoomsClient;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class GameCompletionMixin {
    @Inject(method = "onGameStateChange", at = @At("TAIL"))
    private void zsgRooms$detectEndExitPortal(GameStateChangeS2CPacket packet, CallbackInfo ci) {
        if (packet.getReason() == GameStateChangeS2CPacket.GAME_WON) {
            ZsgRoomsClient.onEndExitPortalEntered();
        }
    }
}
