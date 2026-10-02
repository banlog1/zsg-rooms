package zsgrooms.modid.replay;

/** Keep resets and assigned sequence stages together; apply retention to other seed changes. */
final class ReplaySeedRetention {
    enum Action { CONTINUE, SAVE, DISCARD }

    private long seed;
    private Long sequenceSeed;
    private boolean completed;

    ReplaySeedRetention(long seed) {
        this.seed = seed;
    }

    void completed() {
        completed = true;
    }

    void expectSequenceSeed(Long nextSeed) { sequenceSeed = nextSeed; }

    Action onReplacement(long nextSeed, boolean keepSeedChanges) {
        if (sequenceSeed != null && sequenceSeed == nextSeed) {
            seed = nextSeed;
            sequenceSeed = null;
            return Action.CONTINUE;
        }
        if (seed == nextSeed) return Action.CONTINUE;
        return completed || keepSeedChanges ? Action.SAVE : Action.DISCARD;
    }
}
