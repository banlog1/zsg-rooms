package zsgrooms.modid.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.replay.ReplayPrototype;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ReplayWorldChangeMixin {
    @Shadow @Final private ClientConnection connection;

    @Inject(method = {"onGameJoin", "onPlayerRespawn"}, at = @At("TAIL"))
    private void zsgRooms$worldReady(CallbackInfo ci) {
        ReplayPrototype.worldApplied(connection);
    }
}
