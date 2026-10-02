package zsgrooms.modid;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TournamentTest {
    private TournamentSettings settings(boolean bracket, int players) {
        TournamentSettings settings = new TournamentSettings();
        settings.enabled = true; settings.bracket = bracket;
        for (int i = 0; i < players; i++) settings.order.add("P" + i);
        for (int i = 0; bracket && i < TournamentSettings.byeCount(players); i++) settings.byes.add("P" + i);
        return settings;
    }
    private RaceSequence race(Tournament tournament, int seeds) {
        RaceSequence race = new RaceSequence(UUID.randomUUID().toString(), Collections.nCopies(seeds, "123"), tournament.participants(), 64);
        tournament.beginRace(race.raceId);
        return race;
    }
    private void finish(RaceSequence race, String player, long time) {
        for (int stage = 0; stage < race.goal; stage++) assertTrue(race.submit(player, RaceSequence.report(race.raceId, stage, time + stage, "finish")));
    }
    private void win(Tournament tournament, String name) {
        RaceSequence race = race(tournament, 1);
        finish(race, name, 1000);
        for (RaceSequence.Runner runner : race.standings()) if (!runner.done()) race.withdraw(runner.name);
        assertTrue(tournament.record(race));
    }

    @Test void defaultsAndValidation() {
        assertFalse(new TournamentSettings().enabled);
        TournamentSettings settings = settings(true, 6);
        assertEquals(2, TournamentSettings.byeCount(6));
        assertEquals("", settings.startError(settings.order));
        settings.byes.clear();
        assertFalse(settings.startError(settings.order).isEmpty());
        settings.bestOf = 2; assertFalse(settings.valid());
        settings.bestOf = 7; settings.rounds = 101; assertFalse(settings.valid());
        settings.rounds = 1; settings.points = Arrays.asList(1, 2); assertFalse(settings.valid());
        settings.points = Arrays.asList(10, 0); assertTrue(settings.valid());
        assertFalse(settings.startError(Arrays.asList("Else")).isEmpty());
    }

    @Test void assignedByesAreHonoredAndDoNotAwardMatchWins() {
        TournamentSettings settings = settings(true, 6);
        settings.byes = Arrays.asList("P5", "P3");
        Tournament tournament = new Tournament(settings);
        List<String> winners = new ArrayList<>();
        for (Tournament.Match match : tournament.matches) if (match.complete) {
            winners.add(match.winner);
            assertEquals(0, match.leftWins + match.rightWins);
        }
        assertEquals(settings.byes, winners);
        assertEquals(Arrays.asList("P0", "P1"), tournament.participants());
        assertTrue(tournament.valid());
    }

    @Test void everyRosterSizeProducesOneChampionWithoutDuplicateParticipants() {
        for (int count = 2; count <= 64; count++) {
            Tournament tournament = new Tournament(settings(true, count));
            int played = 0;
            while (!tournament.complete) {
                assertEquals(2, tournament.participants().size());
                assertNotEquals(tournament.participants().get(0), tournament.participants().get(1));
                win(tournament, tournament.participants().get(0));
                assertTrue(tournament.valid());
                assertTrue(++played < 64);
            }
            assertEquals(count - 1, played);
            assertFalse(tournament.champion.isEmpty());
        }
    }

    @Test void bestOfUsesRaceWinsNotSeedsAndReplaysDraws() {
        TournamentSettings settings = settings(true, 2); settings.bestOf = 3;
        Tournament tournament = new Tournament(settings);
        RaceSequence race = race(tournament, 3);
        finish(race, "P0", 100); finish(race, "P1", 100);
        assertTrue(tournament.record(race));
        assertEquals(0, tournament.currentMatch().leftWins);
        assertFalse(tournament.record(race));
        win(tournament, "P0");
        assertFalse(tournament.complete);
        win(tournament, "P1");
        assertFalse(tournament.complete);
        win(tournament, "P0");
        assertTrue(tournament.complete);
        assertEquals("P0", tournament.champion);
        assertEquals(2, tournament.matches.get(0).leftWins);
    }

    @Test void pointsShareTiedPlacesAndGiveDnfAndUnplacedZero() {
        TournamentSettings settings = settings(false, 4); settings.rounds = 2;
        Tournament tournament = new Tournament(settings);
        RaceSequence race = race(tournament, 1);
        finish(race, "P0", 100); finish(race, "P1", 100);
        race.withdraw("P2"); race.runner("P3").stopped = true;
        assertTrue(tournament.record(race));
        assertEquals(Integer.valueOf(10), tournament.scores.get("P0"));
        assertEquals(Integer.valueOf(10), tournament.scores.get("P1"));
        assertEquals(Integer.valueOf(0), tournament.scores.get("P2"));
        assertEquals(Integer.valueOf(0), tournament.scores.get("P3"));
        assertEquals(1, tournament.completedRounds);
        assertEquals(1, tournament.place("P0")); assertEquals(1, tournament.place("P1")); assertEquals(3, tournament.place("P2"));
        assertFalse(tournament.record(race));
        race = race(tournament, 1);
        for (String name : tournament.participants()) race.withdraw(name);
        assertTrue(tournament.record(race));
        assertTrue(tournament.complete);
        assertEquals(Integer.valueOf(10), tournament.scores.get("P0"));
    }

    @Test void wrongRaceAndIncompleteResultsNeverScore() {
        Tournament tournament = new Tournament(settings(false, 2));
        RaceSequence race = race(tournament, 1);
        assertFalse(tournament.record(race));
        race.raceId = "wrong";
        race.withdraw("P0"); race.withdraw("P1");
        assertFalse(tournament.record(race));
        assertEquals(0, tournament.completedRounds);
        assertThrows(IllegalStateException.class, () -> tournament.beginRace("another"));
    }

    @Test void completedRaceScoresOnlyRequestedPlacesIncludingExactTies() {
        Tournament tournament = new Tournament(settings(false, 4));
        RaceSequence race = race(tournament, 1);
        race.finisherLimit = 1;
        finish(race, "P0", 100);
        finish(race, "P1", 100);
        finish(race, "P2", 101);
        race.withdraw("P3");
        assertTrue(tournament.record(race));
        assertEquals(Integer.valueOf(10), tournament.scores.get("P0"));
        assertEquals(Integer.valueOf(10), tournament.scores.get("P1"));
        assertEquals(Integer.valueOf(0), tournament.scores.get("P2"));
        assertEquals(Integer.valueOf(0), tournament.scores.get("P3"));
        assertTrue(race.runner("P2").stopped);
        assertTrue(race.publicCopy().valid(race.raceId));
        assertFalse(tournament.record(race));
    }

    @Test void roomWithdrawalForfeitsWholeBestOfAndHandlesBothAbsent() {
        TournamentSettings settings = settings(true, 4); settings.bestOf = 7;
        Tournament tournament = new Tournament(settings);
        RaceSequence race = race(tournament, 1);
        tournament.withdraw("P0"); race.withdraw("P0");
        race.finisherLimit = 1; race.awardForfeitWin();
        assertTrue(tournament.record(race));
        assertEquals("P1", tournament.matches.get(0).winner);
        tournament.withdraw("P2"); tournament.withdraw("P3");
        assertTrue(tournament.complete);
        assertEquals("P1", tournament.champion);
    }

    @Test void snapshotRoundTripKeepsScoresMatchesAndDuplicateProtection() {
        TournamentSettings settings = settings(true, 3); settings.bestOf = 3;
        Tournament tournament = new Tournament(settings);
        win(tournament, tournament.participants().get(1));
        Gson gson = new Gson();
        Tournament restored = gson.fromJson(gson.toJson(tournament), Tournament.class);
        assertTrue(restored.valid());
        assertEquals(tournament.status(), restored.status());
        assertEquals(tournament.lastRaceId, restored.lastRaceId);
        assertEquals(tournament.participants(), restored.participants());
        assertNotSame(settings, tournament.settings);
    }
}
