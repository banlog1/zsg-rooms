// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.AbstractGuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.AbstractGuiSlider;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.replayviewer.ViewerChrome;

@Mixin(value = {AbstractGuiButton.class, AbstractGuiSlider.class}, remap = false)
public abstract class CompactTransportMixin {
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void zsgViewer$compact(GuiRenderer renderer, ReadableDimension size, RenderInfo info, CallbackInfo ci) {
        if (ViewerChrome.transport(this, renderer, size, info)) ci.cancel();
    }
}
