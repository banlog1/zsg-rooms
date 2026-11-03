package zsgrooms.modid.mixin;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.modid.replay.ReplayPrototype;

@Mixin(GameRenderer.class)
public abstract class ReplayCameraCaptureMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void zsgRooms$captureCamera(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        ReplayPrototype.captureCamera();
    }
}
