package zsgrooms.modid.filter;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/** Bounded snapshot, with an offline-only lazy read mode for immediate pool validation. */
public final class MinecraftSurfaceTerrain implements SurfaceTerrainChecks.Terrain {
    private static final int DEPTH = SurfaceTerrainChecks.MAX_TRUNK_SEARCH + 4;
    private static final int HALO = 4;
    private static final SurfaceTerrainChecks.Cell[] CELLS = SurfaceTerrainChecks.Cell.values();

    private final int minX;
    private final int minZ;
    private final int width;
    private final int[] heights;
    private final byte[] blocks;
    private final ServerWorld liveWorld;

    private MinecraftSurfaceTerrain(int minX, int minZ, int width, ServerWorld liveWorld) {
        this.minX = minX;
        this.minZ = minZ;
        this.width = width;
        this.heights = new int[width * width];
        this.liveWorld = liveWorld;
        this.blocks = liveWorld == null ? new byte[heights.length * DEPTH] : null;
    }

    public static MinecraftSurfaceTerrain capture(ServerWorld world, int originX, int originZ, int radius) {
        return capture(world, originX, originZ, radius, false);
    }

    /** Consume immediately on the owning thread without ticking or modifying the prepared area. */
    static MinecraftSurfaceTerrain captureForPools(ServerWorld world, int originX, int originZ, int radius) {
        return capture(world, originX, originZ, radius, true);
    }

    private static MinecraftSurfaceTerrain capture(ServerWorld world, int originX, int originZ, int radius, boolean lazy) {
        SurfaceTerrainChecks.checkRadius(radius);
        if (!world.getServer().isOnThread()) {
            throw new IllegalStateException("Terrain capture requires the server thread");
        }
        if (world.getRegistryKey() != World.OVERWORLD) {
            throw new IllegalArgumentException("Surface filter checks require the Overworld");
        }
        if (Math.abs((long) originX) > 29999000 || Math.abs((long) originZ) > 29999000) {
            throw new IllegalArgumentException("Terrain origin is too close to the world boundary");
        }
        int paddedRadius = radius + HALO;
        MinecraftSurfaceTerrain result = new MinecraftSurfaceTerrain(originX - paddedRadius,
                originZ - paddedRadius, paddedRadius * 2 + 1, lazy ? world : null);
        // Finish neighboring decoration before reading any column. No partially generated heightmap prediction.
        ValidationTrace.run("terrain.prepareChunks", () -> {
            for (int chunkX = (result.minX >> 4) - 1; chunkX <= ((originX + paddedRadius) >> 4) + 1; chunkX++) {
                for (int chunkZ = (result.minZ >> 4) - 1; chunkZ <= ((originZ + paddedRadius) >> 4) + 1; chunkZ++) {
                    world.getChunk(chunkX, chunkZ);
                }
            }
        });
        ValidationTrace.run("terrain.snapshot", () -> {
            BlockPos.Mutable pos = new BlockPos.Mutable();
            for (int z = 0; z < result.width; z++) {
                for (int x = 0; x < result.width; x++) {
                    int column = z * result.width + x;
                    int worldX = result.minX + x;
                    int worldZ = result.minZ + z;
                    int top = world.getTopY(Heightmap.Type.WORLD_SURFACE, worldX, worldZ) - 1;
                    result.heights[column] = top;
                    if (lazy) continue;
                    for (int depth = 0; depth < DEPTH; depth++) {
                        pos.set(worldX, top - depth, worldZ);
                        SurfaceTerrainChecks.Cell cell = pos.getY() < 0 ? SurfaceTerrainChecks.Cell.OTHER
                                : classify(world, pos, world.getBlockState(pos));
                        result.blocks[column * DEPTH + depth] = (byte) cell.ordinal();
                    }
                }
            }
        });
        return result;
    }

    @Override
    public int surfaceY(int x, int z) {
        int index = column(x, z);
        return index < 0 ? -1 : heights[index];
    }

    @Override
    public SurfaceTerrainChecks.Cell cell(int x, int y, int z) {
        if (liveWorld != null && !liveWorld.getServer().isOnThread()) {
            throw new IllegalStateException("Lazy terrain reads require the server thread");
        }
        int index = column(x, z);
        if (index < 0 || y < 0) return SurfaceTerrainChecks.Cell.OTHER;
        int depth = heights[index] - y;
        if (depth < 0) return SurfaceTerrainChecks.Cell.AIR;
        if (depth >= DEPTH) return SurfaceTerrainChecks.Cell.OTHER;
        if (liveWorld != null) {
            BlockPos pos = new BlockPos(x, y, z);
            return classify(liveWorld, pos, liveWorld.getBlockState(pos));
        }
        return CELLS[blocks[index * DEPTH + depth]];
    }

    private int column(int x, int z) {
        int localX = x - minX;
        int localZ = z - minZ;
        return localX < 0 || localZ < 0 || localX >= width || localZ >= width ? -1 : localZ * width + localX;
    }

    private static SurfaceTerrainChecks.Cell classify(ServerWorld world, BlockPos pos, BlockState state) {
        if (state.isAir()) return SurfaceTerrainChecks.Cell.AIR;
        if (state.isOf(Blocks.LAVA) && state.getFluidState().isStill()) return SurfaceTerrainChecks.Cell.LAVA_SOURCE;
        if (!state.getFluidState().isEmpty()) return SurfaceTerrainChecks.Cell.OTHER;
        if (state.getBlock() instanceof LeavesBlock) {
            return state.get(LeavesBlock.PERSISTENT) ? SurfaceTerrainChecks.Cell.OTHER : SurfaceTerrainChecks.Cell.NATURAL_LEAVES;
        }
        if (state.isOf(Blocks.OAK_LOG) || state.isOf(Blocks.BIRCH_LOG) || state.isOf(Blocks.SPRUCE_LOG)
                || state.isOf(Blocks.JUNGLE_LOG) || state.isOf(Blocks.ACACIA_LOG) || state.isOf(Blocks.DARK_OAK_LOG)) {
            return SurfaceTerrainChecks.Cell.LOG;
        }
        if (state.isOf(Blocks.DIRT) || state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.COARSE_DIRT)
                || state.isOf(Blocks.PODZOL)) return SurfaceTerrainChecks.Cell.SOIL;
        if (state.isOf(Blocks.MAGMA_BLOCK) || state.isOf(Blocks.CACTUS) || state.isOf(Blocks.CAMPFIRE)
                || state.isOf(Blocks.SOUL_CAMPFIRE)) return SurfaceTerrainChecks.Cell.OTHER;
        return state.isSideSolidFullSquare(world, pos, Direction.UP)
                ? SurfaceTerrainChecks.Cell.SOLID : SurfaceTerrainChecks.Cell.OTHER;
    }
}
