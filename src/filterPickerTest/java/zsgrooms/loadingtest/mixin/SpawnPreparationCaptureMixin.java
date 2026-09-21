package zsgrooms.loadingtest.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.SpawnPreparationSmoke;

@Mixin(MinecraftServer.class)
public abstract class SpawnPreparationCaptureMixin {
    @Inject(method = "createWorlds", at = @At("TAIL"))
    private void original(WorldGenerationProgressListener listener, CallbackInfo ci) {
        SpawnPreparationSmoke.original((MinecraftServer) (Object) this);
    }

    @Inject(method = "prepareStartRegion", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/WorldGenerationProgressListener;start(Lnet/minecraft/util/math/ChunkPos;)V"))
    private void preload(WorldGenerationProgressListener listener, CallbackInfo ci) {
        SpawnPreparationSmoke.preload((MinecraftServer) (Object) this);
    }
}
