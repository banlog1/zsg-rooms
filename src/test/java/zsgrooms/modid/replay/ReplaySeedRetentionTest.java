package zsgrooms.modid.replay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static zsgrooms.modid.replay.ReplaySeedRetention.Action.*;

class ReplaySeedRetentionTest {
    @Test void sequenceAdvancementAndResetsStayContinuousButUnrelatedSeedChangesDoNot() {
        ReplaySeedRetention retention = new ReplaySeedRetention(1);
        retention.expectSequenceSeed(2L);
        assertEquals(CONTINUE, retention.onReplacement(2, false));
        assertEquals(CONTINUE, retention.onReplacement(2, false));
        assertEquals(DISCARD, retention.onReplacement(3, false));
        retention.expectSequenceSeed(3L);
        retention.expectSequenceSeed(null);
        assertEquals(DISCARD, retention.onReplacement(3, false));
    }

    @Test void sameSeedResetsStayContinuousWithEitherSetting() {
        ReplaySeedRetention retention = new ReplaySeedRetention(Long.MIN_VALUE);
        assertEquals(CONTINUE, retention.onReplacement(Long.MIN_VALUE, false));
        assertEquals(CONTINUE, retention.onReplacement(Long.MIN_VALUE, true));
    }

    @Test void differentSeedDiscardsUnlessRetentionIsEnabled() {
        ReplaySeedRetention retention = new ReplaySeedRetention(12345L);
        assertEquals(DISCARD, retention.onReplacement(67890L, false));
        assertEquals(SAVE, retention.onReplacement(67890L, true));
        assertEquals(CONTINUE, retention.onReplacement(12345L, false));
    }

    @Test void completedRaceIsNeverDiscarded() {
        ReplaySeedRetention retention = new ReplaySeedRetention(12345L);
        retention.completed();
        assertEquals(SAVE, retention.onReplacement(67890L, false));
        assertEquals(CONTINUE, retention.onReplacement(12345L, false));
    }

    @Test void highSeedBitsAreNotIgnored() {
        assertEquals(DISCARD, new ReplaySeedRetention(1).onReplacement(1L + (1L << 48), false));
    }
}
