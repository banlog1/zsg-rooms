package zsgrooms.modid.filter;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static zsgrooms.modid.filter.SurfaceTerrainChecks.Cell.*;

class SurfaceTerrainChecksTest {
    @Test
    void acceptsConnectedTenSourcePoolWithDryShore() {
        Fixture terrain = new Fixture();
        terrain.pool(8, 64, 4, 5, 2);
        SurfaceTerrainChecks.LavaPool pool = pool(terrain).get();
        assertEquals(10, pool.exposedSources);
        assertEquals(10, new java.util.HashSet<BlockPos>(pool.sources).size());
        for (BlockPos source : pool.sources) assertEquals(LAVA_SOURCE, terrain.cell(source.getX(), source.getY(), source.getZ()));
        assertThrows(UnsupportedOperationException.class, () -> pool.sources.clear());
        assertEquals(new BlockPos(8, 64, 4), pool.anchor);
        assertEquals(AIR, terrain.cell(pool.shore.getX(), pool.shore.getY(), pool.shore.getZ()));
    }

    @Test
    void rejectsTooFewSourcesEvenWhenFlowingLavaFillsTheRest() {
        Fixture terrain = new Fixture();
        terrain.pool(8, 64, 4, 5, 2);
        terrain.set(8, 64, 4, OTHER);
        assertFalse(pool(terrain).isPresent());
    }

    @Test
    void separatePuddlesDoNotAddTogether() {
        Fixture terrain = new Fixture();
        terrain.pool(8, 64, 4, 2, 3);
        terrain.pool(12, 64, 4, 2, 3);
        assertFalse(pool(terrain).isPresent());
    }

    @Test
    void diagonalPuddlesDoNotJoin() {
        Fixture terrain = new Fixture();
        terrain.pool(8, 64, 4, 2, 3);
        terrain.pool(10, 64, 7, 2, 3);
        assertFalse(pool(terrain).isPresent());
    }

    @Test
    void differentHeightsDoNotCombineIntoAPool() {
        Fixture terrain = new Fixture();
        terrain.pool(8, 64, 4, 2, 3);
        terrain.pool(10, 65, 4, 2, 3);
        assertFalse(pool(terrain).isPresent());
    }

    @Test
    void rejectsOneBlockWideLavaChannels() {
        Fixture terrain = new Fixture();
        terrain.pool(8, 64, 4, 10, 1);
        assertFalse(pool(terrain).isPresent());
    }

    @Test
    void rejectsCoveredOrLowRavineLava() {
        Fixture covered = new Fixture();
        covered.pool(8, 64, 4, 5, 2);
        for (int x = 8; x < 13; x++) for (int z = 4; z < 6; z++) covered.set(x, 70, z, SOLID);
        assertFalse(pool(covered).isPresent());
        Fixture low = new Fixture();
        low.defaultY = 59;
        low.pool(8, 59, 4, 5, 2);
        assertFalse(pool(low).isPresent());
    }

    @Test
    void requiresDryShoreAndTwoBlocksOfHeadroom() {
        Fixture terrain = new Fixture();
        terrain.pool(8, 64, 4, 5, 2);
        for (int x = 7; x <= 13; x++) for (int z = 3; z <= 6; z++) {
            if (x < 8 || x > 12 || z < 4 || z > 5) terrain.set(x, 65, z, OTHER);
        }
        assertFalse(pool(terrain).isPresent());
    }

    @Test
    void usesCircularNotSquareDistanceAndIncludesExactBoundary() {
        Fixture outside = new Fixture();
        outside.pool(80, 64, 80, 5, 2);
        assertFalse(pool(outside).isPresent());
        Fixture edge = new Fixture();
        edge.pool(92, 64, -1, 4, 3);
        edge.set(96, 64, 0, LAVA_SOURCE);
        assertEquals(13, pool(edge).get().exposedSources);
    }

    @Test
    void nearestPoolSelectionIsRepeatableAndHandlesNegativeCoordinates() {
        Fixture terrain = new Fixture();
        terrain.pool(-8, 64, -4, 5, 2);
        terrain.pool(20, 64, 20, 5, 2);
        BlockPos expected = new BlockPos(-4, 64, -3);
        assertEquals(expected, pool(terrain).get().anchor);
        assertEquals(expected, pool(terrain).get().anchor);
    }

    @Test
    void acceptsActualRootedTreeOnLand() {
        Fixture terrain = new Fixture();
        terrain.tree(8, 4);
        SurfaceTerrainChecks.WoodedLand land = woods(terrain).get();
        assertEquals(new BlockPos(8, 65, 4), land.trunk);
        assertEquals(65, land.standingSpace.getY());
    }

