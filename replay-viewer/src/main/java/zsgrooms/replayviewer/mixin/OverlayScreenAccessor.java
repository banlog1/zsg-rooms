// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.replaymod.lib.de.johni0702.minecraft.gui.container.AbstractGuiOverlay$UserInputGuiScreen", remap = false)
public interface OverlayScreenAccessor {
    @Accessor("this$0")
    AbstractGuiOverlay<?> zsgViewer$getOverlay();
}
