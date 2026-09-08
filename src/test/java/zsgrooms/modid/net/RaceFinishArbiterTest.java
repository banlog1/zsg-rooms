package zsgrooms.modid.net;

import org.junit.jupiter.api.Test;
import zsgrooms.modid.InGame;
import zsgrooms.modid.ZsgRooms;

import static org.junit.jupiter.api.Assertions.*;

class RaceFinishArbiterTest {
    private static final String RACE = "race-one";

    private RaceFinishArbiter race() {
        RaceFinishArbiter arbiter = new RaceFinishArbiter();
        arbiter.beginRace(RACE);
        return arbiter;
    }

    private String finish(long elapsedNanos) {
        return RaceFinishArbiter.completion(RACE, elapsedNanos, 10000L);
    }

    @Test
    void laterPacketCanWinWithALowerDurationRegardlessOfIgt() {
        RaceFinishArbiter arbiter = race();
        assertTrue(arbiter.submit("Host", RaceFinishArbiter.completion(RACE, 600000000000L, 1L), 1000L, true));
        assertTrue(arbiter.submit("Guest", finish(599950000000L), 1800L, true));
        assertNull(arbiter.poll(2999L));
        RaceFinishArbiter.Decision result = arbiter.poll(3000L);
        assertEquals("Guest", result.winner);
        assertEquals("Beat the seed in 00:10.000 IGT", result.reason);
        assertNull(arbiter.poll(4000L));
    }

    @Test
    void oneNanosecondDifferenceWinsAndOnlyExactEqualityDraws() {
        long duration = 600000000000L;
        for (long gap : new long[] {0L, 1L, 49999999L, 50000000L, 50000001L}) {
            for (boolean reverse : new boolean[] {false, true}) {
                RaceFinishArbiter arbiter = race();
                String first = reverse ? "Slower" : "Faster";
                String second = reverse ? "Faster" : "Slower";
                arbiter.submit(first, finish(duration + (reverse ? gap : 0L)), 1000L, true);
                arbiter.submit(second, finish(duration + (reverse ? 0L : gap)), 1200L, true);
                assertEquals(gap == 0L ? "Draw" : "Faster", arbiter.poll(3000L).winner);
            }
        }
    }

    @Test
    void tiesBetweenSlowerPlayersDoNotMakeTheWinnerDraw() {
        RaceFinishArbiter arbiter = race();
        arbiter.submit("A", finish(100L), 1000L, true);
        arbiter.submit("B", finish(200L), 1100L, true);
        arbiter.submit("C", finish(200L), 1200L, true);
        assertEquals("A", arbiter.poll(3000L).winner);
    }

    @Test
    void nanosecondsRemainExactAtMaximumSupportedDuration() {
        long elapsed = RaceFinishArbiter.MAX_ELAPSED_NANOS - 1L;
        String payload = finish(elapsed);
        assertTrue(payload.contains("\"elapsedNanos\":\"" + elapsed + "\""));
        RaceFinishArbiter arbiter = race();
        assertTrue(arbiter.submit("A", finish(elapsed + 1L), 1000L, true));
        assertTrue(arbiter.submit("B", payload, 1100L, true));
        assertEquals("B", arbiter.poll(3000L).winner);
    }

    @Test
    void missingOrMalformedIgtDoesNotAffectWinner() {
        for (String extra : new String[] {"", ",\"igt\":0", ",\"igt\":-1", ",\"igt\":null",
                ",\"igt\":\"paused\"", ",\"igt\":{}", ",\"igt\":1.5", ",\"igt\":9223372036854775807"}) {
            RaceFinishArbiter arbiter = race();
            String payload = "{\"version\":3,\"raceId\":\"race-one\",\"elapsedNanos\":\"100\"" + extra + "}";
            assertTrue(arbiter.submit("A", payload, 1000L, true));
            arbiter.submit("B", finish(101L), 1100L, true);
            RaceFinishArbiter.Decision decision = arbiter.poll(3000L);
            assertEquals("A", decision.winner);
            assertEquals("Beat the seed", decision.reason);
        }
    }

    @Test
    void uncontestedFinishDoesNotWaitForCollectionWindow() {
        RaceFinishArbiter arbiter = race();
        assertTrue(arbiter.submit("Host", finish(100L), 5000L, false));
        assertEquals("Host", arbiter.poll(5000L).winner);
        assertFalse(arbiter.submit("Guest", finish(90L), 5100L, true));
    }

