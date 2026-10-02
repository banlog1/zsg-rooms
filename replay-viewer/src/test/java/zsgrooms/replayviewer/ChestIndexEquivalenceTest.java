// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;

class ChestIndexEquivalenceTest {
    private static final Gson JSON = new Gson();
    private static final String[] DIMENSIONS = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};
    private static final int[] COORDS = {-33554432, -17, -16, -1, 0, 15, 16, 33554430};

    @Test void randomizedHistoriesMatchStringKeyReference() throws Exception {
        ChestHistory<String> actual = new ChestHistory<>();
        ReferenceChestHistory<String> expected = new ReferenceChestHistory<>();
        ChestLootHistory loot = new ChestLootHistory();
        ReferenceChestLootHistory oldLoot = new ReferenceChestLootHistory();
        Random random = new Random(981234);
        int time = 0;
        for (int i = 0; i < 25000; i++) {
            time += random.nextInt(3);
            int world = random.nextInt(5) * 128, x = COORDS[random.nextInt(COORDS.length)];
            int z = COORDS[random.nextInt(COORDS.length)], sync = random.nextInt(4);
            String dimension = DIMENSIONS[random.nextInt(DIMENSIONS.length)];
            long pos = pos(x, 0, z);
            switch (random.nextInt(8)) {
                case 0:
                    ChestOpening opening = random.nextInt(5) == 0 ? null : new ChestOpening(time, i, world, sync,
                            dimension, pos, random.nextBoolean() ? pos : pos(x + 1, 0, z), 5, 6);
                    actual.open(opening, time); expected.open(opening, time);
                    break;
                case 1:
                    ArrayList<String> items = new ArrayList<>(Collections.nCopies(random.nextBoolean() ? 63 : 90, "empty"));
                    items.set(0, "iron-" + i);
                    actual.inventory(sync, items, time); expected.inventory(sync, items, time);
                    break;
                case 2:
                    int slot = random.nextInt(92) - 1;
                    actual.slot(sync, slot, "item-" + i, time); expected.slot(sync, slot, "item-" + i, time);
                    break;
                case 3:
                    int state = random.nextInt(8);
                    actual.block(world, dimension, pos, state, time); expected.block(world, dimension, pos, state, time);
                    loot.block(world, dimension, pos, state, time); oldLoot.block(world, dimension, pos, state, time);
                    break;
                case 4:
                    actual.chunk(world, dimension, x >> 4, z >> 4, time); expected.chunk(world, dimension, x >> 4, z >> 4, time);
                    loot.chunk(world, dimension, x >> 4, z >> 4, time); oldLoot.chunk(world, dimension, x >> 4, z >> 4, time);
                    break;
                case 5:
                    ChestLoot entry = new ChestLoot(time, i + 1, world, pos, 5, dimension, "minecraft:chests/ruined_portal", i + 1);
                    loot.observe(entry); oldLoot.observe(entry);
                    break;
                case 6:
                    loot.invalidate(world, dimension, pos, time); oldLoot.invalidate(world, dimension, pos, time);
                    break;
                default:
                    actual.close(); expected.close();
            }
            int queryTime = random.nextInt(time + 6);
            assertEquals(JSON.toJson(expected.at(world, dimension, pos, queryTime)), JSON.toJson(actual.at(world, dimension, pos, queryTime)));
            assertSame(oldLoot.at(world, dimension, pos, queryTime), loot.at(world, dimension, pos, queryTime));
            assertEquals(expected.accepts(sync), actual.accepts(sync));
            if (i % 1000 == 0) {
                assertEquals(snapshot(expected), snapshot(actual), "Chest event " + i);
                assertEquals(snapshot(oldLoot), snapshot(loot), "Loot event " + i);
            }
        }
        actual.finish(); expected.finish(); loot.finish(); oldLoot.finish();
        assertEquals(snapshot(expected), snapshot(actual));
        assertEquals(snapshot(oldLoot), snapshot(loot));
        assertEquals(expected.estimatedBytes(), actual.estimatedBytes());
        assertEquals(oldLoot.estimatedBytes(), loot.estimatedBytes());
    }

    @Test void doubleChestAcrossNegativeChunkBoundaryInvalidatesBothHalvesOnlyInItsScope() throws Exception {
        ChestHistory<String> history = new ChestHistory<>();
        long first = pos(-1, 64, -16), second = pos(0, 64, -16);
        for (int world : new int[]{0, 128}) {
            for (String dimension : DIMENSIONS) {
                history.open(new ChestOpening(10, 1, world, 1, dimension, first, second, 5, 6), 10);
                history.inventory(1, Collections.nCopies(90, "iron"), 20);
            }
        }
        history.chunk(128, DIMENSIONS[1], 0, -1, 20);
        assertNull(history.at(128, DIMENSIONS[1], first, 20).items);
        assertNull(history.at(128, DIMENSIONS[1], second, 20).items);
        assertEquals("iron", history.at(0, DIMENSIONS[1], first, 20).items.get(0));
        assertEquals("iron", history.at(128, DIMENSIONS[0], first, 20).items.get(0));
        assertEquals("iron", history.at(128, DIMENSIONS[2], second, 20).items.get(0));
    }

    @Test void primitivePositionsSupportZeroExtremesAndMutableValueIteration() {
        ChestPositions<String> positions = new ChestPositions<>();
        for (long pos : new long[]{0, Long.MIN_VALUE, Long.MAX_VALUE, -1}) {
            positions.put(128, DIMENSIONS[0], pos, "first");
            positions.put(256, DIMENSIONS[0], pos, "reset");
            positions.put(128, DIMENSIONS[1], pos, "nether");
            assertEquals("first", positions.get(128, new String(DIMENSIONS[0]), pos));
            assertNull(positions.get(0, DIMENSIONS[0], pos));
        }
        java.util.Iterator<String> it = positions.values(128, DIMENSIONS[0]).iterator();
        while (it.hasNext()) { it.next(); it.remove(); }
        assertTrue(positions.values(128, DIMENSIONS[0]).isEmpty());
        assertEquals("reset", positions.remove(256, DIMENSIONS[0], 0));
        assertEquals("nether", positions.get(128, DIMENSIONS[1], 0));
        positions.clear();
        assertNull(positions.get(128, DIMENSIONS[1], 0));
    }

    private static long pos(int x, int y, int z) { return ((long)x & 0x3ffffff) << 38 | ((long)z & 0x3ffffff) << 12 | (y & 4095); }

    @SuppressWarnings("unchecked")
    private static String snapshot(Object history) throws Exception {
        Field field = history.getClass().getDeclaredField("frames"); field.setAccessible(true);
        return JSON.toJson(new TreeMap<>((Map<String, ?>)field.get(history)));
    }
}
