// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import com.replaymod.replaystudio.rar.RandomAccessReplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Do not reuse chunk packets cached before the 1.16.1 fluid-count correction. */
@Mixin(value = RandomAccessReplay.class, remap = false)
public abstract class QuickCacheMixin {
    @ModifyConstant(method = {"tryLoadFromCache", "analyseReplay"},
            constant = @Constant(stringValue = "quickModeCache.bin"), require = 2)
    private String zsgViewer$cache(String original) { return "zsg-quick-v1.bin"; }

    @ModifyConstant(method = {"tryLoadFromCache", "analyseReplay"},
            constant = @Constant(stringValue = "quickModeCacheIndex.bin"), require = 2)
    private String zsgViewer$index(String original) { return "zsg-quick-v1-index.bin"; }
}
