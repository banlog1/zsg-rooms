package zsgrooms.modid;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.feature.StructureFeature;

import java.util.Random;

public final class StructureSpawnProximity {
    static final int MIN_TARGET_DISTANCE = 70;
    static final int MAX_TARGET_DISTANCE = 128;
    static final int RELOCATION_THRESHOLD = 140;
    private static final int SEARCH_RADIUS_CHUNKS = 160;
    private static final int CANDIDATE_COUNT = 40;

    private static volatile boolean enabled;
    private static volatile boolean preparedSpawn;
    private static String filterId = "";

    private StructureSpawnProximity() {
    }

    public static synchronized void configure(boolean shouldEnable, String selectedFilter) {
        enabled = shouldEnable;
        preparedSpawn = false;
        filterId = ZsgSeedBridge.normalizeSeedType(selectedFilter);
    }

    public static synchronized void prepare(ServerWorld world) {
        preparedSpawn = false;
        StructureFeature<?> structure = structureForFilter(filterId);
        if (!enabled || world == null || structure == null) {
            return;
        }

        BlockPos originalSpawn = world.getSpawnPos();
        BlockPos target;
        try {
            target = world.locateStructure(structure, originalSpawn, SEARCH_RADIUS_CHUNKS, false);
        } catch (RuntimeException exception) {
            ZsgRooms.LOGGER.warn("Could not locate the {} spawn structure: {}", filterId, exception.getMessage());
            return;
        }
        if (target == null) {
            ZsgRooms.LOGGER.warn("No {} structure was found near the original spawn", filterId);
            return;
        }

        long originalDistanceSquared = horizontalDistanceSquared(originalSpawn, target);
        if (originalDistanceSquared <= (long) RELOCATION_THRESHOLD * RELOCATION_THRESHOLD) {
            ZsgRooms.LOGGER.info("Keeping original spawn; {} is already {} blocks away",
                    filterId, Math.round(Math.sqrt(originalDistanceSquared)));
            return;
        }

        BlockPos safeSpawn = findSafeSpawn(world, target, filterId);
        if (safeSpawn == null) {
            ZsgRooms.LOGGER.warn("Could not find a safe surface near the {} structure", filterId);
            return;
        }
        world.setSpawnPos(safeSpawn);
        preparedSpawn = true;
        ZsgRooms.LOGGER.info("Moved room spawn from {} to {} ({} blocks from {})",
                originalSpawn, safeSpawn, Math.round(Math.sqrt(horizontalDistanceSquared(safeSpawn, target))), filterId);
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

    private static BlockPos findSafeSpawn(ServerWorld world, BlockPos target, String selectedFilter) {
        Random random = deterministicRandom(world.getSeed(), selectedFilter);
        int preferredDistance = MIN_TARGET_DISTANCE
                + random.nextInt(MAX_TARGET_DISTANCE - MIN_TARGET_DISTANCE + 1);
        double startingAngle = random.nextDouble() * Math.PI * 2.0D;
        double angleStep = Math.PI * (3.0D - Math.sqrt(5.0D));

        for (int attempt = 0; attempt < CANDIDATE_COUNT; attempt++) {
            int distanceOffset = ((attempt / 8) * 16) * (attempt % 2 == 0 ? 1 : -1);
            int distance = clamp(preferredDistance + distanceOffset, MIN_TARGET_DISTANCE, MAX_TARGET_DISTANCE);
            double angle = startingAngle + angleStep * attempt;
            int x = target.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = target.getZ() + (int) Math.round(Math.sin(angle) * distance);
            BlockPos surface = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, 0, z));
            if (isSafeSpawn(world, surface)) {
                return surface.toImmutable();
            }
        }
        return null;
    }

    private static boolean isSafeSpawn(ServerWorld world, BlockPos feet) {
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
}
