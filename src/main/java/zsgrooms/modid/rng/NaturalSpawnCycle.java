package zsgrooms.modid.rng;

import zsgrooms.modid.RngStandardization;

import java.util.Random;

public final class NaturalSpawnCycle {
    private final long cycleSeed;
    private final NaturalSpawnSection section;
    private final long cycleIndex;
    private final Random positionRandom;
    private Random attemptCountRandom;
    private Random offsetRandom;
    private Random selectionRandom;
    private Random spawnCheckRandom;
    private int packIndex = -1;
    private int offsetRollCount;
    private boolean spawnCheckNeedsReset = true;
    private boolean currentAttemptFortress;

    NaturalSpawnCycle(long worldSeed, NaturalSpawnSection section, long cycleIndex) {
        this.section = section;
        this.cycleIndex = cycleIndex;
        this.cycleSeed = RngStandardization.naturalSpawnCycleSeed(worldSeed, section.seedKey(), cycleIndex);
        this.positionRandom = new Random(RngStandardization.naturalSpawnStreamSeed(this.cycleSeed, "position"));
    }

    public Random getPositionRandom() {
        return this.positionRandom;
    }

    public void beginPack() {
        this.packIndex++;
        this.offsetRollCount = 0;
        this.currentAttemptFortress = false;
        // Preserve resets for retained RNG references without constructing streams never used.
        if (this.attemptCountRandom != null) {
            resetPackStream(this.attemptCountRandom, "attempt_count");
        }
        if (this.offsetRandom != null) {
            resetPackStream(this.offsetRandom, "offset");
        }
        if (this.selectionRandom != null) {
            resetPackStream(this.selectionRandom, "selection");
        }
        this.spawnCheckNeedsReset = true;
    }

    private Random resetPackStream(Random random, String stream) {
        long seed = RngStandardization.naturalSpawnStreamSeed(this.cycleSeed, stream + "|pack=" + this.packIndex);
        if (random == null) {
            return new Random(seed);
        }
        random.setSeed(seed);
        return random;
    }

    public Random getAttemptCountRandom() {
        requireActivePack();
        if (this.attemptCountRandom == null) {
            this.attemptCountRandom = resetPackStream(null, "attempt_count");
        }
        return this.attemptCountRandom;
    }

    public Random getOffsetRandom() {
        requireActivePack();
        if (this.offsetRandom == null) {
            this.offsetRandom = resetPackStream(null, "offset");
        }
        return this.offsetRandom;
    }

    public int nextOffsetInt(int bound) {
        requireActivePack();
        if (this.offsetRollCount % 4 == 0) {
            this.currentAttemptFortress = false;
            this.spawnCheckNeedsReset = true;
        }
        int result = getOffsetRandom().nextInt(bound);
        this.offsetRollCount++;
        return result;
    }

    public Random getSelectionRandom() {
        requireActivePack();
        if (this.selectionRandom == null) {
            this.selectionRandom = resetPackStream(null, "selection");
        }
        return this.selectionRandom;
    }

    public Random getSpawnCheckRandom() {
        requireActivePack();
        // Earlier rejected attempts must not shift this attempt's restriction rolls.
        if (this.spawnCheckNeedsReset) {
            this.spawnCheckRandom = resetPackStream(this.spawnCheckRandom, "spawn_check|attempt=" + getAttemptIndex());
            this.spawnCheckNeedsReset = false;
        }
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
