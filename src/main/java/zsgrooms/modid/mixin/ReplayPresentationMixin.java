package zsgrooms.modid.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zsgrooms.modid.replay.ReplayPresentationCapture;

/** Runs after ordinary FOV modifiers, without modifying their result. */
@Mixin(value = GameRenderer.class, priority = 500)
public abstract class ReplayPresentationMixin {
    @Shadow private float zoom;
    @Shadow private float zoomX;
    @Shadow private float zoomY;

    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void zsgPresentation$begin(float delta, long time, MatrixStack matrices, CallbackInfo ci) {
        ReplayPresentationCapture.beginFrame(delta);
    }

    @Inject(method = "getFov", at = @At("RETURN"))
    private void zsgPresentation$fov(Camera camera, float delta, boolean world, CallbackInfoReturnable<Double> cir) {
        ReplayPresentationCapture.fov(world, cir.getReturnValueD(), zoom, zoomX, zoomY);
    }

    @Inject(method = "renderWorld", at = @At("RETURN"))
    private void zsgPresentation$end(float delta, long time, MatrixStack matrices, CallbackInfo ci) {
        ReplayPresentationCapture.endFrame();
    }
}
