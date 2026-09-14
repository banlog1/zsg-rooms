package zsgrooms.modid;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import zsgrooms.modid.filter.SurfaceTerrainChecks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static zsgrooms.modid.SurfacePoolRepair.Kind.*;

class SurfacePoolRepairTest {
    private static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
    private static final String PROFILE = "rooms-temple-v5";

    @Test
    void terrainWithNaturalWaterGetsTwoSeparatedPoolsWithoutChangingWater() {
        Fixture terrain = new Fixture();
        terrain.naturalWater();
        Set<BlockPos> originalWater = positionsOf(terrain, WATER);
        SurfacePoolRepair.Result result = SurfacePoolRepair.repair(terrain, ORIGIN, 9123L, PROFILE);
        assertEquals(2, result.pools.size());
        assertEquals(2, result.lavaAdded);
        assertEquals(originalWater, positionsOf(terrain, WATER));
        assertFalse(terrain.waterWritten);
        assertQualified(terrain, result);
        assertTrue(SurfacePoolRepair.separated(result.pools.get(1), result.pools.subList(0, 1), ORIGIN, true));
        assertTrue(terrain.writes > 0);
    }

    @Test
    void elevatedLevelGroundIsUsableButBuildLimitAndFrozenWaterStillApply() {
        Fixture terrain = new Fixture();
        terrain.groundY = 92;
        terrain.naturalWater();
        SurfacePoolRepair.Result result = SurfacePoolRepair.repair(terrain, ORIGIN.up(28), 91, PROFILE);
        assertEquals(2, result.pools.size());
        assertQualified(terrain, result);
        assertTrue(result.pools.stream().allMatch(pos -> pos.getY() == 91));
        terrain.groundY = 250;
        assertNull(VanillaLavaLake.plan(terrain, new BlockPos(150, 250, 0), 0));
    }

    @Test
    void sameSeedAndTerrainProduceIdenticalEditsRegardlessOfUnrelatedRng() {
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        first.naturalWater();
        second.naturalWater();
        SurfacePoolRepair.Result a = SurfacePoolRepair.repair(first, ORIGIN, -728319L, PROFILE);
        java.util.Random unrelated = new java.util.Random();
        for (int i = 0; i < 1000; i++) unrelated.nextLong();
        SurfacePoolRepair.Result b = SurfacePoolRepair.repair(second, ORIGIN, -728319L, PROFILE);
        assertEquals(a.pools, b.pools);
        assertEquals(first.blocks, second.blocks);
        assertNotEquals(SurfacePoolRepair.candidates(ORIGIN, 1L, PROFILE), SurfacePoolRepair.candidates(ORIGIN, 2L, PROFILE));
        assertEquals(SurfacePoolRepair.candidates(ORIGIN, 1L, PROFILE), SurfacePoolRepair.candidates(ORIGIN.up(9), 1L, PROFILE)
                .stream().map(pos -> pos.down(9)).collect(java.util.stream.Collectors.toList()));
    }

    @Test
    void existingQualifiedPoolsAndWaterAreNotReplaced() {
        Fixture terrain = new Fixture();
        terrain.basin(new BlockPos(-60, 64, 0), false);
        terrain.basin(new BlockPos(60, 64, 0), false);
        terrain.basin(new BlockPos(-60, 64, 20), true);
        terrain.basin(new BlockPos(60, 64, 20), true);
        Map<BlockPos, SurfacePoolRepair.Kind> original = new HashMap<>(terrain.blocks);
        terrain.writes = 0;
        SurfacePoolRepair.Result result = SurfacePoolRepair.repair(terrain, ORIGIN, 82, PROFILE);
        assertEquals(2, result.pools.size());
        assertEquals(0, result.lavaAdded);
        assertEquals(0, terrain.writes);
        assertEquals(original, terrain.blocks);
    }

    @Test
    void keepsOneNaturalPoolAndRepairsOnlyTheMissingOne() {
        Fixture terrain = new Fixture();
        BlockPos natural = new BlockPos(-60, 64, 0);
        terrain.basin(natural, false);
        terrain.basin(natural.add(0, 0, 20), true);
        terrain.basin(new BlockPos(60, 64, 20), true);
        SurfacePoolRepair.Result result = SurfacePoolRepair.repair(terrain, ORIGIN, 91, PROFILE);
        assertEquals(2, result.pools.size());
        assertEquals(1, result.lavaAdded);
        assertEquals(LAVA, terrain.kind(natural));
        assertQualified(terrain, result);
    }

