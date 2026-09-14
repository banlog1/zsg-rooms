package zsgrooms.modid.filter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import zsgrooms.modid.ZsgRooms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Calibrates a model on a sample, without changing production acceptance criteria. */
final class SpawnModelCalibration {
    static void run(MinecraftServer server) {
        JsonObject report = new JsonObject();
        JsonArray rows = new JsonArray();
        report.addProperty("status", "RUNNING");
        report.addProperty("model", "Cubiomes.getSpawn MC_1_16_1");
        report.addProperty("revision", CubiomesSpawnModel.REVISION);
        report.addProperty("selection", "Fixed unfiltered corpus plus up to 8 unique saved candidates/type; saved candidates are selection-biased");
        report.addProperty("distanceMetric", "max(abs(dx), abs(dz)); no Y or player spawn-radius jitter");
        report.addProperty("candidateAnchors", "Structure origin or predicted lake attempts, NOT verified usable pools");
        report.add("samples", rows);
        long started = System.nanoTime();
        try (CubiomesSpawnModel model = new CubiomesSpawnModel(Paths.get(System.getProperty("zsgrooms.spawnModelExecutable")))) {
            List<Sample> samples = Boolean.getBoolean("zsgrooms.spawnCalibrationHoldout") ? holdout(model, report) : samples();
            Set<Long> families = new HashSet<Long>();
            List<Long> errors = new ArrayList<Long>();
            int index = 0;
            for (Sample sample : samples) {
                families.add(sample.seed & StagedFilterSearch.MASK);
                JsonObject row = new JsonObject();
                row.addProperty("index", index++);
                row.addProperty("group", sample.candidate == null ? "UNFILTERED" : sample.candidate.type.name());
                long modelStarted = System.nanoTime();
                BlockPos prediction = model.predict(sample.seed);
                row.addProperty("modelMs", (System.nanoTime() - modelStarted) / 1000000.0);
                // Repeated requests must be independent of previous requests and deterministic.
                if (!prediction.equals(model.predict(sample.seed))) throw new AssertionError("Spawn prediction is not repeatable");
                long nativeStarted = System.nanoTime();
                try (ProbeWorlds ignored = new ProbeWorlds(server, sample.seed, UUID.randomUUID().toString())) {
                    ProbeWorlds.determineSpawn(server);
                    row.addProperty("nativeSpawnMs", (System.nanoTime() - nativeStarted) / 1000000.0);
                    BlockPos actual = server.getOverworld().getSpawnPos();
                    long error = SpawnModelDecision.error(prediction, actual);
                    errors.add(error);
                    row.addProperty("errorBlocks", error);
                    row.addProperty("nativeBiomeCategory", server.getOverworld().getBiome(actual).getCategory().name());
                    if (sample.candidate != null) {
                        List<BlockPos> anchors = anchors(sample.candidate);
                        int radius = sample.candidate.type == FilterCandidateSearch.Type.SHIPWRECK ? 48 : 32;
                        boolean actualPass = SpawnModelDecision.classify(actual, anchors, radius, 0) == SpawnModelDecision.Decision.PASS;
                        row.addProperty("nativeAnchorPass", actualPass);
                        JsonObject margins = new JsonObject();
                        for (int margin : new int[]{0, 8, 16, 32, 48, 64, 96, 128}) {
                            margins.addProperty(Integer.toString(margin), SpawnModelDecision.classify(prediction, anchors, radius, margin).name());
                        }
                        row.add("marginDecisions", margins);
                    }
                }
                row.addProperty("nativeWithCleanupMs", (System.nanoTime() - nativeStarted) / 1000000.0);
                rows.add(row);
                report.addProperty("elapsedMs", (System.nanoTime() - started) / 1000000);
                FilterProbeFiles.write(reportPath(), report);
                ZsgRooms.LOGGER.info("[SpawnCalibration] Sample {}/{} complete: group={}, error={} blocks",
                        index, samples.size(), row.get("group").getAsString(), row.get("errorBlocks"));
            }
            Collections.sort(errors);
            report.addProperty("families", families.size());
            report.addProperty("medianError", errors.get((errors.size() - 1) / 2));
            report.addProperty("p95Error", errors.get((int)Math.ceil(errors.size() * .95) - 1));
            report.addProperty("maxError", errors.get(errors.size() - 1));
            report.addProperty("status", "COMPLETE");
            ZsgRooms.LOGGER.info("[SpawnCalibration] COMPLETE: samples={}, median={}, p95={}, max={}",
                    errors.size(), report.get("medianError"), report.get("p95Error"), report.get("maxError"));
        } catch (Throwable failure) {
            report.addProperty("status", "FAILED");
            report.addProperty("errorClass", failure.getClass().getSimpleName());
            ZsgRooms.LOGGER.error("[SpawnCalibration] FAIL: {}", failure.getClass().getSimpleName());
        } finally {
            report.addProperty("elapsedMs", (System.nanoTime() - started) / 1000000);
            try { FilterProbeFiles.write(reportPath(), report); }
            catch (Exception failure) { ZsgRooms.LOGGER.error("[SpawnCalibration] FAIL: report write"); }
            server.stop(false);
        }
    }

