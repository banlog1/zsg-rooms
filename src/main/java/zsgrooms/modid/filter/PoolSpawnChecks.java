package zsgrooms.modid.filter;

import net.minecraft.util.math.BlockPos;

/** Necessary geometric condition only; uses the real spawn, never the biome search hint. */
final class PoolSpawnChecks {
    static boolean canReachPoolArea(BlockPos spawn, BlockPos structure) {
        // Any pool anchor must lie in the 96-block circle. Spawn may be within 32 on each axis.
        long dx = Math.max(0L, Math.abs((long) spawn.getX() - structure.getX()) - TempleCandidateChecks.SPAWN_RADIUS);
        long dz = Math.max(0L, Math.abs((long) spawn.getZ() - structure.getZ()) - TempleCandidateChecks.SPAWN_RADIUS);
        return dx <= SurfaceTerrainChecks.LAVA_POOL_RADIUS && dz <= SurfaceTerrainChecks.LAVA_POOL_RADIUS
                && dx * dx + dz * dz <= (long) SurfaceTerrainChecks.LAVA_POOL_RADIUS * SurfaceTerrainChecks.LAVA_POOL_RADIUS;
    }
}
