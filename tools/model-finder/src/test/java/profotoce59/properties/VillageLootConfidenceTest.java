package profotoce59.properties;

import com.seedfinding.mcbiome.source.OverworldBiomeSource;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.util.block.BlockBox;
import com.seedfinding.mccore.util.block.BlockRotation;
import com.seedfinding.mccore.util.pos.BPos;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcterrain.terrain.OverworldTerrainGenerator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Guards the production chunk-scoped confidence gate and RNG advancement. */
class VillageLootConfidenceTest {
    private static final MCVersion VERSION = MCVersion.v1_16_1;
    private static final OverworldTerrainGenerator TERRAIN = new OverworldTerrainGenerator(
            new OverworldBiomeSource(VERSION, 1L));

    @Test
    void outsideFeaturePreservesLootWithoutBeingPlacedOrChangingRandomState() throws Exception {
        for (BPos position : List.of(new BPos(-1, 64, 0), new BPos(16, 64, 0),
                new BPos(0, 64, -1), new BPos(0, 64, 16))) {
            Smith control = new Smith(0, 0);
            ChunkRand controlRandom = generate(List.of(control), 0, 0);
            assertFalse(control.loot.isEmpty());
            assertTrue(control.confident);

            Feature outside = new Feature(position);
            Smith actual = new Smith(0, 0);
            ChunkRand actualRandom = generate(List.of(outside, actual), 0, 0);
            assertEquals(0, outside.calls);
            assertEquals(1, actual.calls);
            assertTrue(actual.confident);
            assertEquals(control.loot, actual.loot);
            assertEquals(controlRandom.nextLong(), actualRandom.nextLong());
        }
    }

    @Test
    void featureAfterChestDoesNotRetroactivelySuppressLoot() throws Exception {
        Smith control = new Smith(0, 0);
        generate(List.of(control), 0, 0);
        Smith smith = new Smith(0, 0);
        Feature inside = new Feature(new BPos(0, 64, 0));
        generate(List.of(smith, inside), 0, 0);
        assertEquals(1, inside.calls);
        assertTrue(smith.confident);
        assertEquals(control.loot, smith.loot);
    }

    @Test
    void intersectingFeatureBeforeChestMustStillMakeLootUncertain() throws Exception {
        Feature inside = new Feature(new BPos(0, 64, 0));
        Smith smith = new Smith(0, 0);
        generate(List.of(inside, smith), 0, 0);
        assertEquals(1, inside.calls);
        assertEquals(1, smith.calls);
        assertFalse(smith.confident);
        assertTrue(smith.loot.isEmpty());
    }

    @Test
    void outsideNonFeatureDoesNotSuppressLoot() throws Exception {
        Smith outside = new Smith(32, 0);
        Smith inside = new Smith(0, 0);
        generate(List.of(outside, inside), 0, 0);
        assertEquals(0, outside.calls);
        assertTrue(inside.confident);
        assertFalse(inside.loot.isEmpty());
    }

    @Test
    void confidenceStartsFreshForEachChunk() throws Exception {
        Feature feature = new Feature(new BPos(0, 64, 0));
        Smith nextChunk = new Smith(16, 0);
        generate(List.of(feature, nextChunk), 0, 0);
        assertEquals(0, nextChunk.calls);
        generate(List.of(feature, nextChunk), 1, 0);
        assertEquals(1, feature.calls);
        assertTrue(nextChunk.confident);
        assertFalse(nextChunk.loot.isEmpty());

        Smith isolated = new Smith(16, 0);
        generate(List.of(isolated), 1, 0);
        assertTrue(isolated.confident);
        assertFalse(isolated.loot.isEmpty());
        assertEquals(isolated.loot, nextChunk.loot);
    }

    private static ChunkRand generate(List<VillageGenerator.Piece> pieces, int chunkX, int chunkZ) throws Exception {
        Method method = VillageGenerator.class.getDeclaredMethod("generateChunkLoot", List.class,
                OverworldTerrainGenerator.class, int.class, int.class, ChunkRand.class);
        method.setAccessible(true);
        ChunkRand random = new ChunkRand();
        method.invoke(new VillageGenerator(VERSION), new ArrayList<>(pieces), TERRAIN, chunkX, chunkZ, random);
        return random;
    }

    private static final class Smith extends VillageGenerator.Piece {
        int calls;
        boolean confident;

        Smith(int x, int z) {
            super("plains/houses/plains_weaponsmith_1", new BPos(x, 64, z),
                    new BlockBox(x, 64, z, x + 8, 71, z + 10), BlockRotation.NONE,
                    VillageGenerator.PlacementBehaviour.RIGID, 0);
        }

        @Override
        public boolean place(ChunkRand random, OverworldTerrainGenerator terrain, boolean confident, BlockBox box) {
            calls++;
            this.confident = confident;
            return super.place(random, terrain, confident, box);
        }
    }

    private static final class Feature extends VillageGenerator.Piece {
        int calls;

        Feature(BPos pos) {
            super("oak", pos, new BlockBox(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX(), pos.getY(), pos.getZ()), BlockRotation.NONE,
                    VillageGenerator.PlacementBehaviour.RIGID, 0);
        }

        @Override
        public boolean place(ChunkRand random, OverworldTerrainGenerator terrain, boolean confident, BlockBox box) {
            calls++;
            // Isolate confidence/order from the upstream feature model's terrain approximations.
            random.nextInt(3);
            return true;
        }
    }
}
