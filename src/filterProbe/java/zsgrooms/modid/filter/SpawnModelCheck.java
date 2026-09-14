package zsgrooms.modid.filter;

import net.minecraft.util.math.BlockPos;
import java.util.Arrays;
import java.util.function.Supplier;

/** Native spawn is lazy and memoized; model positions never change the world's spawn. */
final class SpawnModelCheck {
    static final int MARGIN = 16;
    private final BlockPos prediction;
    private final Supplier<BlockPos> nativeSpawn;
    private BlockPos actual;
    int modelPasses, modelRejections, nativeRequests;

    SpawnModelCheck(BlockPos prediction, Supplier<BlockPos> nativeSpawn) {
        this.prediction = prediction;
        this.nativeSpawn = nativeSpawn;
    }

    boolean within(int radius, BlockPos... anchors) {
        if (actual == null && prediction != null) {
            SpawnModelDecision.Decision decision = SpawnModelDecision.classify(prediction, Arrays.asList(anchors), radius, MARGIN);
            if (decision == SpawnModelDecision.Decision.PASS) { modelPasses++; return true; }
            if (decision == SpawnModelDecision.Decision.REJECT) { modelRejections++; return false; }
        }
        return SpawnModelDecision.classify(actual(), Arrays.asList(anchors), radius, 0) == SpawnModelDecision.Decision.PASS;
    }

    boolean canReachPoolArea(BlockPos anchor) {
        if (prediction == null || actual != null) return PoolSpawnChecks.canReachPoolArea(actual(), anchor);
        // Broader necessary envelope; do not reject using unverified lake-attempt coordinates.
        return within(128, anchor);
    }

    void prepareExactMode() {
        if (prediction == null) actual();
    }

    private BlockPos actual() {
        if (actual == null) { actual = nativeSpawn.get(); nativeRequests++; }
        return actual;
    }
}
