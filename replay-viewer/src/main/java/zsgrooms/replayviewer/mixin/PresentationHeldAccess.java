// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(HeldItemRenderer.class)
public interface PresentationHeldAccess {
    @Invoker("renderFirstPersonItem")
    void zsgPresentation$renderHand(AbstractClientPlayerEntity player, float delta, float pitch, Hand hand,
                                    float swing, ItemStack item, float equip, MatrixStack matrices,
                                    VertexConsumerProvider consumers, int light);
}
