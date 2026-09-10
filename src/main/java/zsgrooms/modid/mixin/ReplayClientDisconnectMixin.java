package zsgrooms.modid.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.replay.ReplayPrototype;

@Mixin(MinecraftClient.class)
public abstract class ReplayClientDisconnectMixin {
    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("HEAD"))
    private void zsgRooms$saveReplayBeforeDisconnect(Screen screen, CallbackInfo ci) {
        // Vanilla clears queued client tasks here, including a queued channelInactive callback.
        ReplayPrototype.clientDisconnect();
    }
}
