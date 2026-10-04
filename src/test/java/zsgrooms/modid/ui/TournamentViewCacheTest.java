package zsgrooms.modid.ui;

import org.junit.jupiter.api.Test;
import zsgrooms.modid.Tournament;
import zsgrooms.modid.TournamentSettings;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TournamentViewCacheTest {
    static Tournament tournament(int count, boolean bracket) {
        TournamentSettings settings = new TournamentSettings();
        settings.bracket = bracket;
        for (int i = 0; i < count; i++) settings.order.add("Runner" + i);
        return new Tournament(settings);
    }

    static Tournament.Match legacyMatch(Tournament t, int round, int index) {
        for (Tournament.Match m : t.matches) if (m.round == round + 1 && index-- == 0) return m;
        return null;
    }

    private void check(TournamentViewCache cache, Tournament t) {
        cache.updateBracket(t);
        for (int round = -1; round < 7; round++) for (int i = 0; i < 33; i++)
            assertSame(legacyMatch(t, round, i), cache.match(round, i));
        List<String> names = cache.leaderboard(t);
        assertEquals(t.leaderboard(), names);
        for (int i = 0; i < names.size(); i++) assertEquals(t.place(names.get(i)), cache.place(i));
    }

    @Test void structuralEditsAndSnapshotReplacementInvalidateIndex() {
        TournamentViewCache cache = new TournamentViewCache();
        Tournament t = tournament(64, true);
        check(cache, t);
        Collections.reverse(t.matches); check(cache, t);
        t.matches.get(0).round = 3; check(cache, t);
        t.matches.set(1, tournament(2, true).matches.get(0)); check(cache, t);
        t.matches.add(tournament(2, true).matches.get(0)); check(cache, t);
        t.matches.remove(0); check(cache, t);
        check(cache, tournament(64, true));
        t.matches.clear(); check(cache, t);
    }

    @Test void liveResultsDoNotRequireStructuralRebuild() {
        TournamentViewCache cache = new TournamentViewCache();
        Tournament t = tournament(8, true);
        check(cache, t);
        Tournament.Match match = t.matches.get(0);
        match.leftWins = 2; match.complete = true; match.winner = match.left;
        cache.updateBracket(t);
        assertSame(match, cache.match(0, 0));
        assertEquals(2, cache.match(0, 0).leftWins);
        assertTrue(cache.match(0, 0).complete);
        assertEquals(match.left, cache.match(0, 0).winner);
    }

    @Test void tiesScoreChangesAndRosterChangesRetainCompetitionRanks() {
        TournamentViewCache cache = new TournamentViewCache();
        Tournament t = tournament(64, false);
        Random random = new Random(71);
        for (int i = 0; i < 400; i++) {
            t.scores.put("Runner" + random.nextInt(64), random.nextInt(8));
            check(cache, t);
            assertSame(cache.leaderboard(t), cache.leaderboard(t));
        }
        t.scores.remove("Runner0"); t.scores.put("NewRunner", 3); check(cache, t);
        t.scores.clear(); check(cache, t);
        check(cache, tournament(2, false));
    }
}
