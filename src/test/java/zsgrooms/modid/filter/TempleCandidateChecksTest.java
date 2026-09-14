package zsgrooms.modid.filter;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static zsgrooms.modid.filter.TempleCandidateChecks.Rejection.*;

class TempleCandidateChecksTest {
    @Test
    void allocatesIgnitionPickaxeAndBucketWithoutDoubleCountingIron() {
        assertTrue(TempleCandidateChecks.hasResources(7, 0));
        assertFalse(TempleCandidateChecks.hasResources(6, 0));
        assertTrue(TempleCandidateChecks.hasResources(4, 3));
        assertFalse(TempleCandidateChecks.hasResources(3, 3));
        assertFalse(TempleCandidateChecks.hasResources(4, 2));
        assertTrue(TempleCandidateChecks.hasResources(7, 2));
        assertFalse(TempleCandidateChecks.hasResources(-1, 100));
        assertFalse(TempleCandidateChecks.hasResources(100, -1));
    }

    @Test
    void spawnDistanceUsesInclusivePerAxisBoundsIgnoringHeight() {
        assertTrue(TempleCandidateChecks.withinAxes(BlockPos.ORIGIN, new BlockPos(-32, 250, 32), 32));
        assertFalse(TempleCandidateChecks.withinAxes(BlockPos.ORIGIN, new BlockPos(-33, 0, 0), 32));
        assertFalse(TempleCandidateChecks.withinAxes(new BlockPos(Integer.MIN_VALUE, 0, 0),
                new BlockPos(Integer.MAX_VALUE, 0, 0), 32));
    }

    @Test
    void requiresAllFourChestsAndEnoughResources() {
        SurfaceTerrainChecksTest.Fixture terrain = viable();
        assertTrue(check(terrain, BlockPos.ORIGIN, 4, 7, 0).rejections.isEmpty());
        assertTrue(check(terrain, BlockPos.ORIGIN, 3, 7, 0).rejections.contains(TEMPLE_CHESTS));
        assertTrue(check(terrain, BlockPos.ORIGIN, 5, 7, 0).rejections.contains(TEMPLE_CHESTS));
        assertTrue(check(terrain, BlockPos.ORIGIN, 4, 6, 0).rejections.contains(TEMPLE_RESOURCES));
    }

    @Test
    void acceptsSpawnNearPoolEvenWhenFarFromTemple() {
        SurfaceTerrainChecksTest.Fixture terrain = new SurfaceTerrainChecksTest.Fixture();
        terrain.pool(60, 64, 0, 5, 2);
        terrain.tree(70, 0);
        assertTrue(check(terrain, new BlockPos(92, 80, -32), 4, 4, 3).rejections.isEmpty());
        assertTrue(check(terrain, new BlockPos(93, 80, -32), 4, 4, 3).rejections.contains(SPAWN_DISTANCE));
    }

    @Test
    void acceptsSpawnNearTempleEvenWhenFarFromPool() {
        SurfaceTerrainChecksTest.Fixture terrain = new SurfaceTerrainChecksTest.Fixture();
        terrain.pool(60, 64, 0, 5, 2);
        terrain.tree(70, 0);
        assertTrue(check(terrain, BlockPos.ORIGIN, 4, 7, 0).rejections.isEmpty());
    }

    @Test
    void requiresWoodAtPoolNotMerelySomewhereNearTemple() {
        SurfaceTerrainChecksTest.Fixture terrain = viable();
        assertTrue(check(terrain, BlockPos.ORIGIN, 4, 7, 0).pool().isPresent());
        SurfaceTerrainChecksTest.Fixture distant = new SurfaceTerrainChecksTest.Fixture();
        distant.pool(8, 64, 0, 5, 2);
        distant.tree(60, 0);
        assertTrue(check(distant, BlockPos.ORIGIN, 4, 7, 0).rejections.contains(POOL_WOOD));
    }

    @Test
    void checksFartherPoolWhenNearestFailsWoodRequirement() {
        SurfaceTerrainChecksTest.Fixture terrain = new SurfaceTerrainChecksTest.Fixture();
        terrain.pool(8, 64, 0, 5, 2);
        terrain.pool(60, 64, 0, 5, 2);
        terrain.tree(70, 0);
        TempleCandidateChecks.Result result = check(terrain, BlockPos.ORIGIN, 4, 7, 0);
        assertTrue(result.rejections.isEmpty());
        assertEquals(2, result.surfacePools);
        assertEquals(60, result.pool().get().anchor.getX());
    }

