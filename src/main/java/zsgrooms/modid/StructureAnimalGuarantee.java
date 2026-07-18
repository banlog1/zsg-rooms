package zsgrooms.modid;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.RabbitEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class StructureAnimalGuarantee {
    static final int RADIUS = 70;
    static final int MINIMUM_ANIMALS = 3;
    static final int MAX_TERRAIN_CHUNKS = 6;
    private static final int MINIMUM_STRUCTURE_DISTANCE = 12;
    private static final int MAX_POSITION_ATTEMPTS = 48;
    private static final int MAX_SPAWN_POSITIONS = 12;
    private static final int MINIMUM_ANIMAL_SPACING = 6;
    private static final List<EntityType<? extends AnimalEntity>> ELIGIBLE_TYPES = Arrays.asList(
            EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN, EntityType.RABBIT);

    private StructureAnimalGuarantee() {
    }

    public static void ensure(ServerWorld world, BlockPos structurePos, String selectedFilter) {
        if (world == null || structurePos == null) {
            return;
        }

        long started = System.nanoTime();
        Random random = deterministicRandom(world.getSeed(), structurePos, selectedFilter);
        SearchResult search = findSpawnPositions(world, structurePos, random);
        Box area = new Box(
                structurePos.getX() - RADIUS, 0, structurePos.getZ() - RADIUS,
                structurePos.getX() + RADIUS + 1, world.getDimensionHeight(), structurePos.getZ() + RADIUS + 1);
        int existing = world.getEntities(AnimalEntity.class, area,
                animal -> isEligible(animal) && isInsideRadius(animal, structurePos)).size();
        int missing = missingAnimals(existing);
        int spawned = 0;

        for (BlockPos pos : search.positions) {
            if (spawned >= missing) {
                break;
            }
            EntityType<? extends AnimalEntity> type = chooseNaturalType(world, pos, random);
            if (type == null) {
                continue;
            }
            AnimalEntity animal = type.create(world);
            if (animal == null) {
                continue;
            }
            animal.refreshPositionAndAngles(pos, random.nextFloat() * 360.0F, 0.0F);
            if (!world.doesNotCollide(animal)) {
                animal.remove();
                continue;
            }
            animal.initialize(world, world.getLocalDifficulty(pos), SpawnReason.CHUNK_GENERATION, null, null);
            animal.setPersistent();
            if (world.spawnEntity(animal)) {
                spawned++;
            }
        }

        int total = existing + spawned;
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        if (total >= MINIMUM_ANIMALS) {
            SeedDebugLog.info("Guaranteed {} eligible animals within {} blocks of {} "
                            + "({} existing, {} added, {} terrain chunks, {} ms)",
                    total, RADIUS, selectedFilter, existing, spawned, search.chunksLoaded, elapsedMillis);
        } else {
            ZsgRooms.LOGGER.warn("Only {} eligible animals could be placed within {} blocks of {} "
                            + "after checking {} terrain chunks in {} ms",
                    total, RADIUS, selectedFilter, search.chunksLoaded, elapsedMillis);
        }
    }

    static boolean isEligible(Entity entity) {
        return entity instanceof PigEntity
                || entity instanceof CowEntity
                || entity instanceof SheepEntity
                || entity instanceof ChickenEntity
                || entity instanceof RabbitEntity;
    }

    static int missingAnimals(int existing) {
        return Math.max(0, MINIMUM_ANIMALS - existing);
    }

    private static SearchResult findSpawnPositions(ServerWorld world, BlockPos target, Random random) {
        List<BlockPos> positions = new ArrayList<BlockPos>();
        Set<Long> loadedChunks = new LinkedHashSet<Long>();
        double startingAngle = random.nextDouble() * Math.PI * 2.0D;
        double angleStep = Math.PI * (3.0D - Math.sqrt(5.0D));

        for (int attempt = 0; attempt < MAX_POSITION_ATTEMPTS
                && positions.size() < MAX_SPAWN_POSITIONS; attempt++) {
            int distance = MINIMUM_STRUCTURE_DISTANCE
                    + random.nextInt(RADIUS - MINIMUM_STRUCTURE_DISTANCE + 1);
            double angle = startingAngle + angleStep * attempt;
            int x = target.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = target.getZ() + (int) Math.round(Math.sin(angle) * distance);
            if (StructureSpawnProximity.horizontalDistanceSquared(target, new BlockPos(x, 0, z))
                    > (long) RADIUS * RADIUS) {
                continue;
            }

            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            long chunkKey = chunkKey(chunkX, chunkZ);
            if (!loadedChunks.contains(chunkKey)) {
                if (loadedChunks.size() >= MAX_TERRAIN_CHUNKS) {
                    continue;
                }
                world.getChunk(chunkX, chunkZ);
                loadedChunks.add(chunkKey);
            }

            BlockPos surface = world.getTopPosition(
                    Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).toImmutable();
            if (!StructureSpawnProximity.isSafeSpawn(world, surface)
                    || !isSeparated(surface, positions, MINIMUM_ANIMAL_SPACING)) {
                continue;
            }
            positions.add(surface);
        }
        return new SearchResult(positions, loadedChunks.size());
    }

    private static EntityType<? extends AnimalEntity> chooseNaturalType(
            ServerWorld world, BlockPos pos, Random random) {
        int start = random.nextInt(ELIGIBLE_TYPES.size());
        for (int offset = 0; offset < ELIGIBLE_TYPES.size(); offset++) {
            EntityType<? extends AnimalEntity> type = ELIGIBLE_TYPES.get((start + offset) % ELIGIBLE_TYPES.size());
            if (SpawnRestriction.canSpawn(type, world, SpawnReason.CHUNK_GENERATION, pos, random)) {
                return type;
            }
        }
        return null;
    }

    private static boolean isSeparated(BlockPos candidate, List<BlockPos> existing, int minimumDistance) {
        long minimumSquared = (long) minimumDistance * minimumDistance;
        for (BlockPos pos : existing) {
            if (StructureSpawnProximity.horizontalDistanceSquared(candidate, pos) < minimumSquared) {
                return false;
            }
        }
        return true;
    }

    private static boolean isInsideRadius(Entity entity, BlockPos target) {
        double x = entity.getX() - target.getX();
        double z = entity.getZ() - target.getZ();
        return x * x + z * z <= (double) RADIUS * RADIUS;
    }

    private static Random deterministicRandom(long worldSeed, BlockPos target, String selectedFilter) {
        long mixed = worldSeed ^ target.asLong();
        mixed ^= (selectedFilter == null ? 0L : selectedFilter.hashCode()) * 0x9e3779b97f4a7c15L;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        return new Random(mixed);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static final class SearchResult {
        private final List<BlockPos> positions;
        private final int chunksLoaded;

        private SearchResult(List<BlockPos> positions, int chunksLoaded) {
            this.positions = positions;
            this.chunksLoaded = chunksLoaded;
        }
    }
}
