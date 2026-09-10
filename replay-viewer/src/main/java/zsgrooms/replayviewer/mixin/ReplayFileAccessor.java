// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import com.replaymod.replaystudio.replay.ZipReplayFile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.io.File;

@Mixin(value = ZipReplayFile.class, remap = false)
public interface ReplayFileAccessor {
    @Accessor("input") File zsgViewer$getInput();
}
