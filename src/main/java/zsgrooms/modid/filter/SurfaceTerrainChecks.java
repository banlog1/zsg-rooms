package zsgrooms.modid.filter;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Read-only checks for fully decorated Overworld terrain, not biome or feature-attempt predictions. */
public final class SurfaceTerrainChecks {
    public static final int LAVA_POOL_RADIUS = 96;
    public static final int WOODED_LAND_RADIUS = 96;
    public static final int MIN_POOL_SOURCES = 10;
    public static final int MIN_POOL_Y = 60;
    static final int MAX_RADIUS = 128;
    static final int MAX_TRUNK_SEARCH = 32;

    private SurfaceTerrainChecks() {
    }

    public enum Cell {
        AIR, SOLID, SOIL, LOG, NATURAL_LEAVES, LAVA_SOURCE, OTHER
    }

    public interface Terrain {
        /** Y of the highest non-air block, including leaves and fluids. */
        int surfaceY(int x, int z);

        Cell cell(int x, int y, int z);
    }

    public static final class LavaPool {
        public final BlockPos anchor;
        public final BlockPos shore;
        public final int exposedSources;
        public final List<BlockPos> sources;

        private LavaPool(BlockPos anchor, BlockPos shore, List<BlockPos> sources) {
            this.anchor = anchor;
            this.shore = shore;
            this.exposedSources = sources.size();
            this.sources = Collections.unmodifiableList(sources);
        }
    }

    public static final class WoodedLand {
        public final BlockPos trunk;
        public final BlockPos standingSpace;

        private WoodedLand(BlockPos trunk, BlockPos standingSpace) {
            this.trunk = trunk;
            this.standingSpace = standingSpace;
        }
    }

    public static Optional<LavaPool> findLavaPool(Terrain terrain, int originX, int originZ, int radius) {
        List<LavaPool> pools = findLavaPools(terrain, originX, originZ, radius);
        return pools.isEmpty() ? Optional.empty() : Optional.of(pools.get(0));
    }

    /** Closest first, so secondary criteria can reject one pool without hiding other usable pools. */
    public static List<LavaPool> findLavaPools(Terrain terrain, int originX, int originZ, int radius) {
        checkRadius(radius);
        int width = radius * 2 + 1;
        int[] heights = new int[width * width];
        boolean[] sources = new boolean[heights.length];
        boolean[] visited = new boolean[heights.length];
        int[] queue = new int[heights.length];
        int minX = originX - radius;
        int minZ = originZ - radius;
        for (int z = 0; z < width; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                int y = terrain.surfaceY(minX + x, minZ + z);
                heights[index] = y;
                sources[index] = distanceSquared(x, z, radius, radius) <= radius * radius
                        && y >= MIN_POOL_Y && terrain.cell(minX + x, y, minZ + z) == Cell.LAVA_SOURCE
                        && terrain.cell(minX + x, y + 1, minZ + z) == Cell.AIR;
            }
        }

