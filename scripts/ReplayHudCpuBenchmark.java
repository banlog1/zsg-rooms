package zsgrooms.modid.replay;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.zip.ZipFile;

/** Standalone developer benchmark, compiled against either unchanged source version. */
public final class ReplayHudCpuBenchmark {
    private static final java.lang.management.ThreadMXBean THREAD = ManagementFactory.getThreadMXBean();
    private static final com.sun.management.OperatingSystemMXBean PROCESS =
            (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    private static final Field FRAMES = field("frames"), BYTES = field("bytes");
    private static volatile byte[] sink;

    public static void main(String[] args) throws Exception {
        if (!THREAD.isCurrentThreadCpuTimeSupported()) throw new IllegalStateException("No thread CPU counter");
        THREAD.setThreadCpuTimeEnabled(true);
        String version = args[0];
        System.out.printf("JVM=%s processors=%d mode=%s heap=%d%n", System.getProperty("java.version"),
                Runtime.getRuntime().availableProcessors(), version, Runtime.getRuntime().maxMemory());
        if (args.length > 1) {
            try (ZipFile replay = new ZipFile(args[1]);
                 DataInputStream input = new DataInputStream(replay.getInputStream(replay.getEntry("zsg-rooms/player-hud.bin")))) {
                if (input.readInt() != ReplayHudTrack.MAGIC || input.readInt() != 1) throw new AssertionError("HUD header");
                List<Row> rows = new ArrayList<>();
                long left = replay.getEntry("zsg-rooms/player-hud.bin").getSize() - 8;
                while (left > 0) {
                    int time = input.readInt(), world = input.readInt(), entity = input.readInt(), length = input.readInt();
                    if (length < 0 || length > ReplayHudTrack.MAX_FRAME || left < length + 16) throw new AssertionError("HUD frame");
                    byte[] payload = new byte[length];
                    input.readFully(payload);
                    rows.add(new Row(time, world, entity, payload));
                    left -= length + 16;
                }
                measure(version, "live-HUD", rows, 65536);
            }
        }
        measure(version, "12000x64", synthetic(12000, 64), 1024);
        measure(version, "4000x4096", synthetic(4000, 4096), 256);
    }

    private static void measure(String version, String name, List<Row> rows, int operations) throws Exception {
        ReplayHudTrack prototype = new ReplayHudTrack();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream expected = new DataOutputStream(bytes);
        expected.writeInt(ReplayHudTrack.MAGIC);
        expected.writeInt(1);
        for (Row row : rows) {
            prototype.add(row.time, row.world, row.entity, row.payload);
            expected.writeInt(row.time);
            expected.writeInt(row.world);
            expected.writeInt(row.entity);
            expected.writeInt(row.payload.length);
            expected.write(row.payload);
        }
        byte[] expectedBytes = bytes.toByteArray();
        if (!Arrays.equals(expectedBytes, copies(prototype, 1)[0].finish(Long.MAX_VALUE))) {
            throw new AssertionError("Fixture output mismatch: " + name);
        }
        System.out.printf("FIXTURE %s bytes=%d frames=%d sha256=%s%n", name, expectedBytes.length, rows.size(), hash(expectedBytes));

        // Fixed batch sizes keep prebuilt fixture memory identical across source versions and forks.
        // Frames and their immutable payloads are shared only in this test fixture.
        if ((long) rows.size() * operations > 24000000L) throw new IllegalArgumentException("Fixture too large");
        // Warm the exact batch shape, then alternate process order in the PowerShell runner.
        for (int warmup = 0; warmup < 3; warmup++) batch(prototype, operations);
        long totalThread = 0, totalProcess = 0, totalWall = 0;
        for (int round = 0; round < 7; round++) {
            Result result = batch(prototype, operations);
            if (result.thread < 100000000L) throw new AssertionError("Increase batch size for CPU timer resolution");
            totalThread += result.thread;
            totalProcess += result.process;
            totalWall += result.wall;
            if (!Arrays.equals(expectedBytes, sink)) throw new AssertionError("Output mismatch: " + name);
            System.out.printf(Locale.ROOT,
                    "ROUND %s %s index=%d ops=%d thread-cpu-ns=%d process-cpu-ns=%d wall-ns=%d%n",
                    version, name, round, operations, result.thread, result.process, result.wall);
        }
        System.out.printf(Locale.ROOT,
                "RESULT %s %s ops=%d thread-cpu-us/op=%.6f process-cpu-us/op=%.6f wall-us/op=%.6f%n",
                version, name, operations * 7, totalThread / (operations * 7000.0),
                totalProcess / (operations * 7000.0), totalWall / (operations * 7000.0));
    }

    private static Result batch(ReplayHudTrack prototype, int count) throws Exception {
        ReplayHudTrack[] tracks = copies(prototype, count);
        // Reflection, fixture construction, comparison and logging are outside the measured interval.
        long cpu = THREAD.getCurrentThreadCpuTime(), process = PROCESS.getProcessCpuTime(), start = System.nanoTime();
        for (ReplayHudTrack track : tracks) sink = track.finish(Long.MAX_VALUE);
        long wall = System.nanoTime() - start, processUsed = PROCESS.getProcessCpuTime() - process;
        return new Result(THREAD.getCurrentThreadCpuTime() - cpu, processUsed, wall);
    }

    @SuppressWarnings("unchecked")
    private static ReplayHudTrack[] copies(ReplayHudTrack prototype, int count) throws Exception {
        List<Object> frames = (List<Object>) FRAMES.get(prototype);
        int bytes = BYTES.getInt(prototype);
        ReplayHudTrack[] tracks = new ReplayHudTrack[count];
        for (int i = 0; i < count; i++) {
            ReplayHudTrack track = tracks[i] = new ReplayHudTrack();
            ((List<Object>) FRAMES.get(track)).addAll(frames);
            BYTES.setInt(track, bytes);
        }
        return tracks;
    }

    private static List<Row> synthetic(int count, int size) {
        List<Row> rows = new ArrayList<>();
        Random random = new Random(928);
        for (int i = 0; i < count; i++) {
            byte[] payload = new byte[size];
            random.nextBytes(payload);
            rows.add(new Row(i * 200, i, i, payload));
        }
        return rows;
    }

    private static String hash(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) result.append(String.format("%02x", value & 255));
        return result.toString();
    }

    private static Field field(String name) {
        try {
            Field field = ReplayHudTrack.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    private static final class Row {
        final int time, world, entity;
        final byte[] payload;
        Row(int time, int world, int entity, byte[] payload) {
            this.time = time; this.world = world; this.entity = entity; this.payload = payload;
        }
    }

    private static final class Result {
        final long thread, process, wall;
        Result(long thread, long process, long wall) { this.thread = thread; this.process = process; this.wall = wall; }
    }
}
