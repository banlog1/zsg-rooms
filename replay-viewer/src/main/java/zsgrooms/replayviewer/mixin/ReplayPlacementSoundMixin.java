// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zsgrooms.replayviewer.ReplayAudio;

@Mixin(ClientWorld.class)
public abstract class ReplayPlacementSoundMixin {
    @Inject(method = "setBlockStateWithoutNeighborUpdates", at = @At("HEAD"))
    private void zsgViewer$placement(BlockPos pos, BlockState state, CallbackInfo ci) {
        ReplayAudio.blockUpdate((ClientWorld) (Object) this, pos, state);
    }
}
