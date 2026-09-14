package zsgrooms.modid.filter;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** One persistent server process; only its server thread ever touches candidate worlds. */
final class FilterVerifierWorker {
    static JsonObject read(Path path) throws Exception {
        if (Files.size(path) > 1048576) throw new IllegalStateException("Oversized verifier message");
        try (Reader reader = Files.newBufferedReader(path)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        }
    }

    static void run(MinecraftServer server) {
        try {
            FilterProbeFiles.write(Paths.get("ready.json"), new JsonObject());
            while (server.isRunning() && !Files.exists(Paths.get("stop"))) {
                Path request = Paths.get("request.json");
                if (!Files.exists(request)) { Thread.sleep(25); continue; }
                JsonObject job = read(request);
                String id = java.util.UUID.fromString(job.get("id").getAsString()).toString();
                JsonObject result;
                try {
                    if (job.get("protocol").getAsInt() != 1
                            || !OfflineFilterValidator.PROFILE.equals(job.get("profile").getAsString())) {
                        throw new IllegalStateException("Verifier protocol/profile mismatch");
                    }
                    result = validate(server, job);
                } catch (Throwable failure) {
                    result = new JsonObject();
                    result.addProperty("status", "ERROR");
                    result.addProperty("errorClass", failure.getClass().getSimpleName());
                }
                result.addProperty("id", id);
                result.addProperty("protocol", 1);
                result.addProperty("profile", OfflineFilterValidator.PROFILE);
                Files.delete(request);
                FilterProbeFiles.write(Paths.get("response.json"), result);
                if ("ERROR".equals(result.get("status").getAsString())) break;
            }
        } catch (Exception failure) {
            zsgrooms.modid.ZsgRooms.LOGGER.error("[FilterVerifier] Failed: {}", failure.getClass().getSimpleName());
        } finally { server.stop(false); }
    }

    static JsonObject validate(MinecraftServer server, JsonObject job) throws Exception {
        try (ValidationTrace trace = new ValidationTrace()) {
            long started = System.nanoTime();
            JsonObject result;
            try {
                long seed = Long.parseLong(job.get("seed").getAsString());
                FilterCandidateSearch.Candidate candidate = ValidationTrace.measure("worker.reconstructCandidate", () ->
                        FilterCandidateSearch.checkExactSeed(seed, FilterCandidateSearch.Type.parse(job.get("type").getAsString()), false));
                if (candidate == null) throw new IllegalStateException("Candidate reconstruction mismatch");
                boolean early = job.get("earlyChecks").getAsBoolean();
                net.minecraft.util.math.BlockPos spawnPrediction = spawnPrediction(job);
                OfflineFamilyGate gate = new OfflineFamilyGate(server, false, job.get("predictBastion").getAsBoolean());
                if (!ValidationTrace.measure("worker.predictBastion", () -> gate.allows(seed & StagedFilterSearch.MASK, candidate.main, candidate.bastion))) {
                    throw new IllegalStateException("Bastion prediction mismatch");
                }
                VillageLayoutPrediction village = early && candidate.type == FilterCandidateSearch.Type.VILLAGE
                        ? ValidationTrace.measure("worker.predictVillage", () -> VillageLayoutPrediction.predict(server, seed, candidate.main)) : null;
                ProbeWorlds worlds = ValidationTrace.measure("world.create", () -> createWorlds(server, seed, job.get("id").getAsString()));
                try {
                    result = ValidationTrace.measure("stage4", () -> OfflineFilterValidator.validate(server, candidate, gate, village, early, spawnPrediction));
                } finally {
                    ValidationTrace.run("world.close", () -> closeWorlds(worlds));
                }
                result.addProperty("worldGenerated", true);
            } catch (Throwable failure) {
                result = new JsonObject();
                result.addProperty("status", "ERROR");
                result.addProperty("errorClass", failure.getClass().getSimpleName());
            }
            result.add("checks", trace.json());
            result.addProperty("workerMs", (System.nanoTime() - started) / 1000000.0);
            return result;
        }
    }

    static net.minecraft.util.math.BlockPos spawnPrediction(JsonObject job) {
        if (!job.has("spawnModelRevision") && !job.has("spawnModelX") && !job.has("spawnModelZ")) return null;
        if (!job.has("spawnModelRevision") || !CubiomesSpawnModel.REVISION.equals(job.get("spawnModelRevision").getAsString())
                || !job.has("spawnModelX") || !job.has("spawnModelZ")) {
            throw new IllegalStateException("Spawn model handoff mismatch");
        }
        long x = job.get("spawnModelX").getAsLong(), z = job.get("spawnModelZ").getAsLong();
        if (Math.abs(x) > 30000000 || Math.abs(z) > 30000000 || x == Long.MIN_VALUE || z == Long.MIN_VALUE) {
            throw new IllegalStateException("Invalid spawn model handoff position");
        }
        return new net.minecraft.util.math.BlockPos((int)x, 64, (int)z);
    }

    static ProbeWorlds createWorlds(MinecraftServer server, long seed, String id) {
        try { return new ProbeWorlds(server, seed, id); }
        catch (Exception failure) { throw new IllegalStateException("World creation failed", failure); }
    }

    static void closeWorlds(ProbeWorlds worlds) {
        try { worlds.close(); }
        catch (Exception failure) { throw new IllegalStateException("World cleanup failed", failure); }
    }
}
