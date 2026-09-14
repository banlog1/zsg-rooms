package zsgrooms.modid;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.feature.StructureFeature;
import zsgrooms.modid.seedbank.SeedBankProfile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Initial-world preparation only, never a tick-time refill or a filter-search validator. */
public final class SurfaceLavaPoolGuarantee {
    private SurfaceLavaPoolGuarantee() {
    }

    static boolean appliesTo(String filter) {
        SeedBankProfile profile = SeedBankProfile.find(filter);
        return profile == SeedBankProfile.TEMPLE || profile == SeedBankProfile.VILLAGE;
    }

    static boolean needsPreparation(ServerWorld world, String filter) {
        return world != null && world.getRegistryKey() == World.OVERWORLD && appliesTo(filter)
                && world.getTime() == 0L && !state(world).attempted;
    }

    static void ensure(ServerWorld world, BlockPos structure, String filter) {
        if (!needsPreparation(world, filter)) return;
        if (!world.getServer().isOnThread()) throw new IllegalStateException("Pool preparation requires the server thread");
        long started = System.nanoTime();
        State state = state(world);
        // Persist attempts too: unsafe terrain is not a reason to excavate a played world on reconnect.
        state.attempted = true;
        state.markDirty();
        MinecraftTerrain terrain = new MinecraftTerrain(world, structure);
        SurfacePoolRepair.Result result = SurfacePoolRepair.repair(terrain, structure, world.getSeed(),
                ZsgSeedBridge.normalizeSeedType(filter));
        state.pools = result.pools.size();
        long millis = (System.nanoTime() - started) / 1_000_000L;
        SeedDebugLog.info("[ZSG-Rooms/SurfacePools] usable={}, lava added={}, preparation={} ms",
                result.pools.size(), result.lavaAdded, millis);
        if (result.pools.size() < SurfacePoolRepair.REQUIRED_POOLS) {
            if (result.pools.isEmpty()) {
                ZsgRooms.LOGGER.warn("[ZSG-Rooms/SurfacePools] No safe pool near natural water could be prepared within 128 blocks; terrain left intact");
            } else {
                ZsgRooms.LOGGER.warn("[ZSG-Rooms/SurfacePools] Could not prepare two separated pools near natural water within 128 blocks; kept one valid pool");
            }
        }
    }

    private static State state(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(State::new, State.ID);
    }

    static final class State extends PersistentState {
        static final String ID = "zsg_rooms_surface_pools";
        boolean attempted;
        int pools;

        State() { super(ID); }

        @Override
        public void fromTag(CompoundTag tag) {
            attempted = tag.getBoolean("Attempted");
            pools = tag.getInt("Pools");
        }

        @Override
        public CompoundTag toTag(CompoundTag tag) {
            tag.putBoolean("Attempted", attempted);
            tag.putInt("Pools", pools);
            return tag;
        }
    }

    static final class MinecraftTerrain implements SurfacePoolRepair.Terrain {
        private final ServerWorld world;
        private final int minX;
        private final int minZ;
        private final int width = SurfacePoolRepair.SCAN_RADIUS * 2 + 1;
        private final boolean[] protectedColumns = new boolean[width * width];
        private final BlockPos origin;
        private final Set<StructureStart<?>> seen = new HashSet<StructureStart<?>>();
        private int preparedRadius;

        MinecraftTerrain(ServerWorld world, BlockPos origin) {
            this.world = world;
            this.origin = origin;
            minX = origin.getX() - SurfacePoolRepair.SCAN_RADIUS;
            minZ = origin.getZ() - SurfacePoolRepair.SCAN_RADIUS;
            BlockPos spawn = world.getSpawnPos();
            protect(new BlockBox(spawn.getX(), 0, spawn.getZ(), spawn.getX(), 255, spawn.getZ()), 20);
            prepare(SurfacePoolRepair.POOL_RADIUS + SurfacePoolRepair.WATER_RADIUS + 2);
        }

