package zsgrooms.modid;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class RngStandardizationUnbreakingTest {
    private static final long WORLD_SEED = 975318642L;
    private static final String GOLDEN_PICKAXE = "minecraft:golden_pickaxe";
    private static final String GOLDEN_BOOTS = "minecraft:golden_boots";

    @AfterEach
    public void disableStandardization() {
        RngStandardization.configure(false, false);
    }

    @Test
    public void sameWorldSeedItemAndEventIndexProduceSameResult() {
        long expectedSeed = RngStandardization.eventSeed(
                WORLD_SEED, "unbreaking", GOLDEN_PICKAXE, 0L);

        RngStandardization.configure(true, false);
        long firstSeed = RngStandardization.nextUnbreakingSeed(WORLD_SEED, GOLDEN_PICKAXE);
        boolean firstResult = preventsToolDamage(firstSeed, 2);

        RngStandardization.configure(true, false);
        long repeatedSeed = RngStandardization.nextUnbreakingSeed(
                WORLD_SEED, GOLDEN_PICKAXE);
        boolean repeatedResult = preventsToolDamage(repeatedSeed, 2);

        assertEquals(expectedSeed, firstSeed);
        assertEquals(firstSeed, repeatedSeed);
        assertEquals(firstResult, repeatedResult);
    }

    @Test
    public void configureResetsUnbreakingSequences() {
        RngStandardization.configure(true, false);
        long first = RngStandardization.nextUnbreakingSeed(WORLD_SEED, GOLDEN_PICKAXE);
        RngStandardization.nextUnbreakingSeed(WORLD_SEED, GOLDEN_PICKAXE);

        RngStandardization.configure(true, false);

        assertEquals(first, RngStandardization.nextUnbreakingSeed(
                WORLD_SEED, GOLDEN_PICKAXE));
    }

    @Test
    public void itemTypesUseIndependentSequences() {
        RngStandardization.configure(true, false);
        RngStandardization.nextUnbreakingSeed(WORLD_SEED, GOLDEN_PICKAXE);
        long expectedSecondPickaxe = RngStandardization.nextUnbreakingSeed(
                WORLD_SEED, GOLDEN_PICKAXE);

        RngStandardization.configure(true, false);
        RngStandardization.nextUnbreakingSeed(WORLD_SEED, GOLDEN_PICKAXE);
        RngStandardization.nextUnbreakingSeed(WORLD_SEED, GOLDEN_BOOTS);
        RngStandardization.nextUnbreakingSeed(WORLD_SEED, GOLDEN_BOOTS);

        assertEquals(expectedSecondPickaxe, RngStandardization.nextUnbreakingSeed(
                WORLD_SEED, GOLDEN_PICKAXE));
    }

    @Test
    public void otherStandardizedEventsDoNotAffectUnbreakingSequence() {
        long expectedSecond = secondPickaxeSeedWithoutOtherEvents();

        RngStandardization.configure(true, false);
        RngStandardization.nextUnbreakingSeed(WORLD_SEED, GOLDEN_PICKAXE);
        RngStandardization.nextMobDropSeed(
                WORLD_SEED, "minecraft:the_nether|minecraft:blaze");
        RngStandardization.nextPiglinBarterSeed(WORLD_SEED);
        RngStandardization.nextEyeBreakSeed(WORLD_SEED);
        RngStandardization.nextGravelFlintSeed(WORLD_SEED);
        RngStandardization.nextDragonPerchSeed(WORLD_SEED);

        assertEquals(expectedSecond, RngStandardization.nextUnbreakingSeed(
                WORLD_SEED, GOLDEN_PICKAXE));
    }

    @Test
    public void differentIndexesHaveIndependentEventSeeds() {
        RngStandardization.configure(true, false);

        long first = RngStandardization.nextUnbreakingSeed(WORLD_SEED, GOLDEN_PICKAXE);
        long second = RngStandardization.nextUnbreakingSeed(WORLD_SEED, GOLDEN_PICKAXE);
        long third = RngStandardization.nextUnbreakingSeed(WORLD_SEED, GOLDEN_PICKAXE);

        assertEquals(RngStandardization.eventSeed(
                WORLD_SEED, "unbreaking", GOLDEN_PICKAXE, 0L), first);
        assertEquals(RngStandardization.eventSeed(
                WORLD_SEED, "unbreaking", GOLDEN_PICKAXE, 1L), second);
        assertEquals(RngStandardization.eventSeed(
                WORLD_SEED, "unbreaking", GOLDEN_PICKAXE, 2L), third);
        assertNotEquals(first, second);
        assertNotEquals(second, third);
    }

    @Test
    public void disabledStandardizationKeepsUnbreakingReplacementInactive() {
        RngStandardization.configure(false, false);

        assertFalse(RngStandardization.isEnabled());
    }

    private long secondPickaxeSeedWithoutOtherEvents() {
        RngStandardization.configure(true, false);
        RngStandardization.nextUnbreakingSeed(WORLD_SEED, GOLDEN_PICKAXE);
        return RngStandardization.nextUnbreakingSeed(WORLD_SEED, GOLDEN_PICKAXE);
    }

    private boolean preventsToolDamage(long seed, int level) {
        return new Random(seed).nextInt(level + 1) > 0;
    }
}
