package zsgrooms.modid.filter;

import net.minecraft.util.math.BlockPos;

/** A gameplay tolerance, not a mathematical bound on the model's error. */
final class SpawnModelDecision {
    enum Decision { PASS, VERIFY, REJECT }

    static Decision classify(BlockPos prediction, Iterable<BlockPos> anchors, int radius, int margin) {
        if (radius < 0 || margin < 0) throw new IllegalArgumentException("Negative spawn distance");
        long closest = Long.MAX_VALUE;
        for (BlockPos anchor : anchors) closest = Math.min(closest, error(prediction, anchor));
        if (closest <= (long)radius - margin) return Decision.PASS;
        if (closest > (long)radius + margin) return Decision.REJECT;
        return Decision.VERIFY;
    }

    static long error(BlockPos first, BlockPos second) {
        return Math.max(Math.abs((long)first.getX() - second.getX()), Math.abs((long)first.getZ() - second.getZ()));
    }
}
