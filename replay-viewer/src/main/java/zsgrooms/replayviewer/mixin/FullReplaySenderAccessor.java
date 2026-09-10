// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import com.replaymod.replay.FullReplaySender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = FullReplaySender.class, remap = false)
public interface FullReplaySenderAccessor {
    @Accessor("startFromBeginning") void zsgViewer$setStartFromBeginning(boolean value);
}
