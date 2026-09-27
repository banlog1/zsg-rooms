package zsgrooms.modid.mixin;

import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.NetherPortalPreloader;

@Mixin(PlayerManager.class)
public abstract class NetherPreloadDisconnectMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void zsgRooms$releaseNetherWarmup(ServerPlayerEntity player, CallbackInfo ci) {
        NetherPortalPreloader.disconnect(player);
    }
}