        @Override
        public void prepare(int scanRadius) {
            if (scanRadius <= preparedRadius) return;
            if (scanRadius > SurfacePoolRepair.SCAN_RADIUS) throw new IllegalArgumentException("Pool search exceeds maximum radius");
            // Complete a fixed, ordered area plus decoration halo before inspecting or modifying it.
            List<Chunk> chunks = new ArrayList<Chunk>();
            for (int x = ((origin.getX() - scanRadius) >> 4) - 1; x <= ((origin.getX() + scanRadius) >> 4) + 1; x++) {
                for (int z = ((origin.getZ() - scanRadius) >> 4) - 1; z <= ((origin.getZ() + scanRadius) >> 4) + 1; z++) {
                    if (preparedRadius > 0 && x >= ((origin.getX() - preparedRadius) >> 4) - 1
                            && x <= ((origin.getX() + preparedRadius) >> 4) + 1
                            && z >= ((origin.getZ() - preparedRadius) >> 4) - 1
                            && z <= ((origin.getZ() + preparedRadius) >> 4) + 1) continue;
                    chunks.add(world.getChunk(x, z));
                }
            }
            for (Chunk chunk : chunks) {
                for (Map.Entry<StructureFeature<?>, it.unimi.dsi.fastutil.longs.LongSet> entry
                        : chunk.getStructureReferences().entrySet()) {
                    for (long reference : entry.getValue()) {
                        ChunkPos pos = new ChunkPos(reference);
                        StructureStart<?> start = world.getStructureAccessor().getStructureStart(
                                ChunkSectionPos.from(pos, 0), entry.getKey(), world.getChunk(pos.x, pos.z, ChunkStatus.STRUCTURE_STARTS));
                        if (start == null || !start.hasChildren() || !seen.add(start)) continue;
                        for (StructurePiece piece : start.getChildren()) protect(piece.getBoundingBox(), 6);
                    }
                }
            }
            preparedRadius = scanRadius;
        }

        private void protect(BlockBox box, int margin) {
            // Deep mineshafts/fossils cannot intersect above-sea-level surface excavations.
            if (box.maxY + margin < SurfacePoolRepair.MIN_POOL_Y - 4) return;
            for (int z = Math.max(minZ, box.minZ - margin); z <= Math.min(minZ + width - 1, box.maxZ + margin); z++) {
                for (int x = Math.max(minX, box.minX - margin); x <= Math.min(minX + width - 1, box.maxX + margin); x++) {
                    protectedColumns[(z - minZ) * width + x - minX] = true;
                }
            }
        }

        private boolean inside(int x, int z) {
            return x >= origin.getX() - preparedRadius && z >= origin.getZ() - preparedRadius
                    && x <= origin.getX() + preparedRadius && z <= origin.getZ() + preparedRadius;
        }

        @Override
        public int surfaceY(int x, int z) {
            if (!inside(x, z)) return -1;
            int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
            while (y > 0 && kind(new BlockPos(x, y, z)) == SurfacePoolRepair.Kind.PLANT) y--;
            return y;
        }

