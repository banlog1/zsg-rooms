// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zsgrooms.replayviewer.ReplayViewer;

@Mixin(PlayerEntity.class)
public abstract class QuickHeldItemsMixin {
    @Inject(method = "getEquippedStack", at = @At("HEAD"), cancellable = true)
    private void zsgViewer$heldItem(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> ci) {
        if (slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND) return;
        ItemStack stack = ReplayViewer.quickHeldItem((PlayerEntity)(Object)this, slot);
        if (stack != null) ci.setReturnValue(stack);
    }
}
