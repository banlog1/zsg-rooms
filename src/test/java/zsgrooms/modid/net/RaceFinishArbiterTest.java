package zsgrooms.modid.net;

import org.junit.jupiter.api.Test;
import zsgrooms.modid.InGame;
import zsgrooms.modid.ZsgRooms;

import static org.junit.jupiter.api.Assertions.*;

class RaceFinishArbiterTest {
    private static final String RACE = "race-one";

    private RaceFinishArbiter race() {
        RaceFinishArbiter arbiter = new RaceFinishArbiter();
        arbiter.beginRace(RACE, 0L);
        return arbiter;
    }

    private void sample(RaceFinishArbiter arbiter, String player, long offset, long sent,
                        long outbound, long processing, long inbound) {
        String probe = arbiter.probe(sent);
        String reply = RaceFinishArbiter.reply(probe, sent + outbound + offset,
                sent + outbound + offset + processing);
        arbiter.receiveReply(player, reply, sent + outbound + processing + inbound);
    }

    private String finish(long time) {
        return RaceFinishArbiter.completion(RACE, time, 10000L);
    }

    @Test
    void uncontestedFinishDoesNotWaitForCollectionWindow() {
        RaceFinishArbiter arbiter = race();
        assertTrue(arbiter.submit("Host", finish(5000L), true, 5000L, false));
        assertEquals("Host", arbiter.poll(5000L).winner);
        assertFalse(arbiter.submit("Guest", finish(4900L), false, 5100L, true));
    }

    @Test
    void anotherCurrentPlayersDragonMilestoneEnablesCollection() {
        String room = "finish-dragon-readiness";
        ZsgRooms.createRoom(room, 3, 1, "manual:123", "Host");
        ZsgRooms.applyRoomAction("join_room", room, "Guest", "");
        InGame game = ZsgRooms.getGame(room);
        game.startGame();
        game.setPlayerProgress("Host", 8);
        game.setPlayerProgress("Guest", 7);
        assertFalse(RoomFinishTiming.hasOtherDragonFinisher(game, ZsgRooms.getRoom(room), "Host"));
        ZsgRooms.trackAdvancement(room, "Guest", "minecraft:end/kill_dragon\tFree the End");
        assertTrue(RoomFinishTiming.hasOtherDragonFinisher(game, ZsgRooms.getRoom(room), "Host"));
        ZsgRooms.applyRoomAction("reset_run", room, "Guest", "");
        assertFalse(RoomFinishTiming.hasOtherDragonFinisher(game, ZsgRooms.getRoom(room), "Host"));
        game.setPlayerProgress("Departed", 8);
        assertFalse(RoomFinishTiming.hasOtherDragonFinisher(game, ZsgRooms.getRoom(room), "Host"));
    }

    @Test
    void guestWhoEnteredFirstWinsEvenWhenHostReportArrivesFirst() {
        RaceFinishArbiter arbiter = race();
        sample(arbiter, "Guest", 1000000L, 1000L, 20L, 0L, 20L);
        assertTrue(arbiter.submit("Host", RaceFinishArbiter.completion(RACE, 5100L, 1000L), true, 5100L));
        assertTrue(arbiter.submit("Guest", finish(1005000L), false, 5500L));
        assertNull(arbiter.poll(7099L));
        RaceFinishArbiter.Decision result = arbiter.poll(7100L);
        assertEquals("Guest", result.winner);
        assertEquals("Beat the seed in 00:10.000 IGT", result.reason);
        assertNull(arbiter.poll(8000L));
    }

    @Test
    void guestArrivalOrderAndUnrelatedClockOriginsDoNotChooseWinner() {
        RaceFinishArbiter arbiter = race();
        sample(arbiter, "A", -5000000L, 1000L, 15L, 0L, 15L);
        sample(arbiter, "B", 9000000L, 1100L, 10L, 0L, 10L);
        arbiter.submit("B", finish(9005100L), false, 5130L);
        arbiter.submit("A", finish(-4995000L), false, 5800L);
        assertEquals("A", arbiter.poll(7130L).winner);
    }

    @Test
    void hostCanWinDespiteLaterDeliveryOfItsReport() {
        RaceFinishArbiter arbiter = race();
        sample(arbiter, "Guest", 200000L, 1000L, 20L, 0L, 20L);
        arbiter.submit("Guest", finish(205200L), false, 5230L);
        arbiter.submit("Host", finish(5000L), true, 5300L);
        assertEquals("Host", arbiter.poll(7230L).winner);
    }

