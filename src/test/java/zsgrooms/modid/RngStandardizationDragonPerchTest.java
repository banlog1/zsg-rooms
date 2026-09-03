package zsgrooms.modid;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RngStandardizationDragonPerchTest {
    private static final long WORLD_SEED = 246813579L;
    private static final int VANILLA_BOUND_WITHOUT_CRYSTALS = 3;

    @AfterEach
    public void disableStandardization() {
        RngStandardization.configure(false, false);
    }

    @Test
    public void sameWorldSeedAndEventIndexProduceSamePerchResult() {
        long expectedSeed = RngStandardization.eventSeed(
                WORLD_SEED, "dragon_perch", "global", 0L);

        RngStandardization.configure(true, false);
        long firstSeed = RngStandardization.nextDragonPerchSeed(WORLD_SEED);
        int firstResult = new Random(firstSeed).nextInt(VANILLA_BOUND_WITHOUT_CRYSTALS);

        RngStandardization.configure(true, false);
        long repeatedSeed = RngStandardization.nextDragonPerchSeed(WORLD_SEED);
        int repeatedResult = new Random(repeatedSeed).nextInt(VANILLA_BOUND_WITHOUT_CRYSTALS);

        assertEquals(expectedSeed, firstSeed);
        assertEquals(firstSeed, repeatedSeed);
        assertEquals(firstResult, repeatedResult);
    }

    @Test
    public void configureResetsPerchSequence() {
        RngStandardization.configure(true, false);
        long first = RngStandardization.nextDragonPerchSeed(WORLD_SEED);
        RngStandardization.nextDragonPerchSeed(WORLD_SEED);

        RngStandardization.configure(true, false);

        assertEquals(first, RngStandardization.nextDragonPerchSeed(WORLD_SEED));
    }

    @Test
    public void otherStandardizedEventsDoNotAffectPerchSequence() {
        long expectedSecond = secondPerchSeedWithoutOtherEvents();

        RngStandardization.configure(true, false);
        RngStandardization.nextDragonPerchSeed(WORLD_SEED);
        RngStandardization.nextMobDropSeed(
                WORLD_SEED, "minecraft:the_end|minecraft:enderman");
        RngStandardization.nextPiglinBarterSeed(WORLD_SEED);
        RngStandardization.nextEyeBreakSeed(WORLD_SEED);

        assertEquals(expectedSecond, RngStandardization.nextDragonPerchSeed(WORLD_SEED));
    }

    @Test
    public void differentPerchIndexesHaveIndependentEventSeeds() {
        RngStandardization.configure(true, false);

        long first = RngStandardization.nextDragonPerchSeed(WORLD_SEED);
        long second = RngStandardization.nextDragonPerchSeed(WORLD_SEED);
        long third = RngStandardization.nextDragonPerchSeed(WORLD_SEED);

        assertEquals(RngStandardization.eventSeed(
                WORLD_SEED, "dragon_perch", "global", 0L), first);
        assertEquals(RngStandardization.eventSeed(
                WORLD_SEED, "dragon_perch", "global", 1L), second);
        assertEquals(RngStandardization.eventSeed(
                WORLD_SEED, "dragon_perch", "global", 2L), third);
        assertNotEquals(first, second);
        assertNotEquals(second, third);
    }

    @Test
    public void disabledStandardizationLeavesReplacementInactive() {
        RngStandardization.configure(false, false);

        assertFalse(RngStandardization.isEnabled());
        assertFalse(RngStandardization.isDragonPerchStandardizationActive(1300));
    }

    @Test
    public void perchStandardizationStartsAfterVanillaGracePeriod() {
        RngStandardization.configure(true, false);

        assertFalse(RngStandardization.isDragonPerchStandardizationActive(0));
        assertFalse(RngStandardization.isDragonPerchStandardizationActive(1299));
        assertTrue(RngStandardization.isDragonPerchStandardizationActive(1300));
    }

    @Test
    public void testingOverrideCanStandardizeFromDragonAgeZero() {
        Random vanillaRandom = new Random(86420L);
        int expected = new Random(RngStandardization.eventSeed(
                WORLD_SEED, "dragon_perch", "global", 0L))
                .nextInt(VANILLA_BOUND_WITHOUT_CRYSTALS);

        RngStandardization.configure(true, false);
        RngStandardization.setDragonPerchGraceTicksForTesting(0);

        assertTrue(RngStandardization.isDragonPerchStandardizationActive(0));
        assertEquals(
                expected,
                RngStandardization.nextDragonPerchRoll(
                        vanillaRandom,
                        VANILLA_BOUND_WITHOUT_CRYSTALS,
                        0,
                        WORLD_SEED));
    }

    @Test
    public void configureRestoresProductionGraceAfterTestingOverride() {
        RngStandardization.configure(true, false);
        RngStandardization.setDragonPerchGraceTicksForTesting(0);
        assertTrue(RngStandardization.isDragonPerchStandardizationActive(0));

        RngStandardization.configure(true, false);

        assertEquals(1300, RngStandardization.dragonPerchGraceTicks());
        assertFalse(RngStandardization.isDragonPerchStandardizationActive(1299));
        assertTrue(RngStandardization.isDragonPerchStandardizationActive(1300));
    }

    @Test
    public void disabledRollSelectionMatchesVanillaAtEveryAge() {
        Random expectedVanilla = new Random(987654321L);
        Random actualVanilla = new Random(987654321L);
        int[] ages = {0, 400, 1299, 1300, 2000};

        RngStandardization.configure(false, false);
        for (int age : ages) {
            assertEquals(
                    expectedVanilla.nextInt(VANILLA_BOUND_WITHOUT_CRYSTALS),
                    RngStandardization.nextDragonPerchRoll(
                            actualVanilla,
                            VANILLA_BOUND_WITHOUT_CRYSTALS,
                            age,
                            WORLD_SEED));
        }
        assertEquals(expectedVanilla.nextLong(), actualVanilla.nextLong());
    }

    @Test
    public void graceChecksStayVanillaAndBoundaryStartsAtEventZero() {
        Random expectedVanilla = new Random(13579L);
        Random actualVanilla = new Random(13579L);

        RngStandardization.configure(true, false);
        assertEquals(
                expectedVanilla.nextInt(VANILLA_BOUND_WITHOUT_CRYSTALS),
                RngStandardization.nextDragonPerchRoll(
                        actualVanilla, VANILLA_BOUND_WITHOUT_CRYSTALS, 0, WORLD_SEED));
        assertEquals(
                expectedVanilla.nextInt(VANILLA_BOUND_WITHOUT_CRYSTALS),
                RngStandardization.nextDragonPerchRoll(
                        actualVanilla, VANILLA_BOUND_WITHOUT_CRYSTALS, 1299, WORLD_SEED));

        int expectedFirstStandardized = new Random(RngStandardization.eventSeed(
                WORLD_SEED, "dragon_perch", "global", 0L))
                .nextInt(VANILLA_BOUND_WITHOUT_CRYSTALS);
        expectedVanilla.nextInt(VANILLA_BOUND_WITHOUT_CRYSTALS);
        assertEquals(
                expectedFirstStandardized,
                RngStandardization.nextDragonPerchRoll(
                        actualVanilla, VANILLA_BOUND_WITHOUT_CRYSTALS, 1300, WORLD_SEED));

        int expectedSecondStandardized = new Random(RngStandardization.eventSeed(
                WORLD_SEED, "dragon_perch", "global", 1L))
                .nextInt(VANILLA_BOUND_WITHOUT_CRYSTALS);
        expectedVanilla.nextInt(VANILLA_BOUND_WITHOUT_CRYSTALS);
        assertEquals(
                expectedSecondStandardized,
                RngStandardization.nextDragonPerchRoll(
                        actualVanilla, VANILLA_BOUND_WITHOUT_CRYSTALS, 1500, WORLD_SEED));

        assertEquals(expectedVanilla.nextLong(), actualVanilla.nextLong());
    }

    @Test
    public void jumpingToBoundaryTestsImmediateStandardizationWithoutWaiting() {
        Random vanillaRandom = new Random(24680L);
        int expected = new Random(RngStandardization.eventSeed(
                WORLD_SEED, "dragon_perch", "global", 0L))
                .nextInt(VANILLA_BOUND_WITHOUT_CRYSTALS);

        RngStandardization.configure(true, false);

        assertEquals(
                expected,
                RngStandardization.nextDragonPerchRoll(
                        vanillaRandom,
                        VANILLA_BOUND_WITHOUT_CRYSTALS,
                        RngStandardization.dragonPerchGraceTicks(),
                        WORLD_SEED));
    }

    private long secondPerchSeedWithoutOtherEvents() {
        RngStandardization.configure(true, false);
        RngStandardization.nextDragonPerchSeed(WORLD_SEED);
        return RngStandardization.nextDragonPerchSeed(WORLD_SEED);
    }
}
