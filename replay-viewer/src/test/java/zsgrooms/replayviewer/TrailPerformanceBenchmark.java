// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in only: ZSG_TRAIL_BENCHMARK=1, then run test --tests '*TrailPerformanceBenchmark' --rerun-tasks --info.
 * Compares duplicate request coalescing with the frozen original request method, without a disk worker.
 * Reflection, equality checks, warmup, calibration and reporting are outside measured batches.
 * CPU samples under 100 ms are flagged, not asserted. Allocation counters include the complete request method.
 */
@EnabledIfEnvironmentVariable(named = "ZSG_TRAIL_BENCHMARK", matches = "1")
class TrailPerformanceBenchmark {
    private static final int REQUEST_OPERATIONS = 120_000, WARMUPS = 3, ROUNDS = 6;
    private static final int MAX_BATCH_MULTIPLIER = 16;
    private static final long MIN_BATCH_CPU = 100_000_000L;
    private static volatile Object sink;

    @Test void originalVersusActualPendingRequestCoalescing() throws Throwable {
        Counters counters = new Counters();
        environment();
        requestScenario(1, counters);
        requestScenario(32, counters);
    }

    private static void environment() {
        System.out.printf(Locale.ROOT, "TRAIL JVM=%s heap=%d request-min-ops=%d rounds=%d min-batch-cpu-ns=%d%n",
                System.getProperty("java.version"), Runtime.getRuntime().maxMemory(),
                REQUEST_OPERATIONS, ROUNDS, MIN_BATCH_CPU);
    }