    @Test
    void probeProcessingDelayIsExcludedFromRtt() {
        RaceFinishArbiter arbiter = race();
        sample(arbiter, "Guest", 10000L, 1000L, 20L, 900L, 20L);
        arbiter.submit("Host", finish(5100L), true, 5100L);
        arbiter.submit("Guest", finish(15000L), false, 5500L);
        assertEquals("Guest", arbiter.poll(7100L).winner);
    }

    @Test
    void lowestRecentRttSampleAvoidsInflatingUncertaintyFromOneLagSpike() {
        RaceFinishArbiter arbiter = race();
        sample(arbiter, "Guest", 10000L, 1000L, 10L, 0L, 10L);
        sample(arbiter, "Guest", 10000L, 2000L, 400L, 0L, 400L);
        arbiter.submit("Host", finish(5100L), true, 5100L);
        arbiter.submit("Guest", finish(15000L), false, 5500L);
        assertEquals("Guest", arbiter.poll(7100L).winner);
    }

    @Test
    void correctedTimesWithinOneTickAreADrawEvenOnAnAsymmetricPath() {
        RaceFinishArbiter arbiter = race();
        sample(arbiter, "Guest", 10000L, 1000L, 20L, 0L, 180L);
        arbiter.submit("Host", finish(5050L), true, 5050L);
        arbiter.submit("Guest", finish(15000L), false, 5500L);
        assertEquals("Draw", arbiter.poll(7050L).winner);
    }

    @Test
    void drawMarginIsStrictlyBelow50MillisecondsRegardlessOfRtt() {
        for (long rtt : new long[] {20L, 400L}) {
            for (long gap : new long[] {0L, 49L, 50L, 51L, 120L}) {
                RaceFinishArbiter arbiter = race();
                sample(arbiter, "Guest", 10000L, 1000L, rtt / 2L, 0L, rtt / 2L);
                arbiter.submit("Host", finish(5000L + gap), true, 5000L + gap);
                arbiter.submit("Guest", finish(15000L), false, 5500L);
                assertEquals(gap < 50L ? "Draw" : "Guest", arbiter.poll(8000L).winner,
                        "RTT=" + rtt + ", gap=" + gap);
            }
        }
    }

    @Test
    void identicalTimesAreADrawNotAnArrivalOrderTieBreak() {
        RaceFinishArbiter arbiter = race();
        sample(arbiter, "Guest", 10000L, 1000L, 10L, 0L, 10L);
        arbiter.submit("Host", finish(5000L), true, 5000L);
        arbiter.submit("Guest", finish(15000L), false, 5010L);
        assertEquals(RaceFinishArbiter.UNRESOLVED_REASON, arbiter.poll(7000L).reason);
    }

    @Test
    void missingOrExpiredMeasurementsDoNotGiveHostAnAutomaticWin() {
        for (boolean expired : new boolean[] {false, true}) {
            RaceFinishArbiter arbiter = race();
            if (expired) {
                sample(arbiter, "Guest", 10000L, 1000L, 10L, 0L, 10L);
            }
            arbiter.submit("Host", finish(70000L), true, 70000L);
            arbiter.submit("Guest", finish(79000L), false, 70100L);
            assertEquals("Draw", arbiter.poll(72000L).winner);
        }
    }

    @Test
    void legacyReportIsConservativeAndNeverParsedAsAnIgtRanking() {
        RaceFinishArbiter arbiter = race();
        arbiter.submit("Host", finish(5000L), true, 5000L);
        assertTrue(arbiter.submit("Guest", "Beat the seed in 00:01.000 IGT", false, 5100L));
        assertEquals("Draw", arbiter.poll(7000L).winner);
    }

    @Test
    void aSingleCompletionStillEndsTheRace() {
        RaceFinishArbiter arbiter = race();
        arbiter.submit("Guest", finish(5000L), false, 6000L);
        assertEquals("Guest", arbiter.poll(8000L).winner);
    }

    @Test
    void duplicatesCannotChangeTimestampOrExtendDeadline() {
        RaceFinishArbiter arbiter = race();
        assertTrue(arbiter.submit("Host", finish(5000L), true, 5000L));
        assertFalse(arbiter.submit("Host", finish(2000L), true, 6500L));
        assertNotNull(arbiter.poll(7000L));
        assertFalse(arbiter.submit("Other", finish(4900L), true, 7100L));
    }

