package zsgrooms.modid;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BlazeSpawnerStandardizationTest {
    private static final long WORLD_SEED = 246813579L;
    private static final String NETHER_KEY =
            BlazeSpawnerStandardization.spawnerKey("minecraft:the_nether", 123456L, "minecraft:blaze");

    @AfterEach
    public void resetStandardization() {
        RngStandardization.configure(false, false);
    }

    @Test
    public void delayIsDeterministicAndInsideVanillaBounds() {
        int first = BlazeSpawnerStandardization.standardizedSpawnerDelay(
                WORLD_SEED, NETHER_KEY, 4L, 200, 800);
        int repeated = BlazeSpawnerStandardization.standardizedSpawnerDelay(
                WORLD_SEED, NETHER_KEY, 4L, 200, 800);

        assertEquals(first, repeated);
        assertTrue(first >= 200);
        assertTrue(first < 800);
    }

    @Test
    public void delayUsesIndependentCyclesAndSpawnerKeys() {
        String otherPosition =
                BlazeSpawnerStandardization.spawnerKey("minecraft:the_nether", 654321L, "minecraft:blaze");
        String otherDimension =
                BlazeSpawnerStandardization.spawnerKey("minecraft:overworld", 123456L, "minecraft:blaze");

        assertNotEquals(delaySequence(NETHER_KEY), delaySequence(otherPosition));
        assertNotEquals(delaySequence(NETHER_KEY), delaySequence(otherDimension));
        assertNotEquals(
                RngStandardization.eventSeed(WORLD_SEED, "blaze_spawner_delay", NETHER_KEY, 0L),
                RngStandardization.eventSeed(WORLD_SEED, "blaze_spawner_delay", NETHER_KEY, 1L));
    }

    @Test
    public void invalidDelayRangeReturnsMinimumExactly() {
        assertEquals(400, BlazeSpawnerStandardization.standardizedSpawnerDelay(
                WORLD_SEED, NETHER_KEY, 0L, 400, 400));
        assertEquals(500, BlazeSpawnerStandardization.standardizedSpawnerDelay(
                WORLD_SEED, NETHER_KEY, 0L, 500, 400));
    }

    @Test
    public void candidateCoordinatesAreDeterministicAndMatchVanillaFormula() {
        long attemptSeed = BlazeSpawnerStandardization.standardizedSpawnerAttemptSeed(
                WORLD_SEED, NETHER_KEY, 2L, 3L, 1);
        Random vanillaFormula = new Random(attemptSeed);
        double expectedX = 120 + (vanillaFormula.nextDouble() - vanillaFormula.nextDouble()) * 4 + 0.5D;
        double expectedY = 64 + vanillaFormula.nextInt(3) - 1;
        double expectedZ = -350 + (vanillaFormula.nextDouble() - vanillaFormula.nextDouble()) * 4 + 0.5D;

        BlazeSpawnerStandardization.CandidatePosition candidate =
                BlazeSpawnerStandardization.standardizedSpawnerPosition(
                        WORLD_SEED, NETHER_KEY, 2L, 3L, 1, 120, 64, -350, 4);
        BlazeSpawnerStandardization.CandidatePosition repeated =
                BlazeSpawnerStandardization.standardizedSpawnerPosition(
                        WORLD_SEED, NETHER_KEY, 2L, 3L, 1, 120, 64, -350, 4);

        assertEquals(expectedX, candidate.getX(), 0.0D);
        assertEquals(expectedY, candidate.getY(), 0.0D);
        assertEquals(expectedZ, candidate.getZ(), 0.0D);
        assertEquals(candidate.getX(), repeated.getX(), 0.0D);
        assertEquals(candidate.getY(), repeated.getY(), 0.0D);
        assertEquals(candidate.getZ(), repeated.getZ(), 0.0D);
        assertTrue(candidate.getX() > 116.5D && candidate.getX() < 124.5D);
        assertTrue(candidate.getY() >= 63.0D && candidate.getY() <= 65.0D);
        assertTrue(candidate.getZ() > -353.5D && candidate.getZ() < -345.5D);
    }

    @Test
    public void attemptsBatchesAndCyclesHaveIndependentSeeds() {
        long base = BlazeSpawnerStandardization.standardizedSpawnerAttemptSeed(
                WORLD_SEED, NETHER_KEY, 0L, 0L, 0);
        long nextAttempt = BlazeSpawnerStandardization.standardizedSpawnerAttemptSeed(
                WORLD_SEED, NETHER_KEY, 0L, 0L, 1);
        long nextBatch = BlazeSpawnerStandardization.standardizedSpawnerAttemptSeed(
                WORLD_SEED, NETHER_KEY, 0L, 1L, 0);
        long nextCycle = BlazeSpawnerStandardization.standardizedSpawnerAttemptSeed(
                WORLD_SEED, NETHER_KEY, 1L, 0L, 0);

        assertNotEquals(base, nextAttempt);
        assertNotEquals(base, nextBatch);
        assertNotEquals(base, nextCycle);
    }

    @Test
    public void unrelatedRngChannelsDoNotAffectSpawnerResults() {
        int expectedDelay = BlazeSpawnerStandardization.standardizedSpawnerDelay(
                WORLD_SEED, NETHER_KEY, 7L, 200, 800);
        long expectedAttempt = BlazeSpawnerStandardization.standardizedSpawnerAttemptSeed(
                WORLD_SEED, NETHER_KEY, 7L, 2L, 3);

        RngStandardization.configure(true, false);
        RngStandardization.nextMobDropSeed(WORLD_SEED, "minecraft:the_nether|minecraft:blaze");
        RngStandardization.nextPiglinBarterSeed(WORLD_SEED);
        RngStandardization.nextEyeBreakSeed(WORLD_SEED);

        assertEquals(expectedDelay, BlazeSpawnerStandardization.standardizedSpawnerDelay(
                WORLD_SEED, NETHER_KEY, 7L, 200, 800));
        assertEquals(expectedAttempt, BlazeSpawnerStandardization.standardizedSpawnerAttemptSeed(
                WORLD_SEED, NETHER_KEY, 7L, 2L, 3));
    }

    @Test
    public void activityAtOneSpawnerDoesNotAdvanceAnother() {
        String other =
                BlazeSpawnerStandardization.spawnerKey("minecraft:the_nether", 999999L, "minecraft:blaze");
        RngStandardization.configure(true, false);

        BlazeSpawnerStandardization.advanceCycle(NETHER_KEY);
        BlazeSpawnerStandardization.advanceRetryBatch(NETHER_KEY);

        BlazeSpawnerStandardization.SpawnerEvent untouched =
                BlazeSpawnerStandardization.currentEvent(other);
        assertEquals(0L, untouched.getCycleIndex());
        assertEquals(0L, untouched.getRetryBatchIndex());
    }

    @Test
    public void configureResetsSpawnerStateAndReproducesSequence() {
        RngStandardization.configure(true, false);
        BlazeSpawnerStandardization.advanceCycle(NETHER_KEY);
        BlazeSpawnerStandardization.advanceRetryBatch(NETHER_KEY);

        RngStandardization.configure(true, false);

        BlazeSpawnerStandardization.SpawnerEvent reset =
                BlazeSpawnerStandardization.currentEvent(NETHER_KEY);
        assertEquals(0L, reset.getCycleIndex());
        assertEquals(0L, reset.getRetryBatchIndex());
        assertEquals(
                BlazeSpawnerStandardization.standardizedSpawnerDelay(
                        WORLD_SEED, NETHER_KEY, 0L, 200, 800),
                BlazeSpawnerStandardization.standardizedSpawnerDelay(
                        WORLD_SEED, NETHER_KEY, reset.getCycleIndex(), 200, 800));
    }

    @Test
    public void cycleCompletionAndNearbyCapAdvanceAndResetBatch() {
        RngStandardization.configure(true, false);
        BlazeSpawnerStandardization.advanceRetryBatch(NETHER_KEY);
        BlazeSpawnerStandardization.advanceRetryBatch(NETHER_KEY);

        BlazeSpawnerStandardization.SpawnerEvent afterSuccessfulCycle =
                BlazeSpawnerStandardization.advanceCycle(NETHER_KEY);
        assertEquals(1L, afterSuccessfulCycle.getCycleIndex());
        assertEquals(0L, afterSuccessfulCycle.getRetryBatchIndex());

        BlazeSpawnerStandardization.advanceRetryBatch(NETHER_KEY);
        BlazeSpawnerStandardization.SpawnerEvent afterNearbyCap =
                BlazeSpawnerStandardization.advanceCycle(NETHER_KEY);
        assertEquals(2L, afterNearbyCap.getCycleIndex());
        assertEquals(0L, afterNearbyCap.getRetryBatchIndex());
    }

    @Test
    public void fullyFailedBatchAdvancesOnlyRetryIndex() {
        RngStandardization.configure(true, false);

        BlazeSpawnerStandardization.SpawnerEvent retry =
                BlazeSpawnerStandardization.advanceRetryBatch(NETHER_KEY);

        assertEquals(0L, retry.getCycleIndex());
        assertEquals(1L, retry.getRetryBatchIndex());
    }

    @Test
    public void readingSpawnerStateDoesNotAdvanceIt() {
        RngStandardization.configure(true, false);
        BlazeSpawnerStandardization.SpawnerEvent before =
                BlazeSpawnerStandardization.currentEvent(NETHER_KEY);

        BlazeSpawnerStandardization.SpawnerEvent after =
                BlazeSpawnerStandardization.currentEvent(NETHER_KEY);

        assertEquals(before.getCycleIndex(), after.getCycleIndex());
        assertEquals(before.getRetryBatchIndex(), after.getRetryBatchIndex());
    }

    @Test
    public void validFortressBlazeBlockSpawnerIsQualified() {
        assertEquals(
                BlazeSpawnerStandardization.QualificationResult.QUALIFIED,
                qualification(true, true, true, true, true, false, true, true,
                        200, 800, 4, 6, 16, 4));
    }

    @Test
    public void spawnerTypeRejectionsHaveDistinctReasons() {
        assertEquals(
                BlazeSpawnerStandardization.QualificationResult.NOT_BLAZE,
                qualification(true, true, true, false, true, false, true, true,
                        200, 800, 4, 6, 16, 4));
        assertEquals(
                BlazeSpawnerStandardization.QualificationResult.NOT_OWNED_BLOCK_SPAWNER,
                qualification(true, true, false, true, true, false, true, true,
                        200, 800, 4, 6, 16, 4));
        assertEquals(
                BlazeSpawnerStandardization.QualificationResult.NOT_INSIDE_FORTRESS,
                qualification(true, true, true, true, false, false, true, true,
                        200, 800, 4, 6, 16, 4));
    }

    @Test
    public void spawnDataRejectionsHaveDistinctReasons() {
        assertEquals(
                BlazeSpawnerStandardization.QualificationResult.EXPLICIT_SPAWN_POSITION,
                qualification(true, true, true, true, true, true, true, true,
                        200, 800, 4, 6, 16, 4));
        assertEquals(
                BlazeSpawnerStandardization.QualificationResult.UNSUPPORTED_SPAWN_DATA,
                qualification(true, true, true, true, true, false, false, true,
                        200, 800, 4, 6, 16, 4));
        assertEquals(
                BlazeSpawnerStandardization.QualificationResult.UNSUPPORTED_SPAWN_POTENTIALS,
                qualification(true, true, true, true, true, false, true, false,
                        200, 800, 4, 6, 16, 4));
    }

    @Test
    public void delayConfigurationRejectionsHaveDistinctReasons() {
        assertEquals(
                BlazeSpawnerStandardization.QualificationResult.NON_VANILLA_MIN_DELAY,
                qualification(true, true, true, true, true, false, true, true,
                        100, 800, 4, 6, 16, 4));
        assertEquals(
                BlazeSpawnerStandardization.QualificationResult.NON_VANILLA_MAX_DELAY,
                qualification(true, true, true, true, true, false, true, true,
                        200, 900, 4, 6, 16, 4));
    }

    @Test
    public void numericConfigurationRejectionsHaveDistinctReasons() {
        assertEquals(
                BlazeSpawnerStandardization.QualificationResult.NON_VANILLA_SPAWN_COUNT,
                qualification(true, true, true, true, true, false, true, true,
                        200, 800, 5, 6, 16, 4));
        assertEquals(
                BlazeSpawnerStandardization.QualificationResult.NON_VANILLA_NEARBY_LIMIT,
                qualification(true, true, true, true, true, false, true, true,
                        200, 800, 4, 7, 16, 4));
        assertEquals(
                BlazeSpawnerStandardization.QualificationResult.NON_VANILLA_PLAYER_RANGE,
                qualification(true, true, true, true, true, false, true, true,
                        200, 800, 4, 6, 20, 4));
        assertEquals(
                BlazeSpawnerStandardization.QualificationResult.NON_VANILLA_SPAWN_RANGE,
                qualification(true, true, true, true, true, false, true, true,
                        200, 800, 4, 6, 16, 5));
    }

    @Test
    public void environmentRejectionsHaveDistinctReasons() {
        assertEquals(
                BlazeSpawnerStandardization.QualificationResult.STANDARDISATION_DISABLED,
                qualification(false, true, true, true, true, false, true, true,
                        200, 800, 4, 6, 16, 4));
        assertEquals(
                BlazeSpawnerStandardization.QualificationResult.NOT_SERVER_WORLD,
                qualification(true, false, true, true, true, false, true, true,
                        200, 800, 4, 6, 16, 4));
    }

    @Test
    public void emptyAndSingleDefaultBlazePotentialAreSupported() {
        assertTrue(BlazeSpawnerStandardization.hasSupportedSpawnPotentials(0, false));
        assertTrue(BlazeSpawnerStandardization.hasSupportedSpawnPotentials(1, true));
        assertFalse(BlazeSpawnerStandardization.hasSupportedSpawnPotentials(1, false));
        assertFalse(BlazeSpawnerStandardization.hasSupportedSpawnPotentials(2, true));
        assertTrue(BlazeSpawnerStandardization.isDefaultBlazeSpawnData(
                "minecraft:blaze", 1));
        assertFalse(BlazeSpawnerStandardization.isDefaultBlazeSpawnData(
                "minecraft:blaze", 2));
        assertFalse(BlazeSpawnerStandardization.isDefaultBlazeSpawnData(
                "minecraft:skeleton", 1));

        assertEquals(
                BlazeSpawnerStandardization.QualificationResult.QUALIFIED,
                qualification(true, true, true, true, true, false, true,
                        BlazeSpawnerStandardization.hasSupportedSpawnPotentials(1, true),
                        200, 800, 4, 6, 16, 4));
    }

    @Test
    public void legacyBooleanQualificationMatchesDetailedResult() {
        assertTrue(qualifies(true, true, true, true, true, false, true, true,
                200, 800, 4, 6, 16, 4));
        assertFalse(qualifies(true, true, true, false, true, false, true, true,
                200, 800, 4, 6, 16, 4));
    }

    private List<Integer> delaySequence(String key) {
        List<Integer> delays = new ArrayList<Integer>();
        for (long cycle = 0L; cycle < 8L; cycle++) {
            delays.add(BlazeSpawnerStandardization.standardizedSpawnerDelay(
                    WORLD_SEED, key, cycle, 200, 800));
        }
        return delays;
    }

    private boolean qualifies(
            boolean enabled,
            boolean serverWorld,
            boolean ownedBlockSpawner,
            boolean blaze,
            boolean fortress,
            boolean explicitPosition,
            boolean supportedSpawnData,
            boolean supportedPotentials,
            int minDelay,
            int maxDelay,
            int spawnCount,
            int maxNearby,
            int playerRange,
            int spawnRange
    ) {
        return BlazeSpawnerStandardization.qualifies(
                enabled,
                serverWorld,
                ownedBlockSpawner,
                blaze,
                fortress,
                explicitPosition,
                supportedSpawnData,
                supportedPotentials,
                minDelay,
                maxDelay,
                spawnCount,
                maxNearby,
                playerRange,
                spawnRange);
    }

    private BlazeSpawnerStandardization.QualificationResult qualification(
            boolean enabled,
            boolean serverWorld,
            boolean ownedBlockSpawner,
            boolean blaze,
            boolean fortress,
            boolean explicitPosition,
            boolean supportedSpawnData,
            boolean supportedPotentials,
            int minDelay,
            int maxDelay,
            int spawnCount,
            int maxNearby,
            int playerRange,
            int spawnRange
    ) {
        return BlazeSpawnerStandardization.qualificationResult(
                enabled,
                serverWorld,
                ownedBlockSpawner,
                blaze,
                fortress,
                explicitPosition,
                supportedSpawnData,
                supportedPotentials,
                minDelay,
                maxDelay,
                spawnCount,
                maxNearby,
                playerRange,
                spawnRange);
    }
}