        @Override
        public SurfacePoolRepair.Kind kind(BlockPos pos) {
            if (!inside(pos.getX(), pos.getZ()) || pos.getY() < 0 || pos.getY() > 255) return SurfacePoolRepair.Kind.BLOCKED;
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) return SurfacePoolRepair.Kind.AIR;
            if (state.isOf(Blocks.LAVA) && state.getFluidState().isStill()) return SurfacePoolRepair.Kind.LAVA;
            if (state.isOf(Blocks.WATER) && state.getFluidState().isStill()) return SurfacePoolRepair.Kind.WATER;
            if (state.isOf(Blocks.STONE)) return SurfacePoolRepair.Kind.STONE;
            if (state.isOf(Blocks.DIRT) || state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.COARSE_DIRT)
                    || state.isOf(Blocks.SAND) || state.isOf(Blocks.RED_SAND) || state.isOf(Blocks.GRAVEL)
                    || state.isOf(Blocks.SANDSTONE) || state.isOf(Blocks.RED_SANDSTONE)
                    || state.isOf(Blocks.GRANITE) || state.isOf(Blocks.DIORITE) || state.isOf(Blocks.ANDESITE)) {
                return SurfacePoolRepair.Kind.GROUND;
            }
            if (state.isOf(Blocks.GRASS) || state.isOf(Blocks.TALL_GRASS) || state.isOf(Blocks.FERN)
                    || state.isOf(Blocks.LARGE_FERN) || state.isOf(Blocks.DEAD_BUSH)) return SurfacePoolRepair.Kind.PLANT;
            return SurfacePoolRepair.Kind.BLOCKED;
        }

        @Override
        public boolean protectedAt(BlockPos pos) {
            return !inside(pos.getX(), pos.getZ())
                    || protectedColumns[(pos.getZ() - minZ) * width + pos.getX() - minX]
                    || world.getBlockEntity(pos) != null || !world.getWorldBorder().contains(pos);
        }

        @Override
        public boolean unfrozen(BlockPos pos) { return world.getBiome(pos).getTemperature(pos) >= 0.15F; }

        @Override
        public boolean apply(Map<BlockPos, SurfacePoolRepair.Kind> patch) {
            if (patch.containsValue(SurfacePoolRepair.Kind.WATER)) return false;
            Map<BlockPos, BlockState> originals = new LinkedHashMap<BlockPos, BlockState>();
            for (BlockPos pos : patch.keySet()) {
                if (protectedAt(pos)) return false;
                originals.put(pos, world.getBlockState(pos));
            }
            try {
                // Solid containment and headroom first; fluids last, all before the first simulation tick.
                for (int pass = 0; pass < 2; pass++) {
                    for (Map.Entry<BlockPos, SurfacePoolRepair.Kind> entry : patch.entrySet()) {
                        boolean fluid = entry.getValue() == SurfacePoolRepair.Kind.LAVA;
                        if (fluid != (pass == 1)) continue;
                        BlockState block = block(entry.getValue());
                        world.setBlockState(entry.getKey(), block, 18);
                        if (!world.getBlockState(entry.getKey()).equals(block)) throw new IllegalStateException("Pool write failed");
                    }
                }
            } catch (RuntimeException error) {
                for (Map.Entry<BlockPos, BlockState> entry : originals.entrySet()) world.setBlockState(entry.getKey(), entry.getValue(), 18);
                ZsgRooms.LOGGER.warn("[ZSG-Rooms/SurfacePools] Could not apply a terrain patch; restored original blocks");
                return false;
            }
            moveGenerationMobs(patch);
            return true;
        }

        private void moveGenerationMobs(Map<BlockPos, SurfacePoolRepair.Kind> patch) {
            // Chunk-generation animals must not be harmed by the preparation, or influence pool selection.
            Set<Entity> moved = new HashSet<Entity>();
            for (Map.Entry<BlockPos, SurfacePoolRepair.Kind> change : patch.entrySet()) {
                if (change.getValue() != SurfacePoolRepair.Kind.LAVA) continue;
                for (Entity entity : world.getEntities((Entity) null, new Box(change.getKey()).expand(1, 2, 1))) {
                    if (!moved.add(entity)) continue;
                    boolean placed = false;
                    for (int radius = 6; radius <= 16 && !placed; radius++) {
                        for (int z = -radius; z <= radius && !placed; z++) {
                            for (int x = -radius; x <= radius; x++) {
                                if (Math.abs(x) != radius && Math.abs(z) != radius) continue;
                                if (!inside(change.getKey().getX() + x, change.getKey().getZ() + z)) continue;
                                BlockPos ground = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                                        change.getKey().add(x, 0, z));
                                SurfacePoolRepair.Kind support = kind(ground.down());
                                if ((support != SurfacePoolRepair.Kind.GROUND && support != SurfacePoolRepair.Kind.STONE)
                                        || !world.getBlockState(ground.down())
                                        .isSideSolidFullSquare(world, ground.down(), Direction.UP)
                                        || !world.getBlockState(ground).isAir() || !world.getBlockState(ground.up()).isAir()) continue;
                                Box target = entity.getBoundingBox().offset(ground.getX() + 0.5D - entity.getX(),
                                        ground.getY() - entity.getY(), ground.getZ() + 0.5D - entity.getZ());
                                if (!world.doesNotCollide(entity, target)) continue;
                                entity.refreshPositionAndAngles(ground.getX() + 0.5D, ground.getY(), ground.getZ() + 0.5D, entity.yaw, entity.pitch);
                                placed = true;
                                break;
                            }
                        }
                    }
                    if (!placed) ZsgRooms.LOGGER.warn("[ZSG-Rooms/SurfacePools] Could not move a generation mob onto a dry bank");
                }
            }
        }

        private static BlockState block(SurfacePoolRepair.Kind kind) {
            switch (kind) {
                case STONE: return Blocks.STONE.getDefaultState();
                case LAVA: return Blocks.LAVA.getDefaultState();
                case AIR: return Blocks.AIR.getDefaultState();
                case CAVE_AIR: return Blocks.CAVE_AIR.getDefaultState();
                default: throw new IllegalArgumentException("Not a placement block");
            }
        }
    }
}
