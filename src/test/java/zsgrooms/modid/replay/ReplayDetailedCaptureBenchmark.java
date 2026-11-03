package zsgrooms.modid.replay;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in scratch/envelope benchmark. Does not time live client/HUD observation or the spool. */
@EnabledIfEnvironmentVariable(named = "ZSGROOMS_DETAILED_CAPTURE_BENCHMARK", matches = "true")
class ReplayDetailedCaptureBenchmark {
    private static final int WARMUPS = 4, ROUNDS = 9;
    private static final com.sun.management.ThreadMXBean METRICS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static volatile byte[] sink;

    @BeforeAll static void bootstrap() { ReplayDetailedCaptureTest.bootstrap(); }

    @Test void compareOriginalAllocationWithBoundedDetailScratchReuse() {
        assertTrue(METRICS.isThreadAllocatedMemorySupported());
        assertTrue(METRICS.isCurrentThreadCpuTimeSupported());
        METRICS.setThreadAllocatedMemoryEnabled(true);
        METRICS.setThreadCpuTimeEnabled(true);
        System.out.println("Detailed Capture JVM=" + System.getProperty("java.version")
                + "; original fresh-buffer baseline vs production scratch reuse; paired alternating order; "
                + WARMUPS + " warmup + " + ROUNDS + " measured rounds; every payload compared outside timing");
        System.out.println("Scope: detailed envelope + cached item encoding + owned payload copy; "
                + "HUD/client reads, sampling, queue and disk excluded. No production mode switch.");
        for (int[] shape : new int[][]{{0, 0}, {46, 0}, {90, 0}, {54, 16}, {90, 128}}) {
            for (int mutation : new int[]{0, 1, 2}) {
                if (shape[0] == 0 && mutation != 0) continue;
                compare(shape[0], shape[1], mutation);
            }
        }
    }

    private static void compare(int slots, int nbtSize, int mutation) {
        String name = slots + "-slot-NBT" + nbtSize + "-"
                + new String[]{"unchanged", "one-changing", "all-changing"}[mutation];
        byte[] sample = new ReplayDetailedCaptureTest.DetailFixture(slots, nbtSize).encode(false);
        int operations = Math.max(256, Math.min(10000, 16 * 1024 * 1024 / sample.length));
        long[][][] values = new long[2][3][ROUNDS];
        long thread = Thread.currentThread().getId();
        int outputBytes = 0;
        for (int round = -WARMUPS; round < ROUNDS; round++) {
            byte[][][] results = new byte[2][][];
            for (int order = 0; order < 2; order++) {
                int mode = (order + (round & 1)) & 1;
                ReplayDetailedCaptureTest.DetailFixture fixture =
                        new ReplayDetailedCaptureTest.DetailFixture(slots, nbtSize);
                // Prime item caches and scratch capacity equally; construction is outside timing.
                sink = fixture.encode(mode == 1);
                byte[][] output = results[mode] = new byte[operations][];
                long allocated = METRICS.getThreadAllocatedBytes(thread);
                long cpu = METRICS.getCurrentThreadCpuTime();
                long start = System.nanoTime();
                for (int i = 0; i < operations; i++) {
                    if (mutation != 0) fixture.changeItems(i, mutation == 2);
                    sink = output[i] = fixture.encode(mode == 1);
                }
                long wallUsed = System.nanoTime() - start;
                long cpuUsed = METRICS.getCurrentThreadCpuTime() - cpu;
                long allocatedUsed = METRICS.getThreadAllocatedBytes(thread) - allocated;
                if (round >= 0) {
                    values[mode][0][round] = wallUsed / operations;
                    values[mode][1][round] = cpuUsed / operations;
                    values[mode][2][round] = allocatedUsed / operations;
                }
            }
            for (int i = 0; i < operations; i++)
                assertArrayEquals(results[0][i], results[1][i], name + " sample=" + i);
            outputBytes = results[0][operations - 1].length;
        }
        for (int mode = 0; mode < 2; mode++) {
            for (long[] metric : values[mode]) Arrays.sort(metric);
            System.out.printf(Locale.ROOT,
                    "%s %s ops/round=%d wall-median-ns/op=%d cpu-mean-ns/op=%d "
                            + "cpu-range-ns/op=[%d..%d] allocated-median-B/op=%d payload-B=%d%n",
                    name, mode == 0 ? "baseline" : "pooled", operations, values[mode][0][ROUNDS / 2],
                    Arrays.stream(values[mode][1]).sum() / ROUNDS,
                    values[mode][1][0], values[mode][1][ROUNDS - 1],
                    values[mode][2][ROUNDS / 2], outputBytes);
        }
        ReplayEncodingBuffers.acquireDetail().release();
    }
}
