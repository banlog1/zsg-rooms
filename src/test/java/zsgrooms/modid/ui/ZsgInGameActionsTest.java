package zsgrooms.modid.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ZsgInGameActionsTest {
    @Test
    public void exitPortalResultDisplaysFinalIgt() {
        assertEquals("Final IGT: 12:34.567",
                ZsgInGameActions.matchResultSubtitle("Beat the seed in 12:34.567 IGT"));
        assertEquals("Seed completed", ZsgInGameActions.matchResultSubtitle("Beat the seed"));
    }

    @Test
    public void nonPortalResultDoesNotDisplayATime() {
        assertEquals("Runner forfeited", ZsgInGameActions.matchResultSubtitle("Runner forfeited"));
        assertEquals("Runner left the match", ZsgInGameActions.matchResultSubtitle("Runner left the match"));
    }
}
