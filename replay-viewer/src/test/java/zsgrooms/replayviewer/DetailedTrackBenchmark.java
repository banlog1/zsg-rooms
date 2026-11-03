// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in disk-index lookup benchmark, with a fresh, synthetic three-hour detail track. */
@EnabledIfSystemProperty(named = "zsgrooms.viewerDetailBenchmark", matches = "true")
class DetailedTrackBenchmark {
    private static final int FRAMES = 216000, QUERIES = 8192;
    private static volatile long sink;

    @Test void profileLookups() throws Exception {
        com.sun.management.ThreadMXBean metrics = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertTrue(metrics.isThreadAllocatedMemorySupported());
        metrics.setThreadAllocatedMemoryEnabled(true);
        metrics.setThreadCpuTimeEnabled(true);
        Path source = Files.createTempFile("zsg-detail-benchmark-", ".bin");
        try {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(source)))) {
                out.writeInt(DetailedTrack.MAGIC); out.writeInt(1);
                byte[] payload = new byte[256];
                payload[0] = 1; payload[4] = 16;
                for (int i = 0; i < FRAMES; i++) {
                    out.writeInt(i * 50); out.writeInt(0); out.writeInt(7);
                    out.writeInt(payload.length); out.write(payload);
                }
            }
            try (InputStream input = Files.newInputStream(source);
                 DetailedTrack track = DetailedTrack.read(input, FRAMES * 50);
                 OriginalLookup original = new OriginalLookup(track)) {
                for (String mode : new String[]{"sequential", "reverse", "random", "same-frame"}) {
                    int[] times = new int[QUERIES];
                    Random random = new Random(318721);
                    for (int i = 0; i < times.length; i++) {
                        int frame = mode.equals("random") ? random.nextInt(FRAMES)
                                : mode.equals("reverse") ? 120000 - i : mode.equals("same-frame") ? 100000 : 100000 + i;
                        times[i] = frame * 50 + 20;
                    }
                    long[][][] results = new long[2][3][9];
                    for (int round = -4; round < 9; round++) {
                      for (int order = 0; order < 2; order++) {
                        int version = (order + (round & 1)) & 1;
                        long thread = Thread.currentThread().getId();
                        long bytes = metrics.getThreadAllocatedBytes(thread), cpu = metrics.getCurrentThreadCpuTime();
                        long start = System.nanoTime(), digest = 0;
                        for (int time : times) {
                            DetailedTrack.Frame frame = version == 0 ? original.at(time) : track.at(time, 0, 7, 0);
                            if (frame == null || frame.time != time / 50 * 50 || frame.renderDistance != 16)
                                throw new AssertionError("Lookup changed at " + time);
                            digest += frame.time;
                        }
                        long elapsed = System.nanoTime() - start;
                        long usedCpu = metrics.getCurrentThreadCpuTime() - cpu;
                        long allocated = metrics.getThreadAllocatedBytes(thread) - bytes;
                        sink = digest;
                        if (round >= 0) {
                            results[version][0][round] = elapsed / QUERIES;
                            results[version][1][round] = usedCpu / QUERIES;
                            results[version][2][round] = allocated / QUERIES;
                        }
                      }
                    }
                    for (int version = 0; version < 2; version++) {
                        for (long[] result : results[version]) Arrays.sort(result);
                        System.out.printf(Locale.ROOT, "detail-lookup %s %s frames=%d queries=%d wall-ns/op=%d [%d..%d] cpu-ns/op=%d allocated-B/op=%d JVM=%s%n",
                                mode, version == 0 ? "original" : "current", FRAMES, QUERIES,
                                results[version][0][4], results[version][0][0], results[version][0][8],
                                results[version][1][4], results[version][2][4], System.getProperty("java.version"));
                    }
                }
            }
        } finally { Files.deleteIfExists(source); }
    }

    /** 1.1.0 lookup, using independent read handles on the same immutable fixture files. */
    private static final class OriginalLookup implements AutoCloseable {
        private final RandomAccessFile data, index;
        private final int[] times = new int[256];
        private final long[] offsets = new long[256];
        private final byte[] bytes = new byte[256 * 12];
        private int pageStart = -1, pageLength, cachedEnd;
        private DetailedTrack.Frame cached;

        OriginalLookup(DetailedTrack track) throws Exception {
            data = new RandomAccessFile(path(track, "dataPath").toFile(), "r");
            index = new RandomAccessFile(path(track, "indexPath").toFile(), "r");
        }

        private static Path path(DetailedTrack track, String name) throws Exception {
            java.lang.reflect.Field field = DetailedTrack.class.getDeclaredField(name);
            field.setAccessible(true);
            return (Path) field.get(track);
        }

        synchronized DetailedTrack.Frame at(int time) throws Exception {
            if (cached == null || time < cached.time || time >= cachedEnd) {
                int low = 0, high = FRAMES;
                while (low < high) {
                    int mid = (low + high) >>> 1;
                    if (timeAt(mid) <= time) low = mid + 1; else high = mid;
                }
                if (low == 0) return null;
                int slot = low - 1;
                cachedEnd = low == FRAMES ? Integer.MAX_VALUE : timeAt(low);
                page(slot);
                data.seek(offsets[slot - pageStart]);
                byte[] header = new byte[16];
                data.readFully(header);
                java.nio.ByteBuffer fields = java.nio.ByteBuffer.wrap(header);
                int frameTime = fields.getInt(), world = fields.getInt(), entity = fields.getInt();
                byte[] payload = new byte[fields.getInt()];
                data.readFully(payload);
                cached = new DetailedTrack.Frame(frameTime, world, entity, payload);
            }
            return cached.world == 0 && cached.entity == 7 && cached.time >= 0
                    && time - cached.time <= 1500 && cached.payload.length > 0 ? cached : null;
        }

        private int timeAt(int slot) throws Exception { page(slot); return times[slot - pageStart]; }

        private void page(int slot) throws Exception {
            if (pageStart >= 0 && slot >= pageStart && slot < pageStart + pageLength) return;
            pageStart = slot / 256 * 256;
            pageLength = Math.min(256, FRAMES - pageStart);
            index.seek(pageStart * 12L);
            index.readFully(bytes, 0, pageLength * 12);
            java.nio.ByteBuffer fields = java.nio.ByteBuffer.wrap(bytes);
            for (int i = 0; i < pageLength; i++) { times[i] = fields.getInt(); offsets[i] = fields.getLong(); }
        }

        @Override public void close() throws Exception {
            try { data.close(); } finally { index.close(); }
        }
    }
}