    @Test
    void reconsidersFirstChoiceWhenAnotherPairProvidesSeparatedRoutes() {
        Fixture terrain = new Fixture();
        terrain.basin(ORIGIN, false);
        terrain.basin(ORIGIN.add(0, 0, 20), true);
        // Neither outer site is 48 blocks from the initial pool, but they are 80 apart.
        terrain.protection = pos -> !(Math.abs(pos.getZ()) <= 12
                && (Math.abs(pos.getX() - 40) <= 12 || Math.abs(pos.getX() + 40) <= 12));
        terrain.applyCalls = 0;
        SurfacePoolRepair.Result result = SurfacePoolRepair.repair(terrain, ORIGIN, 5, PROFILE);
        assertEquals(2, result.pools.size());
        assertTrue(result.pools.stream().anyMatch(pos -> pos.getX() < -20));
        assertTrue(result.pools.stream().anyMatch(pos -> pos.getX() > 20));
        assertEquals(1, terrain.applyCalls, "Only the final chosen pair is committed");
        assertEquals(LAVA, terrain.kind(ORIGIN), "Existing lava is not removed to create a pair");
    }

    @Test
    void missingWaterLeavesExistingLavaAndTerrainUntouched() {
        Fixture terrain = new Fixture();
        terrain.basin(new BlockPos(-60, 64, 0), false);
        terrain.basin(new BlockPos(60, 64, 0), false);
        Set<BlockPos> originalLava = positionsOf(terrain, LAVA);
        Map<BlockPos, SurfacePoolRepair.Kind> original = new HashMap<>(terrain.blocks);
        terrain.writes = 0;
        SurfacePoolRepair.Result result = SurfacePoolRepair.repair(terrain, ORIGIN, 55, PROFILE);
        assertEquals(0, result.pools.size());
        assertEquals(0, result.lavaAdded);
        assertEquals(originalLava, positionsOf(terrain, LAVA));
        assertEquals(original, terrain.blocks);
        assertEquals(0, terrain.writes);
        assertFalse(terrain.waterWritten);
    }

    @Test
    void isolatedWaterSourcesAndDistantPondsDoNotSatisfyWaterRequirement() {
        Fixture terrain = new Fixture();
        terrain.basin(new BlockPos(-60, 64, 0), false);
        terrain.basin(new BlockPos(60, 64, 0), false);
        terrain.blocks.put(new BlockPos(-60, 64, 20), WATER);
        terrain.blocks.put(new BlockPos(60, 64, 20), WATER);
        terrain.basin(new BlockPos(0, 64, 100), true);
        terrain.protectedArea = true;
        terrain.writes = 0;
        Map<BlockPos, SurfacePoolRepair.Kind> original = new HashMap<>(terrain.blocks);
        SurfacePoolRepair.Result result = SurfacePoolRepair.repair(terrain, ORIGIN, 923, PROFILE);
        assertEquals(0, result.pools.size());
        assertEquals(0, result.lavaAdded);
        assertEquals(original, terrain.blocks);
        assertEquals(0, terrain.writes);
    }

    @Test
    void basinHasContainedSourcesAndClearHeadroom() {
        Fixture terrain = new Fixture();
        Map<BlockPos, SurfacePoolRepair.Kind> lava = VanillaLavaLake.plan(terrain, ORIGIN, 0);
        assertNotNull(lava);
        assertTrue(lava.values().stream().filter(kind -> kind == LAVA).count() > 21);
        assertTrue(lava.entrySet().stream().filter(entry -> entry.getValue() == LAVA)
                .map(entry -> entry.getKey().getY()).distinct().count() > 1);
        lava.forEach((pos, kind) -> {
            if (kind != LAVA) return;
            assertTrue(lava.get(pos.down()) == STONE || lava.get(pos.down()) == LAVA);
            for (BlockPos neighbor : new BlockPos[]{pos.north(), pos.south(), pos.east(), pos.west()}) {
                SurfacePoolRepair.Kind neighborKind = lava.getOrDefault(neighbor, terrain.kind(neighbor));
                assertTrue(neighborKind == LAVA || neighborKind == STONE || neighborKind == GROUND);
            }
        });
    }

