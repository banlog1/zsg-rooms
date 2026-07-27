package zsgrooms.modid;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StriderJockeyControlTest {
    @AfterEach
    public void disableControl() {
        StriderJockeyControl.configure(false);
    }

    @Test
    public void featureDefaultsToDisabled() {
        assertFalse(StriderJockeyControl.isEnabled());
    }

    @Test
    public void configureEnablesAndDisablesControl() {
        StriderJockeyControl.configure(true);
        assertTrue(StriderJockeyControl.isEnabled());

        StriderJockeyControl.configure(false);
        assertFalse(StriderJockeyControl.isEnabled());
    }

    @Test
    public void successfulFirstRollConsumesOnlyTheOneInThirtyRoll() {
        CountingRandom random = new CountingRandom(0);

        StriderJockeyControl.JockeyOutcome outcome =
                StriderJockeyControl.consumeVanillaSelectionRolls(random);

        assertEquals(StriderJockeyControl.JockeyOutcome.NO_JOCKEY, outcome);
        assertEquals(Arrays.asList(30), random.getBounds());
    }

    @Test
    public void failedFirstRollAlsoConsumesTheOneInTenRoll() {
        CountingRandom random = new CountingRandom(1, 0);

        StriderJockeyControl.JockeyOutcome outcome =
                StriderJockeyControl.consumeVanillaSelectionRolls(random);

        assertEquals(StriderJockeyControl.JockeyOutcome.NO_JOCKEY, outcome);
        assertEquals(Arrays.asList(30, 10), random.getBounds());
    }

    @Test
    public void everySelectionRollPathProducesNoJockey() {
        assertEquals(
                StriderJockeyControl.JockeyOutcome.NO_JOCKEY,
                StriderJockeyControl.consumeVanillaSelectionRolls(new CountingRandom(0)));
        assertEquals(
                StriderJockeyControl.JockeyOutcome.NO_JOCKEY,
                StriderJockeyControl.consumeVanillaSelectionRolls(new CountingRandom(1, 0)));
        assertEquals(
                StriderJockeyControl.JockeyOutcome.NO_JOCKEY,
                StriderJockeyControl.consumeVanillaSelectionRolls(new CountingRandom(1, 1)));
    }

    private static final class CountingRandom extends Random {
        private final int[] results;
        private final List<Integer> bounds = new ArrayList<Integer>();
        private int index;

        private CountingRandom(int... results) {
            this.results = results;
        }

        @Override
        public int nextInt(int bound) {
            bounds.add(bound);
            return results[index++];
        }

        private List<Integer> getBounds() {
            return bounds;
        }
    }
}
