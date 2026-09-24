package zsgrooms.modid.replay;

/** Client-thread association only; never reads container inventories or generates loot. */
final class ReplayChestCapture {
    private int opening;
    private Candidate pending;

    void interaction(long time, int world, String dimension, long first, long second, int stateFirst, int stateSecond) {
        pending = new Candidate(time, world, dimension, first, second, stateFirst, stateSecond);
    }

    void clear() { pending = null; }

    void opened(ReplayRaceManifest manifest, long time, int world, int syncId, int slots) {
        int ordinal = ++opening;
        Candidate candidate = pending;
        pending = null;
        if (candidate == null || candidate.world != world || time < candidate.time || time - candidate.time > 5000
                || slots != (candidate.first == candidate.second ? 27 : 54)) return;
        manifest.recordChest(time, ordinal, world, syncId, candidate.dimension, candidate.first, candidate.second,
                candidate.stateFirst, candidate.stateSecond);
    }

    private static final class Candidate {
        final long time, first, second;
        final int world, stateFirst, stateSecond;
        final String dimension;

        Candidate(long time, int world, String dimension, long first, long second, int stateFirst, int stateSecond) {
            this.time = time;
            this.world = world;
            this.dimension = dimension;
            this.first = first;
            this.second = second;
            this.stateFirst = stateFirst;
            this.stateSecond = stateSecond;
        }
    }
}
