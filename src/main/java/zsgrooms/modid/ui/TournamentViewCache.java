package zsgrooms.modid.ui;

import zsgrooms.modid.Tournament;
import java.util.*;

/** Indexes presentation data while leaving live match results on the tournament objects. */
final class TournamentViewCache {
    private final List<Tournament.Match> snapshot = new ArrayList<>();
    private final List<Integer> rounds = new ArrayList<>();
    private final Map<Integer, List<Tournament.Match>> matches = new HashMap<>();
    private final Map<String, Integer> scores = new HashMap<>();
    private List<String> names = Collections.emptyList();
    private int[] places = new int[0];

    void updateBracket(Tournament tournament) {
        boolean same = snapshot.size() == tournament.matches.size();
        for (int i = 0; same && i < snapshot.size(); i++) {
            Tournament.Match match = tournament.matches.get(i);
            same = snapshot.get(i) == match && rounds.get(i) == match.round;
        }
        if (same) return;
        snapshot.clear();
        rounds.clear();
        matches.clear();
        for (Tournament.Match match : tournament.matches) {
            snapshot.add(match);
            rounds.add(match.round);
            matches.computeIfAbsent(match.round, r -> new ArrayList<>()).add(match);
        }
    }

    Tournament.Match match(int round, int index) {
        List<Tournament.Match> entries = matches.get(round + 1);
        return entries == null || index < 0 || index >= entries.size() ? null : entries.get(index);
    }

    List<String> leaderboard(Tournament tournament) {
        if (!scores.equals(tournament.scores)) {
            scores.clear();
            scores.putAll(tournament.scores);
            names = tournament.leaderboard();
            places = new int[names.size()];
            for (int i = 0; i < names.size(); i++) {
                places[i] = i > 0 && scores.get(names.get(i)).equals(scores.get(names.get(i - 1)))
                        ? places[i - 1] : i + 1;
            }
        }
        return names;
    }

    int place(int row) { return places[row]; }
}
