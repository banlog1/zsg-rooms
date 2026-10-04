package zsgrooms.modid.rng;

import org.junit.jupiter.api.Test;
import zsgrooms.modid.RngStandardization;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SpawnCounterOptimizationTest {
    @Test void mixedSectionsRetainOriginalSequencesAndReset() {
        NaturalSpawnRngManager manager = new NaturalSpawnRngManager(-7192789L);
        Map<NaturalSpawnSection, Long> counts = new HashMap<>();
        int[] coordinates = {0, -1, 1, Integer.MIN_VALUE, Integer.MAX_VALUE, -53, 6};
        Random schedule = new Random(17);
        try {
            for (int i = 0; i < 20000; i++) {
                if (i == 10000) {
                    RngStandardization.configure(false, false);
                    counts.clear();
                }
                String dimension = i % 2 == 0 ? "minecraft:the_nether" : "minecraft:overworld";
                int group = schedule.nextInt(8);
                int x = coordinates[schedule.nextInt(coordinates.length)];
                int z = coordinates[schedule.nextInt(coordinates.length)];
                NaturalSpawnSection section = new NaturalSpawnSection(dimension, group, x, z);
                long index = counts.getOrDefault(section, 0L);
                counts.put(section, index + 1);
                NaturalSpawnCycle expected = new NaturalSpawnCycle(-7192789L, section, index);
                NaturalSpawnCycle actual = manager.nextCycle(dimension, group, x, z);
                assertEquals(index, actual.getCycleIndex());
                assertEquals(expected.getPositionRandom().nextLong(), actual.getPositionRandom().nextLong());
                for (int pack = 0; pack < 3; pack++) {
                    expected.beginPack(); actual.beginPack();
                    assertEquals(expected.getAttemptCountRandom().nextLong(), actual.getAttemptCountRandom().nextLong());
                    assertEquals(expected.getSelectionRandom().nextLong(), actual.getSelectionRandom().nextLong());
                    assertEquals(expected.nextOffsetInt(6), actual.nextOffsetInt(6));
                    assertEquals(expected.getSpawnCheckRandom().nextLong(), actual.getSpawnCheckRandom().nextLong());
                }
            }
        } finally { RngStandardization.configure(false, false); }
    }

    @Test void retainedCycleDoesNotChangeWhenSectionIsReused() {
        NaturalSpawnRngManager manager = new NaturalSpawnRngManager(123);
        NaturalSpawnCycle first = manager.nextCycle("nether", 0, 5, -7);
        NaturalSpawnCycle second = manager.nextCycle("nether", 0, 5, -7);
        assertNotSame(first, second);
        assertEquals(0, first.getCycleIndex());
        assertEquals(1, second.getCycleIndex());
        assertEquals(new NaturalSpawnCycle(123, new NaturalSpawnSection("nether", 0, 5, -7), 0)
                .getPositionRandom().nextLong(), first.getPositionRandom().nextLong());
    }
}
