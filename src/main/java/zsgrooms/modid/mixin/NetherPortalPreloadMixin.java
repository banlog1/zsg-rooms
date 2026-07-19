package zsgrooms.modid.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.NetherPortalPreloader;

@Mixin(Entity.class)
public abstract class NetherPortalPreloadMixin {
    @Shadow
    protected boolean inNetherPortal;

    @Inject(method = "tickNetherPortal", at = @At("HEAD"))
    private void zsgRooms$preloadNetherDestination(CallbackInfo ci) {
        Object entity = this;
        if (entity instanceof ServerPlayerEntity) {
            NetherPortalPreloader.tick((ServerPlayerEntity) entity, this.inNetherPortal);
        }
    }
}
