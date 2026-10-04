package zsgrooms.modid.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import zsgrooms.modid.OptimizationMeasurements;
import zsgrooms.modid.Tournament;
import java.util.List;

@EnabledIfSystemProperty(named = "zsgrooms.optimizationBenchmark", matches = "true")
class TournamentOptimizationBenchmark {
    @Test void comparePresentationDataCosts() {
        for (int players : new int[] {8, 64}) {
            Tournament t = TournamentViewCacheTest.tournament(players, true);
            int score = 0;
            for (String name : t.scores.keySet()) t.scores.put(name, score++ % 7);
            TournamentBracketLayout layout = new TournamentBracketLayout(players);
            TournamentViewCache cache = new TournamentViewCache();
            OptimizationMeasurements.compare("Bracket data, " + players + " players", 10000,
                    () -> bracket(t, layout, null), () -> bracket(t, layout, cache));
            OptimizationMeasurements.compare("Points data, " + players + " players", 10000,
                    () -> points(t, null), () -> points(t, cache));
        }
    }

    private long bracket(Tournament t, TournamentBracketLayout layout, TournamentViewCache cache) {
        long result = 0;
        for (int frame = 0; frame < 10000; frame++) {
            if (cache != null) cache.updateBracket(t);
            for (int r = 0; r < layout.rounds; r++) for (int i = 0; i < layout.count(r); i++) {
                // Main row plus the two participant lookups performed by the screen.
                for (int lookup = 0; lookup < 3; lookup++) {
                    Tournament.Match m = cache == null ? TournamentViewCacheTest.legacyMatch(t, r, i) : cache.match(r, i);
                    if (m != null) result += m.left.length();
                }
            }
        }
        return result;
    }

    private long points(Tournament t, TournamentViewCache cache) {
        long result = 0;
        for (int frame = 0; frame < 10000; frame++) {
            List<String> names = cache == null ? t.leaderboard() : cache.leaderboard(t);
            for (int i = 0; i < Math.min(8, names.size()); i++) result += cache == null ? t.place(names.get(i)) : cache.place(i);
        }
        return result;
    }
}
