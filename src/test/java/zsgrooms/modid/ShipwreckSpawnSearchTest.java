package zsgrooms.modid;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class ShipwreckSpawnSearchTest {
    private static final class Terrain implements ShipwreckSpawnSearch.Terrain {
        final Set<Long> chunks = new HashSet<Long>();
        final BiFunction<Integer, Integer, BlockPos> surface;
        Terrain(BiFunction<Integer, Integer, BlockPos> surface) { this.surface = surface; }
        public void loadChunk(int x, int z) { assertTrue(chunks.add(((long) x << 32) ^ (z & 0xffffffffL))); }
        public BlockPos safeNaturalSurface(int x, int z) {
            assertTrue(chunks.contains(((long) (x >> 4) << 32) ^ ((z >> 4) & 0xffffffffL)));
            return surface.apply(x, z);
        }
    }

    @Test void choosesNearestLandAcrossChunksInsteadOfFirstSuccessfulChunk() {
        Terrain terrain = new Terrain((x, z) -> (x == 15 && z == 15) || (x == -2 && z == 0)
                ? new BlockPos(x, 65, z) : null);
        assertEquals(new BlockPos(-2, 65, 0), ShipwreckSpawnSearch.find(BlockPos.ORIGIN, terrain).spawn);
        assertTrue(terrain.chunks.size() <= 4);
    }

    @Test void supportsCloseLandCoastlinesAndNegativeChunkCoordinates() {
        BlockPos target = new BlockPos(-17, 20, -33);
        Terrain terrain = new Terrain((x, z) -> x <= -20 ? new BlockPos(x, 64, z) : null);
        assertEquals(new BlockPos(-20, 64, -33), ShipwreckSpawnSearch.find(target, terrain).spawn);
    }

    @Test void tiesUseCoordinatesNotChunkOrderOrRandomness() {
        for (int i = 0; i < 3; i++) {
            Terrain terrain = new Terrain((x, z) -> Math.abs(x) + Math.abs(z) == 1 ? new BlockPos(x, 65, z) : null);
            assertEquals(new BlockPos(-1, 65, 0), ShipwreckSpawnSearch.find(BlockPos.ORIGIN, terrain).spawn);
        }
    }

    @Test void emptyOceanStopsAtBudgetAndDoesNotInventAnIsland() {
        Terrain terrain = new Terrain((x, z) -> null);
        ShipwreckSpawnSearch.Result result = ShipwreckSpawnSearch.find(BlockPos.ORIGIN, terrain);
        assertNull(result.spawn);
        assertEquals(ShipwreckSpawnSearch.MAX_CHUNKS, result.chunksChecked);
        assertEquals(result.chunksChecked, terrain.chunks.size());
    }

    @Test void acceptsNaturalTerrainButNotShipDecksLeavesOrIce() {
        for (net.minecraft.block.Block block : new net.minecraft.block.Block[]{Blocks.SAND, Blocks.GRASS_BLOCK, Blocks.STONE, Blocks.GRAVEL}) {
            assertTrue(StructureSpawnProximity.isNaturalShipwreckFloor(block.getDefaultState()));
        }
        for (net.minecraft.block.Block block : new net.minecraft.block.Block[]{Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.OAK_LEAVES, Blocks.ICE, Blocks.WATER}) {
            assertFalse(StructureSpawnProximity.isNaturalShipwreckFloor(block.getDefaultState()));
        }
    }
}
