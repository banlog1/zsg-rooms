// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zsgrooms.replayviewer.ReplayViewer;
import zsgrooms.replayviewer.PresentationPlayback;
import zsgrooms.replayviewer.PresentationState;

/** Paused seeks do not tick vanilla's held-item cache. Read the captured hands for rendering only. */
@Mixin(HeldItemRenderer.class)
public abstract class FaithfulHeldRendererMixin {
    @Shadow private ItemStack mainHand;
    @Shadow private ItemStack offHand;

    @Redirect(method = {"renderItem", "renderMapInBothHands"}, at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;mainHand:Lnet/minecraft/item/ItemStack;"))
    private ItemStack zsgViewer$recordedMainHand(HeldItemRenderer renderer) {
        ItemStack captured = captured(EquipmentSlot.MAINHAND);
        return captured == null ? mainHand : captured;
    }

    @Redirect(method = {"renderItem", "renderFirstPersonItem"}, at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;offHand:Lnet/minecraft/item/ItemStack;"))
    private ItemStack zsgViewer$recordedOffHand(HeldItemRenderer renderer) {
        ItemStack captured = captured(EquipmentSlot.OFFHAND);
        return captured == null ? offHand : captured;
    }

    private static ItemStack captured(EquipmentSlot slot) {
        PresentationState presentation = PresentationPlayback.handState();
        if (presentation != null) return slot == EquipmentSlot.MAINHAND ? presentation.main : presentation.off;
        MinecraftClient client = MinecraftClient.getInstance();
        return ReplayViewer.faithfulActive() && client.player != null ? ReplayViewer.quickHeldItem(client.player, slot) : null;
    }
}
