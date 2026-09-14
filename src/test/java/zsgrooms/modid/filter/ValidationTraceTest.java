package zsgrooms.modid.filter;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ValidationTraceTest {
    @Test void summaryCountsCandidatesSeparatelyFromCalls() {
        com.google.gson.JsonArray attempts = new com.google.gson.JsonArray();
        try (ValidationTrace trace = new ValidationTrace()) {
            ValidationTrace.measure("pool", () -> false);
            ValidationTrace.measure("pool", () -> true);
            JsonObject attempt = new JsonObject();
            attempt.add("checks", trace.json());
            attempts.add(attempt);
            attempts.add(new com.google.gson.JsonParser().parse(attempt.toString()));
            attempts.add(new JsonObject());
        }
        JsonObject item = ValidationTraceSummary.aggregate(attempts).getAsJsonObject("pool");
        assertEquals(2, item.get("candidatesReached").getAsInt());
        assertEquals(4, item.get("calls").getAsInt());
        assertEquals(2, item.get("falseResults").getAsInt());
    }
    @Test void countsRepeatedChecksWithoutDoubleCountingNestedTime() {
        try (ValidationTrace trace = new ValidationTrace()) {
            ValidationTrace.run("parent", () -> {
                assertFalse(ValidationTrace.measure("child", () -> false));
                assertTrue(ValidationTrace.measure("child", () -> true));
            });
            JsonObject parent = trace.json().getAsJsonObject("parent");
            JsonObject child = trace.json().getAsJsonObject("child");
            assertEquals(2, child.get("calls").getAsInt());
            assertEquals(1, child.get("falseResults").getAsInt());
            assertEquals(parent.get("inclusiveMs").getAsDouble(), parent.get("exclusiveMs").getAsDouble()
                    + child.get("inclusiveMs").getAsDouble(), 0.000001);
        }
    }

    @Test void errorsAreCountedAndContextIsCleared() {
        try (ValidationTrace trace = new ValidationTrace()) {
            assertThrows(IllegalArgumentException.class, () -> ValidationTrace.run("failed", () -> { throw new IllegalArgumentException(); }));
            assertEquals(1, trace.json().getAsJsonObject("failed").get("errors").getAsInt());
        }
        assertEquals("unchanged", ValidationTrace.measure("disabled", () -> "unchanged"));
        try (ValidationTrace trace = new ValidationTrace()) { assertEquals(0, trace.json().size()); }
    }

    @Test void workerThreadsHaveIndependentContexts() throws Exception {
        try (ValidationTrace trace = new ValidationTrace()) {
            Thread other = new Thread(() -> ValidationTrace.measure("other", () -> true));
            other.start();
            other.join();
            assertEquals(0, trace.json().size());
        }
    }
}
