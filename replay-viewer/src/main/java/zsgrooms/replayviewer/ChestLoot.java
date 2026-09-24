// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Loot identity observed in a particular original chunk packet, not inferred from coordinates. */
final class ChestLoot {
    final int time, chunk, world, state;
    final long pos, seed;
    final String dimension, table;

    ChestLoot(int time, int chunk, int world, long pos, int state, String dimension, String table, long seed) {
        this.time = time; this.chunk = chunk; this.world = world; this.pos = pos; this.state = state;
        this.dimension = dimension; this.table = table; this.seed = seed;
    }

    String key() { return ChestOpening.key(world, dimension, pos); }
    boolean inChunk(int x, int z) { return (pos >> 42) == x && (pos << 26 >> 42) == z; }

    static List<ChestLoot> read(JsonArray array, int duration) throws IOException {
        List<ChestLoot> result = new ArrayList<>();
        if (array == null) return result;
        if (array.size() > 2048) throw new IOException("Too many chest loot entries");
        int previousTime = 0, previousChunk = 0;
        try {
            for (int i = 0; i < array.size(); i++) {
                JsonObject row = array.get(i).getAsJsonObject(), loot = row.getAsJsonObject("loot");
                ChestLoot value = new ChestLoot(integer(row, "time"), integer(row, "chunk"), integer(row, "world"),
                        number(loot, "pos"), integer(loot, "state"), loot.get("dimension").getAsString(),
                        loot.get("table").getAsString(), number(loot, "seed"));
                if (value.time < previousTime || value.time > duration || value.chunk < 1 || value.chunk < previousChunk
                        || value.world < 0 || value.state < 0 || value.seed == 0 || value.dimension.length() > 128
                        || !value.dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || value.table.length() > 128
                        || !value.table.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IOException("Invalid chest loot entry");
                result.add(value);
                previousTime = value.time; previousChunk = value.chunk;
            }
        } catch (RuntimeException error) { throw new IOException("Invalid chest loot metadata", error); }
        return result;
    }

    private static long number(JsonObject row, String key) { return row.get(key).getAsBigDecimal().longValueExact(); }
    private static int integer(JsonObject row, String key) { return Math.toIntExact(number(row, key)); }
}
