// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** At most one observation and one invalidation per bounded manifest entry. */
final class ChestLootHistory {
    private final Map<String, List<Frame>> frames = new HashMap<>();
    private final Map<String, ChestLoot> live = new HashMap<>();

    void observe(ChestLoot loot) {
        live.put(loot.key(), loot);
        append(loot.key(), loot.time, loot);
    }

    void invalidate(int world, String dimension, long pos, int time) {
        String key = ChestOpening.key(world, dimension, pos);
        if (live.remove(key) != null) append(key, time, null);
    }

    void block(int world, String dimension, long pos, int state, int time) {
        ChestLoot loot = live.get(ChestOpening.key(world, dimension, pos));
        if (loot != null && loot.state != state) invalidate(world, dimension, pos, time);
    }

    void chunk(int world, String dimension, int x, int z, int time) {
        java.util.Iterator<ChestLoot> it = live.values().iterator();
        while (it.hasNext()) {
            ChestLoot loot = it.next();
            if (loot.world == world && loot.dimension.equals(dimension) && loot.inChunk(x, z)) {
                it.remove();
                append(loot.key(), time, null);
            }
        }
    }

    private void append(String key, int time, ChestLoot loot) {
        frames.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new Frame(time, loot));
    }

    ChestLoot at(int world, String dimension, long pos, int time) {
        List<Frame> values = frames.get(ChestOpening.key(world, dimension, pos));
        if (values == null) return null;
        int low = 0, high = values.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (values.get(mid).time <= time) low = mid + 1; else high = mid;
        }
        return low == 0 ? null : values.get(low - 1).loot;
    }

    private static final class Frame {
        final int time;
        final ChestLoot loot;
        Frame(int time, ChestLoot loot) { this.time = time; this.loot = loot; }
    }
}
