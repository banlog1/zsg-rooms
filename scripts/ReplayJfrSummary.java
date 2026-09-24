import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Developer tool: run with a JDK 17+ source launcher, not shipped in either mod. */
class ReplayJfrSummary {
    public static void main(String[] args) throws Exception {
        for (String path : args) summarize(path);
    }

    private static void summarize(String path) throws Exception {
        Map<String, long[]> groups = new HashMap<>();
        Map<String, Long> allocationSites = new HashMap<>(), cpuSites = new HashMap<>();
        long cpu = 0, allocationEvents = 0, gcCount = 0, gcNanos = 0, maxGc = 0, heapAfterGc = 0;
        long locks = 0, lockNanos = 0, writerReadBytes = 0, writerWriteBytes = 0;
        Instant start = Instant.MAX, end = Instant.MIN;
        try (RecordingFile recording = new RecordingFile(Paths.get(path))) {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent();
                if (event.getStartTime().isBefore(start)) start = event.getStartTime();
                if (event.getEndTime().isAfter(end)) end = event.getEndTime();
                String type = event.getEventType().getName();
                String group = group(event.getStackTrace());
                if (type.equals("jdk.ExecutionSample") || type.equals("jdk.NativeMethodSample")) {
                    cpu++;
                    if (group != null) {
                        groups.computeIfAbsent(group, key -> new long[3])[0]++;
                        cpuSites.merge(site(event), 1L, Long::sum);
                    }
                } else if (type.equals("jdk.ObjectAllocationSample")) {
                    allocationEvents++;
                    if (group != null) {
                        long weight = event.getLong("weight");
                        long[] counts = groups.computeIfAbsent(group, key -> new long[3]);
                        counts[1]++; counts[2] += weight;
                        allocationSites.merge(site(event) + " -> " + event.getClass("objectClass").getName(), weight, Long::sum);
                    }
                } else if (type.equals("jdk.GCPhasePause")) {
                    long duration = event.getDuration().toNanos();
                    gcCount++; gcNanos += duration; maxGc = Math.max(maxGc, duration);
                } else if (type.equals("jdk.GCHeapSummary") && event.getString("when").equals("After GC")) {
                    heapAfterGc = Math.max(heapAfterGc, event.getLong("heapUsed"));
                } else if (type.equals("jdk.JavaMonitorEnter") && group != null) {
                    locks++; lockNanos += event.getDuration().toNanos();
                    System.out.printf(Locale.ROOT, "Monitor %s thread=%s %.2fms %s %s%n", event.getStartTime(),
                            event.getThread().getJavaName(), event.getDuration().toNanos() / 1e6,
                            event.getClass("monitorClass").getName(), site(event));
                } else if (event.getThread() != null && "ZSG replay prototype writer".equals(event.getThread().getJavaName())) {
                    if (type.equals("jdk.FileWrite")) writerWriteBytes += event.getLong("bytesWritten");
                    if (type.equals("jdk.FileRead")) writerReadBytes += event.getLong("bytesRead");
                }
            }
        }
        System.out.println("\nJFR " + path + "\nwindow=" + start + " to " + end);
        System.out.printf(Locale.ROOT, "All JVM: CPU samples=%d allocation samples=%d GC pauses=%d total=%.1fms max=%.1fms peak-after-GC=%.1fMiB%n",
                cpu, allocationEvents, gcCount, gcNanos / 1e6, maxGc / 1e6, heapAfterGc / 1048576.0);
        groups.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            long[] value = entry.getValue();
            System.out.printf(Locale.ROOT, "%s: CPU samples=%d alloc samples=%d estimated alloc weight=%.2fMiB%n",
                    entry.getKey(), value[0], value[1], value[2] / 1048576.0);
        });
        System.out.printf(Locale.ROOT, "Recorder monitor events=%d duration=%.2fms; writer thresholded file events read=%dB write=%dB%n",
                locks, lockNanos / 1e6, writerReadBytes, writerWriteBytes);
        System.out.println("Top recorder CPU stacks (leaf <- nearest owned frame):"); top(cpuSites);
        System.out.println("Top recorder allocation weights (bytes, sampled estimates):"); top(allocationSites);
        System.out.println("JFR is sampling, not a stopwatch: zero samples do not imply zero cost. GC/heap include the whole game and startup. File/monitor events have thresholds.");
    }

    private static String group(RecordedStackTrace trace) {
        if (trace == null) return null;
        String result = null;
        for (RecordedFrame frame : trace.getFrames()) {
            String type = frame.getMethod().getType().getName(), name = frame.getMethod().getName();
            if (type.startsWith("zsgrooms.modid.replay.ReplayPrototype")) {
                if (name.equals("capture") || name.startsWith("lambda$capture")) return "packet-capture";
                if (name.equals("copyTrackedState")) return "player-metadata";
                if (name.equals("writeReplay")) result = "writer";
            }
            if (type.equals("zsgrooms.modid.replay.ReplayHudCapture")) return "HUD-capture";
            if (name.contains("captureLoot")) return "chest-loot-hook";
            if (owned(type) && result == null) result = "recorder-other";
        }
        return result;
    }

    private static boolean owned(String type) {
        return type.startsWith("zsgrooms.modid.replay.") && !type.contains("Smoke")
                && !type.contains("Calibration") && !type.contains("Benchmark")
                || type.equals("zsgrooms.replayprobe.ReplayStudioWriter");
    }

    private static String site(RecordedEvent event) {
        String leaf = "unknown", owner = "unknown";
        boolean first = true;
        for (RecordedFrame frame : event.getStackTrace().getFrames()) {
            String type = frame.getMethod().getType().getName();
            String method = type + "." + frame.getMethod().getName() + ":" + frame.getLineNumber();
            if (first) { leaf = method; first = false; }
            if (owned(type)) { owner = method; break; }
        }
        return leaf + " <- " + owner;
    }

    private static void top(Map<String, Long> entries) {
        entries.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(12).forEach(entry -> System.out.println(entry.getValue() + " " + entry.getKey()));
    }
}
