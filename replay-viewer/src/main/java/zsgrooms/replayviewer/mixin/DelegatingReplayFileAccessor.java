// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import com.replaymod.core.files.DelegatingReplayFile;
import com.replaymod.replaystudio.replay.ReplayFile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = DelegatingReplayFile.class, remap = false)
public interface DelegatingReplayFileAccessor {
    @Accessor("delegate") ReplayFile zsgViewer$getDelegate();
}
