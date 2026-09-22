// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.replayviewer.ReplayViewer;

@Mixin(WorldRenderer.class)
public abstract class DragonTrailRenderMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void zsgViewer$projection(MatrixStack matrices, float delta, long limit, boolean outline, Camera camera,
                                     GameRenderer renderer, LightmapTextureManager lightmap, Matrix4f projection, CallbackInfo ci) {
        ReplayViewer.worldProjection(projection);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void zsgViewer$trail(MatrixStack matrices, float delta, long limit, boolean outline, Camera camera,
                                 GameRenderer renderer, LightmapTextureManager lightmap, Matrix4f projection, CallbackInfo ci) {
        ReplayViewer.renderTrails(matrices, camera);
    }
}
