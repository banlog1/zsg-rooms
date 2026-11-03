package zsgrooms.modid.mixin;

import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import zsgrooms.modid.replay.ReplayPresentationCapture;
import zsgrooms.modid.replay.ReplayPresentationHeldAccess;

@Mixin(HeldItemRenderer.class)
public abstract class ReplayPresentationHeldMixin implements ReplayPresentationHeldAccess {
    @Accessor("mainHand") public abstract ItemStack zsgPresentation$mainHand();
    @Accessor("offHand") public abstract ItemStack zsgPresentation$offHand();
    @Accessor("equipProgressMainHand") public abstract float zsgPresentation$mainEquip();
    @Accessor("prevEquipProgressMainHand") public abstract float zsgPresentation$previousMainEquip();
    @Accessor("equipProgressOffHand") public abstract float zsgPresentation$offEquip();
    @Accessor("prevEquipProgressOffHand") public abstract float zsgPresentation$previousOffEquip();

    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("HEAD"))
    private void zsgPresentation$hands(float delta, MatrixStack matrices, VertexConsumerProvider.Immediate consumers,
                                      ClientPlayerEntity player, int light, CallbackInfo ci) {
        ReplayPresentationCapture.hands(this, player, delta);
    }
}
