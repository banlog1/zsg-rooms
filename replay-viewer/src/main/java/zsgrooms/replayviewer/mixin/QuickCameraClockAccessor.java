// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import com.replaymod.replay.QuickReplaySender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = QuickReplaySender.class, remap = false)
public interface QuickCameraClockAccessor {
    @Accessor("lastAsyncUpdateTime") long zsgViewer$cameraUpdateTime();
}
