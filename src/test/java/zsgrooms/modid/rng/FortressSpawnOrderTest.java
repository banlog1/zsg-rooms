package zsgrooms.modid.rng;

import net.minecraft.util.math.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class FortressSpawnOrderTest {
    @Test
    public void shuffledEligibleSlotsDispatchEachChunkOnceInCoordinateOrder() {
        ChunkPos a = new ChunkPos(-3, 5);
        ChunkPos b = new ChunkPos(2, -1);
        ChunkPos c = new ChunkPos(2, 4);
        List<Long> expected = Arrays.asList(a.toLong(), b.toLong(), c.toLong());
        for (List<ChunkPos> slots : Arrays.asList(Arrays.asList(c, a, b), Arrays.asList(b, c, a))) {
            Map<Long, Long> order = FortressSpawnOrder.orderedTargets(slots);
            assertEquals(expected, slots.stream().map(pos -> order.get(pos.toLong())).collect(Collectors.toList()));
            assertEquals(3, new HashSet<Long>(order.values()).size());
            assertFalse(order.containsKey(new ChunkPos(50, 50).toLong()));
        }
    }

    @Test
    public void noEligibleChunksMeansNoReassignment() {
        assertTrue(FortressSpawnOrder.orderedTargets(Collections.emptyList()).isEmpty());
    }
}
