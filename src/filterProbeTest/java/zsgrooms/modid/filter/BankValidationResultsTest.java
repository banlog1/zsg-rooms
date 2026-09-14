package zsgrooms.modid.filter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class BankValidationResultsTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.getGameVersion();
        net.minecraft.Bootstrap.initialize();
    }

    @Test void queuedSistersCannotBeStagedTwice() throws Exception {
        JsonArray staged = new JsonArray();
        StagedFilterSearch session = new StagedFilterSearch(FilterCandidateSearch.Type.TEMPLE, false, 1, 100,
                (seed, main, bastion) -> true);
        BankValidationResults results = new BankValidationResults(staged, session, System.nanoTime(), 3);
        JsonObject sister = new JsonObject();
        results.add(CompletableFuture.completedFuture(pass()), new JsonObject(), 1, "staged", 1);
        results.add(CompletableFuture.completedFuture(pass()), sister, 1 | (3L << 48), "staged", 1);
        results.collect(false);
        assertEquals(1, staged.size());
        assertEquals("DUPLICATE_FAMILY", sister.get("stagingDisposition").getAsString());
    }
    @Test void resultsCommitInSubmissionOrder() throws Exception {
        JsonArray staged = new JsonArray();
        BankValidationResults results = new BankValidationResults(staged, null, System.nanoTime(), 2);
        CompletableFuture<JsonObject> first = new CompletableFuture<JsonObject>();
        results.add(first, new JsonObject(), 1, "legacy", 1);
        results.add(CompletableFuture.completedFuture(pass()), new JsonObject(), 2, "legacy", 2);
        results.collect(false);
        assertEquals(0, staged.size());
        first.complete(pass());
        results.collect(false);
        assertEquals("1", staged.get(0).getAsJsonObject().get("seed").getAsString());
        assertEquals("2", staged.get(1).getAsJsonObject().get("seed").getAsString());
        assertEquals(2, results.highWater);
        assertEquals(0, results.size());
    }

    @Test void boundedBacklogDoesNotOverfillTarget() throws Exception {
        JsonArray staged = new JsonArray();
        BankValidationResults results = new BankValidationResults(staged, null, System.nanoTime(), 1);
        JsonObject extra = new JsonObject();
        results.add(CompletableFuture.completedFuture(pass()), new JsonObject(), 1, "legacy", 1);
        results.add(CompletableFuture.completedFuture(pass()), extra, 2, "legacy", 2);
        results.collect(false);
        assertEquals(1, staged.size());
        assertEquals("TARGET_ALREADY_REACHED", extra.get("stagingDisposition").getAsString());
        assertFalse(extra.has("seed"));
    }

    @Test void errorsKeepDiagnosticsAndNeverStage() {
        JsonArray staged = new JsonArray();
        BankValidationResults results = new BankValidationResults(staged, null, System.nanoTime(), 1);
        JsonObject error = new JsonObject();
        error.addProperty("status", "ERROR");
        error.add("checks", new JsonObject());
        JsonObject output = new JsonObject();
        results.add(CompletableFuture.completedFuture(error), output, 1, "legacy", 1);
        assertThrows(IllegalStateException.class, () -> results.collect(false));
        assertEquals("ERROR", output.get("status").getAsString());
        assertTrue(output.has("checks"));
        assertEquals(0, staged.size());
    }

    @Test void exceptionalFutureDoesNotStage() {
        BankValidationResults results = new BankValidationResults(new JsonArray(), null, System.nanoTime(), 1);
        CompletableFuture<JsonObject> future = new CompletableFuture<JsonObject>();
        future.completeExceptionally(new IllegalStateException("worker failed"));
        results.add(future, new JsonObject(), 1, "legacy", 1);
        assertThrows(java.util.concurrent.ExecutionException.class, () -> results.collect(false));
    }

    private static JsonObject pass() {
        JsonObject result = new JsonObject();
        result.addProperty("status", "VALIDATION_PASS");
        result.add("rejections", new JsonArray());
        return result;
    }
}
