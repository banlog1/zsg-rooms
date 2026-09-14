package zsgrooms.modid.filter;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TreeBiomeChecksTest {
    @Test
    void preservesTheSamePublicBiomeCategoriesForTempleAndVillage() {
        for (Biome.Category category : new Biome.Category[]{Biome.Category.FOREST, Biome.Category.JUNGLE,
                Biome.Category.SWAMP, Biome.Category.TAIGA, Biome.Category.SAVANNA}) {
            assertTrue(TreeBiomeChecks.nearby(pos -> category, BlockPos.ORIGIN, false));
            assertTrue(TreeBiomeChecks.nearby(pos -> category, BlockPos.ORIGIN, true));
        }
        for (boolean village : new boolean[]{false, true}) {
            assertFalse(TreeBiomeChecks.nearby(pos -> Biome.Category.PLAINS, BlockPos.ORIGIN, village));
            assertFalse(TreeBiomeChecks.nearby(pos -> Biome.Category.DESERT, BlockPos.ORIGIN, village));
        }
    }

    @Test
    void templeAcceptsSavannaAtTheLastGridPosition() {
        BlockPos anchor = new BlockPos(-80, 64, 100);
        assertTrue(TreeBiomeChecks.nearby(pos -> pos.equals(new BlockPos(-60, 255, 120))
                ? Biome.Category.SAVANNA : Biome.Category.DESERT, anchor, false));
    }

    @Test
    void checksAllNinePositionsAtTheOriginalRadiusAndHeight() {
        for (boolean village : new boolean[]{false, true}) {
            int distance = village ? 30 : 20;
            java.util.Set<BlockPos> visited = new java.util.HashSet<BlockPos>();
            BlockPos anchor = new BlockPos(-80, 64, 100);
            assertFalse(TreeBiomeChecks.nearby(pos -> { visited.add(pos); return Biome.Category.DESERT; }, anchor, village));
            assertEquals(9, visited.size());
            for (int x = -distance; x <= distance; x += distance) {
                for (int z = -distance; z <= distance; z += distance) {
                    assertTrue(visited.contains(new BlockPos(-80 + x, 255, 100 + z)));
                }
            }
            assertTrue(TreeBiomeChecks.nearby(pos -> pos.getX() == -80 + distance && pos.getZ() == 100 + distance
                    ? Biome.Category.FOREST : Biome.Category.DESERT, anchor, village));
        }
    }
}
