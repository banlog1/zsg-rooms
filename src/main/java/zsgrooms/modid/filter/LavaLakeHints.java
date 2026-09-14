package zsgrooms.modid.filter;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.gen.ChunkRandom;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.decorator.ChanceDecoratorConfig;
import net.minecraft.world.gen.decorator.Decorator;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.DecoratedFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.SingleStateFeatureConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.BooleanSupplier;

/** Vanilla 1.16.1 lake attempts only. Neither surface exposure nor successful placement is implied. */
final class LavaLakeHints {
    private LavaLakeHints() {
    }

    static List<BlockPos> find(long seed, BiomeSource biomes, int x, int z, int radius, BooleanSupplier stop) {
        SurfaceTerrainChecks.checkRadius(radius);
        List<BlockPos> positions = new ArrayList<BlockPos>();
        ChunkRandom random = new ChunkRandom();
        // LakeFeature writes its 16x16 mask in the positive X/Z direction from the decorator position.
        for (int chunkX = (x - radius - 15) >> 4; chunkX <= (x + radius) >> 4; chunkX++) {
            for (int chunkZ = (z - radius - 15) >> 4; chunkZ <= (z + radius) >> 4; chunkZ++) {
                if (stop.getAsBoolean()) return Collections.emptyList();
                Biome biome = biomes.getBiomeForNoiseGen((chunkX << 2) + 2, 2, (chunkZ << 2) + 2);
                List<ConfiguredFeature<?, ?>> features = biome.getFeaturesForStep(GenerationStep.Feature.LAKES);
                if (features == null) continue;
                long populationSeed = random.setPopulationSeed(seed, chunkX << 4, chunkZ << 4);
                for (int index = 0; index < features.size(); index++) {
                    ConfiguredFeature<?, ?> outer = features.get(index);
                    if (outer.feature != Feature.DECORATED || !(outer.config instanceof DecoratedFeatureConfig)) continue;
                    DecoratedFeatureConfig decorated = (DecoratedFeatureConfig) outer.config;
                    if (decorated.feature.feature != Feature.LAKE
                            || !(decorated.feature.config instanceof SingleStateFeatureConfig)
                            || !((SingleStateFeatureConfig) decorated.feature.config).state.isOf(Blocks.LAVA)
                            || decorated.decorator.decorator != Decorator.LAVA_LAKE
                            || !(decorated.decorator.config instanceof ChanceDecoratorConfig)) continue;
                    // Vanilla has no structures in the LAKES generation step.
                    random.setDecoratorSeed(populationSeed, index, GenerationStep.Feature.LAKES.ordinal());
                    Optional<BlockPos> attempt = rollPosition(random, chunkX << 4, chunkZ << 4,
                            ((ChanceDecoratorConfig) decorated.decorator.config).chance, 256, 63);
                    if (attempt.isPresent() && couldReachSurfaceArea(attempt.get(), x, z, radius)) {
                        positions.add(attempt.get());
                    }
                }
            }
        }
        return Collections.unmodifiableList(positions);
    }

    // Kept small and checked against LavaLakeDecorator in tests; no world/generator construction per roll.
    static Optional<BlockPos> rollPosition(Random random, int x, int z, int chance, int maxY, int seaLevel) {
        if (random.nextInt(chance / 10) != 0) return Optional.empty();
        int targetX = x + random.nextInt(16);
        int targetZ = z + random.nextInt(16);
        int targetY = random.nextInt(random.nextInt(maxY - 8) + 8);
        if (targetY < seaLevel || random.nextInt(chance / 8) == 0) {
            return Optional.of(new BlockPos(targetX, targetY, targetZ));
        }
        return Optional.empty();
    }

    static boolean couldReachSurfaceArea(BlockPos attempt, int x, int z, int radius) {
        // Liquid reaches at most input Y - 1, even before the downward terrain search.
        if (attempt.getY() <= SurfaceTerrainChecks.MIN_POOL_Y) return false;
        int nearestX = Math.max(attempt.getX(), Math.min(x, attempt.getX() + 15));
        int nearestZ = Math.max(attempt.getZ(), Math.min(z, attempt.getZ() + 15));
        long dx = (long) nearestX - x;
        long dz = (long) nearestZ - z;
        return dx * dx + dz * dz <= (long) radius * radius;
    }
}
