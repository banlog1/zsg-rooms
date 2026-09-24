package zsgrooms.modid;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ServerWorldAccess;
import zsgrooms.modid.ui.RoomUiPreferences;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RuinedPortalChestRepair {
    private static final Identifier RUINED_PORTAL_LOOT = new Identifier("minecraft", "chests/ruined_portal");
    private static final int WATCH_TICKS = 2400;
    private static volatile LaunchFilter launchFilter;
    private static final Map<ChestKey, PendingChest> PENDING = new ConcurrentHashMap<ChestKey, PendingChest>();
    private static final Map<ChestKey, PendingPortalBlock> PENDING_PORTAL_BLOCKS =
            new ConcurrentHashMap<ChestKey, PendingPortalBlock>();

    private RuinedPortalChestRepair() {
    }

    static void prepareNextLaunch(String seed) {
        LaunchFilter next = null;
        if (seed != null) {
            try {
                next = new LaunchFilter(Long.parseLong(ZsgSeedBridge.extractMinecraftSeed(seed)),
                        "rpseedbank".equals(ZsgSeedBridge.resolveStructure(seed)));
            } catch (NumberFormatException ignored) {
            }
        }
        launchFilter = next;
    }

    static boolean isPortalWorld(long worldSeed, String fallbackFilter) {
        LaunchFilter launch = launchFilter;
        return launch == null ? "rpseedbank".equals(fallbackFilter)
                : launch.seed == worldSeed && launch.repair;
    }

    public static void capture(ChestBlockEntity chest, BlockState state, CompoundTag tag) {
        if (chest == null || !(chest.getWorld() instanceof ServerWorld)) {
            return;
        }
        capture((ServerWorld) chest.getWorld(), chest.getPos(), state, tag);
    }

    public static void captureGenerated(ServerWorldAccess worldAccess, BlockPos pos, BlockState state) {
        if (worldAccess == null || !(worldAccess.getWorld() instanceof ServerWorld)) {
            return;
        }
        BlockEntity blockEntity = worldAccess.getBlockEntity(pos);
        if (!(blockEntity instanceof ChestBlockEntity)) {
            return;
        }
        capture((ServerWorld) worldAccess.getWorld(), pos, state, blockEntity.toTag(new CompoundTag()));
    }

    public static void captureGeneratedObsidian(ServerWorldAccess worldAccess, BlockPos pos) {
        if (worldAccess == null
                || !(worldAccess.getWorld() instanceof ServerWorld) || pos == null) {
            return;
        }
        ServerWorld world = (ServerWorld) worldAccess.getWorld();
        if (!isEnabledForCurrentRoom(world)) {
            return;
        }
        BlockState state = worldAccess.getBlockState(pos);
        if (!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.CRYING_OBSIDIAN)) {
            return;
        }
        pos = pos.toImmutable();
        PENDING_PORTAL_BLOCKS.put(new ChestKey(world, pos), new PendingPortalBlock(world, pos, state));
    }

    private static void capture(ServerWorld world, BlockPos pos, BlockState state, CompoundTag tag) {
        if (!isEnabledForCurrentRoom(world) || pos == null || state == null || tag == null
                || !RUINED_PORTAL_LOOT.toString().equals(tag.getString("LootTable"))) {
            return;
        }
        pos = pos.toImmutable();
        long lootSeed = tag.getLong("LootTableSeed");
        PENDING.put(new ChestKey(world, pos), new PendingChest(world, pos, state, lootSeed));
        SeedDebugLog.info("Watching ruined portal chest at {}", pos);
    }

    public static void tick(MinecraftServer server) {
        if (!isEnabledForCurrentRoom(server.getOverworld())) {
            PENDING.clear();
            PENDING_PORTAL_BLOCKS.clear();
            return;
        }

        repairGeneratedPortalBlocks(server);

        Iterator<Map.Entry<ChestKey, PendingChest>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ChestKey, PendingChest> entry = iterator.next();
            PendingChest pending = entry.getValue();
            if (pending.world.getServer() != server) {
                PENDING.remove(entry.getKey(), pending);
                continue;
            }
            if (!pending.world.isChunkLoaded(pending.pos)) {
                continue;
            }
            if (--pending.ticksRemaining <= 0) {
                PENDING.remove(entry.getKey(), pending);
                continue;
            }

            BlockEntity blockEntity = pending.world.getBlockEntity(pending.pos);
            if (blockEntity instanceof ChestBlockEntity) {
                CompoundTag current = blockEntity.toTag(new CompoundTag());
                if (RUINED_PORTAL_LOOT.toString().equals(current.getString("LootTable"))
                        && current.getLong("LootTableSeed") == pending.lootSeed) {
                    pending.sawValidLootTable = true;
                    continue;
                }
                if (pending.sawValidLootTable && current.contains("Items", 9)
                        && !current.contains("LootTable", 8)) {
                    PENDING.remove(entry.getKey(), pending);
                    continue;
                }
            }

            repair(pending);
            PENDING.remove(entry.getKey(), pending);
        }
    }

    private static void repairGeneratedPortalBlocks(MinecraftServer server) {
        Iterator<Map.Entry<ChestKey, PendingPortalBlock>> iterator = PENDING_PORTAL_BLOCKS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ChestKey, PendingPortalBlock> entry = iterator.next();
            PendingPortalBlock pending = entry.getValue();
            if (pending.world.getServer() != server) {
                PENDING_PORTAL_BLOCKS.remove(entry.getKey(), pending);
                continue;
            }
            if (!pending.world.isChunkLoaded(pending.pos)) {
                continue;
            }
            BlockState current = pending.world.getBlockState(pending.pos);
            if (!current.equals(pending.state)) {
                pending.world.setBlockState(pending.pos, pending.state, 3);
                ZsgRooms.LOGGER.info("Restored corrupted ruined portal block at {} to {}",
                        pending.pos, pending.state.getBlock().getTranslationKey());
            }
            PENDING_PORTAL_BLOCKS.remove(entry.getKey(), pending);
        }
    }

    private static void repair(PendingChest pending) {
        pending.world.removeBlockEntity(pending.pos);
        pending.world.setBlockState(pending.pos, Blocks.AIR.getDefaultState(), 18);
        pending.world.setBlockState(pending.pos, pending.state, 3);
        BlockEntity restored = pending.world.getBlockEntity(pending.pos);
        if (restored instanceof ChestBlockEntity) {
            ((ChestBlockEntity) restored).setLootTable(RUINED_PORTAL_LOOT, pending.lootSeed);
            restored.markDirty();
            ZsgRooms.LOGGER.info("Restored corrupted ruined portal chest at {}", pending.pos);
        } else {
            ZsgRooms.LOGGER.warn("Could not restore ruined portal chest at {}", pending.pos);
        }
    }

    private static boolean isEnabledForCurrentRoom(ServerWorld world) {
        Room room = ZsgRooms.getActiveRoom();
        InGame game = room == null ? null : ZsgRooms.getGame(room.roomName);
        return RoomUiPreferences.isRuinedPortalChestRepairEnabled()
                && game != null
                && world != null
                && isPortalWorld(world.getSeed(), game.getActiveFilter());
    }

    /** Atum can finish world generation before the room commits the new random-mode seed. */
    private static final class LaunchFilter {
        private final long seed;
        private final boolean repair;

        private LaunchFilter(long seed, boolean repair) {
            this.seed = seed;
            this.repair = repair;
        }
    }

    private static final class ChestKey {
        private final ServerWorld world;
        private final BlockPos pos;

        private ChestKey(ServerWorld world, BlockPos pos) {
            this.world = world;
            this.pos = pos;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ChestKey)) return false;
            ChestKey key = (ChestKey) other;
            return this.world == key.world && this.pos.equals(key.pos);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this.world) * 31 + this.pos.hashCode();
        }
    }

    private static final class PendingChest {
        private final ServerWorld world;
        private final BlockPos pos;
        private final BlockState state;
        private final long lootSeed;
        private int ticksRemaining = WATCH_TICKS;
        private boolean sawValidLootTable;

        private PendingChest(ServerWorld world, BlockPos pos, BlockState state, long lootSeed) {
            this.world = world;
            this.pos = pos;
            this.state = state;
            this.lootSeed = lootSeed;
        }
    }

    private static final class PendingPortalBlock {
        private final ServerWorld world;
        private final BlockPos pos;
        private final BlockState state;

        private PendingPortalBlock(ServerWorld world, BlockPos pos, BlockState state) {
            this.world = world;
            this.pos = pos;
            this.state = state;
        }
    }
}
