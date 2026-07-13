package zsgrooms.modid;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class RngStandardization {
    private static final Map<String, Long> MOB_DROP_COUNTS = new HashMap<String, Long>();
    private static volatile boolean enabled;
    private static volatile boolean boostedBarters;
    private static long barterCount;

    private RngStandardization() {
    }

    public static synchronized void configure(boolean standardize) {
        configure(standardize, false);
    }

    public static synchronized void configure(boolean standardize, boolean boostBarters) {
        enabled = standardize;
        boostedBarters = boostBarters;
        MOB_DROP_COUNTS.clear();
        barterCount = 0L;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean areBartersBoosted() {
        return boostedBarters;
    }

    public static synchronized long nextMobDropSeed(MobEntity mob) {
        ServerWorld world = (ServerWorld) mob.world;
        Identifier entityId = Registry.ENTITY_TYPE.getId(mob.getType());
        String key = world.getRegistryKey().getValue() + "|" + entityId;
        long eventIndex = MOB_DROP_COUNTS.containsKey(key) ? MOB_DROP_COUNTS.get(key) : 0L;
        MOB_DROP_COUNTS.put(key, eventIndex + 1L);
        return eventSeed(world.getSeed(), "mob_drop", key, eventIndex);
    }

    public static synchronized Random nextPiglinBarterRandom(ServerWorld world) {
        long eventIndex = barterCount++;
        return new Random(eventSeed(world.getSeed(), "piglin_barter", "global", eventIndex));
    }

    static long eventSeed(long worldSeed, String channel, String key, long eventIndex) {
        long hash = 0xcbf29ce484222325L ^ worldSeed;
        hash = hashString(hash, channel);
        hash = hashString(hash, key);
        hash ^= eventIndex * 0x9e3779b97f4a7c15L;
        return mix64(hash);
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
