package zsgrooms.modid.rng;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import zsgrooms.modid.RngStandardization;

import java.util.HashMap;
import java.util.Map;

public final class NaturalSpawnRngManager {
    private final long worldSeed;
    private final Map<NaturalSpawnSection, Long> cycleCounts =
            new HashMap<NaturalSpawnSection, Long>();
    private long configurationGeneration;

    public NaturalSpawnRngManager(long worldSeed) {
        this.worldSeed = worldSeed;
        this.configurationGeneration = RngStandardization.getConfigurationGeneration();
    }

    public synchronized NaturalSpawnCycle nextCycle(
            Identifier dimension,
            SpawnGroup spawnGroup,
            ChunkPos chunkPos
    ) {
        return nextCycle(
                dimension.toString(), spawnGroup.ordinal(), chunkPos.x, chunkPos.z);
    }

    synchronized NaturalSpawnCycle nextCycle(
            String dimension,
            int spawnGroup,
            int chunkX,
            int chunkZ
    ) {
        resetIfConfigurationChanged();
        NaturalSpawnSection section = new NaturalSpawnSection(
                dimension, spawnGroup, chunkX, chunkZ);
        Long previousCount = this.cycleCounts.get(section);
        long cycleIndex = previousCount == null ? 0L : previousCount;
        this.cycleCounts.put(section, cycleIndex + 1L);
        return new NaturalSpawnCycle(this.worldSeed, section, cycleIndex);
    }

    private void resetIfConfigurationChanged() {
        long currentGeneration = RngStandardization.getConfigurationGeneration();
        if (this.configurationGeneration == currentGeneration) {
            return;
        }
        this.configurationGeneration = currentGeneration;
        this.cycleCounts.clear();
    }
}
