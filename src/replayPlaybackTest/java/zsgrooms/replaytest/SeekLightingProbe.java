package zsgrooms.replaytest;

import net.minecraft.client.MinecraftClient;
import net.minecraft.world.chunk.light.LightingProvider;

/** Rejected experiment: whole-batch deferral changes settled light arrays. Development only. */
public final class SeekLightingProbe {
    private static final boolean ENABLED = Boolean.getBoolean("zsgrooms.viewerSeekProfile");
    private static final boolean BATCH = Boolean.getBoolean("zsgrooms.viewerSeekBatchLights");
    private static final boolean PRODUCTION = Boolean.getBoolean("zsgrooms.viewerSeekFast");
    private static boolean active;
    private static long lightStart;
    static long drains, deferred, nanos, batches;

    public static void begin() { if (ENABLED) { active = true; batches++; } }
    public static boolean light() {
        if (!ENABLED || !active || PRODUCTION) return false;
        if (BATCH) { deferred++; return true; }
        drains++; lightStart = System.nanoTime();
        return false;
    }
    public static void lit() {
        if (ENABLED && active && !BATCH && !PRODUCTION) nanos += System.nanoTime() - lightStart;
    }
    public static void end() {
        if (!ENABLED || !active) return;
        try {
            if (BATCH) {
                long start = System.nanoTime();
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.world != null) {
                    LightingProvider provider = client.world.getLightingProvider();
                    drains++;
                    while (provider.hasUpdates()) provider.doLightUpdates(Integer.MAX_VALUE, true, true);
                }
                nanos += System.nanoTime() - start;
            }
        } finally { active = false; }
    }
    static void reset() { drains = deferred = nanos = batches = 0; }
    public static void productionDeferred(boolean value) { if (ENABLED && PRODUCTION && value) deferred++; }
    public static void productionDrainStart() {
        if (ENABLED && PRODUCTION) { drains++; lightStart = System.nanoTime(); }
    }
    public static void productionDrainEnd() {
        if (ENABLED && PRODUCTION) nanos += System.nanoTime() - lightStart;
    }
}