    @Test
    void enforcesPoolEntryEnvelopeAndIncludesItsBoundary() {
        SurfaceTerrainChecksTest.Fixture terrain = new SurfaceTerrainChecksTest.Fixture();
        BlockPos temple = new BlockPos(210, 64, 0);
        terrain.pool(224, 64, 0, 5, 2);
        terrain.tree(230, 8);
        assertTrue(TempleCandidateChecks.check(terrain, temple, temple, 4, 7, 0).rejections.isEmpty());
        SurfaceTerrainChecksTest.Fixture outside = new SurfaceTerrainChecksTest.Fixture();
        outside.pool(225, 64, 0, 5, 2);
        outside.tree(230, 8);
        assertTrue(TempleCandidateChecks.check(outside, temple, temple, 4, 7, 0).rejections.contains(POOL_ENTRY_DISTANCE));
    }

    @Test
    void rejectsNoPoolWithoutClaimingSecondaryChecksFailed() {
        TempleCandidateChecks.Result result = check(new SurfaceTerrainChecksTest.Fixture(), BlockPos.ORIGIN, 4, 7, 0);
        assertEquals(java.util.Collections.singleton(NO_SURFACE_POOL), result.rejections);
        assertFalse(result.pool().isPresent());
        assertThrows(UnsupportedOperationException.class, () -> result.rejections.clear());
    }

    @Test
    void checksWoodBeyondTemplePoolSearchRadiusAndIncludesTwentyBlockBoundary() {
        SurfaceTerrainChecksTest.Fixture terrain = new SurfaceTerrainChecksTest.Fixture();
        terrain.pool(90, 64, 0, 5, 2);
        terrain.tree(110, 0);
        assertTrue(check(terrain, BlockPos.ORIGIN, 4, 7, 0).rejections.isEmpty());
        SurfaceTerrainChecksTest.Fixture outside = new SurfaceTerrainChecksTest.Fixture();
        outside.pool(90, 64, 0, 5, 2);
        outside.tree(111, 0);
        assertTrue(check(outside, BlockPos.ORIGIN, 4, 7, 0).rejections.contains(POOL_WOOD));
    }

    @Test
    void rejectsPortalLavaEvenWhenItsTerrainShapePasses() {
        TempleCandidateChecks.Result result = TempleCandidateChecks.check(viable(), BlockPos.ORIGIN, BlockPos.ORIGIN,
                4, 7, 0, pool -> false);
        assertEquals(java.util.Collections.singleton(NO_NATURAL_POOL), result.rejections);
        assertFalse(result.pool().isPresent());
    }

    @Test
    void biomeProfileDoesNotRequireAnActualTreeOrLetOneOverrideTheBiomeRule() {
        SurfaceTerrainChecksTest.Fixture noTree = new SurfaceTerrainChecksTest.Fixture();
        noTree.pool(8, 64, 0, 5, 2);
        TempleCandidateChecks.Result accepted = TempleCandidateChecks.check(noTree, BlockPos.ORIGIN, BlockPos.ORIGIN,
                4, 7, 0, pool -> true, pos -> true);
        assertTrue(accepted.rejections.isEmpty());
        assertTrue(accepted.pool().isPresent());

        TempleCandidateChecks.Result rejected = TempleCandidateChecks.check(viable(), BlockPos.ORIGIN, BlockPos.ORIGIN,
                4, 7, 0, pool -> true, pos -> false);
        assertEquals(java.util.Collections.singleton(POOL_WOOD), rejected.rejections);
        assertFalse(rejected.pool().isPresent());
    }

    @Test
    void allPoolsAreImmutableAndUseDeterministicDistanceAndCoordinateOrdering() {
        SurfaceTerrainChecksTest.Fixture terrain = new SurfaceTerrainChecksTest.Fixture();
        terrain.pool(8, 64, 0, 5, 2);
        terrain.pool(-12, 64, 0, 5, 2);
        List<SurfaceTerrainChecks.LavaPool> pools = SurfaceTerrainChecks.findLavaPools(terrain, 0, 0, 96);
        assertEquals(2, pools.size());
        assertEquals(new BlockPos(-8, 64, 0), pools.get(0).anchor);
        assertEquals(new BlockPos(8, 64, 0), pools.get(1).anchor);
        assertThrows(UnsupportedOperationException.class, () -> pools.clear());
    }

    private static SurfaceTerrainChecksTest.Fixture viable() {
        SurfaceTerrainChecksTest.Fixture terrain = new SurfaceTerrainChecksTest.Fixture();
        terrain.pool(8, 64, 0, 5, 2);
        terrain.tree(20, 0);
        return terrain;
    }

    private static TempleCandidateChecks.Result check(SurfaceTerrainChecksTest.Fixture terrain, BlockPos spawn,
                                                       int chests, int iron, int diamonds) {
        return TempleCandidateChecks.check(terrain, BlockPos.ORIGIN, spawn, chests, iron, diamonds);
    }
}
