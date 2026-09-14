package zsgrooms.modid.filter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Replay private jobs against isolated persistent workers and the unchanged serial validator. */
final class FilterVerifierParityTest {
    static void run(MinecraftServer server, String input) {
        JsonObject report = new JsonObject();
        JsonArray comparisons = new JsonArray();
        report.add("comparisons", comparisons);
        try (FilterVerifierPool pool = new FilterVerifierPool(UUID.randomUUID().toString(), 2, 2)) {
            List<Path> paths = new ArrayList<Path>();
            try (java.util.stream.Stream<Path> files = Files.list(Paths.get(input))) {
                files.filter(path -> path.toString().endsWith(".json")).sorted().limit(3).forEach(paths::add);
            }
            if (paths.isEmpty()) throw new IllegalStateException("No saved private verifier jobs");
            List<JsonObject> jobs = new ArrayList<JsonObject>();
            List<CompletableFuture<JsonObject>> futures = new ArrayList<CompletableFuture<JsonObject>>();
            for (int i = 0; i < 4; i++) {
                JsonObject job = FilterVerifierWorker.read(paths.get(i % paths.size()));
                job.addProperty("id", UUID.randomUUID().toString());
                jobs.add(job);
                futures.add(pool.submit(job));
            }
            for (int i = 0; i < jobs.size(); i++) {
                JsonObject worker = futures.get(i).get();
                if ("ERROR".equals(worker.get("status").getAsString())) throw new AssertionError("Worker validation failed");
                JsonObject job = jobs.get(i);
                long seed = Long.parseLong(job.get("seed").getAsString());
                FilterCandidateSearch.Candidate candidate = FilterCandidateSearch.checkExactSeed(seed,
                        FilterCandidateSearch.Type.parse(job.get("type").getAsString()), false);
                OfflineFamilyGate gate = new OfflineFamilyGate(server, false, job.get("predictBastion").getAsBoolean());
                if (!gate.allows(seed & StagedFilterSearch.MASK, candidate.main, candidate.bastion)) throw new AssertionError("Serial gate mismatch");
                boolean early = job.get("earlyChecks").getAsBoolean();
                VillageLayoutPrediction village = early && candidate.type == FilterCandidateSearch.Type.VILLAGE
                        ? VillageLayoutPrediction.predict(server, seed, candidate.main) : null;
                JsonObject serial;
                try (ProbeWorlds worlds = new ProbeWorlds(server, seed, UUID.randomUUID().toString())) {
                    serial = OfflineFilterValidator.validate(server, candidate, gate, village, early, FilterVerifierWorker.spawnPrediction(job));
                }
                boolean equivalent = semantic(worker).equals(semantic(serial));
                if (!equivalent) throw new AssertionError("Serial/worker acceptance mismatch");
                JsonObject comparison = new JsonObject();
                comparison.addProperty("equivalent", true);
                comparison.add("workerReasons", worker.get("rejections"));
                comparison.add("serialReasons", serial.get("rejections"));
                comparisons.add(comparison);
            }
            report.addProperty("status", "PASS");
            zsgrooms.modid.ZsgRooms.LOGGER.info("[FilterVerifierTest] COMPLETE: {} comparisons", comparisons.size());
        } catch (Throwable failure) {
            report.addProperty("status", "FAIL");
            report.addProperty("errorClass", failure.getClass().getSimpleName());
            zsgrooms.modid.ZsgRooms.LOGGER.error("[FilterVerifierTest] FAIL: {}", failure.getClass().getSimpleName());
        } finally {
            try { FilterProbeFiles.write(Paths.get("verifier-test.json"), report); }
            catch (Exception failure) { throw new IllegalStateException("Cannot write verifier test report", failure); }
            server.stop(false);
        }
    }

    private static JsonObject semantic(JsonObject source) {
        JsonObject result = new JsonObject();
        for (String field : new String[]{"profile", "type", "status", "rejections", "chests", "iron", "diamonds",
                "golem", "stables", "woodedLand", "deepRavineMiddles", "portal", "structure", "spawnPolicy",
                "spawnModelPasses", "spawnModelRejections", "spawnNativeRequests"}) {
            if (source.has(field)) result.add(field, source.get(field));
        }
        return result;
    }
}
