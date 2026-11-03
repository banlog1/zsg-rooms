// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector3f;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.replayviewer.PresentationPlayback;
import zsgrooms.replayviewer.PresentationState;

/** Invokes vanilla's item animation code with recorded render inputs, never altering the player. */
@Mixin(HeldItemRenderer.class)
public abstract class PresentationHeldMixin {
    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("HEAD"), cancellable = true)
    private void zsgPresentation$render(float delta, MatrixStack matrices, VertexConsumerProvider.Immediate consumers,
                                       ClientPlayerEntity player, int light, CallbackInfo ci) {
        PresentationState state = PresentationPlayback.current();
        if (state == null) return;
        if (state.hands()) {
            matrices.multiply(Vector3f.POSITIVE_X.getDegreesQuaternion(state.swayPitch));
            matrices.multiply(Vector3f.POSITIVE_Y.getDegreesQuaternion(state.swayYaw));
            float useDelta = (float) Math.floor(state.useRemaining) + 1 - state.useRemaining;
            PresentationHeldAccess renderer = (PresentationHeldAccess) this;
            PresentationPlayback.beginHand();
            try {
                if (state.showMain()) renderer.zsgPresentation$renderHand(player, useDelta, state.pitch, Hand.MAIN_HAND,
                        state.swingOff() ? 0 : state.swing, state.main, state.mainEquip, matrices, consumers, light);
                if (state.showOff()) renderer.zsgPresentation$renderHand(player, useDelta, state.pitch, Hand.OFF_HAND,
                        state.swingOff() ? state.swing : 0, state.off, state.offEquip, matrices, consumers, light);
            } finally { PresentationPlayback.endHand(); }
            consumers.draw();
        }
        ci.cancel();
    }

    @Redirect(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;isUsingItem()Z"))
    private boolean zsgPresentation$using(AbstractClientPlayerEntity player) {
        PresentationState state = PresentationPlayback.handState();
        return state == null ? player.isUsingItem() : state.using();
    }

    @Redirect(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;getItemUseTimeLeft()I"))
    private int zsgPresentation$remaining(AbstractClientPlayerEntity player) {
        PresentationState state = PresentationPlayback.handState();
        return state == null ? player.getItemUseTimeLeft() : (int) Math.floor(state.useRemaining);
    }

    @Redirect(method = {"renderFirstPersonItem", "applyEatOrDrinkTransformation"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getItemUseTimeLeft()I"))
    private int zsgPresentation$clientRemaining(ClientPlayerEntity player) {
        PresentationState state = PresentationPlayback.handState();
        return state == null ? player.getItemUseTimeLeft() : (int) Math.floor(state.useRemaining);
    }

    @Redirect(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;getActiveHand()Lnet/minecraft/util/Hand;"))
    private Hand zsgPresentation$activeHand(AbstractClientPlayerEntity player) {
        PresentationState state = PresentationPlayback.handState();
        return state == null ? player.getActiveHand() : state.activeOff() ? Hand.OFF_HAND : Hand.MAIN_HAND;
    }

    @Redirect(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;isUsingRiptide()Z"))
    private boolean zsgPresentation$riptide(AbstractClientPlayerEntity player) {
        PresentationState state = PresentationPlayback.handState();
        return state == null ? player.isUsingRiptide() : state.riptide();
    }

    @Redirect(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;getMainArm()Lnet/minecraft/util/Arm;"))
    private Arm zsgPresentation$mainArm(AbstractClientPlayerEntity player) {
        PresentationState state = PresentationPlayback.handState();
        return state == null ? player.getMainArm() : state.leftMain() ? Arm.LEFT : Arm.RIGHT;
    }
}
