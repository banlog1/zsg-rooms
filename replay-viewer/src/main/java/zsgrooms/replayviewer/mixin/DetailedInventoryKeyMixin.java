// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.replayviewer.ReplayViewer;

@Mixin(Keyboard.class)
public abstract class DetailedInventoryKeyMixin {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void zsgViewer$inventory(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (window == MinecraftClient.getInstance().getWindow().getHandle() && ReplayViewer.inventoryKey(key, scanCode, action)) ci.cancel();
    }
}