        List<LavaPool> pools = new ArrayList<LavaPool>();
        for (int start = 0; start < sources.length; start++) {
            if (!sources[start] || visited[start]) {
                continue;
            }
            int count = 1;
            queue[0] = start;
            visited[start] = true;
            int y = heights[start];
            boolean hasSquare = false;
            BlockPos anchor = null;
            BlockPos shore = null;
            for (int head = 0; head < count; head++) {
                int index = queue[head];
                int x = index % width;
                int z = index / width;
                BlockPos position = new BlockPos(minX + x, y, minZ + z);
                if (closer(position, anchor, originX, originZ)) {
                    anchor = position;
                }
                BlockPos candidateShore = standingSpace(terrain, position, originX, originZ);
                if (candidateShore != null && closer(candidateShore, shore, originX, originZ)) {
                    shore = candidateShore;
                }
                hasSquare |= x + 1 < width && z + 1 < width
                        && sourceAt(index + 1, y, sources, heights)
                        && sourceAt(index + width, y, sources, heights)
                        && sourceAt(index + width + 1, y, sources, heights);
                if (x > 0) count = enqueue(index - 1, y, sources, heights, visited, queue, count);
                if (x + 1 < width) count = enqueue(index + 1, y, sources, heights, visited, queue, count);
                if (z > 0) count = enqueue(index - width, y, sources, heights, visited, queue, count);
                if (z + 1 < width) count = enqueue(index + width, y, sources, heights, visited, queue, count);
            }
            if (count >= MIN_POOL_SOURCES && hasSquare && shore != null) {
                List<BlockPos> positions = new ArrayList<BlockPos>(count);
                for (int i = 0; i < count; i++) positions.add(new BlockPos(minX + queue[i] % width, y, minZ + queue[i] / width));
                pools.add(new LavaPool(anchor, shore, positions));
            }
        }
        pools.sort((first, second) -> first.anchor.equals(second.anchor) ? 0
                : closer(first.anchor, second.anchor, originX, originZ) ? -1 : 1);
        return Collections.unmodifiableList(pools);
    }

    public static Optional<WoodedLand> findWoodedLand(Terrain terrain, int originX, int originZ, int radius) {
        checkRadius(radius);
        WoodedLand best = null;
        for (int z = originZ - radius; z <= originZ + radius; z++) {
            for (int x = originX - radius; x <= originX + radius; x++) {
                if (distanceSquared(x, z, originX, originZ) > radius * radius) {
                    continue;
                }
                int surface = terrain.surfaceY(x, z);
                Cell top = terrain.cell(x, surface, z);
                if (top != Cell.LOG && top != Cell.NATURAL_LEAVES) {
                    continue;
                }
                int y = surface;
                while (surface - y < MAX_TRUNK_SEARCH
                        && (terrain.cell(x, y, z) == Cell.NATURAL_LEAVES || terrain.cell(x, y, z) == Cell.AIR)) {
                    y--;
                }
                int trunkTop = y;
                while (surface - y < MAX_TRUNK_SEARCH && terrain.cell(x, y, z) == Cell.LOG) {
                    y--;
                }
                if (trunkTop - y < 4 || terrain.cell(x, y, z) != Cell.SOIL) {
                    continue;
                }
                BlockPos trunk = new BlockPos(x, y + 1, z);
                BlockPos space = standingSpace(terrain, new BlockPos(x, y, z), originX, originZ);
                if (space != null && closer(trunk, best == null ? null : best.trunk, originX, originZ)
                        && hasCanopy(terrain, x, trunkTop, z)) {
                    best = new WoodedLand(trunk, space);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean hasCanopy(Terrain terrain, int x, int y, int z) {
        int leaves = 0;
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -1; dy <= 3; dy++) {
                    if (terrain.cell(x + dx, y + dy, z + dz) == Cell.NATURAL_LEAVES && ++leaves >= 8) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static BlockPos standingSpace(Terrain terrain, BlockPos beside, int originX, int originZ) {
        BlockPos best = null;
        for (int direction = 0; direction < 4; direction++) {
            int x = beside.getX() + (direction == 0 ? -1 : direction == 1 ? 1 : 0);
            int z = beside.getZ() + (direction == 2 ? -1 : direction == 3 ? 1 : 0);
            // A canopy is allowed above the shore; require actual two-block standing space below it.
            for (int y = beside.getY() - 1; y <= beside.getY() + 1; y++) {
                Cell ground = terrain.cell(x, y, z);
                if ((ground == Cell.SOLID || ground == Cell.SOIL)
                        && terrain.cell(x, y + 1, z) == Cell.AIR && terrain.cell(x, y + 2, z) == Cell.AIR) {
                    BlockPos candidate = new BlockPos(x, y + 1, z);
                    if (closer(candidate, best, originX, originZ)) {
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    private static int enqueue(int index, int y, boolean[] sources, int[] heights,
                               boolean[] visited, int[] queue, int count) {
        if (!visited[index] && sourceAt(index, y, sources, heights)) {
            visited[index] = true;
            queue[count++] = index;
        }
        return count;
    }

    private static boolean sourceAt(int index, int y, boolean[] sources, int[] heights) {
        return sources[index] && heights[index] == y;
    }

    private static boolean closer(BlockPos candidate, BlockPos current, int originX, int originZ) {
        if (current == null) return true;
        int distance = distanceSquared(candidate.getX(), candidate.getZ(), originX, originZ);
        int otherDistance = distanceSquared(current.getX(), current.getZ(), originX, originZ);
        if (distance != otherDistance) return distance < otherDistance;
        if (candidate.getZ() != current.getZ()) return candidate.getZ() < current.getZ();
        if (candidate.getX() != current.getX()) return candidate.getX() < current.getX();
        return candidate.getY() < current.getY();
    }

    private static int distanceSquared(int x, int z, int originX, int originZ) {
        int dx = x - originX;
        int dz = z - originZ;
        return dx * dx + dz * dz;
    }

    static void checkRadius(int radius) {
        if (radius < 1 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("Terrain check radius must be between 1 and " + MAX_RADIUS);
        }
    }
}
