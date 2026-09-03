package zsgrooms.modid;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class RngStandardizationGravelFlintTest {
    private static final long WORLD_SEED = 246813579L;
    private static final float VANILLA_FLINT_CHANCE = 0.1F;

    @AfterEach
    public void disableStandardization() {
        RngStandardization.configure(false, false);
    }

    @Test
    public void sameWorldSeedAndEventIndexProduceSameFlintResult() {
        long expectedSeed = RngStandardization.eventSeed(
                WORLD_SEED, "gravel_flint", "global", 0L);

        RngStandardization.configure(true, false);
        long firstSeed = RngStandardization.nextGravelFlintSeed(WORLD_SEED);
        boolean firstResult = rollsFlint(firstSeed);

        RngStandardization.configure(true, false);
        long repeatedSeed = RngStandardization.nextGravelFlintSeed(WORLD_SEED);
        boolean repeatedResult = rollsFlint(repeatedSeed);

        assertEquals(expectedSeed, firstSeed);
        assertEquals(firstSeed, repeatedSeed);
        assertEquals(firstResult, repeatedResult);
    }

    @Test
    public void configureResetsGravelSequence() {
        RngStandardization.configure(true, false);
        long first = RngStandardization.nextGravelFlintSeed(WORLD_SEED);
        RngStandardization.nextGravelFlintSeed(WORLD_SEED);

        RngStandardization.configure(true, false);

        assertEquals(first, RngStandardization.nextGravelFlintSeed(WORLD_SEED));
    }

    @Test
    public void otherStandardizedEventsDoNotAffectGravelSequence() {
        long expectedSecond = secondGravelSeedWithoutOtherEvents();

        RngStandardization.configure(true, false);
        RngStandardization.nextGravelFlintSeed(WORLD_SEED);
        RngStandardization.nextMobDropSeed(
                WORLD_SEED, "minecraft:overworld|minecraft:enderman");
        RngStandardization.nextPiglinBarterSeed(WORLD_SEED);
        RngStandardization.nextEyeBreakSeed(WORLD_SEED);
        RngStandardization.nextDragonPerchSeed(WORLD_SEED);

        assertEquals(expectedSecond, RngStandardization.nextGravelFlintSeed(WORLD_SEED));
    }

    @Test
    public void gravelEventsDoNotAffectOtherStandardizedSequences() {
        RngStandardization.configure(true, false);
        RngStandardization.nextEyeBreakSeed(WORLD_SEED);
        long expectedSecondEye = RngStandardization.nextEyeBreakSeed(WORLD_SEED);
        RngStandardization.configure(true, false);
        RngStandardization.nextPiglinBarterSeed(WORLD_SEED);
        long expectedSecondBarter = RngStandardization.nextPiglinBarterSeed(WORLD_SEED);
        RngStandardization.configure(true, false);
        RngStandardization.nextMobDropSeed(WORLD_SEED, "minecraft:overworld|minecraft:enderman");
        long expectedSecondDrop = RngStandardization.nextMobDropSeed(
                WORLD_SEED, "minecraft:overworld|minecraft:enderman");
        RngStandardization.configure(true, false);
        RngStandardization.nextDragonPerchSeed(WORLD_SEED);
        long expectedSecondPerch = RngStandardization.nextDragonPerchSeed(WORLD_SEED);

        RngStandardization.configure(true, false);
        RngStandardization.nextEyeBreakSeed(WORLD_SEED);
        RngStandardization.nextPiglinBarterSeed(WORLD_SEED);
        RngStandardization.nextMobDropSeed(
                WORLD_SEED, "minecraft:overworld|minecraft:enderman");
        RngStandardization.nextDragonPerchSeed(WORLD_SEED);
        RngStandardization.nextGravelFlintSeed(WORLD_SEED);
        RngStandardization.nextGravelFlintSeed(WORLD_SEED);

        assertEquals(expectedSecondEye, RngStandardization.nextEyeBreakSeed(WORLD_SEED));
        assertEquals(expectedSecondBarter, RngStandardization.nextPiglinBarterSeed(WORLD_SEED));
        assertEquals(expectedSecondDrop, RngStandardization.nextMobDropSeed(
                WORLD_SEED, "minecraft:overworld|minecraft:enderman"));
        assertEquals(expectedSecondPerch, RngStandardization.nextDragonPerchSeed(WORLD_SEED));
    }

    @Test
    public void differentGravelIndexesHaveIndependentEventSeeds() {
        RngStandardization.configure(true, false);

        long first = RngStandardization.nextGravelFlintSeed(WORLD_SEED);
        long second = RngStandardization.nextGravelFlintSeed(WORLD_SEED);
        long third = RngStandardization.nextGravelFlintSeed(WORLD_SEED);

        assertEquals(RngStandardization.eventSeed(
                WORLD_SEED, "gravel_flint", "global", 0L), first);
        assertEquals(RngStandardization.eventSeed(
                WORLD_SEED, "gravel_flint", "global", 1L), second);
        assertEquals(RngStandardization.eventSeed(
                WORLD_SEED, "gravel_flint", "global", 2L), third);
        assertNotEquals(first, second);
        assertNotEquals(second, third);
    }

    @Test
    public void disabledStandardizationKeepsGravelReplacementInactive() {
        RngStandardization.configure(false, false);

        assertFalse(RngStandardization.isEnabled());
    }

    private long secondGravelSeedWithoutOtherEvents() {
        RngStandardization.configure(true, false);
        RngStandardization.nextGravelFlintSeed(WORLD_SEED);
        return RngStandardization.nextGravelFlintSeed(WORLD_SEED);
    }

    private boolean rollsFlint(long seed) {
        return new Random(seed).nextFloat() < VANILLA_FLINT_CHANCE;
    }
}
