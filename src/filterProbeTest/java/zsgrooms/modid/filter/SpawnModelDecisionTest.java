package zsgrooms.modid.filter;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class SpawnModelDecisionTest {
    @Test void separatesClearAndBoundaryCases() {
        assertEquals(SpawnModelDecision.Decision.PASS, decide(16, 32, 16));
        assertEquals(SpawnModelDecision.Decision.VERIFY, decide(17, 32, 16));
        assertEquals(SpawnModelDecision.Decision.VERIFY, decide(48, 32, 16));
        assertEquals(SpawnModelDecision.Decision.REJECT, decide(49, 32, 16));
        assertEquals(SpawnModelDecision.Decision.VERIFY, decide(0, 32, 64));
    }

    @Test void supportsAlternativeAnchorsAndNegativePositions() {
        assertEquals(SpawnModelDecision.Decision.PASS, SpawnModelDecision.classify(new BlockPos(-80, 200, -80),
                Arrays.asList(BlockPos.ORIGIN, new BlockPos(-90, 0, -90)), 32, 16));
        assertEquals(SpawnModelDecision.Decision.REJECT, SpawnModelDecision.classify(BlockPos.ORIGIN, Collections.emptyList(), 32, 16));
        assertEquals(4294967295L, SpawnModelDecision.error(new BlockPos(Integer.MIN_VALUE, 0, 0), new BlockPos(Integer.MAX_VALUE, 0, 0)));
    }

    @Test void validatesDistances() {
        assertThrows(IllegalArgumentException.class, () -> decide(0, 32, -1));
        assertThrows(IllegalArgumentException.class, () -> decide(0, -1, 16));
    }

    private static SpawnModelDecision.Decision decide(int distance, int radius, int margin) {
        return SpawnModelDecision.classify(new BlockPos(distance, 64, 0), Collections.singleton(BlockPos.ORIGIN), radius, margin);
    }
}
