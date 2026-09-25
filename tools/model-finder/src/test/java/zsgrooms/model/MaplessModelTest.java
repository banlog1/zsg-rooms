package zsgrooms.model;

import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.loot.LootContext;
import com.seedfinding.mcfeature.loot.MCLootTables;
import com.seedfinding.mcfeature.loot.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MaplessModelTest {
    @Test
    void lootMatchesSeedFindingAndRavinesMatchOrderedJavaReference() throws Exception {
        Process process = new ProcessBuilder(Path.of("run/filter-worker/mapless-test.exe").toAbsolutePath().toString()).start();
        try (BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            for (int i = 0; i < 4096; i++) {
                String line = input.readLine();
                assertNotNull(line, "Native mapless tests ended early");
                String[] actual = line.split(" ");
                assertEquals(14, actual.length);
                assertEquals(i, Integer.parseInt(actual[0]));
                long seed = i * 0x9e3779b97f4a7c15L;
                int x = i % 31 - 15, z = i % 23 - 11;
                ChunkRand random = new ChunkRand();
                long population = random.setPopulationSeed(seed, x * 16, z * 16, MCVersion.v1_16_1);
                random.setSeed(population + 30001);
                int[] counts = new int[5];
                for (ItemStack stack : MCLootTables.BURIED_TREASURE_CHEST.get().apply(MCVersion.v1_16_1)
                        .generate(new LootContext(random.nextLong(), MCVersion.v1_16_1))) {
                    String name = stack.getItem().getName();
                    String[] names = {"iron_ingot", "gold_ingot", "diamond", "tnt", "emerald"};
                    for (int j = 0; j < names.length; j++) if (names[j].equals(name)) counts[j] += stack.getCount();
                }
                for (int j = 0; j < counts.length; j++) assertEquals(counts[j], Integer.parseInt(actual[j + 1]), "Loot vector " + i);
                double[] ravine = reference(seed, x, z);
                for (int j = 0; j < ravine.length; j++) {
                    assertEquals(ravine[j], Double.parseDouble(actual[j + 6]), 1e-8, "Ravine vector " + i + " field " + j);
                }
            }
            assertNull(input.readLine());
            assertEquals(0, process.waitFor());
        } finally {
            process.destroyForcibly();
            process.waitFor();
        }
    }

    // Independent Java Random evaluation of DuncanRuns' CC0 midpoint model.
    // This verifies RNG ordering/math, not block-perfect vanilla terrain carving.
    private static double[] reference(long seed, int cx, int cz) {
        Random random = new Random(seed);
        random.setSeed(cx * random.nextLong() ^ cz * random.nextLong() ^ seed);
        if (random.nextFloat() > 0.02) return new double[8];
        double x = cx * 16 + random.nextInt(16);
        double y = random.nextInt(random.nextInt(40) + 8) + 20;
        double z = cz * 16 + random.nextInt(16);
        float yaw = random.nextFloat() * (float) (Math.PI * 2);
        float pitch = (random.nextFloat() - 0.5F) / 4.0F;
        double radius = (1.5 + (double) ((random.nextFloat() * 2.0F + random.nextFloat()) * 2.0F)) * 3;
        int length = 112 - random.nextInt(28);
        random.setSeed(random.nextLong());
        for (int i = 0; i < 256; i++) if (i == 0 || random.nextInt(3) == 0) {
            random.nextFloat(); random.nextFloat();
        }
        float yawShift = 0, pitchShift = 0;
        for (int i = 0; i < length / 2; i++) {
            float horizontal = (float) Math.cos(pitch), dy = (float) Math.sin(pitch);
            random.nextFloat(); random.nextFloat();
            x += Math.cos(yaw) * horizontal;
            y += dy;
            z += Math.sin(yaw) * horizontal;
            pitch *= 0.7F;
            pitch += pitchShift * 0.05F;
            yaw += yawShift * 0.05F;
            pitchShift *= 0.8F;
            yawShift *= 0.5F;
            pitchShift += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 2.0F;
            yawShift += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 4.0F;
            random.nextInt(4);
        }
        return new double[]{1, length, radius, x, y, z, Math.max(1, (int) (y - radius)), Math.min(248, (int) (y + radius + 1))};
    }
}
