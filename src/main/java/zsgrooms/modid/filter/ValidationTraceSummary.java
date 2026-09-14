package zsgrooms.modid.filter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;

final class ValidationTraceSummary {
    static JsonObject aggregate(JsonArray attempts) {
        JsonObject totals = new JsonObject();
        for (JsonElement attempt : attempts) {
            JsonObject checks = attempt.getAsJsonObject().getAsJsonObject("checks");
            if (checks == null) continue;
            for (Map.Entry<String, JsonElement> entry : checks.entrySet()) {
                JsonObject source = entry.getValue().getAsJsonObject();
                JsonObject total = totals.getAsJsonObject(entry.getKey());
                if (total == null) { total = new JsonObject(); totals.add(entry.getKey(), total); }
                total.addProperty("candidatesReached", number(total, "candidatesReached") + 1);
                for (String field : new String[]{"calls", "falseResults", "errors", "inclusiveMs", "exclusiveMs"}) {
                    total.addProperty(field, number(total, field) + number(source, field));
                }
                total.addProperty("maxCandidateMs", Math.max(number(total, "maxCandidateMs"), number(source, "inclusiveMs")));
            }
        }
        return totals;
    }

    private static double number(JsonObject value, String field) {
        return value.has(field) ? value.get(field).getAsDouble() : 0;
    }
}
