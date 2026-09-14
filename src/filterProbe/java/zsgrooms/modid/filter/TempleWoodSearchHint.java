package zsgrooms.modid.filter;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.VanillaLayeredBiomeSource;
import net.minecraft.world.dimension.DimensionType;

/** Coarse proposal screening only; thin biome edges or unusual joined pools can be skipped. */
final class TempleWoodSearchHint {
    static boolean promising(FilterCandidateSearch.Candidate candidate) {
        long seed = candidate.seedForValidation();
        BiomeAccess access = new BiomeAccess(new VanillaLayeredBiomeSource(seed, false, false), BiomeAccess.hashSeed(seed),
                DimensionType.getOverworldDimensionType().getBiomeAccessType());
        for (BlockPos lake : candidate.lakeAttempts) {
            // Native lake masks extend positively up to 16 blocks; include the 20-block biome check and a margin.
            for (int x = -24; x <= 40; x += 4) {
                for (int z = -24; z <= 40; z += 4) {
                    if (TreeBiomeChecks.allows(access.getBiome(new BlockPos(lake.getX() + x, 255, lake.getZ() + z)).getCategory())) return true;
                }
            }
        }
        return false;
    }
}
