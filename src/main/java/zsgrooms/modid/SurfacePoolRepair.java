package zsgrooms.modid;

import net.minecraft.util.math.BlockPos;
import zsgrooms.modid.filter.SurfaceTerrainChecks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Bounded, deterministic planning. World writes are delegated as complete, validated patches. */
final class SurfacePoolRepair {
    static final int POOL_RADIUS = 96;
    static final int MAX_POOL_RADIUS = 128;
    static final int WATER_RADIUS = 48;
    static final int SCAN_RADIUS = MAX_POOL_RADIUS + WATER_RADIUS + 2;
    static final int MIN_SEPARATION = 48;
    static final int REQUIRED_POOLS = 2;
    static final int MIN_POOL_Y = 63;
    static final int MAX_POOL_Y = 248;

    enum Kind { AIR, CAVE_AIR, PLANT, GROUND, STONE, LAVA, WATER, BLOCKED }

    interface Terrain extends SurfaceTerrainChecks.Terrain {
        Kind kind(BlockPos pos);
        boolean protectedAt(BlockPos pos);
        boolean unfrozen(BlockPos pos);
        boolean apply(Map<BlockPos, Kind> patch);

        default void prepare(int scanRadius) { }

        @Override
        default SurfaceTerrainChecks.Cell cell(int x, int y, int z) {
            switch (kind(new BlockPos(x, y, z))) {
                case AIR: case CAVE_AIR: return SurfaceTerrainChecks.Cell.AIR;
                case GROUND: case STONE: return SurfaceTerrainChecks.Cell.SOLID;
                case LAVA: return SurfaceTerrainChecks.Cell.LAVA_SOURCE;
                default: return SurfaceTerrainChecks.Cell.OTHER;
            }
        }
    }

    static final class Result {
        final List<BlockPos> pools = new ArrayList<BlockPos>();
        int lavaAdded;
    }

    private SurfacePoolRepair() {
    }

    static Result repair(Terrain terrain, BlockPos structure, long seed, String profile) {
        Site fallback = null;
        for (int radius : new int[]{POOL_RADIUS, MAX_POOL_RADIUS}) {
            int scanRadius = radius + WATER_RADIUS + 2;
            terrain.prepare(scanRadius);
            WaterIndex water = new WaterIndex(findWater(terrain, structure, scanRadius));
            if (water.empty()) continue;
            List<Site> sites = new ArrayList<Site>();
            for (SurfaceTerrainChecks.LavaPool pool : SurfaceTerrainChecks.findLavaPools(
                    terrain, structure.getX(), structure.getZ(), radius)) {
                if (!water.near(pool.anchor, WATER_RADIUS - 1, 8)) continue;
                Result pair = addSite(terrain, structure, sites, new Site(pool.anchor, null), seed, profile);
                if (pair != null) return pair;
            }
            // Coarse sites usually suffice. Visit every remaining block column before widening the radius.
            for (int step : new int[]{4, 1}) {
                for (BlockPos column : candidates(structure, seed, profile, radius, step)) {
                    BlockPos center = new BlockPos(column.getX(), terrain.surfaceY(column.getX(), column.getZ()), column.getZ());
                    // The qualifying source can be offset from the lake center; this is only a broad-phase rejection.
                    if (!water.near(center, WATER_RADIUS + 12, 9)) continue;
                    Map<BlockPos, Kind> patch = VanillaLavaLake.plan(terrain, center, lakeSeed(seed, profile, center));
                    if (patch == null) continue;
                    BlockPos pool = qualifiedAnchor(terrain, patch, center, structure, radius);
                    if (pool == null || !water.near(pool, WATER_RADIUS - 1, 8)) continue;
                    Result pair = addSite(terrain, structure, sites, new Site(pool, center), seed, profile);
                    if (pair != null) return pair;
                }
            }
            if (!sites.isEmpty() && (fallback == null || (fallback.center != null && sites.get(0).center == null))) {
                fallback = sites.get(0);
            }
        }
        // Exhaust the pair search first; retain one safe route rather than discard it.
        if (fallback == null) return new Result();
        Preview preview = new Preview(terrain);
        Result result = new Result();
        if (!stage(preview, fallback, seed, profile, result)) return new Result();
        if (!preview.patch.isEmpty() && !terrain.apply(preview.patch)) return new Result();
        return result;
    }

