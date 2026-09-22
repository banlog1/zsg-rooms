// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer.mixin;

import com.replaymod.replaystudio.protocol.Packet;
import com.replaymod.replaystudio.protocol.packets.PacketChunkData;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = PacketChunkData.Chunk.class, remap = false)
public abstract class QuickChunkFormatMixin {
    // ReplayMod 2.6.27 reads fluidCount at 26.1+, but incorrectly writes it at 1.14+.
    @Redirect(method = "write", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lcom/replaymod/replaystudio/protocol/Packet;atLeast(Lcom/replaymod/replaystudio/lib/viaversion/api/protocol/version/ProtocolVersion;)Z"))
    private boolean zsgViewer$fluidCountVersion(Packet packet, ProtocolVersion ignored) {
        return packet.atLeast(ProtocolVersion.v26_1);
    }
}
