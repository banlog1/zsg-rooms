package zsgrooms.modid.filter;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class BastionLayoutChecksTest {
    private static final String BASE = "minecraft:bastion/hoglin_stable/";

    @Test
    void stablesNeedsBothGoodGapAndTripleRampart() {
        assertTrue(BastionLayoutChecks.accepts(Arrays.asList(BASE + "walls/side_wall_1", BASE + "ramparts/ramparts_1")));
        assertFalse(BastionLayoutChecks.accepts(Arrays.asList(BASE + "walls/side_wall_0", BASE + "ramparts/ramparts_1")));
        assertFalse(BastionLayoutChecks.accepts(Arrays.asList(BASE + "walls/side_wall_1", BASE + "ramparts/ramparts_2")));
        assertFalse(BastionLayoutChecks.accepts(Arrays.asList(BASE + "walls/side_wall_0", BASE + "ramparts/ramparts_3")));
    }

    @Test
    void otherBastionsDoNotNeedStablesPieces() {
        assertTrue(BastionLayoutChecks.accepts(Collections.singletonList("minecraft:bastion/bridge/starting_pieces/entrance")));
        assertFalse(BastionLayoutChecks.accepts(Collections.emptyList()));
    }

    @Test
    void usesExactTemplateNamesNotSimilarPrefixes() {
        assertFalse(BastionLayoutChecks.accepts(Arrays.asList(BASE + "walls/side_wall_10", BASE + "ramparts/ramparts_1")));
        assertFalse(BastionLayoutChecks.accepts(Arrays.asList(BASE + "walls/side_wall_1", BASE + "ramparts/ramparts_10")));
    }
}
