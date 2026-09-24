package zsgrooms.modid.replay;

/** A saved file or an individual finish is not permission to reveal unopened loot. */
final class ReplayPredictionPolicy {
    private final long seed;
    private boolean room;
    private boolean released;

    ReplayPredictionPolicy(long seed, boolean room) { this.seed = seed; this.room = room; }
    void joinedRoom() { room = true; released = false; }
    void matchEnded() { released = true; }
    void disconnected(boolean finished) { if (finished && !room) released = true; }
    Long releasedSeed() { return released ? seed : null; }
}
