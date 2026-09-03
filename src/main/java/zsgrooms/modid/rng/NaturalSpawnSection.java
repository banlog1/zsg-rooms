package zsgrooms.modid.rng;

import java.util.Objects;

public final class NaturalSpawnSection {
    private final String dimension;
    private final int spawnGroup;
    private final int chunkX;
    private final int chunkZ;

    public NaturalSpawnSection(String dimension, int spawnGroup, int chunkX, int chunkZ) {
        this.dimension = dimension;
        this.spawnGroup = spawnGroup;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    String seedKey() {
        return this.dimension + "|" + this.spawnGroup + "|"
                + this.chunkX + "|" + this.chunkZ;
    }

    public int getChunkX() {
        return this.chunkX;
    }

    public int getChunkZ() {
        return this.chunkZ;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NaturalSpawnSection)) {
            return false;
        }
        NaturalSpawnSection section = (NaturalSpawnSection) other;
        return this.spawnGroup == section.spawnGroup
                && this.chunkX == section.chunkX
                && this.chunkZ == section.chunkZ
                && this.dimension.equals(section.dimension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.dimension, this.spawnGroup, this.chunkX, this.chunkZ);
    }
}
