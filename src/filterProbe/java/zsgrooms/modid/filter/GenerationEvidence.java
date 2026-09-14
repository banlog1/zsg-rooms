package zsgrooms.modid.filter;

import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Disposable, development-only evidence from real feature generation; never used by the race mod. */
public final class GenerationEvidence {
    private static final ConcurrentHashMap<ServerWorld, Set<Long>> LAVA = new ConcurrentHashMap<ServerWorld, Set<Long>>();

    private GenerationEvidence() {
    }

    public static void naturalLava(ServerWorld world, BlockPos position) {
        LAVA.computeIfAbsent(world, key -> ConcurrentHashMap.newKeySet()).add(position.asLong());
    }

    public static boolean isNaturalPool(ServerWorld world, SurfaceTerrainChecks.LavaPool pool) {
        Set<Long> sources = LAVA.get(world);
        return sources != null && pool.sources.stream().filter(pos -> sources.contains(pos.asLong())).limit(10).count() >= 10;
    }

    public static void discard(ServerWorld world) {
        LAVA.remove(world);
    }

    public static void clear() {
        LAVA.clear();
    }
}
