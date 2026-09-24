// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class ChestOpening {
    final int time, opening, world, syncId, stateFirst, stateSecond;
    final long first, second;
    final String dimension;

    ChestOpening(int time, int opening, int world, int syncId, String dimension,
                 long first, long second, int stateFirst, int stateSecond) {
        this.time = time; this.opening = opening; this.world = world; this.syncId = syncId;
        this.dimension = dimension; this.first = first; this.second = second;
        this.stateFirst = stateFirst; this.stateSecond = stateSecond;
    }

    int slots() { return first == second ? 27 : 54; }
    String key(long pos) { return key(world, dimension, pos); }
    static String key(int world, String dimension, long pos) { return world + ":" + dimension + ":" + pos; }

    static List<ChestOpening> read(JsonArray array, int duration) throws IOException {
        List<ChestOpening> result = new ArrayList<>();
        if (array == null) return result;
        if (array.size() > 4096) throw new IOException("Too many chest openings");
        int previous = 0, previousTime = 0;
        try {
            for (int i = 0; i < array.size(); i++) {
                JsonObject row = array.get(i).getAsJsonObject();
                ChestOpening value = new ChestOpening(integer(row, "time"), integer(row, "opening"), integer(row, "world"),
                        integer(row, "syncId"), row.get("dimension").getAsString(), number(row, "first"), number(row, "second"),
                        integer(row, "stateFirst"), integer(row, "stateSecond"));
                if (value.time < previousTime || value.time > duration || value.opening <= previous || value.world < 0
                        || value.syncId < 1 || value.syncId > 100 || value.stateFirst < 0 || value.stateSecond < 0
                        || !value.dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || value.dimension.length() > 128) {
                    throw new IOException("Invalid chest opening");
                }
                long a = value.first, b = value.second;
                if (a != b && ((a & 4095) != (b & 4095)
                        || Math.abs((a >> 38) - (b >> 38)) + Math.abs((a << 26 >> 38) - (b << 26 >> 38)) != 1)) {
                    throw new IOException("Invalid double chest positions");
                }
                result.add(value);
                previous = value.opening; previousTime = value.time;
            }
        } catch (RuntimeException error) { throw new IOException("Invalid chest metadata", error); }
        return result;
    }

    private static long number(JsonObject object, String key) { return object.get(key).getAsBigDecimal().longValueExact(); }
    private static int integer(JsonObject object, String key) { return Math.toIntExact(number(object, key)); }
}
