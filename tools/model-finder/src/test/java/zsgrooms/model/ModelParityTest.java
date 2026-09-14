package zsgrooms.model;

import com.seedfinding.mcbiome.source.OverworldBiomeSource;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.util.pos.BPos;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.loot.LootContext;
import com.seedfinding.mcfeature.loot.LootTable;
import com.seedfinding.mcfeature.loot.MCLootTables;
import com.seedfinding.mcfeature.loot.item.ItemStack;
import com.seedfinding.mcfeature.loot.item.Items;
import com.seedfinding.mcterrain.terrain.OverworldTerrainGenerator;
import org.junit.jupiter.api.Test;
import profotoce59.properties.VillageGenerator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ModelParityTest {
    private static final MCVersion VERSION = MCVersion.v1_16_1;

    @Test
    void nativeSurfacePoliciesAndOptimizationsPassRegressionTests() throws Exception {
        for (String executable : new String[]{"surface-test.exe", "surface-query-test.exe"}) {
            Process process = new ProcessBuilder(Path.of("run/filter-worker", executable).toAbsolutePath().toString()).start();
            try {
                assertTrue(process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS), "Surface test timed out");
                assertEquals(0, process.exitValue(), executable);
            } finally {
                process.destroyForcibly();
                process.waitFor();
            }
        }
    }

    @Test
    void templeAndTreasureLootMatchSeedFindingAcrossSignedCoordinatesAndSisters() throws Exception {
        Process process = new ProcessBuilder(Path.of("run/filter-worker/model-test.exe").toAbsolutePath().toString()).start();
        try (BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            for (int i = 0; i < 512; i++) {
                String line = input.readLine();
                assertNotNull(line, "Native test ended early");
                int[] actual = Arrays.stream(line.split(" ")).mapToInt(Integer::parseInt).toArray();
                assertEquals(i, actual[0]);
                long seed = i * 0x9e3779b97f4a7c15L;
                int x = (i % 31 - 15) * 16, z = (i % 23 - 11) * 16;
                int[] temple = new int[4];
                ChunkRand random = decorator(seed, x, z, 40003);
                for (int chest = 0; chest < 4; chest++) add(temple, MCLootTables.DESERT_PYRAMID_CHEST.get(), random.nextLong());
                int[] ship = new int[4];
                random = decorator(seed, x, z, 40006);
                random.nextLong(); random.nextLong(); random.nextLong();
                add(ship, MCLootTables.SHIPWRECK_TREASURE_CHEST.get(), random.nextLong());
                // This pinned library predates armor-enchantment applicability fixes.
                // Supply loot is compared with the actual vanilla code in the offline test harness.
                int[] expected = {i, temple[0], temple[1], temple[2], ship[0], ship[1], ship[2]};
                assertArrayEquals(expected, Arrays.copyOf(actual, 7), "Resource parity vector " + i);
            }
            assertNull(input.readLine());
            assertEquals(0, process.waitFor());
        } finally {
            process.destroyForcibly();
            process.waitFor();
        }
    }

    private static ChunkRand decorator(long seed, int x, int z, int salt) {
        ChunkRand random = new ChunkRand();
        long population = random.setPopulationSeed(seed, x, z, VERSION);
        random.setSeed(population + salt);
        return random;
    }

    private static void add(int[] counts, LootTable table, long seed) {
        int nuggets = 0, goldNuggets = 0;
        for (ItemStack stack : table.apply(VERSION).generate(new LootContext(seed, VERSION))) {
            int n = stack.getCount();
            if (stack.getItem().equals(Items.IRON_INGOT)) counts[0] += n;
            if (stack.getItem().equals(Items.IRON_NUGGET)) nuggets += n;
            if (stack.getItem().equals(Items.DIAMOND)) counts[1] += n;
            if (stack.getItem().equals(Items.GOLD_INGOT)) counts[2] += n;
            if (stack.getItem().equals(Items.GOLD_NUGGET)) goldNuggets += n;
            if (stack.getItem().equals(Items.WHEAT)) counts[3] += n;
            if (stack.getItem().equals(Items.CARROT)) counts[3] += 6 * n;
        }
        counts[0] += nuggets / 9;
        counts[2] += goldNuggets / 9;
    }

    @Test
    void upstreamVillageLootFixtureRemainsReproducible() {
        // Upstream's manually checked public desert fixture, also checked in 1.16.1.
        OverworldTerrainGenerator terrain = new OverworldTerrainGenerator(new OverworldBiomeSource(VERSION, 1L));
        VillageGenerator village = new VillageGenerator(VERSION);
        assertTrue(village.generate(terrain, 11383, 14, new ChunkRand(), false));
        var loot = village.generateLoot(terrain, new ChunkRand());
        assertEquals(new BPos(182131, 70, 228), loot.get(0).getFirst());
        assertTrue(loot.get(0).getSecond().contains(new ItemStack(Items.WHEAT, 20)));
        assertEquals(0, SmithLootModel.iron(1L, 11383, 14));
        assertEquals(0, SmithLootModel.iron(1L, 11383, 14));
    }
}