    private static final class Site {
        final BlockPos anchor;
        final BlockPos center;

        Site(BlockPos anchor, BlockPos center) { this.anchor = anchor; this.center = center; }
    }

    private static Result addSite(Terrain terrain, BlockPos structure, List<Site> sites, Site candidate,
            long seed, String profile) {
        for (boolean opposite : new boolean[]{true, false}) {
            for (Site first : sites) {
                if (!separated(candidate.anchor, Collections.singletonList(first.anchor), structure, opposite)) continue;
                Preview preview = new Preview(terrain);
                Result result = new Result();
                if (!stage(preview, first, seed, profile, result) || !stage(preview, candidate, seed, profile, result)) continue;
                if (!preview.patch.isEmpty() && !terrain.apply(preview.patch)) return new Result();
                return result;
            }
        }
        sites.add(candidate);
        return null;
    }

    private static boolean stage(Preview preview, Site site, long seed, String profile, Result result) {
        if (site.center != null) {
            Map<BlockPos, Kind> patch = VanillaLavaLake.plan(preview, site.center, lakeSeed(seed, profile, site.center));
            if (patch == null) return false;
            preview.apply(patch);
            result.lavaAdded++;
        }
        result.pools.add(site.anchor);
        return true;
    }

    static long lakeSeed(long seed, String profile, BlockPos center) {
        return RngStandardization.eventSeed(seed, "surface_lava_lake",
                profile + ":" + center.getX() + ":" + center.getY() + ":" + center.getZ(), 0L);
    }

