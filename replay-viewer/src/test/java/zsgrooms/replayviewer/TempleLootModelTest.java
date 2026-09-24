// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class TempleLootModelTest {
    @Test void signedRegionsAndAllFourChestIndexesRemainStableAcrossSisters() {
        for (int i = 0; i < 128; i++) {
            long seed = i * 0x9e3779b97f4a7c15L;
            int rx = i % 13 - 6, rz = i % 17 - 8;
            Random random = new Random(rx * 341873128712L + rz * 132897987541L + seed + 14357617);
            int cx = rx * 32 + random.nextInt(24), cz = rz * 32 + random.nextInt(24);
            assertTrue(TempleLootModel.isStart(seed, cx, cz));
            assertFalse(TempleLootModel.isStart(seed, cx + 1, cz));
            Set<Integer> indexes = new HashSet<>();
            for (int[] offset : new int[][]{{10, 8}, {12, 10}, {10, 12}, {8, 10}}) {
                int index = TempleLootModel.chestIndex(seed, cx * 16 + offset[0], 53, cz * 16 + offset[1]);
                indexes.add(index);
                assertEquals(index, TempleLootModel.chestIndex(seed ^ 0xabc0000000000000L, cx * 16 + offset[0], 53, cz * 16 + offset[1]));
                assertEquals(TempleLootModel.lootSeed(seed, cx, cz, index),
                        TempleLootModel.lootSeed(seed ^ 0xabc0000000000000L, cx, cz, index));
            }
            assertEquals(new HashSet<>(java.util.Arrays.asList(0, 1, 2, 3)), indexes);
            assertEquals(-1, TempleLootModel.chestIndex(seed, cx * 16 + 10, 54, cz * 16 + 8));
            assertEquals(-1, TempleLootModel.chestIndex(seed, cx * 16, 53, cz * 16));
        }
    }

    @Test void chestSeedsMatchFilterDecoratorSequence() {
        for (int i = 0; i < 512; i++) {
            long seed = i * 0x9e3779b97f4a7c15L;
            int cx = i % 31 - 15, cz = i % 23 - 11;
            Random random = new Random(seed);
            long a = random.nextLong() | 1, b = random.nextLong() | 1;
            random.setSeed(((cx * 16L * a + cz * 16L * b) ^ seed) + 40003);
            for (int index = 0; index < 4; index++) assertEquals(random.nextLong(), TempleLootModel.lootSeed(seed, cx, cz, index));
        }
        assertThrows(IllegalArgumentException.class, () -> TempleLootModel.lootSeed(1, 0, 0, 4));
    }
}
