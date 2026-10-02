// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Live indexing state only. Block-update misses allocate neither strings nor boxed positions. */
final class ChestPositions<T> {
    private final Int2ObjectOpenHashMap<Map<String, Long2ObjectOpenHashMap<T>>> worlds = new Int2ObjectOpenHashMap<>();

    private Long2ObjectOpenHashMap<T> scope(int world, String dimension) {
        Map<String, Long2ObjectOpenHashMap<T>> dimensions = worlds.get(world);
        return dimensions == null ? null : dimensions.get(dimension);
    }

    T get(int world, String dimension, long pos) {
        Long2ObjectOpenHashMap<T> positions = scope(world, dimension);
        return positions == null ? null : positions.get(pos);
    }

    void put(int world, String dimension, long pos, T value) {
        Map<String, Long2ObjectOpenHashMap<T>> dimensions = worlds.get(world);
        if (dimensions == null) { dimensions = new HashMap<>(); worlds.put(world, dimensions); }
        Long2ObjectOpenHashMap<T> positions = dimensions.get(dimension);
        if (positions == null) { positions = new Long2ObjectOpenHashMap<>(); dimensions.put(dimension, positions); }
        positions.put(pos, value);
    }

    T remove(int world, String dimension, long pos) {
        Long2ObjectOpenHashMap<T> positions = scope(world, dimension);
        return positions == null ? null : positions.remove(pos);
    }

    Collection<T> values(int world, String dimension) {
        Long2ObjectOpenHashMap<T> positions = scope(world, dimension);
        return positions == null ? Collections.emptyList() : positions.values();
    }

    void clear() { worlds.clear(); }
}
