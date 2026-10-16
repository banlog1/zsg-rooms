// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.advanced.AbstractGuiTimeline;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.replayviewer.ReplayViewer;
import zsgrooms.replayviewer.ViewerChrome;

@Mixin(value = AbstractGuiTimeline.class, remap = false)
public abstract class CompactTimelineMixin {
    @Shadow protected ReadableDimension size;

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void zsgViewer$compact(GuiRenderer renderer, ReadableDimension dimensions, RenderInfo info, CallbackInfo ci) {
        if (ReplayViewer.compactWidget(this) != 3) return;
        // Native hit testing needs this draw-time size; GuiMarkerTimeline still draws its markers afterward.
        size = dimensions;
        ViewerChrome.timeline((AbstractGuiTimeline<?>) (Object) this, renderer, dimensions);
        ci.cancel();
    }
}
