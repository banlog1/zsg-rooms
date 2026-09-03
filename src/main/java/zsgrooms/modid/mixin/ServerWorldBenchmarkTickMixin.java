package zsgrooms.modid.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.benchmark.DragonPerchHeadlessBenchmark;

@Mixin(ServerWorld.class)
public abstract class ServerWorldBenchmarkTickMixin {
    @Inject(method = "tickEntity", at = @At("HEAD"), cancellable = true)
    private void zsgRooms$letBenchmarkDriveDragon(Entity entity, CallbackInfo ci) {
        if (DragonPerchHeadlessBenchmark.shouldSuppressWorldTick(entity)) {
            ci.cancel();
        }
    }
}