    @Test
    void refusesProtectedStructuresBlockEntitiesTreesFluidsAndCaves() {
        Fixture protectedArea = new Fixture();
        protectedArea.protectedArea = true;
        assertNull(VanillaLavaLake.plan(protectedArea, ORIGIN, 0));
        for (SurfacePoolRepair.Kind obstacle : new SurfacePoolRepair.Kind[]{BLOCKED, WATER, LAVA}) {
            Fixture terrain = new Fixture();
            terrain.blocks.put(ORIGIN.add(4, 1, 0), obstacle);
            assertNull(VanillaLavaLake.plan(terrain, ORIGIN, 0));
            assertEquals(0, terrain.writes);
        }
        Fixture cave = new Fixture();
        for (int x = -8; x < 8; x++) for (int z = -8; z < 8; z++) cave.blocks.put(ORIGIN.add(x, -2, z), AIR);
        assertNull(VanillaLavaLake.plan(cave, ORIGIN, 0));
        Fixture frozen = new Fixture();
        frozen.naturalWater();
        frozen.frozen = true;
        assertEquals(0, SurfacePoolRepair.repair(frozen, ORIGIN, 2, PROFILE).pools.size());
        assertEquals(0, frozen.writes, "No lava without viable water");
    }

    @Test
    void unsafeAreaAndFailedWritesDoNotCountAsGuaranteedPools() {
        Fixture terrain = new Fixture();
        terrain.naturalWater();
        terrain.protectedArea = true;
        assertEquals(0, SurfacePoolRepair.repair(terrain, ORIGIN, 1, PROFILE).pools.size());
        assertEquals(0, terrain.writes);
        Fixture failure = new Fixture();
        failure.naturalWater();
        Map<BlockPos, SurfacePoolRepair.Kind> original = new HashMap<>(failure.blocks);
        failure.failWrites = true;
        assertEquals(0, SurfacePoolRepair.repair(failure, ORIGIN, 1, PROFILE).pools.size());
        assertEquals(original, failure.blocks);
    }

    @Test
    void dryTerrainDoesNotCreateLavaOrWater() {
        Fixture terrain = new Fixture();
        assertEquals(0, SurfacePoolRepair.repair(terrain, ORIGIN, 1, PROFILE).pools.size());
        assertTrue(terrain.blocks.isEmpty());
        assertEquals(0, terrain.applyCalls);
    }

    @Test
    void countsNaturalWaterBeyondTheOldScanWithoutWideningPoolDistance() {
        Fixture terrain = new Fixture();
        terrain.basin(new BlockPos(-130, 64, 0), true);
        terrain.basin(new BlockPos(130, 64, 0), true);
        SurfacePoolRepair.Result result = SurfacePoolRepair.repair(terrain, ORIGIN, 9123, PROFILE);
        assertEquals(2, result.pools.size());
        assertTrue(result.pools.stream().allMatch(pos -> SurfacePoolRepair.distanceSquared(pos, ORIGIN) <= 96 * 96));
        assertEquals(java.util.Arrays.asList(146), terrain.preparedRadii);
        assertQualified(terrain, result);
    }

    @Test
    void expandsTo128OnlyWhenNeededAndKeepsBothPoolsNearExistingWater() {
        Fixture terrain = new Fixture();
        terrain.basin(new BlockPos(-158, 64, 0), true);
        terrain.basin(new BlockPos(158, 64, 0), true);
        SurfacePoolRepair.Result result = SurfacePoolRepair.repair(terrain, ORIGIN, 82, PROFILE);
        assertEquals(2, result.pools.size());
        assertTrue(result.pools.stream().allMatch(pos -> SurfacePoolRepair.distanceSquared(pos, ORIGIN) > 96 * 96));
        assertEquals(java.util.Arrays.asList(146, 178), terrain.preparedRadii);
        assertQualified(terrain, result);
        assertFalse(terrain.waterWritten);
        assertEquals(1, terrain.applyCalls);
    }

    @Test
    void keepsOnePassingNaturalPoolWithoutCountingPoolsPast128() {
        Fixture terrain = new Fixture();
        terrain.basin(new BlockPos(-60, 64, 0), false);
        terrain.basin(new BlockPos(-60, 64, 20), true);
        terrain.basin(new BlockPos(140, 64, 0), false);
        terrain.basin(new BlockPos(140, 64, 20), true);
        terrain.protectedArea = true;
        Map<BlockPos, SurfacePoolRepair.Kind> original = new HashMap<>(terrain.blocks);
        SurfacePoolRepair.Result result = SurfacePoolRepair.repair(terrain, ORIGIN, 1, PROFILE);
        assertEquals(1, result.pools.size());
        assertEquals(0, result.lavaAdded);
        assertTrue(SurfacePoolRepair.distanceSquared(result.pools.get(0), ORIGIN) <= 128 * 128);
        assertEquals(original, terrain.blocks);
        assertEquals(0, terrain.applyCalls);
        assertEquals(java.util.Arrays.asList(146, 178), terrain.preparedRadii);
    }

