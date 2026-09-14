package zsgrooms.modid.filter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/** Standalone development runner: initializes registries, but never starts a server or creates chunks. */
public final class FilterCandidateMain {
    private static final Path OUTPUT = Paths.get("run", "filter-probe", "candidate.json");

    public static void main(String[] args) throws Exception {
        write(status("SEARCHING"));
        try {
            FilterCandidateSearch.Type type = FilterCandidateSearch.Type.parse(System.getProperty("zsgrooms.filterType", "village"));
            boolean overpowered = Boolean.getBoolean("zsgrooms.filterOp");
            int samples = Integer.getInteger("zsgrooms.filterSamples", 1);
            if (samples < 1 || samples > 100) throw new IllegalArgumentException("Sample count must be 1-100");
            long start = Long.getLong("zsgrooms.filterSearchSeed", 1);
            long limit = Long.getLong("zsgrooms.filterAttempts", 100000);
            long timeout = Long.getLong("zsgrooms.filterTimeoutMillis", 15000);
            SharedConstants.getGameVersion();
            Bootstrap.initialize();
            FilterCandidateSearch.Result last = null;
            long totalMillis = 0;
            int successful = 0;
            for (int sample = 0; sample < samples; sample++) {
                last = FilterCandidateSearch.search(type, overpowered, start + sample, limit, timeout, () -> false);
                totalMillis += last.elapsedMillis;
                if (last.candidate().isPresent()) successful++;
                StringBuilder counts = new StringBuilder();
                for (FilterCandidateSearch.Rejection reason : FilterCandidateSearch.Rejection.values()) {
                    counts.append(' ').append(reason.name()).append('=').append(last.rejected(reason));
                }
                System.out.println("[FilterCandidate] sample=" + (sample + 1) + " status=" + last.status
                        + " attempts=" + last.attempts + " searchMs=" + last.elapsedMillis + counts);
            }
            System.out.println("[FilterCandidate] samples=" + samples + " preliminary=" + successful
                    + " meanSearchMs=" + totalMillis / samples + " (excludes bootstrap and final validation)");
            if (last == null || !last.candidate().isPresent()) {
                write(status(last == null ? "FAILED" : last.status.name()));
                throw new IllegalStateException("No preliminary candidate within the search budget");
            }
            FilterCandidateSearch.Candidate candidate = last.candidate().get();
            JsonObject output = status("PRELIMINARY");
            output.addProperty("id", UUID.randomUUID().toString());
            output.addProperty("type", type.name());
            output.addProperty("overpowered", overpowered);
            output.addProperty("seed", Long.toString(candidate.seedForValidation()));
            output.addProperty("mainChunkX", candidate.main.x);
            output.addProperty("mainChunkZ", candidate.main.z);
            output.addProperty("bastionChunkX", candidate.bastion.x);
            output.addProperty("bastionChunkZ", candidate.bastion.z);
            output.addProperty("fortressChunkX", candidate.fortress.x);
            output.addProperty("fortressChunkZ", candidate.fortress.z);
            JsonArray hints = new JsonArray();
            for (BlockPos hint : candidate.lakeAttempts) {
                JsonObject position = new JsonObject();
                position.addProperty("x", hint.getX());
                position.addProperty("y", hint.getY());
                position.addProperty("z", hint.getZ());
                hints.add(position);
            }
            output.add("lakeAttempts", hints);
            write(output);
            System.out.println("[FilterCandidate] Private preliminary handoff written; not approved for racing");
        } catch (Exception failure) {
            write(status("FAILED"));
            throw failure;
        }
    }

    private static JsonObject status(String status) {
        JsonObject object = new JsonObject();
        object.addProperty("formatVersion", FilterCandidateSearch.FORMAT_VERSION);
        object.addProperty("status", status);
        return object;
    }

    private static void write(JsonObject result) throws Exception {
        FilterProbeFiles.write(OUTPUT, result);
    }
}
