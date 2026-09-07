package zsgrooms.modid;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class RngStandardizationDragonOpeningHeightTest {
    @AfterEach
    public void reset() {
        RngStandardization.configure(false);
    }

    @Test
    public void matchingEventsRepeatRegardlessOfNativeRandomAndResetOnConfigure() {
        float[] expected = new float[100];
        RngStandardization.configure(true);
        Random first = new Random(1L);
        for (int i = 0; i < expected.length; i++) {
            expected[i] = RngStandardization.nextDragonOpeningHeightRoll(first, i, 123L);
        }
        RngStandardization.configure(true);
        Random other = new Random(987L);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], RngStandardization.nextDragonOpeningHeightRoll(other, i, 123L));
        }
    }

    @Test
    public void preservesVanillaRangeAndAdvancesNativeRandomExactlyOnce() {
        RngStandardization.configure(true);
        Random actual = new Random(99L);
        Random original = new Random(99L);
        boolean upperQuarter = false;
        for (int i = 0; i < 1000; i++) {
            float result = RngStandardization.nextDragonOpeningHeightRoll(actual, 12, 43L);
            original.nextFloat();
            assertTrue(result >= 0 && result < 1);
            upperQuarter |= result >= 0.75F;
        }
        assertTrue(upperQuarter, "Fairness mode must not remove vanilla's higher offsets");
        assertEquals(original.nextLong(), actual.nextLong());
    }

    @Test
    public void assistScalesTheSameDeterministicRollWithoutChangingItsSequence() {
        float[] full = new float[100];
        RngStandardization.configure(true, false, false);
        for (int i = 0; i < full.length; i++) {
            full[i] = RngStandardization.nextDragonOpeningHeightRoll(new Random(i), i, 20L);
        }
        RngStandardization.configure(true, false, true);
        for (int i = 0; i < full.length; i++) {
            float assisted = RngStandardization.nextDragonOpeningHeightRoll(new Random(i), i, 20L);
            assertEquals(full[i] * 0.75F, assisted);
            assertTrue(assisted * 20 < 15);
        }
    }

    @Test
    public void bothOffUsesExactVanillaResultAndAssistCanWorkWithoutStandardization() {
        for (boolean assist : new boolean[]{false, true}) {
            RngStandardization.configure(false, false, assist);
            Random actual = new Random(7L);
            Random expected = new Random(7L);
            for (int i = 0; i < 100; i++) {
                float vanilla = expected.nextFloat();
                assertEquals(assist ? vanilla * 0.75F : vanilla,
                        RngStandardization.nextDragonOpeningHeightRoll(actual, 0, 123L));
            }
            assertEquals(expected.nextLong(), actual.nextLong());
        }
    }

    @Test
    public void openingWindowIsIndependentOfPerchGraceAndDoesNotConsumeEventsAfterwards() {
        RngStandardization.configure(true, false, true);
        long firstSeed = RngStandardization.nextDragonOpeningHeightSeed(16L);
        RngStandardization.configure(true, false, true);
        RngStandardization.setDragonPerchGraceTicksForTesting(0);
        Random expected = new Random(3L);
        Random actual = new Random(3L);
        assertEquals(expected.nextFloat(), RngStandardization.nextDragonOpeningHeightRoll(actual, 1300, 16L));
        assertEquals(expected.nextFloat(), RngStandardization.nextDragonOpeningHeightRoll(actual, 1500, 16L));
        assertEquals(new Random(firstSeed).nextFloat() * 0.75F,
                RngStandardization.nextDragonOpeningHeightRoll(actual, 1299, 16L));
        RngStandardization.configure(true);
        assertFalse(RngStandardization.isDragonPerchStandardizationActive(1299));
        assertTrue(RngStandardization.isDragonPerchStandardizationActive(1300));
    }

    @Test
    public void unrelatedChannelsDoNotAdvanceOpeningHeightOrViceVersa() {
        RngStandardization.configure(true);
        long first = RngStandardization.nextDragonOpeningHeightSeed(99L);
        long second = RngStandardization.nextDragonOpeningHeightSeed(99L);
        long perch = RngStandardization.nextDragonPerchSeed(99L);
        assertNotEquals(first, second);
        RngStandardization.configure(true);
        assertEquals(perch, RngStandardization.nextDragonPerchSeed(99L));
        RngStandardization.nextMobDropSeed(99L, "blaze");
        RngStandardization.nextPiglinBarterSeed(99L);
        RngStandardization.nextEyeBreakSeed(99L);
        RngStandardization.nextGravelFlintSeed(99L);
        RngStandardization.nextUnbreakingSeed(99L, "pickaxe");
        assertEquals(first, RngStandardization.nextDragonOpeningHeightSeed(99L));
        assertEquals(second, RngStandardization.nextDragonOpeningHeightSeed(99L));
        RngStandardization.configure(true);
        assertNotEquals(first, RngStandardization.nextDragonOpeningHeightSeed(100L));
    }

    @Test
    public void legacyConfigureOverloadClearsAssist() {
        RngStandardization.configure(false, false, true);
        RngStandardization.configure(false);
        assertEquals(new Random(2).nextFloat(),
                RngStandardization.nextDragonOpeningHeightRoll(new Random(2), 0, 0L));
    }
}
