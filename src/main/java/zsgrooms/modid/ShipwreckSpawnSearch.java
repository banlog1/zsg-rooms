package zsgrooms.modid;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class ShipwreckSpawnSearch {
    static final int RADIUS = 128;
    static final int MAX_CHUNKS = 64;

    interface Terrain {
        void loadChunk(int x, int z);
        BlockPos safeNaturalSurface(int x, int z);
    }

    static final class Result {
        final BlockPos spawn;
        final int chunksChecked;

        Result(BlockPos spawn, int chunksChecked) {
            this.spawn = spawn;
            this.chunksChecked = chunksChecked;
        }
    }

    static Result find(BlockPos target, Terrain terrain) {
        List<Candidate> chunks = new ArrayList<Candidate>();
        for (int x = (target.getX() - RADIUS) >> 4; x <= (target.getX() + RADIUS) >> 4; x++) {
            for (int z = (target.getZ() - RADIUS) >> 4; z <= (target.getZ() + RADIUS) >> 4; z++) {
                int nearestX = Math.max(x * 16, Math.min(x * 16 + 15, target.getX()));
                int nearestZ = Math.max(z * 16, Math.min(z * 16 + 15, target.getZ()));
                long distance = distanceSquared(target, nearestX, nearestZ);
                if (distance <= (long) RADIUS * RADIUS) chunks.add(new Candidate(x, z, distance));
            }
        }
        chunks.sort(Comparator.comparingLong((Candidate c) -> c.distance)
                .thenComparingInt(c -> c.x).thenComparingInt(c -> c.z));
        BlockPos best = null;
        long bestDistance = (long) RADIUS * RADIUS;
        int checked = 0;
        for (Candidate chunk : chunks) {
            // Once a chunk cannot contain a closer point, no farther chunks are needed.
            if (chunk.distance > bestDistance) break;
            if (checked == MAX_CHUNKS) return new Result(null, checked);
            checked++;
            terrain.loadChunk(chunk.x, chunk.z);
            for (int x = chunk.x * 16; x < chunk.x * 16 + 16; x++) {
                for (int z = chunk.z * 16; z < chunk.z * 16 + 16; z++) {
                    long distance = distanceSquared(target, x, z);
                    if (distance > bestDistance || (distance == bestDistance && best != null
                            && (x > best.getX() || (x == best.getX() && z >= best.getZ())))) continue;
                    BlockPos surface = terrain.safeNaturalSurface(x, z);
                    if (surface != null) {
                        best = surface.toImmutable();
                        bestDistance = distance;
                    }
                }
            }
        }
        return new Result(best, checked);
    }

    private static long distanceSquared(BlockPos target, int x, int z) {
        long dx = (long) x - target.getX(), dz = (long) z - target.getZ();
        return dx * dx + dz * dz;
    }

    private static final class Candidate {
        final int x, z;
        final long distance;

        Candidate(int x, int z, long distance) {
            this.x = x;
            this.z = z;
            this.distance = distance;
        }
    }

    private ShipwreckSpawnSearch() {}
}
