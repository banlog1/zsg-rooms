package zsgrooms.modid;

import java.util.Random;

/** Per-location opportunities, independent of world age, other sources, and mutable fuel layout. */
final class WoodLightingSequence {
    private static final double LOG_MISS = StrictMath.log1p(-1.0D / 4096.0D);
    private final long seed;
    private final String key;
    private long lavaEvent;
    private long fireEvent;
    private long selectionEvent;
    private long selectionsRemaining;
    private int fireTicksRemaining;

    WoodLightingSequence(long seed, String key) {
        this.seed = seed;
        this.key = key;
        this.selectionsRemaining = nextSelectionWait();
        this.fireTicksRemaining = fireDelay(0);
    }

    int advanceLava(int randomTickSpeed) {
        if (randomTickSpeed <= 0) {
            return 0;
        }
        selectionsRemaining -= randomTickSpeed;
        int opportunities = 0;
        while (selectionsRemaining <= 0) {
            opportunities++;
            selectionsRemaining += nextSelectionWait();
        }
        return opportunities;
    }

    Random nextLavaRandom() {
        return random("lava_ignition", lavaEvent++);
    }

    boolean advanceFire() {
        return --fireTicksRemaining <= 0;
    }

    long beginFireEvent() {
        long index = fireEvent++;
        fireTicksRemaining = fireDelay(fireEvent);
        return index;
    }

    int remainingFireTicks() {
        return Math.max(1, fireTicksRemaining);
    }

    void restartFireDelay() {
        fireTicksRemaining = fireDelay(fireEvent);
    }

    Random fireRandom(long event) {
        return random("fire_state", event);
    }

    Random targetRandom(String channel, String targetKey, long event) {
        return new Random(RngStandardization.woodLightingSeed(seed, channel, key + ">" + targetKey, event));
    }

    private Random random(String channel, long event) {
        return new Random(RngStandardization.woodLightingSeed(seed, channel, key, event));
    }

    private int fireDelay(long event) {
        return 30 + random("fire_delay", event).nextInt(10);
    }

    private long nextSelectionWait() {
        // Vanilla selects randomTickSpeed positions from a 4096-block section per tick.
        // Independent geometric gaps preserve each source's marginal rate, including repeat hits.
        double sample = random("lava_schedule", selectionEvent++).nextDouble();
        return 1L + (long) StrictMath.floor(StrictMath.log1p(-sample) / LOG_MISS);
    }
}
