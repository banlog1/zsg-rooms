// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.replaystudio.PacketData;
import com.replaymod.replaystudio.io.ReplayInputStream;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion;
import com.replaymod.replaystudio.protocol.PacketTypeRegistry;
import com.replaymod.replaystudio.replay.ReplayFile;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkState;
import net.minecraft.network.packet.s2c.play.AdvancementUpdateS2CPacket;
import org.apache.logging.log4j.LogManager;

import java.util.Collections;
import java.util.List;

/** One streaming scan on a cache miss; never seeks the active player. */
final class MilestoneIndex implements AutoCloseable {
    private static final CompletedIndexCache<Result> CACHE = new CompletedIndexCache<>(4, 64L * 1024 * 1024);
    volatile List<Milestones.Entry> entries = Collections.emptyList();
    volatile String status = "Indexing milestones...";
    volatile ChestHistory<net.minecraft.item.ItemStack> chests;
    volatile String chestStatus = "Indexing recorded chests...";
    volatile ChestLootHistory chestLoot;
    volatile boolean complete;
    volatile boolean available;
    private volatile boolean cancelled;
    private final Thread worker;
    private volatile boolean cacheHit;

    MilestoneIndex(ReplayFile file) {
        int packetId = NetworkState.PLAY.getPacketId(NetworkSide.CLIENTBOUND, new AdvancementUpdateS2CPacket());
        worker = new Thread(() -> {
            try { scan(file, packetId); }
            finally { complete = true; }
        }, "ZSG replay milestones");
        worker.setDaemon(true);
        worker.start();
    }

    private void scan(ReplayFile file, int packetId) {
        CompletedIndexCache.Key key = cacheKey(file);
        Result cached = CACHE.get(key);
        if (cached != null) {
            synchronized (this) {
                if (!cancelled) { publish(cached); cacheHit = true; }
            }
            return;
        }
        MilestonePacketTracker tracker = new MilestonePacketTracker(packetId);
        ChestPacketIndex chestIndex = null;
        boolean cacheable = true;
        try {
            java.io.File archive = RecordingIndex.file(file);
            RaceRecording recording = archive == null ? null : RaceRecording.read(archive.toPath());
            if (recording != null && (!recording.chests.isEmpty() || recording.predictionSeed != null)) chestIndex = new ChestPacketIndex(recording.chests, recording.chestLoot);
            else chestStatus = "No recorded chest positions";
        } catch (Exception ignored) { chestStatus = "No recorded chest positions"; cacheable = false; }
        try {
            if (file.getMetaData().getRawProtocolVersion() != 736) {
                status = "Milestones require a Minecraft 1.16.1 recording";
                return;
            }
            try (ReplayInputStream input = file.getPacketData(PacketTypeRegistry.get(ProtocolVersion.v1_16_1, State.LOGIN))) {
                PacketData data;
                while (!cancelled && (data = input.readPacket()) != null) {
                    try {
                        if (chestIndex != null) {
                            try { chestIndex.packet(data); }
                            catch (Exception error) {
                                chestIndex = null;
                                cacheable = false;
                                chestStatus = "Chest contents unavailable";
                                LogManager.getLogger("ZSG Replay Viewer").warn("Chest indexing failed", error);
                            }
                        }
                        tracker.packet(data);
                    } finally {
                        data.release();
                    }
                }
            }
            if (!cancelled) {
                if (chestIndex != null) {
                    chestIndex.history.finish(); chestIndex.lootHistory.finish();
                    chests = chestIndex.history; chestLoot = chestIndex.lootHistory; chestStatus = "";
                }
                entries = tracker.snapshot();
                status = entries.isEmpty() ? "No recorded milestones" : "Milestones";
                available = true;
                CompletedIndexCache.Key after = cacheable && key != null ? cacheKey(file) : null;
                synchronized (this) {
                    if (!cancelled && key != null && key.equals(after)) {
                        CACHE.put(key, new Result(this), 4096L + 64L * entries.size()
                                + (chestIndex == null ? 0 : chestIndex.estimatedBytes()));
                    }
                }
                LogManager.getLogger("ZSG Replay Viewer").info("Indexed {} replay milestones", entries.size());
            }
        } catch (Exception error) {
            if (!cancelled) {
                chestStatus = "Chest contents unavailable";
                status = "Milestone indexing unavailable";
                LogManager.getLogger("ZSG Replay Viewer").warn("Milestone indexing failed: {}", error.getClass().getSimpleName());
            }
        }
    }

    private static CompletedIndexCache.Key cacheKey(ReplayFile file) {
        try {
            if (file.getMetaData().getRawProtocolVersion() != 736) return null;
            zsgrooms.replayviewer.mixin.ReplayFileAccessor archive = RecordingIndex.archive(file);
            if (archive == null || archive.zsgViewer$getInput() == null) return null;
            String packets = "recording.tmcpr";
            if (archive.zsgViewer$getChangedEntries().containsKey(packets)
                    || archive.zsgViewer$getRemovedEntries().contains(packets)
                    || archive.zsgViewer$getOutputStreams().containsKey(packets)) return null;
            CompletedIndexCache.Key key = CompletedIndexCache.Key.read(archive.zsgViewer$getInput().toPath());
            // ReplayMod may still hold the old ZIP after the path has been replaced externally.
            java.util.zip.ZipFile zip = archive.zsgViewer$getZipFile();
            if (zip == null || !key.signature.equals(CompletedIndexCache.Key.signature(zip))) return null;
            return key;
        } catch (Exception ignored) { return null; }
    }

    private void publish(Result result) {
        chests = result.chests; chestLoot = result.chestLoot; chestStatus = result.chestStatus;
        entries = result.entries; status = result.status;
        available = true;
    }

    private static final class Result {
        final List<Milestones.Entry> entries;
        final String status, chestStatus;
        final ChestHistory<net.minecraft.item.ItemStack> chests;
        final ChestLootHistory chestLoot;
        Result(MilestoneIndex index) {
            entries = index.entries; status = index.status; chestStatus = index.chestStatus;
            chests = index.chests; chestLoot = index.chestLoot;
        }
    }

    @Override public synchronized void close() { cancelled = true; worker.interrupt(); }
}
