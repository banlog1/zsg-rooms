package zsgrooms.model.nether;

import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.loot.LootContext;
import com.seedfinding.mcfeature.loot.MCLootTables;
import com.seedfinding.mcfeature.loot.item.ItemStack;
import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PortalLootParityTest {
    @Test
    void nativeLootMatchesSeedFindingIncludingEnchantmentOverrides() throws Exception {
        // 1.171.5 lacks the thorns/axe enchantment overrides; use ZSG's pinned 1.171.9.
        Process process = new ProcessBuilder(Path.of("../../run/filter-worker/portal-test.exe").toAbsolutePath().toString()).start();
        try (BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            for (int i = 0; i < 4096; i++) {
                String line = input.readLine();
                assertNotNull(line);
                String[] fields = line.split(" ");
                assertEquals(8, fields.length);
                assertEquals(i, Integer.parseInt(fields[0]));
                ChunkRand random = new ChunkRand();
                long population = random.setPopulationSeed(i * 0x9e3779b97f4a7c15L, (i % 31 - 15) * 16, (i % 23 - 11) * 16, MCVersion.v1_16_1);
                random.setSeed(population + 40005);
                int[] counts = new int[7];
                String[] names = {"obsidian", "iron_nugget", "flint", "flint_and_steel", "fire_charge", "golden_axe", "golden_pickaxe"};
                for (ItemStack stack : MCLootTables.RUINED_PORTAL_CHEST.get().apply(MCVersion.v1_16_1)
                        .generate(new LootContext(random.nextLong(), MCVersion.v1_16_1))) {
                    for (int j = 0; j < names.length; j++) if (stack.getItem().getName().equals(names[j])) counts[j] += stack.getCount();
                }
                for (int j = 0; j < counts.length; j++) assertEquals(counts[j], Integer.parseInt(fields[j + 1]), "vector " + i + " field " + j);
            }
            assertNull(input.readLine());
            assertEquals(0, process.waitFor());
        } finally { process.destroyForcibly(); process.waitFor(); }
    }
}
