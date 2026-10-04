package zsgrooms.modid.rng;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import zsgrooms.modid.RngStandardization;

import java.util.HashMap;
import java.util.Map;

public final class NaturalSpawnRngManager {
    private final long worldSeed;
    private final Map<String, Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<SectionCounter>>> cycleCounts =
            new HashMap<>();
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
        Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<SectionCounter>> groups = this.cycleCounts.get(dimension);
        if (groups == null) {
            groups = new Int2ObjectOpenHashMap<>();
            this.cycleCounts.put(dimension, groups);
        }
        Long2ObjectOpenHashMap<SectionCounter> chunks = groups.get(spawnGroup);
        if (chunks == null) {
            chunks = new Long2ObjectOpenHashMap<>();
            groups.put(spawnGroup, chunks);
        }
        long key = ChunkPos.toLong(chunkX, chunkZ);
        SectionCounter counter = chunks.get(key);
        if (counter == null) {
            counter = new SectionCounter(new NaturalSpawnSection(dimension, spawnGroup, chunkX, chunkZ));
            chunks.put(key, counter);
        }
        return new NaturalSpawnCycle(this.worldSeed, counter.section, counter.nextCycle++);
    }

    private static final class SectionCounter {
        final NaturalSpawnSection section;
        long nextCycle;

        SectionCounter(NaturalSpawnSection section) { this.section = section; }
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
