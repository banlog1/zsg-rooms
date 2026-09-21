package zsgrooms.modid.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.ZsgRooms;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerSpawnPreparationMixin {
    @Inject(method = "prepareStartRegion", at = @At("HEAD"))
    private void zsgRooms$prepareDestination(WorldGenerationProgressListener listener, CallbackInfo ci) {
        ZsgRooms.prepareRoomSpawn((MinecraftServer) (Object) this);
    }
}
