package zsgrooms.modid;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class RaceSequenceTest {
    private RaceSequence race(int goal) {
        return new RaceSequence("race", Arrays.asList("11", "22", "33").subList(0, goal), Arrays.asList("A", "B"));
    }
    private boolean report(RaceSequence race, String name, int stage, long seconds, String action) {
        return race.submit(name, RaceSequence.report("race", stage, seconds * 1_000_000_000L, action));
    }

    @Test void defaultOneFinisherStopsRemainingRunnersWithoutCallingThemWithdrawn() {
        RaceSequence race = race(1);
        assertEquals(1, race.finisherLimit);
        report(race, "A", 0, 60, "finish");
        assertTrue(race.closeAtLimit(60_000_000_000L, 100L, false));
        assertTrue(race.complete());
        assertTrue(race.runner("B").stopped);
        assertFalse(race.runner("B").dnf);
        assertEquals(0, race.place("B"));
        assertFalse(report(race, "B", 0, 61, "finish"));
        assertTrue(race.publicCopy().valid("race"));
    }

    @Test void seedCountAndFinisherLimitAreIndependent() {
        RaceSequence race = new RaceSequence("race", Arrays.asList("11"), Arrays.asList("A", "B", "C"), 2);
        report(race, "A", 0, 10, "finish");
        assertFalse(race.closeAtLimit(10_000_000_000L, 0, false));
        assertTrue(race.active("B"));
        report(race, "B", 0, 12, "finish");
        assertTrue(race.closeAtLimit(12_000_000_000L, 0, false));
        assertTrue(race.runner("C").stopped);
        assertEquals(2, race.place("B"));
        RaceSequence longRace = race(3);
        report(longRace, "A", 0, 10, "finish");
        assertFalse(longRace.closeAtLimit(10_000_000_000L, 0, false));
        report(longRace, "A", 1, 20, "finish");
        report(longRace, "A", 2, 30, "finish");
        assertTrue(longRace.closeAtLimit(30_000_000_000L, 0, false));
    }

    @Test void withdrawalsDoNotCountTowardLimitButCannotLeaveRaceStuck() {
        RaceSequence race = new RaceSequence("race", Arrays.asList("11"), Arrays.asList("A", "B", "C"), 2);
        report(race, "A", 0, 10, "finish");
        race.withdraw("B");
        assertFalse(race.closeAtLimit(11_000_000_000L, 0, false));
        race.withdraw("C");
        assertTrue(race.closeAtLimit(12_000_000_000L, 0, false));
        assertEquals(3, new RaceSequence("race", Arrays.asList("11"), Arrays.asList("A", "B", "C"), 64).finisherLimit);
    }

    @Test void ordinaryForfeitAwardsTheRemainingRunnerWithoutAPenaltyOrFakeSeedCompletion() {
        RaceSequence race = race(1);
        race.withdraw("A");
        race.awardSingleSeedForfeitWin();
        assertTrue(race.runner("A").dnf);
        assertEquals(0, race.runner("A").penaltyNanos());
        assertTrue(race.runner("B").wonByForfeit);
        assertEquals(1, race.place("B"));
        assertEquals(0, race.runner("B").stage);
        assertTrue(race.complete());
        assertTrue(race.publicCopy().valid("race"));
        RaceSequence multiple = race(2);
        multiple.withdraw("A");
        multiple.awardSingleSeedForfeitWin();
        assertTrue(multiple.active("B"));
    }

    @Test void closeFinishesSettleBeforeStoppingOtherPlayersAndKeepExactTies() {
        RaceSequence race = new RaceSequence("race", Arrays.asList("11"), Arrays.asList("A", "B", "C"), 1);
        report(race, "A", 0, 20, "finish");
        assertFalse(race.closeAtLimit(20_000_000_000L, 100, true));
        report(race, "B", 0, 20, "finish");
        assertFalse(race.closeAtLimit(21_000_000_000L, 1_000_000_100L, true));
        assertTrue(race.closeAtLimit(22_000_000_000L, 2_000_000_100L, true));
        assertEquals(1, race.place("A"));
        assertEquals(1, race.place("B"));
        assertTrue(race.runner("C").stopped);
    }

    @Test void penalizedFinisherCannotImmediatelyEndRaceAndBeatBetterPotentialTime() {
        RaceSequence race = race(2);
        report(race, "A", 0, 10, "skip");
        report(race, "A", 1, 500, "finish");
        assertFalse(race.closeAtLimit(500_000_000_000L, 0, false));
        assertFalse(race.closeAtLimit(-1, 1, false));
        assertFalse(race.closeAtLimit(2300_000_000_000L, 2, false));
        assertTrue(race.closeAtLimit(2303_000_000_000L, 3, false));
        assertTrue(race.runner("B").stopped);
    }

    @Test void eachRunnerAdvancesIndependentlyThroughTheSameSeeds() {
        RaceSequence race = race(3);
        assertTrue(report(race, "A", 0, 600, "finish"));
        assertEquals("22", race.seed(race.runner("A").stage));
        assertEquals("11", race.seed(race.runner("B").stage));
        assertFalse(race.complete());
        assertTrue(report(race, "A", 1, 1200, "finish"));
        assertTrue(report(race, "A", 2, 1800, "finish"));
        assertTrue(race.active("B"));
        assertFalse(race.active("A"));
        assertFalse(race.complete());
    }

    @Test void newCutoffAfterPenalizedFinishGetsItsOwnCloseFinishWindow() {
        RaceSequence race = new RaceSequence("race", Arrays.asList("11", "22"), Arrays.asList("A", "B", "C"));
        report(race, "A", 0, 10, "skip");
        report(race, "A", 1, 500, "finish");
        assertFalse(race.closeAtLimit(500_000_000_000L, 500_000_000_000L, false));
        report(race, "B", 0, 600, "finish");
        report(race, "C", 0, 600, "finish");
        report(race, "B", 1, 1000, "finish");
        assertFalse(race.closeAtLimit(1000_000_000_000L, 1000_000_000_000L, true));
        assertFalse(race.closeAtLimit(1001_000_000_000L, 1001_000_000_000L, true));
        assertTrue(report(race, "C", 1, 999, "finish"));
        assertTrue(race.closeAtLimit(1001_000_000_000L, 1001_000_000_000L, false));
        assertEquals(1, race.place("C"));
        assertEquals(0, race.place("A"));
        assertEquals(0, race.place("B"));
        assertTrue(race.publicCopy().valid("race"));
    }

    @Test void collectionWindowDoesNotAwardPlacesBeyondLimit() {
        RaceSequence race = new RaceSequence("race", Arrays.asList("11"), Arrays.asList("A", "B", "C"));
        report(race, "A", 0, 100, "finish");
        assertFalse(race.closeAtLimit(100_000_000_000L, 0, true));
        report(race, "B", 0, 101, "finish");
        assertTrue(race.closeAtLimit(102_000_000_000L, 2_000_000_000L, true));
        assertEquals(1, race.place("A"));
        assertEquals(0, race.place("B"));
        assertTrue(race.runner("B").finished);
        assertTrue(race.runner("B").stopped);
        assertFalse(race.runner("B").dnf);
        assertEquals(101_000_000_000L, race.runner("B").elapsedNanos);
        assertEquals(1, race.runner("B").stage);
        assertTrue(race.runner("C").stopped);
        assertTrue(race.publicCopy().valid("race"));
        assertTrue(race.closeAtLimit(-1, 0, false));
        assertEquals(0, race.place("B"));
    }

    @Test void replacementCutoffGetsWindowEvenWhenAdjustedTimeIsUnchanged() {
        RaceSequence race = new RaceSequence("race", Arrays.asList("11", "22"), Arrays.asList("Z", "A", "B"));
        report(race, "Z", 0, 10, "skip");
        report(race, "Z", 1, 500, "finish");
        assertFalse(race.closeAtLimit(500_000_000_000L, 0, false));
        report(race, "A", 0, 600, "finish");
        report(race, "B", 0, 600, "finish");
        report(race, "A", 1, 2300, "finish");
        assertFalse(race.closeAtLimit(2300_000_000_000L, 10_000_000_000L, true));
        assertFalse(race.closeAtLimit(2301_000_000_000L, 11_000_000_000L, true));
        assertTrue(report(race, "B", 1, 2299, "finish"));
        assertTrue(race.closeAtLimit(2301_000_000_000L, 11_000_000_000L, true));
        assertEquals(1, race.place("B"));
    }

    @Test void allDoneStillEnforcesLimitAndKeepsExactCutoffTies() {
        RaceSequence race = new RaceSequence("race", Arrays.asList("11"), Arrays.asList("A", "B", "C", "D"), 2);
        report(race, "A", 0, 100, "finish");
        report(race, "B", 0, 101, "finish");
        assertFalse(race.closeAtLimit(101_000_000_000L, 0, true));
        report(race, "C", 0, 101, "finish");
        report(race, "D", 0, 102, "finish");
        assertTrue(race.complete());
        assertTrue(race.closeAtLimit(-1, 1, true));
        assertEquals(1, race.place("A"));
        assertEquals(2, race.place("B"));
        assertEquals(2, race.place("C"));
        assertEquals(0, race.place("D"));
        assertTrue(race.publicCopy().valid("race"));
        race.runner("D").dnf = true;
        assertFalse(race.publicCopy().valid("race"));
    }

    @Test void skipsAddThirtyMinutesAndCanLoseToALaterFinisher() {
        RaceSequence race = race(2);
        report(race, "A", 0, 10, "skip");
        report(race, "A", 1, 500, "finish");
        assertEquals(1, race.place("A"));
        report(race, "B", 0, 600, "finish");
        report(race, "B", 1, 1000, "finish");
        assertTrue(race.complete());
        assertEquals(2, race.place("A"));
        assertEquals(2300_000_000_000L, race.runner("A").adjustedNanos());
    }

    @Test void skippingTheLastSeedIsStillPenalized() {
        RaceSequence race = race(2);
        report(race, "A", 0, 1, "skip");
        report(race, "A", 1, 2, "skip");
        assertTrue(race.runner("A").finished);
        assertEquals(3602_000_000_000L, race.runner("A").adjustedNanos());
    }

    @Test void duplicateDelayedStaleAndDecreasingReportsCannotAdvanceTwice() {
        RaceSequence race = race(3);
        assertTrue(report(race, "A", 0, 5, "finish"));
        assertFalse(report(race, "A", 0, 5, "finish"));
        assertFalse(report(race, "A", 0, 6, "skip"));
        assertFalse(report(race, "A", 2, 8, "finish"));
        assertFalse(report(race, "A", 1, 4, "finish"));
        assertFalse(race.submit("A", RaceSequence.report("old", 1, 9, "skip")));
        assertFalse(report(race, "spectator", 0, 1, "finish"));
        assertFalse(race.submit("A", "not json"));
        assertFalse(report(race, "A", 1, Long.MAX_VALUE / 1_000_000_000L, "finish"));
        assertEquals(1, race.runner("A").stage);
    }

    @Test void singleSeedPlacementsWaitForEveryRunnerAndOnlyExactTimesTie() {
        RaceSequence race = race(1);
        race.submit("A", RaceSequence.report("race", 0, 500, "finish"));
        assertFalse(race.complete());
        race.submit("B", RaceSequence.report("race", 0, 501, "finish"));
        assertEquals(2, race.place("B"));
        assertTrue(race.complete());
        RaceSequence tie = race(1);
        report(tie, "B", 0, 5, "finish");
        report(tie, "A", 0, 5, "finish");
        assertEquals(1, tie.place("A"));
        assertEquals(1, tie.place("B"));
    }

    @Test void dnfDoesNotEndOtherRunnersAndSingleSeedForfeitIsDnf() {
        RaceSequence race = race(1);
        assertTrue(report(race, "A", 0, 10, "skip"));
        assertTrue(race.runner("A").dnf);
        assertTrue(race.active("B"));
        assertTrue(race.withdraw("B"));
        assertTrue(race.complete());
        assertFalse(report(race, "B", 0, 20, "finish"));
        assertEquals(0, race.place("A"));
    }

    @Test void snapshotsPreserveProgressAndDoNotRevealFutureSeeds() {
        RaceSequence race = race(3);
        RaceSequence before = race.publicCopy();
        assertEquals("11", before.seed(0));
        assertNull(before.seed(1));
        assertTrue(before.valid("race"));
        report(race, "A", 0, 10, "skip");
        RaceSequence copy = new Gson().fromJson(new Gson().toJson(race.publicCopy()), RaceSequence.class);
        assertTrue(copy.valid("race"));
        assertEquals("22", copy.seed(1));
        assertNull(copy.seed(2));
        assertEquals("33", race.seed(2));
        assertEquals(1, copy.runner("A").skipped);
        assertFalse(copy.valid("old"));
    }
}
