// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Session-only LRU. Values must be completed, read-only results, never file handles or workers. */
final class CompletedIndexCache<T> {
    private final int maxEntries;
    private final long maxWeight;
    private long weight;
    private final Map<Key, Value<T>> values = new LinkedHashMap<>(8, 0.75f, true);

    CompletedIndexCache(int maxEntries, long maxWeight) {
        this.maxEntries = maxEntries;
        this.maxWeight = maxWeight;
    }

    synchronized T get(Key key) {
        Value<T> value = values.get(key);
        return value == null ? null : value.result;
    }

    synchronized void put(Key key, T result, long estimate) {
        if (key == null || result == null) return;
        // A replacement must not leave an older version of this path occupying the cache.
        Iterator<Map.Entry<Key, Value<T>>> it = values.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, Value<T>> entry = it.next();
            if (entry.getKey().path.equals(key.path)) { weight -= entry.getValue().weight; it.remove(); }
        }
        if (estimate < 1 || estimate > maxWeight || maxEntries < 1) return;
        while (!values.isEmpty() && (values.size() >= maxEntries || weight > maxWeight - estimate)) {
            it = values.entrySet().iterator();
            weight -= it.next().getValue().weight;
            it.remove();
        }
        values.put(key, new Value<>(result, estimate));
        weight += estimate;
    }

    private static final class Value<T> {
        final T result;
        final long weight;
        Value(T result, long weight) { this.result = result; this.weight = weight; }
    }

    static final class Key {
        final Path path;
        final String signature;
        private Key(Path path, String signature) { this.path = path; this.signature = signature; }

        static Key read(Path input) throws IOException {
            Path path = input.toRealPath();
            BasicFileAttributes before = Files.readAttributes(path, BasicFileAttributes.class);
            if (!before.isRegularFile() || before.size() > RaceRecording.MAX_FILE) throw new IOException("Replay missing or too large");
            String signature;
            try (ZipFile zip = new ZipFile(path.toFile())) {
                if (zip.size() > 4096) throw new IOException("Too many archive entries");
                java.util.Set<String> names = new java.util.HashSet<>();
                java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    if (!names.add(entries.nextElement().getName())) throw new IOException("Duplicate archive entries");
                }
                signature = signature(zip);
            }
            BasicFileAttributes after = Files.readAttributes(path, BasicFileAttributes.class);
            if (before.size() != after.size() || !before.lastModifiedTime().equals(after.lastModifiedTime())
                    || !java.util.Objects.equals(before.fileKey(), after.fileKey())) throw new IOException("Replay changed while reading identity");
            return new Key(path, signature);
        }

        static String signature(ZipFile zip) throws IOException {
            return entry(zip, "recording.tmcpr") + ":" + entry(zip, "zsg-rooms/races.json")
                    + ":" + entry(zip, "metaData.json");
        }

        static String entry(ZipFile zip, String name) throws IOException {
            ZipEntry entry = zip.getEntry(name);
            if (entry == null || entry.getCrc() < 0 || entry.getSize() < 0) throw new IOException("Missing replay index input");
            return entry.getSize() + ":" + entry.getCrc();
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Key)) return false;
            Key key = (Key) other;
            return path.equals(key.path) && signature.equals(key.signature);
        }
        @Override public int hashCode() { return 31 * path.hashCode() + signature.hashCode(); }
    }
}
