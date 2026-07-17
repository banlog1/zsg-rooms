package zsgrooms.modid.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.ZsgRoomsClient;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerChatMixin {
    @Inject(method = "sendChatMessage(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void zsgRooms$sendActiveRoomChat(String message, CallbackInfo callback) {
        if (ZsgRoomsClient.sendActiveRoomChat(MinecraftClient.getInstance(), message)) {
            callback.cancel();
        }
    }
}
