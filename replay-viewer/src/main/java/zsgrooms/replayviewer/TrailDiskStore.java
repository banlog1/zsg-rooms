// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Worker-owned append log. Only sparse per-UUID anchors, not full trajectories, live in heap. */
final class TrailDiskStore implements AutoCloseable {
    static final long MAX_BYTES = 1024L * 1024 * 1024;
    static final int MAX_ENTITIES = 100_000, MAX_BOUNDARIES = 65_536, MAX_WINDOW_POINTS = 500_000;
    static final int BREAK = 1, END = 2, RECORD_BYTES = 48, ANCHOR_STRIDE = 128;
    private static final int PAGE_BYTES = RECORD_BYTES * 1024, MAX_PAGES = 64;
    final Path path;
    private final long limit;
    private final Map<UUID, Directory> directory = new HashMap<>();
    private final List<Integer> boundaries = new ArrayList<>();
    private final LinkedHashMap<Long, byte[]> pages = new LinkedHashMap<>(64, .75f, true);
    private DataOutputStream output;
    private FileChannel input;
    private long bytes;
    private int epoch;

    TrailDiskStore(Path parent) throws IOException { this(parent, MAX_BYTES); }

    TrailDiskStore(Path parent, long limit) throws IOException {
        this.limit = limit;
        path = parent == null ? Files.createTempFile("zsg-trails-", ".tmp")
                : Files.createTempFile(parent, "zsg-trails-", ".tmp");
        try { output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path), 65536)); }
        catch (IOException e) { Files.deleteIfExists(path); throw e; }
    }

    void boundary(int time) throws IOException {
        if (boundaries.size() >= MAX_BOUNDARIES) throw new IOException("Too many trail world boundaries");
        boundaries.add(time);
        epoch++;
    }

    void append(UUID uuid, int time, double x, double y, double z, int flags) throws IOException {
        if (output == null) throw new IOException("Trail store is not writable");
        if (bytes > limit - RECORD_BYTES) throw new IOException("Trail scratch limit reached");
        Directory entry = directory.get(uuid);
        if (entry == null) {
            if (directory.size() >= MAX_ENTITIES) throw new IOException("Too many recorded trail entities");
            directory.put(uuid, entry = new Directory());
        }
        output.writeLong(entry.head);
        output.writeInt(time); output.writeInt(epoch);
        output.writeDouble(x); output.writeDouble(y); output.writeDouble(z);
        output.writeInt(flags); output.writeInt(0);
        entry.head = bytes;
        if (entry.count++ % ANCHOR_STRIDE == 0) entry.anchors.add(new Anchor(time, bytes));
        bytes += RECORD_BYTES;
    }

    void finish() throws IOException {
        output.close(); output = null;
        input = FileChannel.open(path, StandardOpenOption.READ);
    }

    Window window(int start, int end, Set<UUID> selected, BooleanSupplier cancelled) throws IOException {
        if (input == null) throw new IOException("Trail store is incomplete");
        Map<UUID, List<Record>> result = new HashMap<>();
        int count = 0;
        for (UUID uuid : selected) {
            SplitMilestoneReader.checkCancelled(cancelled);
            Directory entry = directory.get(uuid);
            if (entry == null) continue;
            // Start at the first sparse anchor AFTER end, or the head. At most 128 future records are visited.
            int lo = 0, hi = entry.anchors.size();
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (entry.anchors.get(mid).time <= end) lo = mid + 1; else hi = mid;
            }
            long offset = lo < entry.anchors.size() ? entry.anchors.get(lo).offset : entry.head;
            List<Record> points = new ArrayList<>();
            while (offset >= 0) {
                SplitMilestoneReader.checkCancelled(cancelled);
                Record record = read(offset);
                if (record.time <= end) {
                    if (++count > MAX_WINDOW_POINTS) throw new IOException("Trail history window is too dense");
                    points.add(record);
                    if (record.time < start) break;
                }
                offset = record.previous;
            }
            Collections.reverse(points);
            if (!points.isEmpty()) result.put(uuid, Collections.unmodifiableList(points));
        }
        int[] epochs = new int[boundaries.size()];
        for (int i = 0; i < epochs.length; i++) epochs[i] = boundaries.get(i);
        return new Window(start, end, selected, Collections.unmodifiableMap(result), epochs);
    }

    private Record read(long offset) throws IOException {
        if (offset < 0 || offset % RECORD_BYTES != 0 || offset > bytes - RECORD_BYTES)
            throw new IOException("Invalid trail offset");
        long page = offset / PAGE_BYTES;
        byte[] buffer = pages.get(page);
        if (buffer == null) {
            if (pages.size() >= MAX_PAGES) {
                Long oldest = pages.keySet().iterator().next();
                buffer = pages.remove(oldest);
            } else buffer = new byte[PAGE_BYTES];
            ByteBuffer target = ByteBuffer.wrap(buffer, 0, (int) Math.min(PAGE_BYTES, bytes - page * PAGE_BYTES));
            long position = page * PAGE_BYTES;
            while (target.hasRemaining()) {
                int n = input.read(target, position);
                if (n <= 0) throw new IOException("Truncated trail scratch file");
                position += n;
            }
            pages.put(page, buffer);
        }
        ByteBuffer data = ByteBuffer.wrap(buffer, (int) (offset % PAGE_BYTES), RECORD_BYTES);
        long previous = data.getLong();
        if (previous >= offset || previous < -1) throw new IOException("Invalid trail chain");
        return new Record(previous, data.getInt(), data.getInt(), data.getDouble(), data.getDouble(), data.getDouble(), data.getInt());
    }

    @Override public void close() throws IOException {
        IOException failure = null;
        try { if (output != null) output.close(); } catch (IOException e) { failure = e; }
        try { if (input != null) input.close(); } catch (IOException e) { failure = e; }
        output = null; input = null;
        directory.clear(); boundaries.clear(); pages.clear();
        try { Files.deleteIfExists(path); } catch (IOException e) { failure = e; }
        if (failure != null) throw failure;
    }

    private static final class Directory {
        long head = -1;
        int count;
        final List<Anchor> anchors = new ArrayList<>();
    }

    private static final class Anchor {
        final int time;
        final long offset;
        Anchor(int time, long offset) { this.time = time; this.offset = offset; }
    }

    static final class Record {
        final long previous;
        final int time, epoch, flags;
        final double x, y, z;
        final TrailPoint point;
        Record(long previous, int time, int epoch, double x, double y, double z, int flags) {
            this.previous = previous; this.time = time; this.epoch = epoch;
            this.x = x; this.y = y; this.z = z; this.flags = flags;
            point = new TrailPoint(time, x, y, z, (flags & BREAK) != 0);
        }
    }

    static final class Window {
        final int start, end;
        final Set<UUID> selected;
        final Map<UUID, List<Record>> records;
        final int[] boundaries;

        Window(int start, int end, Set<UUID> selected, Map<UUID, List<Record>> records, int[] boundaries) {
            this.start = start; this.end = end; this.selected = Collections.unmodifiableSet(new java.util.HashSet<>(selected));
            this.records = records; this.boundaries = boundaries;
        }

        boolean covers(int time, Set<UUID> entities, int duration) {
            return time >= start && time <= end && Math.max(0, time - duration) >= start && selected.containsAll(entities);
        }

        Map<UUID, List<TrailPoint>> snapshot(int time, Set<UUID> entities, int duration) {
            if (!covers(time, entities, duration)) return null;
            int first = Math.max(0, time - duration), epoch = epoch(time);
            Map<UUID, List<TrailPoint>> result = new HashMap<>();
            for (UUID uuid : entities) {
                List<Record> source = records.get(uuid);
                if (source == null) continue;
                int lo = 0, hi = source.size();
                while (lo < hi) {
                    int mid = (lo + hi) >>> 1;
                    if (source.get(mid).time < first) lo = mid + 1; else hi = mid;
                }
                List<TrailPoint> points = new ArrayList<>();
                Record last = lo > 0 ? source.get(lo - 1) : null;
                boolean known = last != null && last.epoch == epoch;
                boolean alive = last != null && last.epoch == epoch && (last.flags & END) == 0;
                if (alive) points.add(new TrailPoint(first, last.x, last.y, last.z, true));
                for (int i = lo; i < source.size(); i++) {
                    Record record = source.get(i);
                    if (record.time > time) break;
                    if (record.epoch != epoch) continue;
                    known = true;
                    if ((record.flags & END) != 0) {
                        if (alive) points.add(record.point);
                        alive = false;
                    } else {
                        points.add(!alive && !record.point.breakBefore
                                ? new TrailPoint(record.time, record.x, record.y, record.z, true) : record.point);
                        alive = true;
                    }
                    last = record;
                }
                if (alive && last != null && !points.isEmpty() && points.get(points.size() - 1).time < time)
                    points.add(new TrailPoint(time, last.x, last.y, last.z, false));
                if (known) result.put(uuid, Collections.unmodifiableList(points));
            }
            return Collections.unmodifiableMap(result);
        }

        private int epoch(int time) {
            int lo = 0, hi = boundaries.length;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (boundaries[mid] <= time) lo = mid + 1; else hi = mid;
            }
            return lo;
        }
    }
}
