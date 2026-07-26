package zsgrooms.modid;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class BlazeSpawnerStandardization {
    static final int VANILLA_MIN_DELAY = 200;
    static final int VANILLA_MAX_DELAY = 800;
    static final int VANILLA_SPAWN_COUNT = 4;
    static final int VANILLA_MAX_NEARBY = 6;
    static final int VANILLA_PLAYER_RANGE = 16;
    static final int VANILLA_SPAWN_RANGE = 4;

    private static final Map<String, SpawnerState> STATES = new HashMap<String, SpawnerState>();

    private BlazeSpawnerStandardization() {
    }

    public static synchronized void reset() {
        STATES.clear();
    }

    public static String spawnerKey(String dimensionId, long packedBlockPos, String entityId) {
        return safe(dimensionId) + "|" + packedBlockPos + "|" + safe(entityId);
    }

    public static int standardizedSpawnerDelay(long worldSeed, String spawnerKey, long cycleIndex,
                                               int minDelay, int maxDelay) {
        if (maxDelay <= minDelay) {
            return minDelay;
        }
        Random random = new Random(RngStandardization.eventSeed(
                worldSeed, "blaze_spawner_delay", spawnerKey, cycleIndex));
        return minDelay + random.nextInt(maxDelay - minDelay);
    }

    public static long standardizedSpawnerAttemptSeed(long worldSeed, String spawnerKey, long cycleIndex,
                                                      long retryBatchIndex, int attemptIndex) {
        String eventKey = spawnerKey
                + "|cycle:" + cycleIndex
                + "|batch:" + retryBatchIndex;
        return RngStandardization.eventSeed(
                worldSeed, "blaze_spawner_position", eventKey, attemptIndex);
    }

    public static CandidatePosition standardizedSpawnerPosition(
            long worldSeed,
            String spawnerKey,
            long cycleIndex,
            long retryBatchIndex,
            int attemptIndex,
            int blockX,
            int blockY,
            int blockZ,
            int spawnRange
    ) {
        Random random = new Random(standardizedSpawnerAttemptSeed(
                worldSeed, spawnerKey, cycleIndex, retryBatchIndex, attemptIndex));
        double x = blockX + (random.nextDouble() - random.nextDouble()) * spawnRange + 0.5D;
        double y = blockY + random.nextInt(3) - 1;
        double z = blockZ + (random.nextDouble() - random.nextDouble()) * spawnRange + 0.5D;
        return new CandidatePosition(x, y, z, random);
    }

    public static QualificationResult qualificationResult(
            boolean standardizationEnabled,
            boolean serverWorld,
            boolean ownedBlockSpawner,
            boolean blazeEntity,
            boolean insideFortress,
            boolean hasExplicitPosition,
            boolean supportedSpawnData,
            boolean supportedPotentials,
            int minDelay,
            int maxDelay,
            int spawnCount,
            int maxNearbyEntities,
            int requiredPlayerRange,
            int spawnRange
    ) {
        if (!standardizationEnabled) {
            return QualificationResult.STANDARDISATION_DISABLED;
        }
        if (!serverWorld) {
            return QualificationResult.NOT_SERVER_WORLD;
        }
        if (!ownedBlockSpawner) {
            return QualificationResult.NOT_OWNED_BLOCK_SPAWNER;
        }
        if (!blazeEntity) {
            return QualificationResult.NOT_BLAZE;
        }
        if (!insideFortress) {
            return QualificationResult.NOT_INSIDE_FORTRESS;
        }
        if (hasExplicitPosition) {
            return QualificationResult.EXPLICIT_SPAWN_POSITION;
        }
        if (!supportedSpawnData) {
            return QualificationResult.UNSUPPORTED_SPAWN_DATA;
        }
        if (!supportedPotentials) {
            return QualificationResult.UNSUPPORTED_SPAWN_POTENTIALS;
        }
        if (minDelay != VANILLA_MIN_DELAY) {
            return QualificationResult.NON_VANILLA_MIN_DELAY;
        }
        if (maxDelay != VANILLA_MAX_DELAY) {
            return QualificationResult.NON_VANILLA_MAX_DELAY;
        }
        if (spawnCount != VANILLA_SPAWN_COUNT) {
            return QualificationResult.NON_VANILLA_SPAWN_COUNT;
        }
        if (maxNearbyEntities != VANILLA_MAX_NEARBY) {
            return QualificationResult.NON_VANILLA_NEARBY_LIMIT;
        }
        if (requiredPlayerRange != VANILLA_PLAYER_RANGE) {
            return QualificationResult.NON_VANILLA_PLAYER_RANGE;
        }
        if (spawnRange != VANILLA_SPAWN_RANGE) {
            return QualificationResult.NON_VANILLA_SPAWN_RANGE;
        }
        return QualificationResult.QUALIFIED;
    }

    public static boolean qualifies(
            boolean standardizationEnabled,
            boolean serverWorld,
            boolean ownedBlockSpawner,
            boolean blazeEntity,
            boolean insideFortress,
            boolean hasExplicitPosition,
            boolean supportedSpawnData,
            boolean supportedPotentials,
            int minDelay,
            int maxDelay,
            int spawnCount,
            int maxNearbyEntities,
            int requiredPlayerRange,
            int spawnRange
    ) {
        return qualificationResult(
                standardizationEnabled,
                serverWorld,
                ownedBlockSpawner,
                blazeEntity,
                insideFortress,
                hasExplicitPosition,
                supportedSpawnData,
                supportedPotentials,
                minDelay,
                maxDelay,
                spawnCount,
                maxNearbyEntities,
                requiredPlayerRange,
                spawnRange).isQualified();
    }

    public static boolean isDefaultBlazeSpawnData(String entityId, int tagSize) {
        return "minecraft:blaze".equals(entityId) && tagSize == 1;
    }

    public static boolean hasSupportedSpawnPotentials(
            int potentialCount,
            boolean solePotentialIsDefaultBlaze
    ) {
        return potentialCount == 0
                || (potentialCount == 1 && solePotentialIsDefaultBlaze);
    }

    public static synchronized SpawnerEvent currentEvent(String spawnerKey) {
        SpawnerState state = state(spawnerKey);
        return new SpawnerEvent(state.cycleIndex, state.retryBatchIndex);
    }

    public static synchronized SpawnerEvent advanceCycle(String spawnerKey) {
        SpawnerState state = state(spawnerKey);
        state.cycleIndex++;
        state.retryBatchIndex = 0L;
        return new SpawnerEvent(state.cycleIndex, state.retryBatchIndex);
    }

    public static synchronized SpawnerEvent advanceRetryBatch(String spawnerKey) {
        SpawnerState state = state(spawnerKey);
        state.retryBatchIndex++;
        return new SpawnerEvent(state.cycleIndex, state.retryBatchIndex);
    }

    private static SpawnerState state(String spawnerKey) {
        String key = safe(spawnerKey);
        SpawnerState state = STATES.get(key);
        if (state == null) {
            state = new SpawnerState();
            STATES.put(key, state);
        }
        return state;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public enum QualificationResult {
        QUALIFIED("qualified"),
        STANDARDISATION_DISABLED("standardisation_disabled"),
        NOT_SERVER_WORLD("not_server_world"),
        NOT_OWNED_BLOCK_SPAWNER("not_owned_block_spawner"),
        NOT_BLAZE("not_blaze"),
        NOT_INSIDE_FORTRESS("not_inside_fortress"),
        EXPLICIT_SPAWN_POSITION("explicit_spawn_position"),
        UNSUPPORTED_SPAWN_DATA("unsupported_spawn_data"),
        UNSUPPORTED_SPAWN_POTENTIALS("unsupported_spawn_potentials"),
        NON_VANILLA_MIN_DELAY("non_vanilla_min_delay"),
        NON_VANILLA_MAX_DELAY("non_vanilla_max_delay"),
        NON_VANILLA_SPAWN_COUNT("non_vanilla_spawn_count"),
        NON_VANILLA_NEARBY_LIMIT("non_vanilla_nearby_limit"),
        NON_VANILLA_PLAYER_RANGE("non_vanilla_player_range"),
        NON_VANILLA_SPAWN_RANGE("non_vanilla_spawn_range");

        private final String diagnosticReason;

        QualificationResult(String diagnosticReason) {
            this.diagnosticReason = diagnosticReason;
        }

        public boolean isQualified() {
            return this == QUALIFIED;
        }

        public String getDiagnosticReason() {
            return diagnosticReason;
        }
    }

    public static final class CandidatePosition {
        private final double x;
        private final double y;
        private final double z;
        private final Random attemptRandom;

        private CandidatePosition(double x, double y, double z, Random attemptRandom) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.attemptRandom = attemptRandom;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }

        public Random getAttemptRandom() {
            return attemptRandom;
        }
    }

    public static final class SpawnerEvent {
        private final long cycleIndex;
        private final long retryBatchIndex;

        private SpawnerEvent(long cycleIndex, long retryBatchIndex) {
            this.cycleIndex = cycleIndex;
            this.retryBatchIndex = retryBatchIndex;
        }

        public long getCycleIndex() {
            return cycleIndex;
        }

        public long getRetryBatchIndex() {
            return retryBatchIndex;
        }
    }

    private static final class SpawnerState {
        private long cycleIndex;
        private long retryBatchIndex;
    }
}
