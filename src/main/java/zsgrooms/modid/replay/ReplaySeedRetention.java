package zsgrooms.modid.replay;

/** A recording belongs to one actual world seed, independent of room/filter metadata. */
final class ReplaySeedRetention {
    enum Action { CONTINUE, SAVE, DISCARD }

    private final long seed;
    private boolean completed;

    ReplaySeedRetention(long seed) {
        this.seed = seed;
    }

    void completed() {
        completed = true;
    }

    Action onReplacement(long nextSeed, boolean keepSeedChanges) {
        if (seed == nextSeed) return Action.CONTINUE;
        return completed || keepSeedChanges ? Action.SAVE : Action.DISCARD;
    }
}