    @Test
    void latePacketsCannotChangeResultEvenBeforePollRuns() {
        RaceFinishArbiter arbiter = race();
        arbiter.submit("Host", finish(5000L), true, 5000L);
        assertFalse(arbiter.submit("Guest", finish(4900L), false, 7000L));
        assertEquals("Host", arbiter.poll(8000L).winner);
    }

    @Test
    void newLaunchDiscardsPendingFinishAndRejectsOldRaceReports() {
        RaceFinishArbiter arbiter = race();
        arbiter.submit("Host", finish(5000L), true, 5000L);
        arbiter.beginRace("race-two", 6000L);
        assertFalse(arbiter.hasPendingFinish());
        assertFalse(arbiter.submit("Host", finish(5000L), true, 6100L));
        assertNull(arbiter.poll(8000L));
        assertTrue(arbiter.submit("Host", RaceFinishArbiter.completion("race-two", 9000L, 0L), true, 9000L));
        assertEquals("Beat the seed", arbiter.poll(11000L).reason);
    }

    @Test
    void malformedFutureAndExcessivelyOldReportsAreRejected() {
        RaceFinishArbiter arbiter = race();
        assertFalse(arbiter.submit("Host", "{}", true, 5000L));
        assertFalse(arbiter.submit("Host", "garbage", true, 5000L));
        assertFalse(arbiter.submit("Host", null, true, 5000L));
        assertFalse(arbiter.submit("Host", finish(10000L), true, 5000L));
        assertFalse(arbiter.submit("Host", finish(-100L), true, 5000L));
        assertFalse(arbiter.submit("Host", finish(1000L), true, 15000L));
        assertFalse(arbiter.hasPendingFinish());
    }

    @Test
    void unrecognizedAndDuplicateClockRepliesCannotReplaceGoodMeasurement() {
        RaceFinishArbiter arbiter = race();
        String probe = arbiter.probe(1000L);
        arbiter.receiveReply("Guest", RaceFinishArbiter.reply(probe, 11020L, 11020L), 1040L);
        arbiter.receiveReply("Guest", RaceFinishArbiter.reply(probe, 999999L, 999999L), 1041L);
        arbiter.receiveReply("Guest", "{\"nonce\":99,\"received\":0,\"sent\":0}", 1050L);
        arbiter.submit("Host", finish(5100L), true, 5100L);
        arbiter.submit("Guest", finish(15000L), false, 5500L);
        assertEquals("Guest", arbiter.poll(7100L).winner);
    }

    @Test
    void reconnectClearsOldClockOrigin() {
        RaceFinishArbiter arbiter = race();
        sample(arbiter, "Guest", 10000L, 1000L, 1L, 0L, 1L);
        arbiter.resetClocks();
        sample(arbiter, "Guest", -10000L, 2000L, 20L, 0L, 20L);
        arbiter.submit("Host", finish(5100L), true, 5100L);
        arbiter.submit("Guest", finish(-5000L), false, 5500L);
        assertEquals("Guest", arbiter.poll(7100L).winner);
    }

    @Test
    void sameSeedRelaunchHasNewIdAndSnapshotsPreserveIt() {
        String room = "finish-timing-snapshot";
        ZsgRooms.createRoom(room, 2, 1, "manual:123", "Host");
        InGame game = ZsgRooms.getGame(room);
        game.startGame();
        String first = game.getRaceId();
        assertFalse(first.isEmpty());
        String snapshot = ZsgRooms.createRoomSnapshot(room);
        assertTrue(ZsgRooms.applyRoomSnapshot(snapshot));
        game = ZsgRooms.getGame(room);
        assertEquals(first, game.getRaceId());
        game.startGame();
        assertNotEquals(first, game.getRaceId());
    }

    @Test
    void completionPayloadFitsExistingPacketLimitAndKeepsDisplayReason() {
        String value = RaceFinishArbiter.completion("12345678-1234-1234-1234-123456789abc", Long.MIN_VALUE, 754567L);
        assertTrue(value.length() < 256);
        assertEquals("Beat the seed in 12:34.567 IGT", ZsgRooms.completionReason(value));
        assertEquals("Beat the seed", ZsgRooms.completionReason("Beat the seed"));
    }
}
