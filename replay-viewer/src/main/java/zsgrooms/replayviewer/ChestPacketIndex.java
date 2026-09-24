// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.replaystudio.PacketData;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkState;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.math.BlockPos;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Piggybacks on the milestone scan, reading original packets even during Quick Mode playback. */
final class ChestPacketIndex {
    final ChestHistory<ItemStack> history = new ChestHistory<>();
    final ChestLootHistory lootHistory = new ChestLootHistory();
    private final Map<Integer, List<ChestLoot>> lootChunks = new HashMap<>();
    private int chunkOrdinal;
    private final Map<Integer, ChestOpening> openings = new HashMap<>();
    private final int join = id(new GameJoinS2CPacket()), respawn = id(new PlayerRespawnS2CPacket());
    private final int open = id(new OpenScreenS2CPacket()), inventory = id(new InventoryS2CPacket());
    private final int slot = id(new ScreenHandlerSlotUpdateS2CPacket()), close = id(new CloseScreenS2CPacket());
    private final int block = id(new BlockUpdateS2CPacket()), delta = id(new ChunkDeltaUpdateS2CPacket());
    private final int chunk = id(new ChunkDataS2CPacket()), unload = id(new UnloadChunkS2CPacket());
    private int world = -1, opening, inventoryBytes;
    private String dimension = "";

    ChestPacketIndex(List<ChestOpening> bindings) {
        this(bindings, java.util.Collections.emptyList());
    }

    ChestPacketIndex(List<ChestOpening> bindings, List<ChestLoot> loot) {
        for (ChestOpening binding : bindings) openings.put(binding.opening, binding);
        for (ChestLoot entry : loot) lootChunks.computeIfAbsent(entry.chunk, ignored -> new ArrayList<>()).add(entry);
    }

    void packet(PacketData data) throws IOException {
        if (data.getPacket().getRegistry().getState() != State.PLAY) return;
        int type = data.getPacket().getId();
        int time = (int) data.getTime();
        if (type != join && type != respawn && type != open && type != inventory && type != slot
                && type != close && type != block && type != delta && type != chunk && type != unload) return;
        PacketByteBuf buf = new PacketByteBuf(data.getPacket().getBuf().duplicate());
        if (type == join) {
            GameJoinS2CPacket packet = new GameJoinS2CPacket(); packet.read(buf);
            world++; dimension = packet.getDimensionId().getValue().toString(); history.close();
        } else if (type == respawn) {
            PlayerRespawnS2CPacket packet = new PlayerRespawnS2CPacket(); packet.read(buf);
            dimension = packet.getDimension().getValue().toString(); history.close();
        } else if (type == open) {
            OpenScreenS2CPacket packet = new OpenScreenS2CPacket(); packet.read(buf);
            ChestOpening binding = openings.get(++opening);
            int count = packet.getScreenHandlerType() == ScreenHandlerType.GENERIC_9X3 ? 27
                    : packet.getScreenHandlerType() == ScreenHandlerType.GENERIC_9X6 ? 54 : 0;
            if (binding != null && (binding.time != time || binding.world != world || !binding.dimension.equals(dimension)
                    || binding.syncId != packet.getSyncId() || binding.slots() != count)) binding = null;
            history.open(binding, time);
            if (binding != null) {
                lootHistory.invalidate(world, dimension, binding.first, time);
                lootHistory.invalidate(world, dimension, binding.second, time);
            }
        } else if (type == inventory || type == slot) {
            int syncId = type == inventory ? buf.readUnsignedByte() : buf.readByte();
            if (!history.accepts(syncId)) return;
            inventoryBytes += buf.readableBytes();
            if (buf.readableBytes() > 262144 || inventoryBytes > 16 * 1024 * 1024) throw new IOException("Chest item data limit reached");
            int count = buf.readShort();
            if (type == inventory) {
                if (count < 0 || count > 90) throw new IOException("Invalid container size");
                List<ItemStack> items = new ArrayList<>(count);
                for (int i = 0; i < count; i++) items.add(buf.readItemStack());
                history.inventory(syncId, items, time);
            } else history.slot(syncId, count, buf.readItemStack(), time);
        } else if (type == close) {
            if (history.accepts(buf.readUnsignedByte())) history.close();
        } else if (type == block) {
            long pos = buf.readBlockPos().asLong();
            int state = buf.readVarInt();
            history.block(world, dimension, pos, state, time);
            lootHistory.block(world, dimension, pos, state, time);
        } else if (type == delta) {
            int x = buf.readInt(), z = buf.readInt(), count = buf.readVarInt();
            if (count < 0 || count > 65536) throw new IOException("Invalid chunk delta size");
            for (int i = 0; i < count; i++) {
                int packed = buf.readUnsignedShort();
                long pos = BlockPos.asLong((x << 4) + (packed >> 12 & 15), packed & 255, (z << 4) + (packed >> 8 & 15));
                int state = buf.readVarInt();
                history.block(world, dimension, pos, state, time);
                lootHistory.block(world, dimension, pos, state, time);
            }
        } else {
            int x = buf.readInt(), z = buf.readInt();
            history.chunk(world, dimension, x, z, time);
            lootHistory.chunk(world, dimension, x, z, time);
            if (type == chunk) {
                List<ChestLoot> entries = lootChunks.get(++chunkOrdinal);
                if (entries != null) for (ChestLoot entry : entries) {
                    if (entry.time != time || entry.world != world || !entry.dimension.equals(dimension) || !entry.inChunk(x, z))
                        throw new IOException("Chest loot packet binding mismatch");
                    lootHistory.observe(entry);
                }
            }
        }
    }

    private static int id(Packet<?> packet) { return NetworkState.PLAY.getPacketId(NetworkSide.CLIENTBOUND, packet); }
}