    @Test
    void createsOneSafeFallbackOnlyAfterExhaustingThePairSearch() {
        Fixture first = singleSiteTerrain();
        Fixture second = singleSiteTerrain();
        Set<BlockPos> water = positionsOf(first, WATER);
        SurfacePoolRepair.Result a = SurfacePoolRepair.repair(first, ORIGIN, 5, PROFILE);
        SurfacePoolRepair.Result b = SurfacePoolRepair.repair(second, ORIGIN, 5, PROFILE);
        assertEquals(1, a.pools.size());
        assertEquals(1, a.lavaAdded);
        assertEquals(a.pools, b.pools);
        assertEquals(first.blocks, second.blocks);
        assertEquals(java.util.Arrays.asList(146, 178), first.preparedRadii);
        assertEquals(1, first.applyCalls);
        assertEquals(water, positionsOf(first, WATER));
        assertFalse(first.waterWritten);
        assertEquals(1, SurfaceTerrainChecks.findLavaPools(first, 0, 0, 128).size());
        assertTrue(water.stream().anyMatch(pos -> SurfacePoolRepair.distanceSquared(pos, a.pools.get(0)) <= 48 * 48));
    }

    @Test
    void failedSinglePoolWriteDoesNotReportAPreparedPool() {
        Fixture terrain = singleSiteTerrain();
        Map<BlockPos, SurfacePoolRepair.Kind> original = new HashMap<>(terrain.blocks);
        terrain.failWrites = true;
        SurfacePoolRepair.Result result = SurfacePoolRepair.repair(terrain, ORIGIN, 5, PROFILE);
        assertTrue(result.pools.isEmpty());
        assertEquals(0, result.lavaAdded);
        assertEquals(original, terrain.blocks);
        assertEquals(1, terrain.applyCalls);
    }

    @Test
    void onePassingInnerPoolDoesNotPreventFindingAnOuterPartner() {
        Fixture terrain = new Fixture();
        terrain.basin(new BlockPos(-60, 64, 0), false);
        terrain.basin(new BlockPos(-60, 64, 20), true);
        terrain.basin(new BlockPos(112, 64, 0), false);
        terrain.basin(new BlockPos(112, 64, 20), true);
        terrain.protectedArea = true;
        SurfacePoolRepair.Result result = SurfacePoolRepair.repair(terrain, ORIGIN, 1, PROFILE);
        assertEquals(2, result.pools.size());
        assertEquals(0, result.lavaAdded);
        assertEquals(0, terrain.applyCalls);
        assertEquals(java.util.Arrays.asList(146, 178), terrain.preparedRadii);
    }

    private static Fixture singleSiteTerrain() {
        Fixture terrain = new Fixture();
        terrain.basin(new BlockPos(40, 64, 20), true);
        terrain.protection = pos -> Math.abs(pos.getX() - 40) > 12 || Math.abs(pos.getZ()) > 12;
        return terrain;
    }

    @Test
    void separatedRoutesCannotPointInAlmostTheSameDirection() {
        List<BlockPos> first = java.util.Collections.singletonList(new BlockPos(40, 64, 0));
        assertFalse(SurfacePoolRepair.separated(new BlockPos(100, 64, 10), first, ORIGIN, false));
        assertTrue(SurfacePoolRepair.separated(new BlockPos(20, 64, 60), first, ORIGIN, false));
        assertFalse(SurfacePoolRepair.separated(new BlockPos(20, 64, 60), first, ORIGIN, true));
        assertTrue(SurfacePoolRepair.separated(new BlockPos(-60, 64, 0), first, ORIGIN, true));
    }

