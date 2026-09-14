package zsgrooms.modid.filter;

import net.minecraft.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootGsons;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContext;
import net.minecraft.server.world.ServerWorld;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Optional calibration of mathematical loot models. No server or world is constructed. */
class StandaloneModelLootParityTest {
    @Test
    void nativeModelsMatchVanillaLootWithoutCreatingAWorld() throws Exception {
        assumeTrue(Boolean.getBoolean("zsgrooms.modelParity"));
        Bootstrap.initialize();
        LootTable temple = table("desert_pyramid");
        LootTable treasure = table("shipwreck_treasure");
        LootTable supply = table("shipwreck_supply");
        Process process = new ProcessBuilder(Paths.get("run/filter-worker/model-test.exe").toAbsolutePath().toString()).start();
        try (BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            for (int i = 0; i < 512; i++) {
                String line = input.readLine();
                assertNotNull(line);
                int[] actual = Arrays.stream(line.split(" ")).mapToInt(Integer::parseInt).toArray();
                long seed = i * 0x9e3779b97f4a7c15L;
                int x = (i % 31 - 15) * 16, z = (i % 23 - 11) * 16;
                int[] t = new int[4], s = new int[4];
                Random random = decorator(seed, x, z, 40003);
                for (int chest = 0; chest < 4; chest++) add(t, temple, random.nextLong());
                random = decorator(seed, x, z, 40006);
                random.nextLong(); random.nextLong(); random.nextLong();
                add(s, treasure, random.nextLong());
                random = decorator(seed, x - 16, z, 40006);
                random.nextLong();
                add(s, supply, random.nextLong());
                assertArrayEquals(new int[]{i, t[0], t[1], t[2], s[0], s[1], s[2], s[3]}, actual, "Vanilla vector " + i);
            }
            assertNull(input.readLine());
            assertEquals(0, process.waitFor());
        } finally {
            process.destroyForcibly();
            process.waitFor();
        }
    }

    private static LootTable table(String name) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(java.util.Objects.requireNonNull(
                LootTable.class.getResourceAsStream("/data/minecraft/loot_tables/chests/" + name + ".json")), StandardCharsets.UTF_8)) {
            return LootGsons.getTableGsonBuilder().create().fromJson(reader, LootTable.class);
        }
    }

    private static Random decorator(long seed, int x, int z, int salt) {
        Random random = new Random(seed);
        long a = random.nextLong() | 1L, b = random.nextLong() | 1L;
        return new Random(((x * a + z * b) ^ seed) + salt);
    }

    private static void add(int[] counts, LootTable table, long seed) throws Exception {
        Constructor<LootContext> constructor = LootContext.class.getDeclaredConstructor(Random.class, float.class,
                ServerWorld.class, Function.class, Function.class, Map.class, Map.class);
        constructor.setAccessible(true);
        // These tables need only RNG; deliberately provide no world or nested table access.
        Function<Object, Object> unsupported = id -> { throw new AssertionError("Unexpected world-dependent loot"); };
        LootContext context = constructor.newInstance(new Random(seed), 0F, null, unsupported, unsupported,
                Collections.emptyMap(), Collections.emptyMap());
        int ironNuggets = 0, goldNuggets = 0;
        for (ItemStack stack : table.generateLoot(context)) {
            int n = stack.getCount();
            if (stack.getItem() == Items.IRON_INGOT) counts[0] += n;
            if (stack.getItem() == Items.IRON_NUGGET) ironNuggets += n;
            if (stack.getItem() == Items.DIAMOND) counts[1] += n;
            if (stack.getItem() == Items.GOLD_INGOT) counts[2] += n;
            if (stack.getItem() == Items.GOLD_NUGGET) goldNuggets += n;
            if (stack.getItem() == Items.WHEAT) counts[3] += n;
            if (stack.getItem() == Items.CARROT) counts[3] += n * 6;
        }
        counts[0] += ironNuggets / 9;
        counts[2] += goldNuggets / 9;
    }
}
