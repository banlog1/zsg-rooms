// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector3f;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zsgrooms.replayviewer.PresentationPlayback;
import zsgrooms.replayviewer.PresentationState;

@Mixin(GameRenderer.class)
public abstract class PresentationRendererMixin {
    @Shadow private float viewDistance;

    @Inject(method = "getBasicProjectionMatrix", at = @At("HEAD"), cancellable = true)
    private void zsgPresentation$projection(Camera camera, float delta, boolean world, CallbackInfoReturnable<Matrix4f> cir) {
        PresentationState state = PresentationPlayback.current();
        if (state == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack stack = new MatrixStack();
        float zoom = world ? state.worldZoom : state.handZoom;
        float x = world ? state.worldZoomX : state.handZoomX, y = world ? state.worldZoomY : state.handZoomY;
        if (zoom != 1) { stack.translate(x, -y, 0); stack.scale(zoom, zoom, 1); }
        stack.peek().getModel().multiply(Matrix4f.viewboxMatrix(world ? state.worldFov : state.handFov,
                (float) client.getWindow().getFramebufferWidth() / client.getWindow().getFramebufferHeight(), .05F, viewDistance * 4));
        cir.setReturnValue(stack.peek().getModel());
    }

    @Redirect(method = {"renderWorld", "renderHand"}, at = @At(value = "FIELD", target = "Lnet/minecraft/client/options/GameOptions;bobView:Z"))
    private boolean zsgPresentation$bobEnabled(GameOptions options) {
        PresentationState state = PresentationPlayback.current();
        return state == null ? options.bobView : state.bob();
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void zsgPresentation$bob(MatrixStack matrices, float delta, CallbackInfo ci) {
        PresentationState state = PresentationPlayback.current();
        if (state == null) return;
        if (state.bob()) {
            float phase = state.bobPhase * (float) Math.PI, stride = state.bobStride;
            matrices.translate(MathHelper.sin(phase) * stride * .5F, -Math.abs(MathHelper.cos(phase) * stride), 0);
            matrices.multiply(Vector3f.POSITIVE_Z.getDegreesQuaternion(MathHelper.sin(phase) * stride * 3));
            matrices.multiply(Vector3f.POSITIVE_X.getDegreesQuaternion(Math.abs(MathHelper.cos(phase - .2F) * stride) * 5));
        }
        ci.cancel();
    }
}
