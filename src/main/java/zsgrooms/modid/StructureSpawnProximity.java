package zsgrooms.modid;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.ChunkRandom;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.StructureConfig;
import net.minecraft.world.gen.feature.StructureFeature;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class StructureSpawnProximity {
    static final int MIN_TARGET_DISTANCE = 70;
    static final int MAX_TARGET_DISTANCE = 128;
    static final int RELOCATION_THRESHOLD = 140;
    private static final int SEARCH_RADIUS_CHUNKS = 160;
    private static final int MAX_RUINED_PORTAL_CANDIDATES = 4;
    static final int MAX_TERRAIN_CHUNKS = 8;
    private static final int MAX_CHUNK_CANDIDATE_ATTEMPTS = 32;
    private static final int SPAWN_CACHE_SIZE = 32;
    private static final Map<SpawnCacheKey, BlockPos> SPAWN_CACHE =
            new LinkedHashMap<SpawnCacheKey, BlockPos>(SPAWN_CACHE_SIZE + 1, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<SpawnCacheKey, BlockPos> eldest) {
                    return size() > SPAWN_CACHE_SIZE;
                }
            };

    private static volatile boolean enabled;
    private static volatile boolean minimumNearbyAnimalsEnabled;
    private static volatile boolean preparedSpawn;
    private static String filterId = "";

    private StructureSpawnProximity() {
    }

    public static synchronized void configure(boolean shouldEnable, String selectedFilter) {
        configure(shouldEnable, false, selectedFilter);
    }

    public static synchronized void configure(
            boolean shouldEnable, boolean shouldGuaranteeAnimals, String selectedFilter) {
        enabled = shouldEnable;
        minimumNearbyAnimalsEnabled = shouldGuaranteeAnimals;
        preparedSpawn = false;
        filterId = ZsgSeedBridge.normalizeSeedType(selectedFilter);
    }

    public static synchronized void prepare(ServerWorld world) {
        preparedSpawn = false;
        StructureFeature<?> structure = structureForFilter(filterId);
        if ((!enabled && !minimumNearbyAnimalsEnabled) || world == null || structure == null) {
            return;
        }

        BlockPos originalSpawn = world.getSpawnPos();
        SpawnCacheKey cacheKey = new SpawnCacheKey(world.getSeed(), filterId);
        BlockPos cachedSpawn = SPAWN_CACHE.get(cacheKey);
        if (cachedSpawn != null && !minimumNearbyAnimalsEnabled) {
            BlockPos refreshedSpawn = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(cachedSpawn.getX(), 0, cachedSpawn.getZ()));
            if (isSafeSpawn(world, refreshedSpawn)) {
                world.setSpawnPos(refreshedSpawn);
                preparedSpawn = true;
                SeedDebugLog.info("Reused cached {} room spawn at {}", filterId, refreshedSpawn);
                return;
            }
            SPAWN_CACHE.remove(cacheKey);
        }

        long locateStarted = System.nanoTime();
        LocatedTarget located;
        try {
            located = locateTarget(world, structure, originalSpawn, filterId);
        } catch (RuntimeException exception) {
            ZsgRooms.LOGGER.warn("Could not locate the {} spawn structure: {}", filterId, exception.getMessage());
            return;
        }
        if (located == null || located.pos == null) {
            ZsgRooms.LOGGER.warn("No {} structure was found near the original spawn", filterId);
            return;
        }
        BlockPos target = located.pos;
        long locateMillis = elapsedMillis(locateStarted);

        if (minimumNearbyAnimalsEnabled) {
            StructureAnimalGuarantee.ensure(world, target, filterId);
        }
        if (!enabled) {
            return;
        }

        if (cachedSpawn != null) {
            BlockPos refreshedSpawn = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(cachedSpawn.getX(), 0, cachedSpawn.getZ()));
            if (isSafeSpawn(world, refreshedSpawn)) {
                world.setSpawnPos(refreshedSpawn);
                preparedSpawn = true;
                SeedDebugLog.info("Reused cached {} room spawn at {} after checking nearby animals",
                        filterId, refreshedSpawn);
                return;
            }
            SPAWN_CACHE.remove(cacheKey);
        }

        long originalDistanceSquared = horizontalDistanceSquared(originalSpawn, target);
        if (originalDistanceSquared <= (long) RELOCATION_THRESHOLD * RELOCATION_THRESHOLD) {
            SeedDebugLog.info("Keeping original spawn; {} is already {} blocks away (located in {} ms{})",
                    filterId, Math.round(Math.sqrt(originalDistanceSquared)), locateMillis, located.verificationLog());
            return;
        }

        long terrainSearchStarted = System.nanoTime();
        SafeSpawnResult result = findSafeSpawn(world, target, originalSpawn, filterId);
        if (result.spawn == null) {
            ZsgRooms.LOGGER.warn("Could not find a safe surface near the {} structure after checking {} chunks",
                    filterId, result.chunksChecked);
            return;
        }
        BlockPos safeSpawn = result.spawn;
        world.setSpawnPos(safeSpawn);
        SPAWN_CACHE.put(cacheKey, safeSpawn);
        preparedSpawn = true;
        SeedDebugLog.info("Moved room spawn from {} to {} ({} blocks from {}); locate: {} ms{}, "
                        + "terrain: {} ms across {} chunks",
                originalSpawn, safeSpawn, Math.round(Math.sqrt(horizontalDistanceSquared(safeSpawn, target))),
                filterId, locateMillis, located.verificationLog(),
                elapsedMillis(terrainSearchStarted), result.chunksChecked);
    }

    private static LocatedTarget locateTarget(
            ServerWorld world, StructureFeature<?> structure, BlockPos origin, String selectedFilter) {
        if (!"rpseedbank".equals(ZsgSeedBridge.normalizeSeedType(selectedFilter))) {
            return new LocatedTarget(
                    world.locateStructure(structure, origin, SEARCH_RADIUS_CHUNKS, false), 0, false);
        }
        return locateGeneratedRuinedPortal(world, origin);
    }

    private static LocatedTarget locateGeneratedRuinedPortal(ServerWorld world, BlockPos origin) {
        ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
        StructureConfig config = generator.getConfig().method_28600(StructureFeature.RUINED_PORTAL);
        if (config == null) {
            return null;
        }

        int spacing = config.getSpacing();
        int originChunkX = origin.getX() >> 4;
        int originChunkZ = origin.getZ() >> 4;
        int rejected = 0;
        Set<Long> testedChunks = new LinkedHashSet<Long>();
        ChunkRandom random = new ChunkRandom();

        for (int radius = 0; radius <= SEARCH_RADIUS_CHUNKS; radius++) {
            for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    if (radius > 0 && Math.abs(offsetX) != radius && Math.abs(offsetZ) != radius) {
                        continue;
                    }
                    ChunkPos candidate = StructureFeature.RUINED_PORTAL.method_27218(
                            config,
                            world.getSeed(),
                            random,
                            originChunkX + spacing * offsetX,
                            originChunkZ + spacing * offsetZ);
                    if (!testedChunks.add(candidate.toLong())) {
                        continue;
                    }

                    Chunk chunk = world.getChunk(candidate.x, candidate.z, ChunkStatus.STRUCTURE_STARTS);
                    StructureStart<?> start = world.getStructureAccessor().getStructureStart(
                            ChunkSectionPos.from(candidate, 0), StructureFeature.RUINED_PORTAL, chunk);
                    if (start == null || !start.hasChildren()) {
                        continue;
                    }
                    BlockPos chestPos = generateAndLocateRuinedPortalChest(world, start);
                    if (chestPos != null) {
                        SeedDebugLog.info("Verified Ruined Portal Seedbank Looting chest at {}", chestPos);
                        return new LocatedTarget(chestPos, rejected, true);
                    }

                    rejected++;
                    SeedDebugLog.warn("Rejected ruined portal candidate without the seedbank Looting chest at {}",
                            start.getPos());
                    if (rejected >= MAX_RUINED_PORTAL_CANDIDATES) {
                        ZsgRooms.LOGGER.warn("Stopped ruined portal verification after {} false candidates", rejected);
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private static BlockPos generateAndLocateRuinedPortalChest(ServerWorld world, StructureStart<?> start) {
        BlockBox bounds = start.getBoundingBox();
        int minimumChunkX = bounds.minX >> 4;
        int maximumChunkX = bounds.maxX >> 4;
        int minimumChunkZ = bounds.minZ >> 4;
        int maximumChunkZ = bounds.maxZ >> 4;
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                world.getChunk(chunkX, chunkZ, ChunkStatus.FEATURES);
            }
        }
        if (!RuinedPortalGenerationTracker.wasGenerated(world, bounds)) {
            return null;
        }
        return RuinedPortalGenerationTracker.findGeneratedChest(world, bounds);
    }

    public static boolean hasPreparedSpawn() {
        return enabled && preparedSpawn;
    }

    static StructureFeature<?> structureForFilter(String selectedFilter) {
        String structureKey = structureKeyForFilter(selectedFilter);
        if ("buried_treasure".equals(structureKey)) {
            return StructureFeature.BURIED_TREASURE;
        }
        if ("village".equals(structureKey)) {
            return StructureFeature.VILLAGE;
        }
        if ("shipwreck".equals(structureKey)) {
            return StructureFeature.SHIPWRECK;
        }
        if ("desert_pyramid".equals(structureKey)) {
            return StructureFeature.DESERT_PYRAMID;
        }
        if ("jungle_pyramid".equals(structureKey)) {
            return StructureFeature.JUNGLE_PYRAMID;
        }
        if ("ruined_portal".equals(structureKey)) {
            return StructureFeature.RUINED_PORTAL;
        }
        return null;
    }

    static String structureKeyForFilter(String selectedFilter) {
        String normalized = ZsgSeedBridge.normalizeSeedType(selectedFilter);
        if ("zsg".equals(normalized) || "zsgop".equals(normalized)) return "buried_treasure";
        if ("zsgvillage".equals(normalized) || "zsgvillageop".equals(normalized)) return "village";
        if ("zsgshipwreck".equals(normalized) || "zsgshipwreckop".equals(normalized)) return "shipwreck";
        if ("zsgtemple".equals(normalized) || "zsgtempleop".equals(normalized)) return "desert_pyramid";
        if ("zsgjungletemple".equals(normalized) || "zsgjungletempleop".equals(normalized)) return "jungle_pyramid";
        if ("rpseedbank".equals(normalized)) return "ruined_portal";
        return null;
    }

    static int targetDistance(long worldSeed, String selectedFilter) {
        Random random = deterministicRandom(worldSeed, selectedFilter);
        return MIN_TARGET_DISTANCE + random.nextInt(MAX_TARGET_DISTANCE - MIN_TARGET_DISTANCE + 1);
    }

    static long horizontalDistanceSquared(BlockPos first, BlockPos second) {
        long x = (long) first.getX() - second.getX();
        long z = (long) first.getZ() - second.getZ();
        return x * x + z * z;
    }

    static long[] candidateChunkKeys(long worldSeed, String selectedFilter, BlockPos target, BlockPos originalSpawn) {
        List<ChunkCandidate> candidates = buildChunkCandidates(worldSeed, selectedFilter, target, originalSpawn);
        long[] keys = new long[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            keys[i] = candidates.get(i).key;
        }
        return keys;
    }

    private static SafeSpawnResult findSafeSpawn(
            ServerWorld world, BlockPos target, BlockPos originalSpawn, String selectedFilter) {
        List<ChunkCandidate> candidates = buildChunkCandidates(world.getSeed(), selectedFilter, target, originalSpawn);
        int chunksChecked = 0;
        for (ChunkCandidate candidate : candidates) {
            chunksChecked++;
            BlockPos safeSpawn;
            try {
                safeSpawn = findSafeSpawnInChunk(world, target, candidate);
            } catch (RuntimeException exception) {
                SeedDebugLog.warn("Could not inspect terrain chunk {}, {} for {}: {}",
                        candidate.chunkX, candidate.chunkZ, selectedFilter, exception.getMessage());
                continue;
            }
            if (safeSpawn != null) {
                return new SafeSpawnResult(safeSpawn, chunksChecked);
            }
        }
        return new SafeSpawnResult(null, chunksChecked);
    }

    private static List<ChunkCandidate> buildChunkCandidates(
            long worldSeed, String selectedFilter, BlockPos target, BlockPos originalSpawn) {
        Random random = deterministicRandom(worldSeed, selectedFilter);
        int preferredDistance = MIN_TARGET_DISTANCE
                + random.nextInt(MAX_TARGET_DISTANCE - MIN_TARGET_DISTANCE + 1);
        long towardSpawnX = (long) originalSpawn.getX() - target.getX();
        long towardSpawnZ = (long) originalSpawn.getZ() - target.getZ();
        double startingAngle = towardSpawnX == 0L && towardSpawnZ == 0L
                ? random.nextDouble() * Math.PI * 2.0D
                : Math.atan2(towardSpawnZ, towardSpawnX);
        startingAngle += (random.nextDouble() - 0.5D) * (Math.PI / 6.0D);
        double angleStep = Math.PI * (3.0D - Math.sqrt(5.0D));

        List<ChunkCandidate> candidates = new ArrayList<ChunkCandidate>(MAX_TERRAIN_CHUNKS);
        Set<Long> seenChunks = new LinkedHashSet<Long>();
        int[] distanceOffsets = new int[]{0, 12, -12, 24, -24};
        for (int attempt = 0;
                attempt < MAX_CHUNK_CANDIDATE_ATTEMPTS && candidates.size() < MAX_TERRAIN_CHUNKS;
                attempt++) {
            int distanceOffset = distanceOffsets[attempt % distanceOffsets.length];
            int distance = clamp(preferredDistance + distanceOffset, MIN_TARGET_DISTANCE, MAX_TARGET_DISTANCE);
            double angle = startingAngle + angleStep * attempt;
            int preferredX = target.getX() + (int) Math.round(Math.cos(angle) * distance);
            int preferredZ = target.getZ() + (int) Math.round(Math.sin(angle) * distance);
            int chunkX = preferredX >> 4;
            int chunkZ = preferredZ >> 4;
            long key = chunkKey(chunkX, chunkZ);
            if (seenChunks.add(key)) {
                candidates.add(new ChunkCandidate(key, chunkX, chunkZ, preferredX, preferredZ));
            }
        }
        return candidates;
    }

    private static BlockPos findSafeSpawnInChunk(ServerWorld world, BlockPos target, ChunkCandidate candidate) {
        world.getChunk(candidate.chunkX, candidate.chunkZ);
        int startX = candidate.chunkX << 4;
        int startZ = candidate.chunkZ << 4;
        long minimumDistanceSquared = (long) MIN_TARGET_DISTANCE * MIN_TARGET_DISTANCE;
        long maximumDistanceSquared = (long) MAX_TARGET_DISTANCE * MAX_TARGET_DISTANCE;
        long bestScore = Long.MAX_VALUE;
        BlockPos best = null;

        for (int x = startX; x < startX + 16; x++) {
            for (int z = startZ; z < startZ + 16; z++) {
                long targetX = (long) x - target.getX();
                long targetZ = (long) z - target.getZ();
                long targetDistanceSquared = targetX * targetX + targetZ * targetZ;
                if (targetDistanceSquared < minimumDistanceSquared || targetDistanceSquared > maximumDistanceSquared) {
                    continue;
                }

                long preferredX = (long) x - candidate.preferredX;
                long preferredZ = (long) z - candidate.preferredZ;
                long score = preferredX * preferredX + preferredZ * preferredZ;
                if (score >= bestScore) {
                    continue;
                }

                BlockPos surface = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                        new BlockPos(x, 0, z));
                if (isSafeSpawn(world, surface)) {
                    best = surface.toImmutable();
                    bestScore = score;
                }
            }
        }
        return best;
    }

    static boolean isSafeSpawn(ServerWorld world, BlockPos feet) {
        if (feet.getY() <= 4 || feet.getY() >= world.getDimensionHeight() - 2
                || !world.isAir(feet) || !world.isAir(feet.up())
                || !world.getFluidState(feet).isEmpty() || !world.getFluidState(feet.up()).isEmpty()) {
            return false;
        }
        BlockState floor = world.getBlockState(feet.down());
        Block block = floor.getBlock();
        return floor.getMaterial().blocksMovement()
                && floor.getFluidState().isEmpty()
                && block != Blocks.CACTUS
                && block != Blocks.MAGMA_BLOCK
                && block != Blocks.CAMPFIRE
                && block != Blocks.FIRE;
    }

    private static Random deterministicRandom(long worldSeed, String selectedFilter) {
        long filterHash = selectedFilter == null ? 0L : selectedFilter.hashCode();
        long mixed = worldSeed ^ filterHash * 0x9e3779b97f4a7c15L;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        return new Random(mixed);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    private static final class ChunkCandidate {
        private final long key;
        private final int chunkX;
        private final int chunkZ;
        private final int preferredX;
        private final int preferredZ;

        private ChunkCandidate(long key, int chunkX, int chunkZ, int preferredX, int preferredZ) {
            this.key = key;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.preferredX = preferredX;
            this.preferredZ = preferredZ;
        }
    }

    private static final class SafeSpawnResult {
        private final BlockPos spawn;
        private final int chunksChecked;

        private SafeSpawnResult(BlockPos spawn, int chunksChecked) {
            this.spawn = spawn;
            this.chunksChecked = chunksChecked;
        }
    }

    private static final class LocatedTarget {
        private final BlockPos pos;
        private final int rejectedCandidates;
        private final boolean portalVerified;

        private LocatedTarget(BlockPos pos, int rejectedCandidates, boolean portalVerified) {
            this.pos = pos;
            this.rejectedCandidates = rejectedCandidates;
            this.portalVerified = portalVerified;
        }

        private String verificationLog() {
            if (!this.portalVerified) {
                return "";
            }
            return ", seedbank Looting chest verified, rejected " + this.rejectedCandidates + " false candidates";
        }
    }

    private static final class SpawnCacheKey {
        private final long worldSeed;
        private final String filter;

        private SpawnCacheKey(long worldSeed, String filter) {
            this.worldSeed = worldSeed;
            this.filter = filter == null ? "" : filter;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SpawnCacheKey)) return false;
            SpawnCacheKey key = (SpawnCacheKey) other;
            return this.worldSeed == key.worldSeed && this.filter.equals(key.filter);
        }

        @Override
        public int hashCode() {
            return 31 * Long.hashCode(this.worldSeed) + this.filter.hashCode();
        }
    }
}
