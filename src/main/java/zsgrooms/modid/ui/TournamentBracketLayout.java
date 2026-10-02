package zsgrooms.modid.ui;

/** Stable bracket geometry, including rounds which have not been scheduled yet. */
final class TournamentBracketLayout {
    static final int MATCH_WIDTH = 144, MATCH_HEIGHT = 38, COLUMN = 180, ROW = 58, PADDING = 12;
    final int rounds;
    final int firstRoundMatches;

    TournamentBracketLayout(int players) {
        int slots = 2;
        while (slots < Math.min(64, players)) slots *= 2;
        firstRoundMatches = slots / 2;
        rounds = Integer.numberOfTrailingZeros(slots);
    }

    int count(int round) { return firstRoundMatches >> round; }
    int x(int round) { return PADDING + round * COLUMN; }
    int centerY(int round, int match) { return PADDING + ((2 * match + 1) * (1 << round) * ROW) / 2; }
    int width() { return PADDING * 2 + (rounds - 1) * COLUMN + MATCH_WIDTH; }
    int height() { return PADDING * 2 + firstRoundMatches * ROW; }
    String title(int round) {
        int remaining = rounds - round;
        return remaining == 1 ? "Final" : remaining == 2 ? "Semifinals" : remaining == 3 ? "Quarterfinals" : "Round " + (round + 1);
    }
}
