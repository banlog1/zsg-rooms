// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiScreen;
import com.replaymod.replay.gui.screen.GuiReplayViewer;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.replayviewer.ReplayBrowserScreen;

/** Retain ReplayMod's title-menu entry while replacing only its file chooser. */
@Mixin(value = AbstractGuiScreen.class, remap = false)
public abstract class ReplayBrowserChooserMixin {
    @Inject(method = "display", at = @At("HEAD"), cancellable = true, remap = false)
    private void zsgViewer$openBrowser(CallbackInfo ci) {
        if (!((Object) this instanceof GuiReplayViewer)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        client.openScreen(new ReplayBrowserScreen(client.currentScreen));
        ci.cancel();
    }
}
