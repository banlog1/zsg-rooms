package zsgrooms.modid;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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
    }

    private long secondPerchSeedWithoutOtherEvents() {
        RngStandardization.configure(true, false);
        RngStandardization.nextDragonPerchSeed(WORLD_SEED);
        return RngStandardization.nextDragonPerchSeed(WORLD_SEED);
    }
}
