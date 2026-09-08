package zsgrooms.modid.mixin;

import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.EndExitTimeCapture;

import java.util.function.BooleanSupplier;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerRaceClockMixin {
    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;tick(Ljava/util/function/BooleanSupplier;)V"))
    private void zsgRooms$startOnResumedSimulation(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        EndExitTimeCapture.onResumedTick((IntegratedServer) (Object) this);
    }
}
