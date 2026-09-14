package zsgrooms.modid.filter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import zsgrooms.modid.ZsgRooms;

import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class OfflineBankRunner {
    static void run(MinecraftServer server) {
        if (!Boolean.getBoolean("zsgrooms.filterCalibration")) {
            throw new IllegalStateException("Minecraft bank runner is calibration-only; use the standalone model finder for search");
        }
        JsonObject summary = new JsonObject();
        summary.addProperty("profile", OfflineFilterValidator.PROFILE);
        summary.addProperty("pipelineRevision", 8);
        summary.addProperty("status", "RUNNING");
        summary.addProperty("runId", UUID.randomUUID().toString());
        summary.addProperty("startedAt", java.time.Instant.now().toString());
        summary.addProperty("javaVersion", System.getProperty("java.version"));
        summary.addProperty("availableProcessors", Runtime.getRuntime().availableProcessors());
        summary.addProperty("maxHeapMiB", Runtime.getRuntime().maxMemory() / 1048576);
        summary.addProperty("passes", 0);
        JsonArray attempts = new JsonArray();
        JsonArray staged = new JsonArray();
        summary.add("attempts", attempts);
        long started = System.nanoTime();
        FilterVerifierPool pool = null;
        CubiomesSpawnModel spawnModel = null;
        try {
            BastionLayoutProbe.verifyTemplates(server.getOverworld());
            FilterCandidateSearch.Type type = FilterCandidateSearch.Type.parse(System.getProperty("zsgrooms.filterBank.type", "temple"));
            int limit = Integer.getInteger("zsgrooms.filterBank.trials", 20);
            int target = Integer.getInteger("zsgrooms.filterBank.target", 3);
            int maxMinutes = Integer.getInteger("zsgrooms.filterBank.maxMinutes", 30);
            long searchSeed = Long.getLong("zsgrooms.filterBank.searchSeed", 1000);
            if (limit < 1 || limit > 10000 || target < 1 || target > limit || maxMinutes < 1 || maxMinutes > 180) {
                throw new IllegalArgumentException("Invalid bank trial/target/time budget");
            }
            long deadline = started + java.util.concurrent.TimeUnit.MINUTES.toNanos(maxMinutes);
            boolean earlyChecks = Boolean.parseBoolean(System.getProperty("zsgrooms.filterBank.earlyChecks", "true"));
            summary.addProperty("earlyChecks", earlyChecks);
            long villageChecks = 0, villageRejected = 0, villageNanos = 0;
            summary.addProperty("maxMinutes", maxMinutes);
            summary.addProperty("type", type.name());
            summary.addProperty("predictTempleLoot", Boolean.parseBoolean(System.getProperty("zsgrooms.filterBank.predictLoot", "true")));
            summary.addProperty("spawnSearchHint", Boolean.parseBoolean(System.getProperty("zsgrooms.filterBank.spawnHint", "true")));
            if (summary.get("spawnSearchHint").getAsBoolean()) {
                spawnModel = new CubiomesSpawnModel(Paths.get(System.getProperty("zsgrooms.spawnModelExecutable")));
            }
            summary.addProperty("spawnPolicy", spawnModel == null ? "NATIVE" : "CUBIOMES_MARGIN_16_V1");
            summary.addProperty("spawnModelRevision", spawnModel == null ? "none" : CubiomesSpawnModel.REVISION);
            summary.addProperty("templeWoodSearchHint", Boolean.parseBoolean(System.getProperty("zsgrooms.filterBank.woodHint", "true")));
            String mode = System.getProperty("zsgrooms.filterBank.searchMode", "staged");
            if (!mode.equals("staged") && !mode.equals("legacy")) throw new IllegalArgumentException("Search mode must be staged or legacy");
            int sisterLimit = Integer.getInteger("zsgrooms.filterBank.sisters", 65536);
            boolean familyLootGate = type == FilterCandidateSearch.Type.TEMPLE && summary.get("predictTempleLoot").getAsBoolean();
            boolean predictLayout = Boolean.parseBoolean(System.getProperty("zsgrooms.filterBank.predictBastion", "true"));
            OfflineFamilyGate familyGate = mode.equals("staged") ? new OfflineFamilyGate(server, familyLootGate, predictLayout) : null;
            StagedFilterSearch session = familyGate == null ? null : new StagedFilterSearch(type, false, searchSeed, sisterLimit, familyGate);
            int workers = Integer.getInteger("zsgrooms.filterBank.workers", 1);
            int capacity = Integer.getInteger("zsgrooms.filterBank.queue", 2);
            if (workers < 0 || workers > 2 || capacity < 1 || capacity > 16) throw new IllegalArgumentException("Invalid worker/queue limits");
            if (workers > 0) pool = new FilterVerifierPool(summary.get("runId").getAsString(), workers, capacity);
            summary.addProperty("workers", workers);
            summary.addProperty("queueCapacity", capacity);
            summary.addProperty("workerStartupMs", pool == null ? 0 : pool.startupMs);
            BankValidationResults validations = new BankValidationResults(staged, session, started, target);
            summary.addProperty("predictBastionLayout", familyGate != null && predictLayout);
            summary.addProperty("searchMode", mode);
            summary.addProperty("structureBackend", "minecraft-java");
            summary.addProperty("sisterLimit", session == null ? 0 : sisterLimit);
            Set<Long> seen = new HashSet<Long>();
            save(summary, staged);
            long checkpoint = System.nanoTime();
            long totalSearchMillis = 0;
            long[] nextStopPoll = {0};
            boolean[] stopFile = {false};
            java.util.function.BooleanSupplier cancel = () -> {
                long now = System.nanoTime();
                // Poll the operator's file at most four times/second, not once per seed or lake attempt.
                if (now >= nextStopPoll[0]) {
                    stopFile[0] = stopRequested();
                    nextStopPoll[0] = now + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(250);
                }
                return !server.isRunning() || stopFile[0] || now >= deadline;
            };
            for (int trial = 0; trial < limit && staged.size() < target && System.nanoTime() < deadline && !stopRequested(); trial++) {
                validations.collect(false);
                if (validations.size() >= workers + capacity) validations.collect(true);
                if (staged.size() >= target || cancel.getAsBoolean()) break;
                long trialStarted = System.nanoTime();
                long familiesBefore = session == null ? 0 : session.familiesChecked();
                long sistersBefore = session == null ? 0 : session.sistersChecked();
                FilterCandidateSearch.Result search = session == null
                        ? FilterCandidateSearch.search(type, false, searchSeed + trial, 1000000, 30000, cancel)
                        : session.next(1000000, 30000, cancel);
                totalSearchMillis += search.elapsedMillis;
                summary.addProperty("searchMs", totalSearchMillis);
                summary.addProperty("searchSlices", trial + 1);
                if (session != null) {
                    summary.addProperty("lower48Checked", session.familiesChecked());
                    summary.addProperty("sistersChecked", session.sistersChecked());
                    summary.addProperty("familyLootRejected", familyGate.lootRejected);
                    summary.addProperty("familyLayoutsChecked", familyGate.layoutsChecked);
                    summary.addProperty("familyLayoutsRejected", familyGate.layoutsRejected);
                    summary.addProperty("familyLayoutMs", familyGate.layoutNanos / 1000000.0);
                }
                if (!search.candidate().isPresent()) {
                    summary.addProperty("lastSearchStatus", search.status.name());
                    summary.addProperty("elapsedMs", (System.nanoTime() - started) / 1000000);
                    save(summary, staged);
                    if (cancel.getAsBoolean() || Thread.currentThread().isInterrupted()) break;
                    // A bounded search slice is not an error. The staged cursor resumes on the next slice.
                    continue;
                }
                FilterCandidateSearch.Candidate candidate = search.candidate().get();
                if (!seen.add(candidate.seedForValidation())) continue;
                String id = UUID.randomUUID().toString();
                JsonObject result = null;
                long spawnModelStarted = System.nanoTime();
                final net.minecraft.util.math.BlockPos spawnPrediction = spawnModel == null ? null : spawnModel.predict(candidate.seedForValidation());
                double spawnModelMs = spawnModel == null ? 0 : (System.nanoTime() - spawnModelStarted) / 1000000.0;
                VillageLayoutPrediction village = null;
                if (type == FilterCandidateSearch.Type.TEMPLE && summary.get("predictTempleLoot").getAsBoolean()) {
                    TempleLootProbe prediction = TempleLootPrediction.predict(server.getOverworld(), candidate.seedForValidation(), candidate.main);
                    if (!TempleCandidateChecks.hasResources(prediction.iron, prediction.diamonds)) {
                        result = new JsonObject();
                        result.addProperty("status", "REJECTED");
                        result.addProperty("worldGenerated", false);
                        JsonArray reasons = new JsonArray();
                        reasons.add("TEMPLE_RESOURCES_PREDICTED");
                        result.add("rejections", reasons);
                    }
                }
                if (result == null && spawnPrediction != null && SpawnModelDecision.classify(spawnPrediction,
                        java.util.Collections.singletonList(new net.minecraft.util.math.BlockPos(candidate.main.x << 4, 64, candidate.main.z << 4)),
                        type == FilterCandidateSearch.Type.SHIPWRECK ? 48 : 128, SpawnModelCheck.MARGIN) == SpawnModelDecision.Decision.REJECT) {
                    result = new JsonObject();
                    result.addProperty("status", "SEARCH_SKIPPED");
                    result.addProperty("worldGenerated", false);
                    JsonArray reasons = new JsonArray();
                    reasons.add("SPAWN_MODEL_DISTANCE");
                    result.add("rejections", reasons);
                }
                if (result == null && type == FilterCandidateSearch.Type.TEMPLE && summary.get("templeWoodSearchHint").getAsBoolean()
                        && !TempleWoodSearchHint.promising(candidate)) {
                    result = new JsonObject();
                    result.addProperty("status", "SEARCH_SKIPPED");
                    result.addProperty("worldGenerated", false);
                    JsonArray reasons = new JsonArray();
                    reasons.add("POOL_TREE_SEARCH_HINT");
                    result.add("rejections", reasons);
                }
                if (result == null && earlyChecks && type == FilterCandidateSearch.Type.VILLAGE) {
                    long predictionStarted = System.nanoTime();
                    village = VillageLayoutPrediction.predict(server, candidate.seedForValidation(), candidate.main);
                    villageNanos += System.nanoTime() - predictionStarted;
                    villageChecks++;
                    if (village != null && village.rejection != null) {
                        villageRejected++;
                        result = new JsonObject();
                        result.addProperty("status", "REJECTED");
                        result.addProperty("worldGenerated", false);
                        JsonArray reasons = new JsonArray();
                        reasons.add(village.rejection + "_PREDICTED");
                        result.add("rejections", reasons);
                    }
                    summary.addProperty("villageLayoutsChecked", villageChecks);
                    summary.addProperty("villageLayoutsRejected", villageRejected);
                    summary.addProperty("villageLayoutMs", villageNanos / 1000000.0);
                }
                if (result == null) {
                    java.util.concurrent.CompletableFuture<JsonObject> future;
                    if (pool != null) {
                        JsonObject request = new JsonObject();
                        request.addProperty("protocol", 1);
                        request.addProperty("profile", OfflineFilterValidator.PROFILE);
                        request.addProperty("id", id);
                        request.addProperty("seed", Long.toString(candidate.seedForValidation()));
                        request.addProperty("type", type.name());
                        request.addProperty("earlyChecks", earlyChecks);
                        request.addProperty("predictBastion", familyGate != null && predictLayout);
                        if (spawnPrediction != null) {
                            request.addProperty("spawnModelRevision", CubiomesSpawnModel.REVISION);
                            request.addProperty("spawnModelX", spawnPrediction.getX());
                            request.addProperty("spawnModelZ", spawnPrediction.getZ());
                        }
                        // Private replayable jobs enable exact-candidate serial/worker parity measurements.
                        FilterProbeFiles.write(Paths.get("private-jobs", summary.get("runId").getAsString(), id + ".json"), request);
                        future = pool.submit(request);
                    } else {
                        final VillageLayoutPrediction predictedVillage = village;
                        try (ValidationTrace trace = new ValidationTrace()) {
                            ProbeWorlds worlds = ValidationTrace.measure("world.create", () -> FilterVerifierWorker.createWorlds(server, candidate.seedForValidation(), id));
                            JsonObject checked;
                            try {
                                checked = ValidationTrace.measure("stage4", () -> OfflineFilterValidator.validate(server, candidate, familyGate, predictedVillage, earlyChecks, spawnPrediction));
                            } finally { ValidationTrace.run("world.close", () -> FilterVerifierWorker.closeWorlds(worlds)); }
                            checked.add("checks", trace.json());
                            checked.addProperty("worldGenerated", true);
                            future = java.util.concurrent.CompletableFuture.completedFuture(checked);
                        }
                    }
                    result = new JsonObject();
                    result.addProperty("status", "QUEUED");
                    result.addProperty("worldGenerated", false);
                    result.add("rejections", new JsonArray());
                    validations.add(future, result, candidate.seedForValidation(), mode, session == null ? searchSeed + trial : searchSeed);
                }
                result.addProperty("id", id);
                result.addProperty("spawnModelMs", spawnModelMs);
                result.addProperty("searchMs", search.elapsedMillis);
                result.addProperty("cheapCandidates", search.attempts);
                result.addProperty("lower48Checked", session == null ? 0 : session.familiesChecked() - familiesBefore);
                result.addProperty("sistersChecked", session == null ? 0 : session.sistersChecked() - sistersBefore);
                result.addProperty("producerMs", (System.nanoTime() - trialStarted) / 1000000);
                result.addProperty("proposalStartedAtMs", (trialStarted - started) / 1000000);
                result.addProperty("totalMs", (System.nanoTime() - trialStarted) / 1000000);
                attempts.add(result);
                validations.collect(false);
                summary.addProperty("elapsedMs", (System.nanoTime() - started) / 1000000);
                summary.addProperty("passes", staged.size());
                boolean passed = "VALIDATION_PASS".equals(result.get("status").getAsString());
                boolean checkpointDue = System.nanoTime() - checkpoint >= java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                if (passed || checkpointDue) {
                    save(summary, staged);
                    checkpoint = System.nanoTime();
                }
                if (passed || checkpointDue || result.get("worldGenerated").getAsBoolean()) {
                    ZsgRooms.LOGGER.info("[FilterBank] type={}, trial={}, status={}, reasons={}, totalMs={}, passes={}",
                        type, trial + 1, result.get("status").getAsString(), result.get("rejections"),
                        result.get("totalMs").getAsLong(), staged.size());
                }
            }
            // Stop producing at the soft budget; finish the bounded backlog, never interrupt world validation mid-check.
            String searchStopStatus = stopRequested() ? "STOP_REQUESTED" : System.nanoTime() >= deadline ? "TIME_LIMIT"
                    : !server.isRunning() || Thread.currentThread().isInterrupted() ? "CANCELLED" : "TRIAL_LIMIT";
            summary.addProperty("searchEndedAtMs", (System.nanoTime() - started) / 1000000);
            while (validations.size() > 0) {
                validations.collect(true);
                save(summary, staged);
            }
            summary.addProperty("submitted", validations.submitted);
            summary.addProperty("completed", validations.completed);
            summary.addProperty("inFlightHighWater", validations.highWater);
            summary.addProperty("passes", staged.size());
            summary.addProperty("status", staged.size() >= target ? "TARGET_REACHED" : searchStopStatus);
            summary.addProperty("elapsedMs", (System.nanoTime() - started) / 1000000);
            save(summary, staged);
            ZsgRooms.LOGGER.info("[FilterBank] COMPLETE: status={}, passes={}, elapsedMs={}",
                    summary.get("status").getAsString(), staged.size(), summary.get("elapsedMs").getAsLong());
        } catch (Throwable failure) {
            summary.addProperty("status", "ERROR");
            try { save(summary, staged); } catch (Exception writeFailure) { failure.addSuppressed(writeFailure); }
            ZsgRooms.LOGGER.error("[FilterBank] FAIL: " + failure.getClass().getSimpleName(), failure);
        } finally {
            if (spawnModel != null) spawnModel.close();
            if (pool != null) pool.close();
            server.stop(false);
        }
    }

    private static boolean stopRequested() {
        return java.nio.file.Files.exists(Paths.get("stop-requested"));
    }

    private static void save(JsonObject summary, JsonArray staged) throws Exception {
        summary.add("checkTotals", ValidationTraceSummary.aggregate(summary.getAsJsonArray("attempts")));
        int verified = 0;
        for (com.google.gson.JsonElement attempt : summary.getAsJsonArray("attempts")) {
            JsonObject item = attempt.getAsJsonObject();
            if (item.has("bastionPredictionVerified") && item.get("bastionPredictionVerified").getAsBoolean()) verified++;
        }
        summary.addProperty("layoutPredictionsVerified", verified);
        FilterProbeFiles.write(Paths.get("bank-report.json"), summary);
        FilterProbeFiles.write(Paths.get("reports", summary.get("runId").getAsString() + ".json"), summary);
        JsonObject handoff = new JsonObject();
        handoff.addProperty("profile", OfflineFilterValidator.PROFILE);
        handoff.addProperty("status", "NON_EXPORTABLE_DEVELOPMENT");
        handoff.add("entries", staged);
        FilterProbeFiles.write(Paths.get("bank-staging.json"), handoff);
        FilterProbeFiles.write(Paths.get("private-staging", summary.get("runId").getAsString() + ".json"), handoff);
    }
}
