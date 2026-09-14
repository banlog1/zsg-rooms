package zsgrooms.modid.filter;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.gen.ChunkRandom;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.BastionRemnantFeature;
import net.minecraft.world.gen.feature.BastionRemnantFeatureConfig;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.NetherFortressFeature;
import net.minecraft.world.gen.feature.StructureFeature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FilterCandidateSearchTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.getGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void sameSearchSeedProducesSamePreliminaryCandidateForEveryType() {
        for (FilterCandidateSearch.Type type : FilterCandidateSearch.Type.values()) {
            FilterCandidateSearch.Result first = search(type, 123);
            FilterCandidateSearch.Result second = search(type, 123);
            assertEquals(FilterCandidateSearch.Status.PRELIMINARY, first.status);
            assertEquals(first.status, second.status);
            assertEquals(first.attempts, second.attempts);
            FilterCandidateSearch.Candidate a = first.candidate().get();
            FilterCandidateSearch.Candidate b = second.candidate().get();
            assertEquals(a.seedForValidation(), b.seedForValidation());
            assertEquals(a.main, b.main);
            assertEquals(a.bastion, b.bastion);
            assertEquals(a.fortress, b.fortress);
            assertEquals(a.lakeAttempts, b.lakeAttempts);
            assertTrue(FilterCandidateSearch.within(a.bastion, 96));
            assertTrue(FilterCandidateSearch.within(a.fortress, 256));
            long rejected = 0;
            for (FilterCandidateSearch.Rejection reason : FilterCandidateSearch.Rejection.values()) rejected += first.rejected(reason);
            assertEquals(first.attempts - 1, rejected);
            if (type != FilterCandidateSearch.Type.SHIPWRECK) assertFalse(a.lakeAttempts.isEmpty());
            if (type == FilterCandidateSearch.Type.VILLAGE) {
                assertTrue(TreeBiomeChecks.nearby(new net.minecraft.world.biome.source.VanillaLayeredBiomeSource(a.seedForValidation(), false, false),
                        a.seedForValidation(), new net.minecraft.util.math.BlockPos(a.main.x << 4, 64, a.main.z << 4), true));
            }
        }
    }

    @Test
    void canProduceCandidatesWithoutTheOldNearbyRuinedPortal() {
        boolean found = false;
        for (int i = 1; i <= 10 && !found; i++) {
            FilterCandidateSearch.Candidate candidate = search(FilterCandidateSearch.Type.VILLAGE, i).candidate().get();
            ChunkPos portal = FilterCandidateSearch.placement(candidate.seedForValidation(), StructureFeature.RUINED_PORTAL,
                    0, 0, new ChunkRandom());
            found = Math.abs(portal.x - candidate.main.x) * 16 > 48 || Math.abs(portal.z - candidate.main.z) * 16 > 48;
        }
        assertTrue(found, "Candidate search still appears tied to the old portal requirement");
    }

    @Test
    void netherChoiceMatchesVanillaProtectedStartRollIncludingRngAdvancement() throws Exception {
        Method bastion = startMethod(BastionRemnantFeature.class, BastionRemnantFeatureConfig.class);
        Method fortress = startMethod(NetherFortressFeature.class, DefaultFeatureConfig.class);
        for (long seed = 1; seed < 100; seed++) {
            for (boolean isBastion : new boolean[]{false, true}) {
                StructureFeature<?> feature = isBastion ? StructureFeature.BASTION_REMNANT : StructureFeature.FORTRESS;
                ChunkRandom ours = new ChunkRandom();
                ChunkRandom vanilla = new ChunkRandom();
                ChunkPos pos = FilterCandidateSearch.placement(seed, feature, -1, 0, ours);
                assertEquals(pos, FilterCandidateSearch.placement(seed, feature, -1, 0, vanilla));
                boolean actual = (ours.nextInt(5) >= 2) == isBastion;
                boolean expected = (boolean) (isBastion ? bastion : fortress).invoke(feature,
                        null, null, seed, vanilla, pos.x, pos.z, null, pos, null);
                assertEquals(expected, actual);
                assertEquals(vanilla.nextLong(), ours.nextLong());
            }
        }
    }

    @Test
    void shipwreckLayoutFilterMatchesVanillaTemplateAndRotationTables() throws Exception {
        java.lang.reflect.Field field = net.minecraft.structure.ShipwreckGenerator.class.getDeclaredField("REGULAR_TEMPLATES");
        field.setAccessible(true);
        net.minecraft.util.Identifier[] templates = (net.minecraft.util.Identifier[]) field.get(null);
        java.util.Set<String> allowed = new java.util.HashSet<String>(java.util.Arrays.asList(
                "minecraft:shipwreck/with_mast", "minecraft:shipwreck/rightsideup_full",
                "minecraft:shipwreck/with_mast_degraded", "minecraft:shipwreck/rightsideup_full_degraded"));
        for (long seed = 0; seed < 1000; seed++) {
            ChunkPos pos = new ChunkPos((int) (seed % 15), (int) (seed % 13));
            ChunkRandom vanilla = new ChunkRandom();
            vanilla.setCarverSeed(seed, pos.x, pos.z);
            net.minecraft.util.BlockRotation rotation = net.minecraft.util.BlockRotation.values()[vanilla.nextInt(4)];
            String template = templates[vanilla.nextInt(templates.length)].toString();
            ChunkRandom filtered = new ChunkRandom();
            assertEquals(rotation == net.minecraft.util.BlockRotation.COUNTERCLOCKWISE_90 && allowed.contains(template),
                    FilterCandidateSearch.shipwreckLayout(seed, pos, filtered));
            assertEquals(vanilla.nextLong(), filtered.nextLong());
        }
    }

    @Test
    void negativeRegionsAndPerAxisDistanceUseVanillaConventions() {
        ChunkPos pos = FilterCandidateSearch.placement(1, StructureFeature.VILLAGE, -1, -1, new ChunkRandom());
        assertTrue(pos.x < 0 && pos.z < 0);
        assertTrue(FilterCandidateSearch.within(new ChunkPos(-6, 6), 96));
        assertFalse(FilterCandidateSearch.within(new ChunkPos(-7, 6), 96));
    }

    @Test
    void cancellationAndAttemptLimitNeverReturnAStaleCandidate() {
        FilterCandidateSearch.Result cancelled = FilterCandidateSearch.search(FilterCandidateSearch.Type.TEMPLE,
                false, 1, 100, 10000, () -> true);
        assertEquals(FilterCandidateSearch.Status.CANCELLED, cancelled.status);
        assertEquals(0, cancelled.attempts);
        assertFalse(cancelled.candidate().isPresent());
        FilterCandidateSearch.Result limited = FilterCandidateSearch.search(FilterCandidateSearch.Type.TEMPLE,
                true, 1, 1, 10000, () -> false);
        assertEquals(FilterCandidateSearch.Status.LIMIT_REACHED, limited.status);
        assertEquals(1, limited.attempts);
        assertFalse(limited.candidate().isPresent());
    }

    @Test
    void cancellationCanStopASearchAlreadyInProgress() {
        AtomicInteger checks = new AtomicInteger();
        FilterCandidateSearch.Result result = FilterCandidateSearch.search(FilterCandidateSearch.Type.TEMPLE,
                true, 1, 100000, 10000, () -> checks.incrementAndGet() > 3);
        assertEquals(FilterCandidateSearch.Status.CANCELLED, result.status);
        assertFalse(result.candidate().isPresent());
    }

    @Test
    void invalidLimitsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> FilterCandidateSearch.search(
                FilterCandidateSearch.Type.TEMPLE, false, 1, 0, 1000, () -> false));
        assertThrows(IllegalArgumentException.class, () -> FilterCandidateSearch.search(
                FilterCandidateSearch.Type.TEMPLE, false, 1, 10, 300001, () -> false));
    }

    @Test
    void overpoweredNetherLimitsRemainTighter() {
        int bastions = 0;
        int fortresses = 0;
        ChunkRandom random = new ChunkRandom();
        for (long seed = 0; seed < 10000; seed++) {
            ChunkPos bastion = FilterCandidateSearch.findNetherPlacement(seed, 32, true, random);
            ChunkPos fortress = FilterCandidateSearch.findNetherPlacement(seed, 112, false, random);
            if (bastion != null) { assertTrue(FilterCandidateSearch.within(bastion, 32)); bastions++; }
            if (fortress != null) { assertTrue(FilterCandidateSearch.within(fortress, 112)); fortresses++; }
        }
        assertTrue(bastions > 0 && fortresses > 0);
    }

    @Test
    void threadInterruptionIsPreservedAndStopsBeforeSampling() {
        Thread.currentThread().interrupt();
        try {
            FilterCandidateSearch.Result result = search(FilterCandidateSearch.Type.TEMPLE, 1);
            assertEquals(FilterCandidateSearch.Status.CANCELLED, result.status);
            assertEquals(0, result.attempts);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private static FilterCandidateSearch.Result search(FilterCandidateSearch.Type type, long seed) {
        return FilterCandidateSearch.search(type, false, seed, 100000, 60000, () -> false);
    }

    private static Method startMethod(Class<?> type, Class<?> config) throws Exception {
        Method method = type.getDeclaredMethod("shouldStartAt", ChunkGenerator.class, BiomeSource.class, long.class,
                ChunkRandom.class, int.class, int.class, Biome.class, ChunkPos.class, config);
        method.setAccessible(true);
        return method;
    }
}
