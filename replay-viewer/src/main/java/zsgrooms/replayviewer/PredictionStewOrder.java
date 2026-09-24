// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class PredictionStewOrder {
    static List<String> read(JsonArray array) throws IOException {
        if (array == null) return Collections.emptyList();
        if (array.size() != 6) throw new IOException("Invalid stew effect order");
        Set<String> remaining = new HashSet<>(Arrays.asList("minecraft:jump_boost", "minecraft:weakness", "minecraft:poison",
                "minecraft:night_vision", "minecraft:blindness", "minecraft:saturation"));
        List<String> order = new ArrayList<>(6);
        for (JsonElement entry : array) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString() || !remaining.remove(entry.getAsString()))
                throw new IOException("Invalid stew effect");
            order.add(entry.getAsString());
        }
        return Collections.unmodifiableList(order);
    }
}
