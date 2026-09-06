package zsgrooms.modid;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class WoodLightingSequenceTest {
    @AfterEach
    public void reset() {
        RngStandardization.configure(false);
    }

    @Test
    public void matchingSourcesHaveMatchingSchedulesAndIgnitionRolls() {
        WoodLightingSequence first = new WoodLightingSequence(123L, "X|2x3|-1,0,1");
        WoodLightingSequence second = new WoodLightingSequence(123L, "X|2x3|-1,0,1");
        int hits = 0;
        for (int tick = 0; tick < 100000; tick++) {
            int opportunities = first.advanceLava(3);
            assertEquals(opportunities, second.advanceLava(3));
            for (int hit = 0; hit < opportunities; hit++) {
                assertEquals(first.nextLavaRandom().nextLong(), second.nextLavaRandom().nextLong());
                hits++;
            }
        }
        assertTrue(hits > 0);
    }

    @Test
    public void scheduleHasVanillaPerSourceRateAndAllowsMultipleSelections() {
        long hits = 0;
        for (int source = 0; source < 64; source++) {
            WoodLightingSequence sequence = new WoodLightingSequence(source, "rate");
            hits += sequence.advanceLava(4096 * 1000);
        }
        assertTrue(hits > 62000 && hits < 66000, "Expected about 64000 hits; got " + hits);
    }

    @Test
    public void zeroRandomTickSpeedPausesLavaWithoutPausingFire() {
        WoodLightingSequence paused = new WoodLightingSequence(9L, "a");
        WoodLightingSequence baseline = new WoodLightingSequence(9L, "a");
        for (int i = 0; i < 500; i++) {
            assertEquals(0, paused.advanceLava(0));
        }
        assertEquals(baseline.advanceLava(10000), paused.advanceLava(10000));
        int delay = paused.remainingFireTicks();
        assertTrue(delay >= 30 && delay <= 39);
        for (int i = 1; i < delay; i++) {
            assertFalse(paused.advanceFire());
        }
        assertTrue(paused.advanceFire());
    }

    @Test
    public void unrelatedRngAndNewSourcesDoNotShiftExistingEvents() {
        RngStandardization.configure(true);
        WoodLightingSequence source = new WoodLightingSequence(99L, "source");
        WoodLightingSequence baseline = new WoodLightingSequence(99L, "source");
        for (int i = 0; i < 20; i++) {
            RngStandardization.nextEyeBreakSeed(99L);
            RngStandardization.nextMobDropSeed(99L, "blaze");
            RngStandardization.nextPiglinBarterSeed(99L);
            new WoodLightingSequence(99L, "extra_source_" + i).advanceLava(50000);
            assertEquals(baseline.advanceLava(10000), source.advanceLava(10000));
            assertEquals(baseline.nextLavaRandom().nextLong(), source.nextLavaRandom().nextLong());
        }
    }

    @Test
    public void skippedOrExtraFireTargetsDoNotShiftOtherTargets() {
        WoodLightingSequence source = new WoodLightingSequence(42L, "source");
        long expected = source.targetRandom("fire_air", "target", 4).nextLong();
        source.targetRandom("fire_air", "extra_wood", 4).nextInt(100);
        source.targetRandom("fire_burn", "target", 4).nextInt(300);
        assertEquals(expected, source.targetRandom("fire_air", "target", 4).nextLong());
        assertNotEquals(expected, source.targetRandom("fire_air", "target", 5).nextLong());
        assertNotEquals(expected, source.targetRandom("fire_burn", "target", 4).nextLong());
    }

    @Test
    public void newLaunchRepeatsFireAndLavaSequences() {
        WoodLightingSequence old = new WoodLightingSequence(44L, "source");
        int initialDelay = old.remainingFireTicks();
        long first = old.nextLavaRandom().nextLong();
        old.beginFireEvent();
        old.nextLavaRandom();
        RngStandardization.configure(true);
        WoodLightingSequence fresh = new WoodLightingSequence(44L, "source");
        assertEquals(initialDelay, fresh.remainingFireTicks());
        assertEquals(first, fresh.nextLavaRandom().nextLong());
        assertEquals(0L, fresh.beginFireEvent());
        for (int event = 1; event < 100; event++) {
            assertEquals(event, fresh.beginFireEvent());
            assertTrue(fresh.remainingFireTicks() >= 30 && fresh.remainingFireTicks() <= 39);
        }
    }

    @Test
    public void replacedFireGetsFullDelayWithoutRewindingItsEventCounter() {
        WoodLightingSequence source = new WoodLightingSequence(5L, "fire");
        source.beginFireEvent();
        int delay = source.remainingFireTicks();
        for (int tick = 0; tick < delay - 1; tick++) {
            source.advanceFire();
        }
        assertEquals(1, source.remainingFireTicks());
        source.restartFireDelay();
        assertEquals(delay, source.remainingFireTicks());
        assertEquals(1L, source.beginFireEvent());
    }

    @Test
    public void disabledHooksLeaveOriginalRandomUntouchedExceptForVanillaDelayDraw() {
        RngStandardization.configure(false);
        Random expected = new Random(37L);
        Random actual = new Random(37L);
        assertEquals(30 + expected.nextInt(10), WoodLightingStandardization.fireDelay(null, null, actual));
        assertSame(actual, WoodLightingStandardization.burnRandom(actual, null));
        assertEquals(expected.nextInt(100), WoodLightingStandardization.fireInt(actual, 100));
        assertEquals(expected.nextLong(), actual.nextLong());
        assertFalse(WoodLightingStandardization.suppressNaturalLava(null, null));
        assertFalse(WoodLightingStandardization.suppressScheduledFire(null, null));
    }
}
