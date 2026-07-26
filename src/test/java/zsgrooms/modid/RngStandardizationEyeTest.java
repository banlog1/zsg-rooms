package zsgrooms.modid;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class RngStandardizationEyeTest {
    private static final long WORLD_SEED = 8675309L;

    @AfterEach
    public void disableStandardization() {
        RngStandardization.configure(false, false);
    }

    @Test
    public void sameWorldSeedAndEventIndexProduceSameEyeResult() {
        long expectedSeed = RngStandardization.eventSeed(
                WORLD_SEED, "eye_break", "global", 0L);

        RngStandardization.configure(true, false);
        long firstSeed = RngStandardization.nextEyeBreakSeed(WORLD_SEED);
        int firstResult = new Random(firstSeed).nextInt(5);

        RngStandardization.configure(true, false);
        long repeatedSeed = RngStandardization.nextEyeBreakSeed(WORLD_SEED);
        int repeatedResult = new Random(repeatedSeed).nextInt(5);

        assertEquals(expectedSeed, firstSeed);
        assertEquals(firstSeed, repeatedSeed);
        assertEquals(firstResult, repeatedResult);
    }

    @Test
    public void configureResetsEyeSequence() {
        RngStandardization.configure(true, false);
        long first = RngStandardization.nextEyeBreakSeed(WORLD_SEED);
        RngStandardization.nextEyeBreakSeed(WORLD_SEED);

        RngStandardization.configure(true, false);

        assertEquals(first, RngStandardization.nextEyeBreakSeed(WORLD_SEED));
    }

    @Test
    public void mobDropEventsDoNotAffectEyeSequence() {
        long expectedSecond = secondEyeSeedWithoutOtherEvents();

        RngStandardization.configure(true, false);
        RngStandardization.nextEyeBreakSeed(WORLD_SEED);
        RngStandardization.nextMobDropSeed(WORLD_SEED, "minecraft:overworld|minecraft:blaze");
        RngStandardization.nextMobDropSeed(WORLD_SEED, "minecraft:overworld|minecraft:enderman");
        RngStandardization.nextMobDropSeed(WORLD_SEED, "minecraft:overworld|minecraft:blaze");

        assertEquals(expectedSecond, RngStandardization.nextEyeBreakSeed(WORLD_SEED));
    }

    @Test
    public void piglinBarterEventsDoNotAffectEyeSequence() {
        long expectedSecond = secondEyeSeedWithoutOtherEvents();

        RngStandardization.configure(true, false);
        RngStandardization.nextEyeBreakSeed(WORLD_SEED);
        RngStandardization.nextPiglinBarterSeed(WORLD_SEED);
        RngStandardization.nextPiglinBarterSeed(WORLD_SEED);

        assertEquals(expectedSecond, RngStandardization.nextEyeBreakSeed(WORLD_SEED));
    }

    @Test
    public void differentEyeIndexesHaveIndependentEventSeeds() {
        RngStandardization.configure(true, false);

        long first = RngStandardization.nextEyeBreakSeed(WORLD_SEED);
        long second = RngStandardization.nextEyeBreakSeed(WORLD_SEED);
        long third = RngStandardization.nextEyeBreakSeed(WORLD_SEED);

        assertEquals(RngStandardization.eventSeed(
                WORLD_SEED, "eye_break", "global", 0L), first);
        assertEquals(RngStandardization.eventSeed(
                WORLD_SEED, "eye_break", "global", 1L), second);
        assertEquals(RngStandardization.eventSeed(
                WORLD_SEED, "eye_break", "global", 2L), third);
        assertNotEquals(first, second);
        assertNotEquals(second, third);
    }

    @Test
    public void disabledStandardizationKeepsEyeReplacementInactive() {
        RngStandardization.configure(false, false);

        assertFalse(RngStandardization.isEnabled());
    }

    private long secondEyeSeedWithoutOtherEvents() {
        RngStandardization.configure(true, false);
        RngStandardization.nextEyeBreakSeed(WORLD_SEED);
        return RngStandardization.nextEyeBreakSeed(WORLD_SEED);
    }
}
