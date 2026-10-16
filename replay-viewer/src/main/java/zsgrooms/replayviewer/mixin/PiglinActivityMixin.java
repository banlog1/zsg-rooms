// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.PiglinEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.replayviewer.ReplayViewer;

@Mixin(LivingEntityRenderer.class)
public abstract class PiglinActivityMixin {
    @Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("TAIL"))
    private void zsgViewer$piglinActivity(LivingEntity entity, float yaw, float tickDelta,
                                         MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        if (entity instanceof PiglinEntity) {
            ReplayViewer.renderPiglinActivity((PiglinEntity) entity, matrices, consumers, light);
        }
    }
}