    private static BlockPos qualifiedAnchor(Terrain terrain, Map<BlockPos, Kind> patch, BlockPos center, BlockPos structure, int radius) {
        Preview preview = new Preview(terrain);
        preview.apply(patch);
        for (SurfaceTerrainChecks.LavaPool pool : SurfaceTerrainChecks.findLavaPools(preview, center.getX(), center.getZ(), 12)) {
            if (patch.get(pool.anchor) != Kind.LAVA) continue;
            BlockPos anchor = pool.sources.stream().min(java.util.Comparator
                    .comparingLong((BlockPos pos) -> distanceSquared(pos, structure))
                    .thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY)).get();
            if (distanceSquared(anchor, structure) <= radius * radius) return anchor;
        }
        return null;
    }

    /** Evaluate alternate route pairs without ever excavating a discarded first choice. */
    private static final class Preview implements Terrain {
        private final Terrain base;
        private final Map<BlockPos, Kind> patch = new LinkedHashMap<BlockPos, Kind>();
        private final Map<BlockPos, Integer> heights = new HashMap<BlockPos, Integer>();

        private Preview(Terrain base) { this.base = base; }

        @Override
        public int surfaceY(int x, int z) {
            Integer height = heights.get(new BlockPos(x, 0, z));
            return height == null ? base.surfaceY(x, z) : height;
        }

        @Override
        public Kind kind(BlockPos pos) {
            Kind changed = patch.get(pos);
            if (changed == Kind.CAVE_AIR) return Kind.AIR;
            return changed == null ? base.kind(pos) : changed;
        }

        @Override
        public boolean protectedAt(BlockPos pos) { return base.protectedAt(pos); }

        @Override
        public boolean unfrozen(BlockPos pos) { return base.unfrozen(pos); }

        @Override
        public boolean apply(Map<BlockPos, Kind> changes) {
            Map<BlockPos, Integer> touched = new HashMap<BlockPos, Integer>();
            for (BlockPos pos : changes.keySet()) {
                BlockPos column = new BlockPos(pos.getX(), 0, pos.getZ());
                int top = touched.getOrDefault(column, surfaceY(pos.getX(), pos.getZ()));
                touched.put(column, Math.max(top, pos.getY()));
            }
            patch.putAll(changes);
            for (Map.Entry<BlockPos, Integer> column : touched.entrySet()) {
                BlockPos pos = column.getKey();
                int y = column.getValue();
                while (y > 0 && (kind(new BlockPos(pos.getX(), y, pos.getZ())) == Kind.AIR
                        || kind(new BlockPos(pos.getX(), y, pos.getZ())) == Kind.PLANT)) y--;
                heights.put(pos, y);
            }
            return true;
        }
    }

    static List<BlockPos> candidates(BlockPos structure, long seed, String profile) {
        return candidates(structure, seed, profile, POOL_RADIUS, 4);
    }

    static List<BlockPos> candidates(BlockPos structure, long seed, String profile, int radius, int step) {
        List<BlockPos> result = new ArrayList<BlockPos>();
        // Leave a buffer around the route structure. The dense pass omits already tested coarse columns.
        for (int z = -radius; z <= radius; z += step) {
            for (int x = -radius; x <= radius; x += step) {
                if (step == 1 && x % 4 == 0 && z % 4 == 0) continue;
                int distance = x * x + z * z;
                if (distance >= 28 * 28 && distance <= radius * radius) result.add(structure.add(x, 0, z));
            }
        }
        Collections.shuffle(result, new Random(RngStandardization.eventSeed(seed, "surface_pool_repair",
                profile + ":" + structure.getX() + ":" + structure.getZ(), 0L)));
        return result;
    }

    static boolean separated(BlockPos candidate, List<BlockPos> pools, BlockPos structure, boolean opposite) {
        for (BlockPos pool : pools) {
            if (distanceSquared(pool, candidate) < MIN_SEPARATION * MIN_SEPARATION) return false;
            long dot = ((long) pool.getX() - structure.getX()) * (candidate.getX() - structure.getX())
                    + ((long) pool.getZ() - structure.getZ()) * (candidate.getZ() - structure.getZ());
            long lengths = distanceSquared(pool, structure) * distanceSquared(candidate, structure);
            if (lengths == 0 || (dot > 0 && 4 * dot * dot > lengths)) return false; // At least 60 degrees.
            if (opposite && dot > 0) return false;
        }
        return true;
    }

    private static List<BlockPos> findWater(Terrain terrain, BlockPos origin, int scanRadius) {
        List<BlockPos> result = new ArrayList<BlockPos>();
        for (int z = origin.getZ() - scanRadius; z <= origin.getZ() + scanRadius - 3; z++) {
            for (int x = origin.getX() - scanRadius; x <= origin.getX() + scanRadius - 3; x++) {
                int y = terrain.surfaceY(x, z);
                BlockPos pos = new BlockPos(x, y, z);
                if (y < 60 || terrain.kind(pos) != Kind.WATER || !terrain.unfrozen(pos)) continue;
                boolean patch = true;
                for (int dz = 0; dz < 4 && patch; dz++) {
                    for (int dx = 0; dx < 4; dx++) {
                        BlockPos source = pos.add(dx, 0, dz);
                        if (terrain.kind(source) != Kind.WATER || terrain.kind(source.up()) != Kind.AIR) {
                            patch = false;
                            break;
                        }
                    }
                }
                if (patch) result.add(pos.add(1, 0, 1));
            }
        }
        return result;
    }

    /** Bucket water patches so a shoreline/ocean does not require a full water-list scan at every site. */
    private static final class WaterIndex {
        private final Map<Long, List<BlockPos>> buckets = new HashMap<Long, List<BlockPos>>();

        WaterIndex(List<BlockPos> water) {
            for (BlockPos pos : water) buckets.computeIfAbsent(key(pos.getX() >> 4, pos.getZ() >> 4),
                    ignored -> new ArrayList<BlockPos>()).add(pos);
        }

        boolean empty() { return buckets.isEmpty(); }

        boolean near(BlockPos pool, int radius, int height) {
            for (int z = (pool.getZ() - radius) >> 4; z <= (pool.getZ() + radius) >> 4; z++) {
                for (int x = (pool.getX() - radius) >> 4; x <= (pool.getX() + radius) >> 4; x++) {
                    List<BlockPos> water = buckets.get(key(x, z));
                    if (water == null) continue;
                    for (BlockPos pos : water) {
                        // Reserve one block in callers for the half-block center of the 4x4 patch.
                        if (Math.abs(pos.getY() - pool.getY()) <= height && distanceSquared(pos, pool) <= radius * radius) return true;
                    }
                }
            }
            return false;
        }

        private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
    }

    static long distanceSquared(BlockPos first, BlockPos second) {
        long x = (long) first.getX() - second.getX();
        long z = (long) first.getZ() - second.getZ();
        return x * x + z * z;
    }
}
