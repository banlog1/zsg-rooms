package zsgrooms.modid;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.function.LongSupplier;
import static org.junit.jupiter.api.Assertions.assertEquals;

public final class OptimizationMeasurements {
    private static volatile long sink;

    public static void compare(String label, int operations, LongSupplier before, LongSupplier after) {
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        for (int i = 0; i < 4; i++) assertEquals(before.getAsLong(), after.getAsLong());
        double[][] times = new double[2][7], bytes = new double[2][7];
        long thread = Thread.currentThread().getId();
        for (int round = 0; round < 7; round++) for (int step = 0; step < 2; step++) {
            int side = (round + step) % 2;
            long allocated = bean.getThreadAllocatedBytes(thread), start = System.nanoTime();
            sink = (side == 0 ? before : after).getAsLong();
            times[side][round] = (System.nanoTime() - start) / (double) operations;
            bytes[side][round] = (bean.getThreadAllocatedBytes(thread) - allocated) / (double) operations;
        }
        for (int side = 0; side < 2; side++) { Arrays.sort(times[side]); Arrays.sort(bytes[side]); }
        System.out.printf(java.util.Locale.ROOT,
                "%s: median before %.1f ns/op, %.1f B/op; after %.1f ns/op, %.1f B/op%n",
                label, times[0][3], bytes[0][3], times[1][3], bytes[1][3]);
    }
}
