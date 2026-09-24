// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class ChestLootTest {
    @Test void validatesStewOrderWithoutSortingIt() throws Exception {
        JsonArray order = new JsonArray();
        for (String name : new String[]{"poison", "saturation", "jump_boost", "blindness", "night_vision", "weakness"})
            order.add("minecraft:" + name);
        assertEquals("minecraft:poison", PredictionStewOrder.read(order).get(0));
        assertTrue(PredictionStewOrder.read(null).isEmpty());
        order.set(5, order.get(0));
        assertThrows(IOException.class, () -> PredictionStewOrder.read(order));
        order.remove(5);
        assertThrows(IOException.class, () -> PredictionStewOrder.read(order));
    }
    @Test void seekResetAndDimensionIsolation() {
        ChestLootHistory history = new ChestLootHistory();
        ChestLoot first = loot(10, 0, "minecraft:overworld"), reset = loot(30, 1, "minecraft:overworld");
        history.observe(first); history.observe(reset);
        assertNull(history.at(0, first.dimension, 0, 9));
        assertSame(first, history.at(0, first.dimension, 0, 10));
        assertNull(history.at(1, first.dimension, 0, 29));
        assertSame(reset, history.at(1, first.dimension, 0, 30));
        assertNull(history.at(0, "minecraft:the_nether", 0, 30));
        assertSame(first, history.at(0, first.dimension, 0, 15));
    }

    @Test void blockAndChunkReplacementRequireFreshObservation() {
        ChestLootHistory history = new ChestLootHistory();
        ChestLoot first = loot(10, 0, "minecraft:overworld");
        history.observe(first);
        history.block(0, first.dimension, 0, first.state, 11);
        assertSame(first, history.at(0, first.dimension, 0, 11));
        history.block(0, first.dimension, 0, first.state + 1, 12);
        assertNull(history.at(0, first.dimension, 0, 12));
        assertSame(first, history.at(0, first.dimension, 0, 11));
        ChestLoot fresh = loot(20, 0, first.dimension);
        history.observe(fresh);
        history.chunk(0, first.dimension, 1, 0, 21);
        assertSame(fresh, history.at(0, first.dimension, 0, 21));
        history.chunk(0, first.dimension, 0, 0, 22);
        assertNull(history.at(0, first.dimension, 0, 22));
        history.observe(loot(22, 0, first.dimension));
        assertNotNull(history.at(0, first.dimension, 0, 22));
        history.invalidate(0, first.dimension, 0, 23);
        assertNull(history.at(0, first.dimension, 0, 23));
    }

    @Test void metadataRejectsInvalidBoundsAndPreservesSignedSeeds() throws Exception {
        JsonArray rows = new JsonArray(); rows.add(row());
        assertEquals(Long.MIN_VALUE, ChestLoot.read(rows, 100).get(0).seed);
        rows.get(0).getAsJsonObject().addProperty("time", 101);
        assertThrows(IOException.class, () -> ChestLoot.read(rows, 100));
        rows.set(0, row()); rows.get(0).getAsJsonObject().getAsJsonObject("loot").addProperty("seed", 0);
        assertThrows(IOException.class, () -> ChestLoot.read(rows, 100));
        rows.set(0, row()); rows.get(0).getAsJsonObject().addProperty("chunk", -1);
        assertThrows(IOException.class, () -> ChestLoot.read(rows, 100));
    }

    private static ChestLoot loot(int time, int world, String dimension) {
        return new ChestLoot(time, time, world, 0, 42, dimension, "minecraft:chests/ruined_portal", 123);
    }
    private static JsonObject row() {
        JsonObject row = new JsonObject(), loot = new JsonObject();
        row.addProperty("time", 10); row.addProperty("chunk", 1); row.addProperty("world", 0);
        loot.addProperty("pos", 0); loot.addProperty("state", 42); loot.addProperty("dimension", "minecraft:overworld");
        loot.addProperty("table", "minecraft:chests/ruined_portal"); loot.addProperty("seed", Long.MIN_VALUE);
        row.add("loot", loot); return row;
    }
}
