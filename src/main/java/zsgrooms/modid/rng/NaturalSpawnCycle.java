package zsgrooms.modid.rng;

import zsgrooms.modid.RngStandardization;

import java.util.Random;

public final class NaturalSpawnCycle {
    private final long worldSeed;
    private final NaturalSpawnSection section;
    private final long cycleIndex;
    private final String sectionKey;
    private final Random positionRandom;
    private final Random attemptCountRandom;
    private final Random offsetRandom;
    private final Random selectionRandom;
    private final Random spawnCheckRandom;
    private int packIndex = -1;
    private int offsetRollCount;
    private boolean currentAttemptFortress;

    NaturalSpawnCycle(long worldSeed, NaturalSpawnSection section, long cycleIndex) {
        this.worldSeed = worldSeed;
        this.section = section;
        this.cycleIndex = cycleIndex;
        this.sectionKey = section.seedKey();
        this.positionRandom = stream(worldSeed, this.sectionKey, cycleIndex, "position");
        this.attemptCountRandom = new Random(0L);
        this.offsetRandom = new Random(0L);
        this.selectionRandom = new Random(0L);
        this.spawnCheckRandom = new Random(0L);
    }

    private static Random stream(
            long worldSeed,
            String sectionKey,
            long cycleIndex,
            String stream
    ) {
        return new Random(RngStandardization.naturalSpawnStreamSeed(
                worldSeed, sectionKey, cycleIndex, stream));
    }

    public Random getPositionRandom() {
        return this.positionRandom;
    }

    public void beginPack() {
        this.packIndex++;
        this.offsetRollCount = 0;
        this.currentAttemptFortress = false;
        resetPackStream(this.attemptCountRandom, "attempt_count");
        resetPackStream(this.offsetRandom, "offset");
        resetPackStream(this.selectionRandom, "selection");
        resetPackStream(this.spawnCheckRandom, "spawn_check");
    }

    private void resetPackStream(Random random, String stream) {
        random.setSeed(RngStandardization.naturalSpawnStreamSeed(
                this.worldSeed,
                this.sectionKey,
                this.cycleIndex,
                stream + "|pack=" + this.packIndex));
    }

    public Random getAttemptCountRandom() {
        requireActivePack();
        return this.attemptCountRandom;
    }

    public Random getOffsetRandom() {
        requireActivePack();
        return this.offsetRandom;
    }

    public int nextOffsetInt(int bound) {
        requireActivePack();
        if (this.offsetRollCount % 4 == 0) {
            this.currentAttemptFortress = false;
        }
        int result = this.offsetRandom.nextInt(bound);
        this.offsetRollCount++;
        return result;
    }

    public Random getSelectionRandom() {
        requireActivePack();
        return this.selectionRandom;
    }

    public Random getSpawnCheckRandom() {
        requireActivePack();
        return this.spawnCheckRandom;
    }

    public NaturalSpawnSection getSection() {
        return this.section;
    }

    public long getCycleIndex() {
        return this.cycleIndex;
    }

    public int getPackIndex() {
        return this.packIndex;
    }

    public int getAttemptIndex() {
        return this.offsetRollCount == 0 ? -1 : (this.offsetRollCount - 1) / 4;
    }

    public boolean isCurrentAttemptFortress() {
        return this.currentAttemptFortress;
    }

    public void setCurrentAttemptFortress(boolean currentAttemptFortress) {
        this.currentAttemptFortress = currentAttemptFortress;
    }

    private void requireActivePack() {
        if (this.packIndex < 0) {
            throw new IllegalStateException("Natural spawn pack has not started");
        }
    }

}