    @Test
    void dragonMilestoneEnablesWindowAndResetOrDepartureDisablesIt() {
        String room = "finish-dragon-readiness";
        ZsgRooms.createRoom(room, 3, 1, "manual:123", "Host");
        ZsgRooms.applyRoomAction("join_room", room, "Guest", "");
        InGame game = ZsgRooms.getGame(room);
        game.startGame();
        game.setPlayerProgress("Host", 8);
        game.setPlayerProgress("Guest", 7);
        assertFalse(RoomFinishTiming.hasOtherDragonFinisher(game, ZsgRooms.getRoom(room), "Host"));
        ZsgRooms.trackAdvancement(room, "Guest", "minecraft:end/kill_dragon\tFree the End");
        boolean contested = RoomFinishTiming.hasOtherDragonFinisher(game, ZsgRooms.getRoom(room), "Host");
        assertTrue(contested);
        RaceFinishArbiter arbiter = race();
        arbiter.submit("Host", finish(100L), 1000L, contested);
        assertNull(arbiter.poll(2999L));
        assertEquals("Host", arbiter.poll(3000L).winner);
        ZsgRooms.applyRoomAction("reset_run", room, "Guest", "");
        assertFalse(RoomFinishTiming.hasOtherDragonFinisher(game, ZsgRooms.getRoom(room), "Host"));
        game.setPlayerProgress("Departed", 8);
        assertFalse(RoomFinishTiming.hasOtherDragonFinisher(game, ZsgRooms.getRoom(room), "Host"));
    }

    @Test
    void duplicatesCannotChangeDurationOrExtendDeadline() {
        RaceFinishArbiter arbiter = race();
        assertTrue(arbiter.submit("Host", finish(100L), 5000L, true));
        assertFalse(arbiter.submit("Host", finish(1L), 6500L, true));
        assertTrue(arbiter.submit("Guest", finish(90L), 6600L, true));
        assertEquals("Guest", arbiter.poll(7000L).winner);
        assertFalse(arbiter.submit("Other", finish(1L), 7100L, true));
    }

    @Test
    void latePacketsCannotChangeResultEvenBeforePollRuns() {
        RaceFinishArbiter arbiter = race();
        arbiter.submit("Host", finish(100L), 5000L, true);
        assertFalse(arbiter.submit("Guest", finish(90L), 7000L, true));
        assertEquals("Host", arbiter.poll(8000L).winner);
    }

    @Test
    void newRaceDiscardsPendingFinishAndRejectsOldReports() {
        RaceFinishArbiter arbiter = race();
        arbiter.submit("Host", finish(100L), 5000L, true);
        arbiter.beginRace("race-two");
        assertFalse(arbiter.hasPendingFinish());
        assertFalse(arbiter.submit("Host", finish(100L), 6100L, true));
        assertNull(arbiter.poll(8000L));
        assertTrue(arbiter.submit("Host", RaceFinishArbiter.completion("race-two", 100L, 0L), 9000L, true));
        assertEquals("Beat the seed", arbiter.poll(11000L).reason);
    }

    @Test
    void sameRaceRefreshDoesNotDiscardPendingReports() {
        RaceFinishArbiter arbiter = race();
        arbiter.submit("Host", finish(100L), 1000L, true);
        arbiter.beginRace(RACE);
        assertEquals("Host", arbiter.poll(3000L).winner);
    }

    @Test
    void malformedMissingImpossibleAndOldFormatReportsAreRejected() {
        RaceFinishArbiter arbiter = race();
        for (String value : new String[] {null, "garbage", "{}", "[]", "Beat the seed",
                "{\"version\":2,\"raceId\":\"race-one\",\"entered\":100}",
                "{\"version\":3,\"raceId\":\"race-one\"}",
                "{\"version\":3,\"elapsedNanos\":\"100\"}",
                finish(-1L), finish(RaceFinishArbiter.MAX_ELAPSED_NANOS + 1L)}) {
            assertFalse(arbiter.submit("Host", value, 1000L, true), value);
        }
        for (String elapsed : new String[] {"null", "true", "[]", "{}", "1.5", "\"1.5\"", "1e3",
                "\"NaN\"", "\"9223372036854775808\""}) {
            String value = "{\"version\":3,\"raceId\":\"race-one\",\"elapsedNanos\":" + elapsed + "}";
            assertFalse(arbiter.submit("Host", value, 1000L, true), value);
        }
        assertFalse(arbiter.hasPendingFinish());
    }

    @Test
    void sameSeedRelaunchHasNewIdAndSnapshotsPreserveIt() {
        String room = "finish-timing-snapshot";
        ZsgRooms.createRoom(room, 2, 1, "manual:123", "Host");
        InGame game = ZsgRooms.getGame(room);
        game.startGame();
        String first = game.getRaceId();
        assertTrue(ZsgRooms.applyRoomSnapshot(ZsgRooms.createRoomSnapshot(room)));
        game = ZsgRooms.getGame(room);
        assertEquals(first, game.getRaceId());
        game.startGame();
        assertNotEquals(first, game.getRaceId());
    }

    @Test
    void completionPayloadFitsExistingPacketLimitAndKeepsDisplayReason() {
        String value = RaceFinishArbiter.completion("12345678-1234-1234-1234-123456789abc",
                RaceFinishArbiter.MAX_ELAPSED_NANOS, 754567L);
        assertTrue(value.length() < 256);
        assertEquals("Beat the seed in 12:34.567 IGT", ZsgRooms.completionReason(value));
        assertEquals("Beat the seed", ZsgRooms.completionReason("Beat the seed"));
    }
}
