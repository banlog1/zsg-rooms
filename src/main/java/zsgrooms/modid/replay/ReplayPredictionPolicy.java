package zsgrooms.modid.replay;

/** A saved file or an individual finish is not permission to reveal unopened loot. */
final class ReplayPredictionPolicy {
    private final long seed;
    private boolean room;
    private boolean released;
    private boolean mixedSeeds;

    ReplayPredictionPolicy(long seed, boolean room) { this.seed = seed; this.room = room; }
    void joinedRoom() { room = true; released = false; }
    void matchEnded() { released = true; }
    void disconnected(boolean finished) { if (finished && !room) released = true; }
    void worldSeed(long nextSeed) { if (nextSeed != seed) mixedSeeds = true; }
    // The legacy prediction field describes one seed for the entire file.
    Long releasedSeed() { return released && !mixedSeeds ? seed : null; }
}
