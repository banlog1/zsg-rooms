// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.replaystudio.PacketData;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State;
import com.replaymod.replaystudio.protocol.PacketType;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.AdvancementUpdateS2CPacket;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Worker-local advancement state. Packet ownership stays with the caller; no client tree is touched. */
final class MilestonePacketTracker {
    private final int packetId;
    private final Milestones milestones = new Milestones();
    private final Map<Identifier, Advancement> definitions = new HashMap<>();

    MilestonePacketTracker(int packetId) { this.packetId = packetId; }

    void packet(PacketData data) throws IOException {
        if (data.getPacket().getType() == PacketType.JoinGame) {
            milestones.newWorld();
            definitions.clear();
        } else if (data.getPacket().getRegistry().getState() == State.PLAY
                && data.getPacket().getId() == packetId) {
            AdvancementUpdateS2CPacket packet = new AdvancementUpdateS2CPacket();
            packet.read(new PacketByteBuf(data.getPacket().getBuf().duplicate()));
            if (packet.shouldClearCurrent()) definitions.clear();
            for (Identifier id : packet.getAdvancementIdsToRemove()) definitions.remove(id);
            for (Map.Entry<Identifier, Advancement.Task> entry : packet.getAdvancementsToEarn().entrySet()) {
                if (Milestones.Kind.fromId(entry.getKey().toString()) != null) {
                    definitions.put(entry.getKey(), entry.getValue().parent((Identifier) null).build(entry.getKey()));
                }
            }
            for (Map.Entry<Identifier, AdvancementProgress> entry : packet.getAdvancementsToProgress().entrySet()) {
                Advancement definition = definitions.get(entry.getKey());
                if (definition == null) continue;
                AdvancementProgress progress = entry.getValue();
                progress.init(definition.getCriteria(), definition.getRequirements());
                milestones.observe(Milestones.Kind.fromId(entry.getKey().toString()), progress.isDone(),
                        packet.shouldClearCurrent(), (int) data.getTime());
            }
        }
    }

    List<Milestones.Entry> snapshot() { return milestones.snapshot(); }
}
