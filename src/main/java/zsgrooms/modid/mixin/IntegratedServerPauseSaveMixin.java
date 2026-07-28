package zsgrooms.modid.mixin;

import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zsgrooms.modid.PauseWorldSaveControl;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerPauseSaveMixin {
    @Redirect(
            method = "tick(Ljava/util/function/BooleanSupplier;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/integrated/IntegratedServer;save(ZZZ)Z"
            ),
            require = 1
    )
    private boolean zsgRooms$skipPauseWorldSave(
            IntegratedServer server,
            boolean suppressLogs,
            boolean flush,
            boolean force
    ) {
        if (PauseWorldSaveControl.shouldSkipPauseWorldSave()) {
            PauseWorldSaveControl.onPauseSaveSkipped(server);
            return false;
        }
        return server.save(suppressLogs, flush, force);
    }
}
