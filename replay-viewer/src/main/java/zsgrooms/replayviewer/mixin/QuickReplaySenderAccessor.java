// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import com.replaymod.replay.QuickReplaySender;
import com.replaymod.replay.ReplayHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ReplayHandler.class, remap = false)
public interface QuickReplaySenderAccessor {
    @Accessor("quickReplaySender") QuickReplaySender zsgViewer$getQuickSender();
}
