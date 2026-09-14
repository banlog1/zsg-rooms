package zsgrooms.modid.filter;

import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** Partial temple validation only. Lake provenance and the remaining Nether checks are separate requirements. */
public final class TempleCandidateChecks {
    public static final int SPAWN_RADIUS = 32;
    public static final int ENTRY_ENVELOPE = 224;
    public static final int WOOD_RADIUS = 20;

    public enum Rejection {
        TEMPLE_CHESTS, TEMPLE_RESOURCES, NO_SURFACE_POOL, NO_NATURAL_POOL, POOL_ENTRY_DISTANCE, SPAWN_DISTANCE, POOL_WOOD
    }

    public static final class Result {
        public final Set<Rejection> rejections;
        public final int surfacePools;
        private final SurfaceTerrainChecks.LavaPool pool;

        private Result(EnumSet<Rejection> rejections, int surfacePools, SurfaceTerrainChecks.LavaPool pool) {
            this.rejections = Collections.unmodifiableSet(EnumSet.copyOf(rejections));
            this.surfacePools = surfacePools;
            this.pool = pool;
        }

        public Optional<SurfaceTerrainChecks.LavaPool> pool() {
            return Optional.ofNullable(pool);
        }
    }

    private TempleCandidateChecks() {
    }

    /** One iron for ignition, a diamond or iron pickaxe, and three iron for a bucket. */
    static boolean hasResources(int iron, int diamonds) {
        return iron >= (diamonds >= 3 ? 4 : 7) && diamonds >= 0;
    }

    static boolean withinAxes(BlockPos first, BlockPos second, int distance) {
        return Math.abs((long) first.getX() - second.getX()) <= distance
                && Math.abs((long) first.getZ() - second.getZ()) <= distance;
    }

    public static Result check(SurfaceTerrainChecks.Terrain terrain, BlockPos temple, BlockPos spawn,
                               int chests, int iron, int diamonds) {
        return check(terrain, temple, spawn, chests, iron, diamonds, pool -> true);
    }

    public static Result check(SurfaceTerrainChecks.Terrain terrain, BlockPos temple, BlockPos spawn,
                               int chests, int iron, int diamonds, Predicate<SurfaceTerrainChecks.LavaPool> provenance) {
        return check(terrain, temple, spawn, chests, iron, diamonds, provenance,
                pos -> SurfaceTerrainChecks.findWoodedLand(terrain, pos.getX(), pos.getZ(), WOOD_RADIUS).isPresent());
    }

    public static Result check(SurfaceTerrainChecks.Terrain terrain, BlockPos temple, BlockPos spawn,
                               int chests, int iron, int diamonds, Predicate<SurfaceTerrainChecks.LavaPool> provenance,
                               Predicate<BlockPos> woodCheck) {
        return check(terrain, temple, chests, iron, diamonds, provenance, woodCheck,
                pool -> withinAxes(spawn, temple, SPAWN_RADIUS) || withinAxes(spawn, pool, SPAWN_RADIUS));
    }

    public static Result check(SurfaceTerrainChecks.Terrain terrain, BlockPos temple,
                               int chests, int iron, int diamonds, Predicate<SurfaceTerrainChecks.LavaPool> provenance,
                               Predicate<BlockPos> woodCheck, Predicate<BlockPos> spawnCheck) {
        EnumSet<Rejection> rejected = EnumSet.noneOf(Rejection.class);
        if (chests != 4) rejected.add(Rejection.TEMPLE_CHESTS);
        if (!hasResources(iron, diamonds)) rejected.add(Rejection.TEMPLE_RESOURCES);
        List<SurfaceTerrainChecks.LavaPool> pools = ValidationTrace.measure("pool.shape", () -> SurfaceTerrainChecks.findLavaPools(terrain,
                temple.getX(), temple.getZ(), SurfaceTerrainChecks.LAVA_POOL_RADIUS));
        int entryPools = 0;
        int naturalPools = 0;
        int spawnPools = 0;
        SurfaceTerrainChecks.LavaPool selected = null;
        for (SurfaceTerrainChecks.LavaPool pool : pools) {
            if (!ValidationTrace.measure("pool.provenance", () -> provenance.test(pool))) continue;
            naturalPools++;
            if (!ValidationTrace.measure("pool.entryDistance", () -> withinAxes(pool.anchor, BlockPos.ORIGIN, ENTRY_ENVELOPE))) continue;
            entryPools++;
            if (!ValidationTrace.measure("pool.spawnDistance", () -> spawnCheck.test(pool.anchor))) continue;
            spawnPools++;
            if (ValidationTrace.measure("pool.wood", () -> woodCheck.test(pool.anchor))) {
                selected = pool;
                break;
            }
        }
        if (pools.isEmpty()) rejected.add(Rejection.NO_SURFACE_POOL);
        else if (naturalPools == 0) rejected.add(Rejection.NO_NATURAL_POOL);
        else if (entryPools == 0) rejected.add(Rejection.POOL_ENTRY_DISTANCE);
        else if (spawnPools == 0) rejected.add(Rejection.SPAWN_DISTANCE);
        else if (selected == null) rejected.add(Rejection.POOL_WOOD);
        return new Result(rejected, pools.size(), selected);
    }
}
