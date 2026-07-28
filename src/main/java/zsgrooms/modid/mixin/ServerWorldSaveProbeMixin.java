package zsgrooms.modid.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.ServerSaveProbe;

@Mixin(ServerWorld.class)
public abstract class ServerWorldSaveProbeMixin {
    @Inject(
            method = "save(Lnet/minecraft/util/ProgressListener;ZZ)V",
            at = @At("HEAD")
    )
    private void zsgRooms$beginSaveProbe(
            ProgressListener progressListener,
            boolean flush,
            boolean savingDisabled,
            CallbackInfo ci
    ) {
        ServerSaveProbe.beginDimensionSave(
                (ServerWorld) (Object) this, flush, savingDisabled);
    }

    @Inject(
            method = "save(Lnet/minecraft/util/ProgressListener;ZZ)V",
            at = @At("RETURN")
    )
    private void zsgRooms$endSaveProbe(
            ProgressListener progressListener,
            boolean flush,
            boolean savingDisabled,
            CallbackInfo ci
    ) {
        ServerSaveProbe.endDimensionSave();
    }
}
