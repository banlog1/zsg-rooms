// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class CompletedIndexCacheTest {
    @TempDir Path directory;

    private Path replay(String name, String packets, String metadata) throws Exception {
        return replay(name, packets, metadata, "header");
    }

    private Path replay(String name, String packets, String metadata, String headerValue) throws Exception {
        Path path = directory.resolve(name);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (String[] entry : new String[][]{{"recording.tmcpr", packets}, {"zsg-rooms/races.json", metadata}, {"metaData.json", headerValue}}) {
                ZipEntry header = new ZipEntry(entry[0]); header.setTime(0);
                zip.putNextEntry(header); zip.write(entry[1].getBytes(java.nio.charset.StandardCharsets.UTF_8)); zip.closeEntry();
            }
        }
        return path;
    }

    @Test void changedPacketsAndMetadataInvalidateEvenWithPreservedTimestamp() throws Exception {
        Path path = replay("a.mcpr", "aaaa", "meta");
        FileTime time = Files.getLastModifiedTime(path);
        CompletedIndexCache.Key original = CompletedIndexCache.Key.read(path);
        CompletedIndexCache<String> cache = new CompletedIndexCache<>(4, 100);
        cache.put(original, "old", 10);
        replay("a.mcpr", "bbbb", "meta"); Files.setLastModifiedTime(path, time);
        CompletedIndexCache.Key changed = CompletedIndexCache.Key.read(path);
        assertNotEquals(original, changed); assertNull(cache.get(changed));
        cache.put(changed, "new", 10); assertNull(cache.get(original));
        replay("a.mcpr", "bbbb", "edit"); Files.setLastModifiedTime(path, time);
        assertNull(cache.get(CompletedIndexCache.Key.read(path)));
        replay("a.mcpr", "bbbb", "meta", "edited header");
        assertNull(cache.get(CompletedIndexCache.Key.read(path)));
    }

    @Test void canonicalIdentityAndDifferentPaths() throws Exception {
        Path path = replay("a.mcpr", "data", "meta");
        assertEquals(CompletedIndexCache.Key.read(path), CompletedIndexCache.Key.read(directory.resolve("./a.mcpr")));
        assertNotEquals(CompletedIndexCache.Key.read(path), CompletedIndexCache.Key.read(replay("b.mcpr", "data", "meta")));
        Files.delete(path);
        assertThrows(java.io.IOException.class, () -> CompletedIndexCache.Key.read(path));
    }

    @Test void evictsLeastRecentlyUsedByCountAndWeight() throws Exception {
        CompletedIndexCache.Key a = CompletedIndexCache.Key.read(replay("a", "a", "m"));
        CompletedIndexCache.Key b = CompletedIndexCache.Key.read(replay("b", "b", "m"));
        CompletedIndexCache.Key c = CompletedIndexCache.Key.read(replay("c", "c", "m"));
        CompletedIndexCache<String> count = new CompletedIndexCache<>(2, 100);
        count.put(a, "a", 10); count.put(b, "b", 10); assertEquals("a", count.get(a));
        count.put(c, "c", 10); assertNull(count.get(b)); assertEquals("a", count.get(a));
        CompletedIndexCache<String> weight = new CompletedIndexCache<>(4, 20);
        weight.put(a, "a", 10); weight.put(b, "b", 10); weight.get(a);
        weight.put(c, "c", 11); assertNull(weight.get(a)); assertNull(weight.get(b)); assertEquals("c", weight.get(c));
        weight.put(a, "oversized", 21); assertNull(weight.get(a)); assertEquals("c", weight.get(c));
        weight.put(null, "invalid", 1); weight.put(a, null, 1); weight.put(a, "invalid", 0);
        assertNull(weight.get(a));
    }
}
