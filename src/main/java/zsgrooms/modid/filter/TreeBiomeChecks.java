package zsgrooms.modid.filter;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.dimension.DimensionType;

import java.util.function.Function;

/** Public ZSG category/radius rules, sampling the complete intended three-by-three grid. */
public final class TreeBiomeChecks {
    private TreeBiomeChecks() { }

    public static boolean nearby(BiomeSource source, long seed, BlockPos anchor, boolean village) {
        BiomeAccess access = new BiomeAccess(source, BiomeAccess.hashSeed(seed), DimensionType.getOverworldDimensionType().getBiomeAccessType());
        return nearby(pos -> access.getBiome(pos).getCategory(), anchor, village);
    }

    public static boolean nearby(Function<BlockPos, Biome.Category> biomes, BlockPos anchor, boolean village) {
        int distance = village ? 30 : 20;
        for (int x = -distance; x <= distance; x += distance) {
            for (int z = -distance; z <= distance; z += distance) {
                Biome.Category category = biomes.apply(new BlockPos(anchor.getX() + x, 255, anchor.getZ() + z));
                if (allows(category)) return true;
            }
        }
        return false;
    }

    static boolean allows(Biome.Category category) {
        return category == Biome.Category.FOREST || category == Biome.Category.JUNGLE
                || category == Biome.Category.SWAMP || category == Biome.Category.TAIGA
                || category == Biome.Category.SAVANNA;
    }
}