    @Test
    void acceptsBothSmallIslandAndLargerCoastline() {
        Fixture island = new Fixture();
        island.defaultGround = OTHER;
        for (int x = 6; x <= 10; x++) for (int z = 2; z <= 6; z++) island.set(x, 64, z, SOIL);
        island.tree(8, 4);
        Fixture coast = new Fixture();
        coast.tree(8, 4);
        assertTrue(woods(island).isPresent());
        assertTrue(woods(coast).isPresent());
    }

    @Test
    void rejectsBiomeOnlyBareLandAndLeavesWithoutATrunk() {
        Fixture terrain = new Fixture();
        assertFalse(woods(terrain).isPresent());
        for (int x = 6; x <= 10; x++) terrain.set(x, 68, 4, NATURAL_LEAVES);
        assertFalse(woods(terrain).isPresent());
    }

    @Test
    void rejectsUnrootedLogsAndShortStumps() {
        Fixture floating = new Fixture();
        floating.tree(8, 4);
        floating.set(8, 64, 4, OTHER);
        assertFalse(woods(floating).isPresent());
        Fixture stump = new Fixture();
        stump.tree(8, 4);
        stump.set(8, 68, 4, AIR);
        assertFalse(woods(stump).isPresent());
    }

    @Test
    void requiresCanopyAndDryStandingSpace() {
        Fixture bare = new Fixture();
        for (int y = 65; y <= 68; y++) bare.set(8, y, 4, LOG);
        assertFalse(woods(bare).isPresent());
        Fixture flooded = new Fixture();
        flooded.tree(8, 4);
        flooded.defaultGround = OTHER;
        flooded.set(8, 64, 4, SOIL);
        assertFalse(woods(flooded).isPresent());
    }

    @Test
    void woodDistanceAndSelectionAreDeterministic() {
        Fixture terrain = new Fixture();
        terrain.tree(97, 0);
        assertFalse(woods(terrain).isPresent());
        terrain.tree(96, 0);
        assertEquals(96, woods(terrain).get().trunk.getX());
        terrain.tree(-8, -4);
        assertEquals(new BlockPos(-8, 65, -4), woods(terrain).get().trunk);
    }

    @Test
    void validatesRadiusBeforeAllocatingOrReadingTerrain() {
        assertThrows(IllegalArgumentException.class, () -> SurfaceTerrainChecks.findLavaPool(null, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> SurfaceTerrainChecks.findWoodedLand(null, 0, 0, 129));
    }

    private static Optional<SurfaceTerrainChecks.LavaPool> pool(Fixture terrain) {
        return SurfaceTerrainChecks.findLavaPool(terrain, 0, 0, SurfaceTerrainChecks.LAVA_POOL_RADIUS);
    }

    private static Optional<SurfaceTerrainChecks.WoodedLand> woods(Fixture terrain) {
        return SurfaceTerrainChecks.findWoodedLand(terrain, 0, 0, SurfaceTerrainChecks.WOODED_LAND_RADIUS);
    }

    static final class Fixture implements SurfaceTerrainChecks.Terrain {
        final Map<BlockPos, SurfaceTerrainChecks.Cell> cells = new HashMap<BlockPos, SurfaceTerrainChecks.Cell>();
        final Map<Long, Integer> heights = new HashMap<Long, Integer>();
        SurfaceTerrainChecks.Cell defaultGround = SOIL;
        int defaultY = 64;

        void set(int x, int y, int z, SurfaceTerrainChecks.Cell cell) {
            cells.put(new BlockPos(x, y, z), cell);
            long column = column(x, z);
            if (cell != AIR) heights.put(column, Math.max(heights.getOrDefault(column, defaultY), y));
        }

        void pool(int x, int y, int z, int width, int depth) {
            for (int dx = 0; dx < width; dx++) for (int dz = 0; dz < depth; dz++) set(x + dx, y, z + dz, LAVA_SOURCE);
        }

        void tree(int x, int z) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) set(x + dx, 68, z + dz, NATURAL_LEAVES);
            for (int y = 65; y <= 68; y++) set(x, y, z, LOG);
            set(x, 69, z, NATURAL_LEAVES);
        }

        @Override
        public int surfaceY(int x, int z) {
            return heights.getOrDefault(column(x, z), defaultY);
        }

        @Override
        public SurfaceTerrainChecks.Cell cell(int x, int y, int z) {
            return cells.getOrDefault(new BlockPos(x, y, z), y <= defaultY ? defaultGround : AIR);
        }

        private long column(int x, int z) {
            return ((long) x << 32) ^ (z & 0xffffffffL);
        }
    }
}
