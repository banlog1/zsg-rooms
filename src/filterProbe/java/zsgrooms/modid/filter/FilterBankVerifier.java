package zsgrooms.modid.filter;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import zsgrooms.modid.ZsgRooms;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Rechecks one staged result in an ordinary fresh server world, including vanilla spawn preparation. */
final class FilterBankVerifier {
    static void run(MinecraftServer server) {
        long started = System.nanoTime();
        JsonObject report = new JsonObject();
        report.addProperty("status", "ERROR");
        try {
            JsonObject entry;
            try (Reader reader = Files.newBufferedReader(Paths.get("candidate.json"))) {
                entry = new JsonParser().parse(reader).getAsJsonObject();
            }
            if (!OfflineFilterValidator.PROFILE.equals(entry.get("profile").getAsString())
                    || !"VALIDATION_PASS".equals(entry.get("status").getAsString())) {
                throw new IllegalArgumentException("Unsupported staged candidate");
            }
            String id = java.util.UUID.fromString(entry.get("id").getAsString()).toString();
            JsonObject pending = new JsonObject();
            pending.addProperty("status", "PENDING_REVERIFICATION");
            pending.addProperty("id", id);
            FilterProbeFiles.write(Paths.get("approved", id + ".json"), pending);
            long seed = Long.parseLong(entry.get("seed").getAsString());
            FilterCandidateSearch.Candidate candidate = FilterCandidateSearch.checkExactSeed(seed,
                    FilterCandidateSearch.Type.parse(entry.get("type").getAsString()), false);
            if (candidate == null || candidate.seedForValidation() != server.getOverworld().getSeed()) {
                throw new IllegalStateException("Staged candidate or loaded world mismatch");
            }
            BastionLayoutProbe.verifyTemplates(server.getOverworld());
            report = OfflineFilterValidator.validate(server, candidate);
            report.addProperty("id", id);
            boolean matches = "VALIDATION_PASS".equals(report.get("status").getAsString())
                    && report.get("portal").equals(entry.get("portal"))
                    && report.get("structure").equals(entry.get("structure"));
            report.addProperty("status", matches ? "VERIFIED" : "REVALIDATION_REJECTED");
            if (matches) {
                JsonObject approved = new JsonObject();
                approved.addProperty("formatVersion", 1);
                approved.addProperty("profile", OfflineFilterValidator.PROFILE);
                approved.addProperty("status", "VERIFIED_PROFILE");
                approved.addProperty("seed", entry.get("seed").getAsString());
                approved.addProperty("type", entry.get("type").getAsString());
                approved.addProperty("id", id);
                approved.add("portal", report.get("portal"));
                approved.add("structure", report.get("structure"));
                approved.addProperty("verifiedAt", java.time.Instant.now().toString());
                FilterProbeFiles.write(Paths.get("approved", id + ".json"), approved);
            }
            ZsgRooms.LOGGER.info("[FilterVerify] COMPLETE: status={}", report.get("status").getAsString());
        } catch (Throwable failure) {
            report.addProperty("status", "ERROR");
            ZsgRooms.LOGGER.error("[FilterVerify] FAIL: " + failure.getClass().getSimpleName(), failure);
        } finally {
            report.addProperty("elapsedMs", (System.nanoTime() - started) / 1000000);
            try { FilterProbeFiles.write(Paths.get("verification.json"), report); }
            catch (Exception failure) { ZsgRooms.LOGGER.error("[FilterVerify] FAIL: report write", failure); }
            server.stop(false);
        }
    }
}
