// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable snapshots after indexing; queries never depend on playback/Quick Mode packet order. */
final class ChestHistory<T> {
    private final Map<String, List<Frame<T>>> frames = new HashMap<>();
    private final Map<String, ChestOpening> live = new HashMap<>();
    private ChestOpening active;
    private List<T> contents;
    private int storedSlots;

    void open(ChestOpening opening, int time) throws IOException {
        active = opening;
        contents = null;
        if (opening == null) return;
        invalidate(opening.world, opening.dimension, opening.first, time);
        invalidate(opening.world, opening.dimension, opening.second, time);
        active = opening;
        live.put(opening.key(opening.first), opening);
        live.put(opening.key(opening.second), opening);
        append(opening, time, null);
    }

    void close() { active = null; contents = null; }
    boolean accepts(int syncId) { return active != null && active.syncId == syncId; }

    void inventory(int syncId, List<T> items, int time) throws IOException {
        if (active == null || syncId != active.syncId || items.size() != active.slots() + 36) return;
        contents = new ArrayList<>(items.subList(0, active.slots()));
        append(active, time, contents);
    }

    void slot(int syncId, int slot, T item, int time) throws IOException {
        if (active == null || contents == null || syncId != active.syncId || slot < 0 || slot >= active.slots()) return;
        contents = new ArrayList<>(contents);
        contents.set(slot, item);
        append(active, time, contents);
    }

    void block(int world, String dimension, long pos, int state, int time) throws IOException {
        ChestOpening opening = live.get(ChestOpening.key(world, dimension, pos));
        if (opening != null && state != (pos == opening.first ? opening.stateFirst : opening.stateSecond)) {
            invalidate(world, dimension, pos, time);
        }
    }

    void chunk(int world, String dimension, int x, int z, int time) throws IOException {
        if (live.isEmpty()) return;
        // Chunk replacement cannot establish container identity. Fail closed until another opening.
        for (ChestOpening opening : new ArrayList<>(live.values())) {
            if (opening.world == world && opening.dimension.equals(dimension)
                    && (inChunk(opening.first, x, z) || inChunk(opening.second, x, z))) {
                invalidate(world, dimension, opening.first, time);
            }
        }
    }

    private static boolean inChunk(long pos, int x, int z) { return (pos >> 42) == x && (pos << 26 >> 42) == z; }

    private void invalidate(int world, String dimension, long pos, int time) throws IOException {
        ChestOpening old = live.remove(ChestOpening.key(world, dimension, pos));
        if (old == null) return;
        live.remove(old.key(old.first)); live.remove(old.key(old.second));
        append(old, time, null);
        if (active == old) close();
    }

    private void append(ChestOpening opening, int time, List<T> items) throws IOException {
        storedSlots += items == null ? 1 : items.size();
        if (storedSlots > 1000000) throw new IOException("Chest history limit reached");
        Frame<T> frame = new Frame<>(time, opening, items == null ? null : Collections.unmodifiableList(items));
        frames.computeIfAbsent(opening.key(opening.first), key -> new ArrayList<>()).add(frame);
        if (opening.first != opening.second) frames.computeIfAbsent(opening.key(opening.second), key -> new ArrayList<>()).add(frame);
    }

    Frame<T> at(int world, String dimension, long pos, int time) {
        List<Frame<T>> history = frames.get(ChestOpening.key(world, dimension, pos));
        if (history == null) return null;
        int low = 0, high = history.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (history.get(mid).time <= time) low = mid + 1; else high = mid;
        }
        return low == 0 ? null : history.get(low - 1);
    }

    static final class Frame<T> {
        final int time;
        final ChestOpening opening;
        final List<T> items;
        Frame(int time, ChestOpening opening, List<T> items) { this.time = time; this.opening = opening; this.items = items; }
    }
}
