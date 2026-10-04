package zsgrooms.modid.rng;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import zsgrooms.modid.OptimizationMeasurements;
import zsgrooms.modid.RngStandardization;
import java.util.*;

@EnabledIfSystemProperty(named = "zsgrooms.optimizationBenchmark", matches = "true")
class SpawnCounterOptimizationBenchmark {
    private static final int N = 200000;

    @Test void compareCounterCosts() {
        OptimizationMeasurements.compare("Spawn counter + position RNG (256 active chunks)", N,
                () -> workload(false), () -> workload(true));
    }

    private long workload(boolean optimized) {
        NaturalSpawnRngManager manager = new NaturalSpawnRngManager(123);
        LegacyManager legacy = new LegacyManager();
        long result = 0;
        for (int i = 0; i < N; i++) {
            int x = (i & 15) - 8, z = ((i >> 4) & 15) - 8;
            NaturalSpawnCycle cycle = optimized ? manager.nextCycle("minecraft:the_nether", 0, x, z)
                    : legacy.next(x, z);
            result += cycle.getPositionRandom().nextLong();
        }
        return result;
    }

    private static final class LegacyManager {
        final Map<NaturalSpawnSection, Long> counts = new HashMap<>();
        long generation = RngStandardization.getConfigurationGeneration();
        synchronized NaturalSpawnCycle next(int x, int z) {
            long current = RngStandardization.getConfigurationGeneration();
            if (current != generation) { counts.clear(); generation = current; }
            NaturalSpawnSection section = new NaturalSpawnSection("minecraft:the_nether", 0, x, z);
            Long previous = counts.get(section);
            long index = previous == null ? 0 : previous;
            counts.put(section, index + 1);
            return new NaturalSpawnCycle(123, section, index);
        }
    }
}
