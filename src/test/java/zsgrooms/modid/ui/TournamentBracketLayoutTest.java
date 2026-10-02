package zsgrooms.modid.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TournamentBracketLayoutTest {
    @Test void allSupportedRostersHaveCompleteNonOverlappingBrackets() {
        for (int players = 2; players <= 64; players++) {
            TournamentBracketLayout layout = new TournamentBracketLayout(players);
            int matches = 0;
            assertTrue(layout.firstRoundMatches * 2 >= players);
            for (int round = 0; round < layout.rounds; round++) {
                matches += layout.count(round);
                assertTrue(layout.x(round) >= 0);
                assertTrue(layout.x(round) + TournamentBracketLayout.MATCH_WIDTH < layout.width());
                for (int i = 0; i < layout.count(round); i++) {
                    int y = layout.centerY(round, i);
                    assertTrue(y - TournamentBracketLayout.MATCH_HEIGHT / 2 >= 0);
                    assertTrue(y + TournamentBracketLayout.MATCH_HEIGHT / 2 < layout.height());
                    if (i > 0) assertTrue(y - layout.centerY(round, i - 1) > TournamentBracketLayout.MATCH_HEIGHT);
                    if (round > 0) assertEquals(y, (layout.centerY(round - 1, i * 2) + layout.centerY(round - 1, i * 2 + 1)) / 2);
                }
            }
            assertEquals(layout.firstRoundMatches * 2 - 1, matches);
            assertEquals(1, layout.count(layout.rounds - 1));
            assertEquals("Final", layout.title(layout.rounds - 1));
        }
    }
}