    private static Path reportPath() {
        return Paths.get(Boolean.getBoolean("zsgrooms.spawnCalibrationHoldout") ? "spawn-holdout.json" : "spawn-calibration.json");
    }

    private static List<Sample> holdout(CubiomesSpawnModel model, JsonObject report) throws Exception {
        report.addProperty("selection", "New staged families; target 3 PASS/VERIFY/REJECT per type at preselected margin 16; one sister per selected family");
        report.addProperty("preselectedMargin", 16);
        JsonArray coverage = new JsonArray();
        report.add("holdoutCoverage", coverage);
        List<Sample> samples = new ArrayList<Sample>();
        for (FilterCandidateSearch.Type type : FilterCandidateSearch.Type.values()) {
            StagedFilterSearch search = new StagedFilterSearch(type, false, 93618 + type.ordinal(), 1000, (seed, main, bastion) -> true);
            int[] counts = new int[3];
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(60);
            while (System.nanoTime() < deadline && (counts[0] < 3 || counts[1] < 3 || counts[2] < 3)) {
                FilterCandidateSearch.Result result = search.next(1000000, 10000, () -> System.nanoTime() >= deadline);
                if (!result.candidate().isPresent()) continue;
                FilterCandidateSearch.Candidate candidate = result.candidate().get();
                int bucket = SpawnModelDecision.classify(model.predict(candidate.seedForValidation()), anchors(candidate),
                        type == FilterCandidateSearch.Type.SHIPWRECK ? 48 : 32, 16).ordinal();
                if (counts[bucket] >= 3) continue;
                samples.add(new Sample(candidate.seedForValidation(), candidate));
                counts[bucket]++;
                search.skipRemainingFamily();
            }
            JsonObject group = new JsonObject();
            group.addProperty("type", type.name());
            for (SpawnModelDecision.Decision decision : SpawnModelDecision.Decision.values()) {
                group.addProperty(decision.name(), counts[decision.ordinal()]);
            }
            coverage.add(group);
            ZsgRooms.LOGGER.info("[SpawnCalibration] Holdout selected: {}", group);
        }
        if (samples.isEmpty()) throw new IllegalStateException("No holdout candidates found");
        return samples;
    }

    static List<BlockPos> anchors(FilterCandidateSearch.Candidate candidate) {
        List<BlockPos> anchors = new ArrayList<BlockPos>();
        anchors.add(new BlockPos(candidate.main.x << 4, 64, candidate.main.z << 4));
        if (candidate.type != FilterCandidateSearch.Type.SHIPWRECK) anchors.addAll(candidate.lakeAttempts);
        return anchors;
    }

    private static List<Sample> samples() throws Exception {
        int count = Integer.getInteger("zsgrooms.spawnCalibrationSamples", 24);
        if (count < 1 || count > 256) throw new IllegalArgumentException("Invalid calibration size");
        List<Sample> samples = new ArrayList<Sample>();
        Set<Long> seen = new HashSet<Long>();
        for (int i = 1; i <= count; i++) {
            long seed = i * 0x9e3779b97f4a7c15L;
            samples.add(new Sample(seed, null));
            seen.add(seed);
        }
        Path jobs = Paths.get("private-jobs");
        int[] counts = new int[3];
        if (Files.isDirectory(jobs)) try (java.util.stream.Stream<Path> paths = Files.walk(jobs)) {
            for (Path path : (Iterable<Path>) paths.filter(p -> p.toString().endsWith(".json")).sorted()::iterator) {
                JsonObject job = FilterVerifierWorker.read(path);
                FilterCandidateSearch.Type type = FilterCandidateSearch.Type.parse(job.get("type").getAsString());
                if (counts[type.ordinal()] >= 8) continue;
                long seed = Long.parseLong(job.get("seed").getAsString());
                if (!seen.add(seed)) continue;
                FilterCandidateSearch.Candidate candidate = FilterCandidateSearch.checkExactSeed(seed, type, false);
                if (candidate == null) continue;
                samples.add(new Sample(seed, candidate));
                counts[type.ordinal()]++;
            }
        }
        return samples;
    }

    private static final class Sample {
        final long seed;
        final FilterCandidateSearch.Candidate candidate;
        Sample(long seed, FilterCandidateSearch.Candidate candidate) { this.seed = seed; this.candidate = candidate; }
    }
}
