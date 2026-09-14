package zsgrooms.modid.filter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.world.gen.ChunkRandom;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/** Differential corpus and stage-only timing. No generated worlds or private seed output. */
public final class FilterStructureBenchmark {
    public static void main(String[] args) throws Exception {
        SharedConstants.getGameVersion();
        Bootstrap.initialize();
        int count = Integer.getInteger("zsgrooms.filterStructureSamples", 10000);
        if (count < 1 || count > 1000000) throw new IllegalArgumentException("Sample count must be 1-1000000");
        Path executable = Paths.get("run/filter-worker/structure-screen.exe").toAbsolutePath();
        Path reportPath = Paths.get("run/filter-worker/structure-comparison.json");
        JsonObject report = new JsonObject();
        report.addProperty("status", "RUNNING");
        report.addProperty("javaVersion", System.getProperty("java.version"));
        report.addProperty("corpus", "index-times-golden-ratio-low48-v1");
        JsonArray rows = new JsonArray();
        report.add("comparisons", rows);
        FilterProbeFiles.write(reportPath, report);
        try {
            for (FilterCandidateSearch.Type type : FilterCandidateSearch.Type.values()) {
                for (boolean op : new boolean[]{false, true}) {
                    javaCheck(type, op, Math.min(count, 10000));
                    long started = System.nanoTime();
                    JsonObject java = javaCheck(type, op, count);
                    java.addProperty("wallMs", (System.nanoTime() - started) / 1000000.0);
                    started = System.nanoTime();
                    Process process = new ProcessBuilder(executable.toString(), Integer.toString(type.ordinal()),
                            op ? "1" : "0", Integer.toString(count)).redirectError(ProcessBuilder.Redirect.INHERIT).start();
                    JsonObject nativeResult;
                    try {
                        // This worker emits one bounded summary, smaller than the stdout pipe buffer.
                        if (!process.waitFor(60, TimeUnit.SECONDS)) throw new IllegalStateException("Native screen timed out");
                        if (process.exitValue() != 0) throw new IllegalStateException("Native screen failed");
                        try (InputStreamReader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
                            nativeResult = new JsonParser().parse(reader).getAsJsonObject();
                        }
                    } finally {
                        if (process.isAlive()) { process.destroyForcibly(); process.waitFor(); }
                    }
                    nativeResult.addProperty("processWallMs", (System.nanoTime() - started) / 1000000.0);
                    if (nativeResult.get("protocol").getAsInt() != 1
                            || !"e61f90580cbdd883214a8054670dacae655e59c0".equals(nativeResult.get("cubiomesRevision").getAsString())
                            || nativeResult.get("samples").getAsInt() != count
                            || !java.get("checksum").equals(nativeResult.get("checksum"))
                            || !java.get("counts").equals(nativeResult.get("counts"))) {
                        throw new IllegalStateException("Native/Java structure screening mismatch for " + type + ", op=" + op);
                    }
                    JsonObject row = new JsonObject();
                    row.addProperty("type", type.name());
                    row.addProperty("overpowered", op);
                    row.add("java", java);
                    row.add("native", nativeResult);
                    rows.add(row);
                    System.out.println("[FilterStructure] MATCH type=" + type + " op=" + op + " samples=" + count
                            + " javaMs=" + java.get("wallMs") + " nativeCpuMs=" + nativeResult.get("cpuMs")
                            + " nativeProcessMs=" + nativeResult.get("processWallMs"));
                }
            }
            report.addProperty("status", "MATCHED");
        } catch (Exception failure) {
            report.addProperty("status", "FAILED");
            throw failure;
        } finally {
            FilterProbeFiles.write(reportPath, report);
        }
    }

    private static JsonObject javaCheck(FilterCandidateSearch.Type type, boolean op, int count) {
        long checksum = 0xcbf29ce484222325L;
        int[] counts = new int[4];
        ChunkRandom random = new ChunkRandom();
        for (int i = 0; i < count; i++) {
            long seed = (i * 0x9e3779b97f4a7c15L) & StagedFilterSearch.MASK;
            FilterCandidateSearch.StructureCheck check = FilterCandidateSearch.checkGeometry(seed, type, op, random);
            int result = check.rejection == null ? 3 : check.rejection.ordinal();
            counts[result]++;
            checksum = digest(checksum, result);
            if (result == 3) {
                checksum = digest(checksum, check.main.x);
                checksum = digest(checksum, check.main.z);
                checksum = digest(checksum, check.bastion.x);
                checksum = digest(checksum, check.bastion.z);
                checksum = digest(checksum, check.fortress.x);
                checksum = digest(checksum, check.fortress.z);
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("checksum", String.format(java.util.Locale.ROOT, "%016x", checksum));
        JsonArray totals = new JsonArray();
        for (int value : counts) totals.add(value);
        result.add("counts", totals);
        return result;
    }

    private static long digest(long hash, int value) {
        return (hash ^ (long) value) * 0x100000001b3L;
    }
}