    @Test
    void finePassCoversEveryRemainingColumnWithoutRepeatingCoarseSites() {
        for (int radius : new int[]{96, 128}) {
            Set<BlockPos> seen = new HashSet<>();
            for (int step : new int[]{4, 1}) {
                for (BlockPos site : SurfacePoolRepair.candidates(ORIGIN, 41, PROFILE, radius, step)) {
                    assertTrue(seen.add(site), "Each site is evaluated once per search radius");
                }
            }
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                int distance = x * x + z * z;
                assertEquals(distance >= 28 * 28 && distance <= radius * radius, seen.contains(ORIGIN.add(x, 0, z)));
            }
        }
    }

    @Test
    void onlyNewTempleAndVillageProfilesQualifyAndAttemptMarkerRoundTrips() {
        assertTrue(SurfaceLavaPoolGuarantee.appliesTo("rooms-temple-v5"));
        assertTrue(SurfaceLavaPoolGuarantee.appliesTo("ZSG Rooms Village"));
        for (String filter : new String[]{"rooms-shipwreck-v5", "zsg", "zsgdeserttemple", "rpseedbank", "manual", "random"}) {
            assertFalse(SurfaceLavaPoolGuarantee.appliesTo(filter));
        }
        SurfaceLavaPoolGuarantee.State state = new SurfaceLavaPoolGuarantee.State();
        state.attempted = true;
        state.pools = 1;
        SurfaceLavaPoolGuarantee.State loaded = new SurfaceLavaPoolGuarantee.State();
        loaded.fromTag(state.toTag(new CompoundTag()));
        assertTrue(loaded.attempted);
        assertEquals(1, loaded.pools);
        assertFalse(new SurfaceLavaPoolGuarantee.State().attempted);
    }

    private static Set<BlockPos> positionsOf(Fixture terrain, SurfacePoolRepair.Kind kind) {
        Set<BlockPos> result = new HashSet<>();
        terrain.blocks.forEach((pos, value) -> { if (value == kind) result.add(pos); });
        return result;
    }

    private static void assertQualified(Fixture terrain, SurfacePoolRepair.Result result) {
        List<SurfaceTerrainChecks.LavaPool> actual = SurfaceTerrainChecks.findLavaPools(terrain, 0, 0, 128);
        assertTrue(actual.size() >= 2);
        for (BlockPos pool : result.pools) {
            assertTrue(SurfacePoolRepair.distanceSquared(pool, ORIGIN) <= 128 * 128);
            assertTrue(actual.stream().anyMatch(found -> SurfacePoolRepair.distanceSquared(found.anchor, pool) <= 9));
            assertTrue(positionsOf(terrain, WATER).stream().anyMatch(pos -> {
                if (SurfacePoolRepair.distanceSquared(pos.add(1, 0, 1), pool) > 48 * 48) return false;
                for (int x = 0; x < 4; x++) for (int z = 0; z < 4; z++) {
                    if (terrain.kind(pos.add(x, 0, z)) != WATER || terrain.kind(pos.add(x, 1, z)) != AIR) return false;
                }
                return true;
            }));
        }
    }

    static final class Fixture implements SurfacePoolRepair.Terrain {
        final Map<BlockPos, SurfacePoolRepair.Kind> blocks = new HashMap<>();
        boolean protectedArea;
        boolean frozen;
        boolean failWrites;
        int writes;
        int applyCalls;
        int groundY = 64;
        boolean waterWritten;
        final List<Integer> preparedRadii = new java.util.ArrayList<>();
        java.util.function.Predicate<BlockPos> protection = pos -> false;

        void naturalWater() {
            basin(new BlockPos(-60, groundY, 20), true);
            basin(new BlockPos(60, groundY, 20), true);
        }

        void basin(BlockPos pos, boolean water) {
            // Existing-pool fixtures deliberately do not depend on the new lake generator.
            Map<BlockPos, SurfacePoolRepair.Kind> plan = new HashMap<>();
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                if (water ? x >= -1 && z >= -1 : x * x + z * z <= 5) {
                    plan.put(pos.add(x, 0, z), water ? WATER : LAVA);
                    plan.put(pos.add(x, -1, z), STONE);
                }
            }
            blocks.putAll(plan);
        }

        @Override
        public void prepare(int radius) { preparedRadii.add(radius); }

        @Override
        public int surfaceY(int x, int z) {
            // Fixtures may be edited directly; model their heightmap without depending on map iteration order.
            for (int y = groundY + 4; y > groundY - 6; y--) {
                SurfacePoolRepair.Kind kind = kind(new BlockPos(x, y, z));
                if (kind != AIR && kind != PLANT) return y;
            }
            return groundY - 6;
        }

        @Override
        public SurfacePoolRepair.Kind kind(BlockPos pos) {
            SurfacePoolRepair.Kind kind = blocks.getOrDefault(pos, pos.getY() <= groundY ? GROUND : AIR);
            return kind == CAVE_AIR ? AIR : kind;
        }

        @Override
        public boolean protectedAt(BlockPos pos) { return protectedArea || protection.test(pos); }

        @Override
        public boolean unfrozen(BlockPos pos) { return !frozen; }

        @Override
        public boolean apply(Map<BlockPos, SurfacePoolRepair.Kind> patch) {
            applyCalls++;
            waterWritten |= patch.containsValue(WATER);
            if (failWrites) return false;
            blocks.putAll(patch);
            writes += patch.size();
            return true;
        }
    }
}
