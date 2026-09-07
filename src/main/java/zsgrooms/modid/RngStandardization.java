package zsgrooms.modid;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class RngStandardization {
    private static final int DRAGON_PERCH_GRACE_TICKS = 1300;
    private static final int DRAGON_OPENING_HEIGHT_TICKS = 1300;
    private static final Map<String, Long> MOB_DROP_COUNTS = new HashMap<String, Long>();
    private static final Map<String, Long> UNBREAKING_COUNTS = new HashMap<String, Long>();
    private static volatile boolean enabled;
    private static volatile boolean boostedBarters;
    private static volatile boolean reduceZeroCycleFlyAways;
    private static volatile int dragonPerchGraceTicks = DRAGON_PERCH_GRACE_TICKS;
    private static long barterCount;
    private static long eyeBreakCount;
    private static long gravelFlintCount;
    private static long dragonPerchCount;
    private static long dragonOpeningHeightCount;
    private static long configurationGeneration;

    private RngStandardization() {
    }

    public static synchronized void configure(boolean standardize) {
        configure(standardize, false);
    }

    public static synchronized void configure(boolean standardize, boolean boostBarters) {
        configure(standardize, boostBarters, false);
    }

    public static synchronized void configure(boolean standardize, boolean boostBarters, boolean reduceFlyAways) {
        enabled = standardize;
        boostedBarters = boostBarters;
        reduceZeroCycleFlyAways = reduceFlyAways;
        MOB_DROP_COUNTS.clear();
        UNBREAKING_COUNTS.clear();
        barterCount = 0L;
        eyeBreakCount = 0L;
        gravelFlintCount = 0L;
        dragonPerchCount = 0L;
        dragonOpeningHeightCount = 0L;
        configurationGeneration++;
        dragonPerchGraceTicks = DRAGON_PERCH_GRACE_TICKS;
        BlazeSpawnerStandardization.reset();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isDragonPerchStandardizationActive(int dragonAge) {
        return enabled && dragonAge >= dragonPerchGraceTicks;
    }

    static int dragonPerchGraceTicks() {
        return dragonPerchGraceTicks;
    }

    public static synchronized void setDragonPerchGraceTicksForTesting(int graceTicks) {
        if (graceTicks < 0) {
            throw new IllegalArgumentException("Dragon perch grace ticks must be non-negative");
        }
        dragonPerchGraceTicks = graceTicks;
    }

    public static boolean areBartersBoosted() {
        return boostedBarters;
    }

    public static float nextDragonOpeningHeightRoll(Random vanillaRandom, int dragonAge, long worldSeed) {
        // Always advance the native stream once; do not reseed any other flight decisions.
        float result = vanillaRandom.nextFloat();
        if (dragonAge < 0 || dragonAge >= DRAGON_OPENING_HEIGHT_TICKS) {
            return result;
        }
        if (enabled) {
            result = new Random(nextDragonOpeningHeightSeed(worldSeed)).nextFloat();
        }
        // Vanilla multiplies this roll by 20. The optional assist restricts that offset to [0, 15).
        return reduceZeroCycleFlyAways ? result * 0.75F : result;
    }

    static synchronized long nextDragonOpeningHeightSeed(long worldSeed) {
        return eventSeed(worldSeed, "dragon_opening_height", "global", dragonOpeningHeightCount++);
    }

    public static synchronized long getConfigurationGeneration() {
        return configurationGeneration;
    }

    public static synchronized long nextMobDropSeed(MobEntity mob) {
        ServerWorld world = (ServerWorld) mob.world;
        Identifier entityId = Registry.ENTITY_TYPE.getId(mob.getType());
        String key = world.getRegistryKey().getValue() + "|" + entityId;
        return nextMobDropSeed(world.getSeed(), key);
    }

    static synchronized long nextMobDropSeed(long worldSeed, String key) {
        long eventIndex = MOB_DROP_COUNTS.containsKey(key) ? MOB_DROP_COUNTS.get(key) : 0L;
        MOB_DROP_COUNTS.put(key, eventIndex + 1L);
        return eventSeed(worldSeed, "mob_drop", key, eventIndex);
    }

    public static synchronized Random nextPiglinBarterRandom(ServerWorld world) {
        return new Random(nextPiglinBarterSeed(world.getSeed()));
    }

    static synchronized long nextPiglinBarterSeed(long worldSeed) {
        long eventIndex = barterCount++;
        return eventSeed(worldSeed, "piglin_barter", "global", eventIndex);
    }

    public static synchronized Random nextEyeBreakRandom(ServerWorld world) {
        return new Random(nextEyeBreakSeed(world.getSeed()));
    }

    static synchronized long nextEyeBreakSeed(long worldSeed) {
        long eventIndex = eyeBreakCount++;
        return eventSeed(worldSeed, "eye_break", "global", eventIndex);
    }

    public static synchronized Random nextGravelFlintRandom(ServerWorld world) {
        return new Random(nextGravelFlintSeed(world.getSeed()));
    }

    static synchronized long nextGravelFlintSeed(long worldSeed) {
        long eventIndex = gravelFlintCount++;
        return eventSeed(worldSeed, "gravel_flint", "global", eventIndex);
    }

    public static synchronized Random nextUnbreakingRandom(
            ServerWorld world,
            ItemStack stack
    ) {
        String key = Registry.ITEM.getId(stack.getItem()).toString();
        return new Random(nextUnbreakingSeed(world.getSeed(), key));
    }

    static synchronized long nextUnbreakingSeed(long worldSeed, String key) {
        long eventIndex = UNBREAKING_COUNTS.containsKey(key)
                ? UNBREAKING_COUNTS.get(key)
                : 0L;
        UNBREAKING_COUNTS.put(key, eventIndex + 1L);
        return eventSeed(worldSeed, "unbreaking", key, eventIndex);
    }

    public static synchronized Random nextDragonPerchRandom(ServerWorld world) {
        return new Random(nextDragonPerchSeed(world.getSeed()));
    }

    public static int nextDragonPerchRoll(
            Random vanillaRandom,
            int bound,
            int dragonAge,
            long worldSeed
    ) {
        int vanillaResult = vanillaRandom.nextInt(bound);
        if (!isDragonPerchStandardizationActive(dragonAge)) {
            return vanillaResult;
        }
        return new Random(nextDragonPerchSeed(worldSeed)).nextInt(bound);
    }

    static synchronized long nextDragonPerchSeed(long worldSeed) {
        long eventIndex = dragonPerchCount++;
        return eventSeed(worldSeed, "dragon_perch", "global", eventIndex);
    }

    static long eventSeed(long worldSeed, String channel, String key, long eventIndex) {
        long hash = 0xcbf29ce484222325L ^ worldSeed;
        hash = hashString(hash, channel);
        hash = hashString(hash, key);
        hash ^= eventIndex * 0x9e3779b97f4a7c15L;
        return mix64(hash);
    }

    public static long woodLightingSeed(long worldSeed, String channel, String localKey, long eventIndex) {
        return eventSeed(worldSeed, "wood_light_" + channel, localKey, eventIndex);
    }

    public static long naturalSpawnStreamSeed(
            long worldSeed,
            String sectionKey,
            long cycleIndex,
            String stream
    ) {
        return naturalSpawnStreamSeed(naturalSpawnCycleSeed(worldSeed, sectionKey, cycleIndex), stream);
    }

    public static long naturalSpawnCycleSeed(long worldSeed, String sectionKey, long cycleIndex) {
        return eventSeed(worldSeed, "natural_spawn_cycle", sectionKey, cycleIndex);
    }

    public static long naturalSpawnStreamSeed(long cycleSeed, String stream) {
        return eventSeed(cycleSeed, "natural_spawn_stream", stream, 0L);
    }

    private static long hashString(long hash, String value) {
        String safeValue = value == null ? "" : value;
        for (int i = 0; i < safeValue.length(); i++) {
            hash ^= safeValue.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }
}
