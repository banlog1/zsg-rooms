package zsgrooms.modid.rng;

import net.minecraft.util.math.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
            Map<Long, ChunkPos> order = FortressSpawnOrder.orderedTargets(slots, ChunkPos::toLong);
            assertEquals(expected, slots.stream().map(pos -> order.get(pos.toLong()).toLong()).collect(Collectors.toList()));
            assertEquals(3, new HashSet<ChunkPos>(order.values()).size());
            assertFalse(order.containsKey(new ChunkPos(50, 50).toLong()));
        }
    }

    @Test
    public void noEligibleChunksMeansNoReassignment() {
        assertTrue(FortressSpawnOrder.orderedTargets(Collections.<ChunkPos>emptyList(), ChunkPos::toLong).isEmpty());
    }

    @Test
    public void directMappingMatchesPreviousCoordinateOrderingWithoutChangingInput() {
        Random random = new Random(1849L);
        List<ChunkPos> positions = new ArrayList<ChunkPos>();
        positions.add(new ChunkPos(Integer.MIN_VALUE, Integer.MAX_VALUE));
        positions.add(new ChunkPos(Integer.MAX_VALUE, Integer.MIN_VALUE));
        for (int i = 0; i < 100; i++) {
            positions.add(new ChunkPos(random.nextInt(), random.nextInt()));
        }
        for (int trial = 0; trial < 100; trial++) {
            Collections.shuffle(positions, random);
            List<ChunkPos> original = new ArrayList<ChunkPos>(positions);
            List<ChunkPos> expected = new ArrayList<ChunkPos>(positions);
            expected.sort(Comparator.comparingInt((ChunkPos pos) -> pos.x).thenComparingInt(pos -> pos.z));
            Map<Long, ChunkPos> order = FortressSpawnOrder.orderedTargets(positions, ChunkPos::toLong);
            for (int i = 0; i < positions.size(); i++) {
                assertSame(expected.get(i), order.get(positions.get(i).toLong()));
            }
            assertEquals(original, positions);
        }
    }
}
