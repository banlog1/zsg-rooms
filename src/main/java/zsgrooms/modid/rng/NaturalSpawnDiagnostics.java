package zsgrooms.modid.rng;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.feature.StructureFeature;
import zsgrooms.modid.SeedDebugLog;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class NaturalSpawnDiagnostics {
    private static final Map<ServerWorld, Map<Long, CapWindow>> CAP_WINDOWS =
            new WeakHashMap<ServerWorld, Map<Long, CapWindow>>();

    private NaturalSpawnDiagnostics() {
    }

    public static boolean hasFortressReference(Chunk chunk) {
        return !chunk.getStructureReferences(StructureFeature.FORTRESS).isEmpty();
    }

    public static synchronized void recordCap(
            ServerWorld world, Chunk chunk, boolean allowed, int count, int cap, int spawningChunks
    ) {
        Map<Long, CapWindow> windows = CAP_WINDOWS.get(world);
        if (windows == null) {
            windows = new LinkedHashMap<Long, CapWindow>(32, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, CapWindow> eldest) {
                    return size() > 512;
                }
            };
            CAP_WINDOWS.put(world, windows);
        }
        long key = chunk.getPos().toLong();
        CapWindow window = windows.get(key);
        if (window == null) {
            window = new CapWindow();
            windows.put(key, window);
        }
        long tick = world.getTime();
        if (window.record(tick, allowed)) {
            SeedDebugLog.info(
                    "[ZSG-Rooms/NaturalSpawn] cap-summary chunk={},{} tick={} "
                            + "allowedNow={} monsters={} cap={} spawningChunks={} "
                            + "admitted={} blocked={} initialBelowWorld={} initialSolid={} "
                            + "lastObservedCycle={} sinceTick={} cycle=not_started",
                    chunk.getPos().x, chunk.getPos().z, tick, allowed, count, cap, spawningChunks,
                    window.admitted, window.blocked, window.initialBelowWorld, window.initialSolid,
                    window.lastCycle, window.sinceTick);
            window.clear(tick);
        }
    }

    public static synchronized void recordInitial(
            ServerWorld world, Chunk chunk, long cycle, boolean belowWorld, boolean solid
    ) {
        Map<Long, CapWindow> windows = CAP_WINDOWS.get(world);
        CapWindow window = windows == null ? null : windows.get(chunk.getPos().toLong());
        if (window != null) {
            window.lastCycle = cycle;
            if (belowWorld) {
                window.initialBelowWorld++;
            } else if (solid) {
                window.initialSolid++;
            }
        }
    }

    public static void rejectionDetails(
            ServerWorld world, BlockPos pos, EntityType<?> type, Entity mob, double squaredDistance
    ) {
        Box box = mob == null
                ? type.createSimpleBoundingBox(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)
                : mob.getBoundingBox();
        // Inspect geometry only. Re-running spawn predicates could consume more RNG.
        boolean blockCollision = world.getBlockCollisions(mob, box).anyMatch(shape -> !shape.isEmpty());
        StringBuilder overlaps = new StringBuilder();
        int overlapCount = 0;
        for (Entity other : world.getEntities(mob, box)) {
            if (other.removed || !other.inanimate
                    || (mob != null && other.isConnectedThroughVehicle(mob))) {
                continue;
            }
            if (overlapCount++ < 3) {
                if (overlaps.length() > 0) {
                    overlaps.append(',');
                }
                overlaps.append(Registry.ENTITY_TYPE.getId(other.getType()))
                        .append('@').append(other.getBlockPos());
            }
        }
        SeedDebugLog.info(
                "[ZSG-Rooms/NaturalSpawn] rejection-details tick={} pos={} entity={} "
                        + "distanceSquared={} below={} feet={} head={} blockLight={} skyLight={} "
                        + "bounds={} blockCollision={} fluid={} blockingEntities={} overlaps=[{}]",
                world.getTime(), pos, Registry.ENTITY_TYPE.getId(type), squaredDistance,
                world.getBlockState(pos.down()), world.getBlockState(pos), world.getBlockState(pos.up()),
                world.getLightLevel(LightType.BLOCK, pos), world.getLightLevel(LightType.SKY, pos),
                box, blockCollision, world.containsFluid(box), overlapCount, overlaps);
    }

    static final class CapWindow {
        long sinceTick;
        long lastLogTick;
        int admitted;
        int blocked;
        int initialBelowWorld;
        int initialSolid;
        long lastCycle = -1L;
        boolean initialized;
        boolean lastAllowed;

        boolean record(long tick, boolean allowed) {
            if (!this.initialized) {
                this.sinceTick = tick;
            }
            if (allowed) {
                this.admitted++;
            } else {
                this.blocked++;
            }
            boolean report = !this.initialized || allowed != this.lastAllowed
                    || tick < this.lastLogTick || tick - this.lastLogTick >= 100L;
            this.lastAllowed = allowed;
            return report;
        }

        void clear(long tick) {
            this.initialized = true;
            this.lastLogTick = tick;
            this.sinceTick = tick;
            this.admitted = 0;
            this.blocked = 0;
            this.initialBelowWorld = 0;
            this.initialSolid = 0;
        }
    }
}