    private void requestScenario(int entities, Counters counters) throws Throwable {
        Set<UUID> selected = new LinkedHashSet<>();
        for (int i = 0; i < entities; i++) selected.add(new UUID(13, i));
        Thread dormant = new Thread(() -> { throw new AssertionError("Benchmark worker must never start"); }, "trail-benchmark-placeholder");
        Field worker = field(TrailIndex.class, "worker"), pending = field(TrailIndex.class, "pending");
        OriginalRequests before = new OriginalRequests(dormant);
        try (TrailIndex after = new TrailIndex(null, null)) {
            // A non-null, never-started worker bypasses thread creation without changing the real request method.
            worker.set(after, dormant);
            before.request(7000, selected, Integer.MAX_VALUE);
            after.request(7000, selected, Integer.MAX_VALUE);
            OriginalRequests.Request originalPending = before.pending;
            Object actualPending = pending.get(after);
            assertNotNull(actualPending);
            Field time = field(actualPending.getClass(), "time"), duration = field(actualPending.getClass(), "duration");
            Field selection = field(actualPending.getClass(), "entities");
            assertEquals(originalPending.time, time.getInt(actualPending));
            assertEquals(originalPending.duration, duration.getInt(actualPending));
            assertEquals(originalPending.entities, selection.get(actualPending));
            assertNotSame(selected, selection.get(actualPending));
            assertEquals(Thread.State.NEW, dormant.getState());
            Operation original = n -> { before.request(7000, selected, 30_000); return before; };
            Operation current = n -> { after.request(7000, selected, 30_000); return after; };
            compare("pending-request-" + entities, counters, original, current, REQUEST_OPERATIONS);
            assertSame(originalPending, before.pending);
            assertSame(actualPending, pending.get(after));
            assertSame(dormant, worker.get(after));
            assertEquals(Thread.State.NEW, dormant.getState());
            assertEquals(before.status, after.status);
        } finally {
            sink = null;
        }
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void compare(String name, Counters counters, Operation original, Operation current, int minimum) throws Throwable {
        for (int round = 0; round < WARMUPS; round++) {
            batch(counters, (round & 1) == 0 ? original : current, minimum);
            batch(counters, (round & 1) == 0 ? current : original, minimum);
        }
        int operations = minimum;
        for (int attempt = 0; ; attempt++) {
            Result a, b;
            if ((attempt & 1) == 0) { a = batch(counters, original, operations); b = batch(counters, current, operations); }
            else { b = batch(counters, current, operations); a = batch(counters, original, operations); }
            System.out.printf(Locale.ROOT, "TRAIL CALIBRATE %s ops=%d original-cpu-ns=%d current-cpu-ns=%d%n",
                    name, operations, a.cpu, b.cpu);
            if (Math.min(a.cpu, b.cpu) >= MIN_BATCH_CPU || operations >= minimum * MAX_BATCH_MULTIPLIER) break;
            operations *= 2;
        }
        long originalCpu = 0, currentCpu = 0, originalBytes = 0, currentBytes = 0, originalWall = 0, currentWall = 0;
        for (int round = 0; round < ROUNDS; round++) {
            Result a, b;
            if ((round & 1) == 0) { a = batch(counters, original, operations); b = batch(counters, current, operations); }
            else { b = batch(counters, current, operations); a = batch(counters, original, operations); }
            report("ROUND " + round, name, "original", a, operations);
            report("ROUND " + round, name, "current", b, operations);
            originalCpu += a.cpu; currentCpu += b.cpu;
            originalBytes += a.allocated; currentBytes += b.allocated;
            originalWall += a.wall; currentWall += b.wall;
        }
        report("TOTAL", name, "original", new Result(originalCpu, originalBytes, originalWall), operations * ROUNDS);
        report("TOTAL", name, "current", new Result(currentCpu, currentBytes, currentWall), operations * ROUNDS);
    }

    private static Result batch(Counters counters, Operation operation, int operations) throws Throwable {
        long allocated = counters.allocated(), cpu = counters.thread.getCurrentThreadCpuTime(), wall = System.nanoTime();
        for (int n = 0; n < operations; n++) sink = operation.run(n);
        long usedWall = System.nanoTime() - wall, usedCpu = counters.thread.getCurrentThreadCpuTime() - cpu;
        long usedBytes = counters.allocated() - allocated;
        return new Result(usedCpu, usedBytes, usedWall);
    }

    private static void report(String label, String name, String version, Result result, int operations) {
        System.out.printf(Locale.ROOT,
                "TRAIL %s %s %s ops=%d thread-cpu-ns=%d thread-cpu-ns/op=%.1f allocated-B/op=%.1f wall-ns/op=%.1f cpu-sample-short=%s%n",
                label, name, version, operations, result.cpu, result.cpu / (double) operations,
                result.allocated / (double) operations, result.wall / (double) operations, result.cpu < MIN_BATCH_CPU);
    }

    // Frozen original request path and Request value type; the worker is always a never-started placeholder.
    private static final class OriginalRequests {
        private static final int MAX_DURATION = TrailIndex.MAX_DURATION, MAX_SELECTED = TrailIndex.MAX_SELECTED;
        volatile String status = "Trail history not requested";
        private final String unavailable;
        private volatile boolean closed, failed;
        private volatile TrailDiskStore.Window window;
        private Request pending;
        private Thread worker;

        OriginalRequests(Thread worker) { this.worker = worker; unavailable = null; }

        synchronized void request(int time, Set<UUID> entities, int durationMs) {
            if (closed || failed) return;
            if (!valid(time, entities)) { status = "Too many selected trail entities or invalid time"; return; }
            if (unavailable != null) { status = unavailable; failed = true; return; }
            int duration = duration(durationMs);
            TrailDiskStore.Window prepared = window;
            if (prepared != null && prepared.covers(time, entities, duration) && time < prepared.end - 1000) return;
            Request value = new Request(time, entities, duration);
            if (pending == null || !pending.equals(value)) pending = value;
            if (worker == null) {
                status = "Indexing trail history...";
                worker = new Thread(this::run, "ZSG replay trails");
                worker.setDaemon(true);
                worker.start();
            }
            notifyAll();
        }

        private static boolean valid(int time, Set<UUID> entities) {
            if (time < 0 || entities == null || entities.size() > MAX_SELECTED) return false;
            for (UUID entity : entities) if (entity == null) return false;
            return true;
        }

        private static int duration(int duration) { return Math.max(0, Math.min(MAX_DURATION, duration)); }

        private void run() { throw new AssertionError("Benchmark worker must never start"); }

        private static final class Request {
            final int time, duration;
            final Set<UUID> entities;
            Request(int time, Set<UUID> entities, int duration) {
                this.time = time; this.duration = duration;
                this.entities = Collections.unmodifiableSet(new HashSet<>(entities));
            }
            @Override public boolean equals(Object other) {
                if (!(other instanceof Request)) return false;
                Request value = (Request) other;
                return time == value.time && duration == value.duration && entities.equals(value.entities);
            }
            @Override public int hashCode() { return Objects.hash(time, duration, entities); }
        }
    }

    private interface Operation { Object run(int n); }

    private static final class Result {
        final long cpu, allocated, wall;
        Result(long cpu, long allocated, long wall) { this.cpu = cpu; this.allocated = allocated; this.wall = wall; }
    }

    private static final class Counters {
        final ThreadMXBean thread = ManagementFactory.getThreadMXBean();
        final long threadId = Thread.currentThread().getId();
        final MethodHandle allocatedBytes;

        Counters() throws ReflectiveOperationException {
            assertTrue(thread.isCurrentThreadCpuTimeSupported(), "Benchmark requires thread CPU counters");
            if (!thread.isThreadCpuTimeEnabled()) thread.setThreadCpuTimeEnabled(true);
            // Resolve the optional HotSpot counter at runtime, keeping the test source Java 8 API compatible.
            Class<?> extended = Class.forName("com.sun.management.ThreadMXBean");
            assertTrue(extended.isInstance(thread), "Benchmark requires thread allocation counters");
            assertEquals(Boolean.TRUE, extended.getMethod("isThreadAllocatedMemorySupported").invoke(thread));
            if (!Boolean.TRUE.equals(extended.getMethod("isThreadAllocatedMemoryEnabled").invoke(thread)))
                extended.getMethod("setThreadAllocatedMemoryEnabled", boolean.class).invoke(thread, true);
            allocatedBytes = MethodHandles.publicLookup()
                    .unreflect(extended.getMethod("getThreadAllocatedBytes", long.class)).bindTo(thread);
        }

        long allocated() throws Throwable { return (long) allocatedBytes.invokeExact(threadId); }
    }
}
