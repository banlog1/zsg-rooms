// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.replaystudio.PacketData;
import com.replaymod.replaystudio.io.ReplayInputStream;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion;
import com.replaymod.replaystudio.protocol.PacketType;
import com.replaymod.replaystudio.protocol.PacketTypeRegistry;
import com.replaymod.replaystudio.replay.ReplayFile;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.AdvancementUpdateS2CPacket;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One streaming, read-only scan per opened replay; never seeks the active player. */
final class MilestoneIndex implements AutoCloseable {
    volatile List<Milestones.Entry> entries = Collections.emptyList();
    volatile String status = "Indexing milestones...";
    private volatile boolean cancelled;
    private final Thread worker;

    MilestoneIndex(ReplayFile file) {
        int packetId = NetworkState.PLAY.getPacketId(NetworkSide.CLIENTBOUND, new AdvancementUpdateS2CPacket());
        worker = new Thread(() -> scan(file, packetId), "ZSG replay milestones");
        worker.setDaemon(true);
        worker.start();
    }

    private void scan(ReplayFile file, int packetId) {
        Milestones tracker = new Milestones();
        Map<Identifier, Advancement> definitions = new HashMap<>();
        try {
            if (file.getMetaData().getRawProtocolVersion() != 736) {
                status = "Milestones require a Minecraft 1.16.1 recording";
                return;
            }
            try (ReplayInputStream input = file.getPacketData(PacketTypeRegistry.get(ProtocolVersion.v1_16_1, State.LOGIN))) {
                PacketData data;
                while (!cancelled && (data = input.readPacket()) != null) {
                    try {
                        if (data.getPacket().getType() == PacketType.JoinGame) {
                            tracker.newWorld();
                            definitions.clear();
                        } else if (data.getPacket().getRegistry().getState() == State.PLAY && data.getPacket().getId() == packetId) {
                            AdvancementUpdateS2CPacket packet = new AdvancementUpdateS2CPacket();
                            packet.read(new PacketByteBuf(data.getPacket().getBuf().duplicate()));
                            if (packet.shouldClearCurrent()) definitions.clear();
                            for (Identifier id : packet.getAdvancementIdsToRemove()) definitions.remove(id);
                            for (Map.Entry<Identifier, Advancement.Task> entry : packet.getAdvancementsToEarn().entrySet()) {
                                if (Milestones.Kind.fromId(entry.getKey().toString()) != null) {
                                    // Only criteria/requirements matter here; no client advancement tree is changed.
                                    definitions.put(entry.getKey(), entry.getValue().parent((Identifier) null).build(entry.getKey()));
                                }
                            }
                            for (Map.Entry<Identifier, AdvancementProgress> entry : packet.getAdvancementsToProgress().entrySet()) {
                                Advancement definition = definitions.get(entry.getKey());
                                if (definition == null) continue;
                                AdvancementProgress progress = entry.getValue();
                                progress.init(definition.getCriteria(), definition.getRequirements());
                                tracker.observe(Milestones.Kind.fromId(entry.getKey().toString()), progress.isDone(),
                                        packet.shouldClearCurrent(), (int) data.getTime());
                            }
                        }
                    } finally {
                        data.release();
                    }
                }
            }
            if (!cancelled) {
                entries = tracker.snapshot();
                status = entries.isEmpty() ? "No recorded milestones" : "Milestones";
                LogManager.getLogger("ZSG Replay Viewer").info("Indexed {} replay milestones", entries.size());
            }
        } catch (Exception error) {
            if (!cancelled) {
                status = "Milestone indexing unavailable";
                LogManager.getLogger("ZSG Replay Viewer").warn("Milestone indexing failed: {}", error.getClass().getSimpleName());
            }
        }
    }

    @Override public void close() { cancelled = true; worker.interrupt(); }
}
