// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import com.replaymod.core.ReplayMod;
import com.replaymod.replay.FullReplaySender;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(value = FullReplaySender.class, remap = false)
abstract class ReplayTerrainScreenMixin {
    @Shadow private void schedulePacketHandler(Runnable task) { throw new AssertionError(); }

    @Redirect(method = "processPacket", slice = @Slice(from = @At(value = "FIELD",
            target = "Lcom/replaymod/replay/FullReplaySender;hasWorldLoaded:Z", opcode = Opcodes.GETFIELD)),
            at = @At(value = "INVOKE", target = "Lcom/replaymod/core/ReplayMod;runLater(Ljava/lang/Runnable;)V"),
            require = 1, allow = 1)
    private void zsgViewer$orderTerrainDismissal(ReplayMod replayMod, Runnable task) {
        // ReplayMod's separate queue can dismiss before JoinGame opens the screen.
        // Keep the original screen check, but order it with Minecraft's packet tasks.
        schedulePacketHandler(task);
    }
}
