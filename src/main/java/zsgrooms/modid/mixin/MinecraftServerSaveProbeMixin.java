package zsgrooms.modid.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zsgrooms.modid.ServerSaveProbe;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerSaveProbeMixin {
    @Inject(method = "save(ZZZ)Z", at = @At("HEAD"))
    private void zsgRooms$beginSaveProbe(
            boolean suppressLogs,
            boolean flush,
            boolean force,
            CallbackInfoReturnable<Boolean> cir
    ) {
        ServerSaveProbe.beginServerSave(
                (MinecraftServer) (Object) this, suppressLogs, flush, force);
    }

    @Inject(method = "save(ZZZ)Z", at = @At("RETURN"))
    private void zsgRooms$endSaveProbe(
            boolean suppressLogs,
            boolean flush,
            boolean force,
            CallbackInfoReturnable<Boolean> cir
    ) {
        ServerSaveProbe.endServerSave();
    }
}
