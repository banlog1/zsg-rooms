package zsgrooms.modid.filter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Commit in submission order, not worker completion order. Never stage two sisters from one family. */
final class BankValidationResults {
    private final ArrayDeque<Pending> pending = new ArrayDeque<Pending>();
    private final Set<Long> acceptedFamilies = new HashSet<Long>();
    private final JsonArray staged;
    private final StagedFilterSearch session;
    private final long started;
    private final int target;
    int submitted, completed, highWater;

    BankValidationResults(JsonArray staged, StagedFilterSearch session, long started, int target) {
        this.staged = staged;
        this.session = session;
        this.started = started;
        this.target = target;
    }

    void add(CompletableFuture<JsonObject> future, JsonObject placeholder, long seed, String mode, long offset) {
        pending.add(new Pending(future, placeholder, seed, mode, offset));
        submitted++;
        highWater = Math.max(highWater, pending.size());
    }

    int size() { return pending.size(); }

    void collect(boolean wait) throws Exception {
        while (!pending.isEmpty()) {
            Pending job = pending.peek();
            if (!wait && !job.future.isDone()) return;
            JsonObject result = job.future.get();
            pending.remove();
            completed++;
            result.entrySet().forEach(field -> job.placeholder.add(field.getKey(), field.getValue()));
            job.placeholder.addProperty("completedAtMs", (System.nanoTime() - started) / 1000000);
            if (job.placeholder.has("proposalStartedAtMs")) {
                job.placeholder.addProperty("totalMs", job.placeholder.get("completedAtMs").getAsLong()
                        - job.placeholder.get("proposalStartedAtMs").getAsLong());
            }
            if ("ERROR".equals(result.get("status").getAsString())) throw new IllegalStateException("Verifier reported an error; see candidate check diagnostics");
            if ("VALIDATION_PASS".equals(result.get("status").getAsString())) {
                long family = job.seed & StagedFilterSearch.MASK;
                if (session != null && !acceptedFamilies.add(family)) {
                    job.placeholder.addProperty("stagingDisposition", "DUPLICATE_FAMILY");
                } else if (staged.size() >= target) {
                    job.placeholder.addProperty("stagingDisposition", "TARGET_ALREADY_REACHED");
                } else {
                    job.placeholder.addProperty("foundAtMs", (System.nanoTime() - started) / 1000000);
                    JsonObject entry = new JsonObject();
                    job.placeholder.entrySet().forEach(field -> entry.add(field.getKey(), field.getValue()));
                    entry.addProperty("seed", Long.toString(job.seed));
                    entry.addProperty("searchMode", job.mode);
                    entry.addProperty("searchOffset", Long.toString(job.offset));
                    staged.add(entry);
                    if (session != null) session.skipRemainingFamily(job.seed);
                }
            }
            // Backpressure waits for only the oldest result, then resumes producing.
            if (wait) return;
        }
    }

    private static final class Pending {
        final CompletableFuture<JsonObject> future;
        final JsonObject placeholder;
        final long seed, offset;
        final String mode;
        Pending(CompletableFuture<JsonObject> future, JsonObject placeholder, long seed, String mode, long offset) {
            this.future = future; this.placeholder = placeholder; this.seed = seed; this.mode = mode; this.offset = offset;
        }
    }
}
