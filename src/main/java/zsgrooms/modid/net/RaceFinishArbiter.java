package zsgrooms.modid.net;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import zsgrooms.modid.SpeedRunIgtBridge;

import java.util.LinkedHashMap;
import java.util.Map;

/** Compares local race durations; the host clock is used only for collecting reports. */
public final class RaceFinishArbiter {
    public static final long COLLECTION_MILLIS = 2000L;
    public static final long MAX_ELAPSED_NANOS = 7L * 24L * 3600L * 1000000000L;
    public static final String DRAW_REASON = "Identical recorded race times";
    private static final Gson GSON = new Gson();
    private final Map<String, Finish> finishes = new LinkedHashMap<String, Finish>();
    private String raceId = "";
    private long deadline;
    private boolean decided;

    public static long now() {
        return System.nanoTime() / 1000000L;
    }

    public synchronized void beginRace(String id) {
        if (!raceId.equals(id)) {
            raceId = id == null ? "" : id;
            finishes.clear();
            decided = false;
        }
    }

    public static String completion(String raceId, long elapsedNanos, long igt) {
        JsonObject report = new JsonObject();
        report.addProperty("version", 3);
        report.addProperty("raceId", raceId);
        // A decimal string preserves every nanosecond in relays/tools using JavaScript numbers.
        report.addProperty("elapsedNanos", Long.toString(elapsedNanos));
        report.addProperty("igt", safeIgt(igt));
        return GSON.toJson(report);
    }

    public static String reason(String value) {
        JsonObject report = parse(value);
        if (report != null) {
            long igt = displayIgt(report);
            return igt > 0L ? "Beat the seed in " + SpeedRunIgtBridge.formatMilliseconds(igt) + " IGT"
                    : "Beat the seed";
        }
        return value == null || value.trim().isEmpty() ? "Beat the seed" : value.trim();
    }

    public synchronized boolean submit(String player, String value, long received, boolean contested) {
        if (decided || raceId.isEmpty() || player == null || player.trim().isEmpty()
                || finishes.containsKey(player) || !finishes.isEmpty() && received >= deadline) {
            return false;
        }
        JsonObject report = parse(value);
        Long elapsed = integer(report == null ? null : report.get("elapsedNanos"));
        Long version = integer(report == null ? null : report.get("version"));
        if (report == null || version == null || version != 3L
                || !report.has("raceId") || !report.get("raceId").isJsonPrimitive()
                || !raceId.equals(report.get("raceId").getAsString())
                || elapsed == null || elapsed < 0L || elapsed > MAX_ELAPSED_NANOS) {
            return false;
        }
        if (finishes.isEmpty()) {
            deadline = received + (contested ? COLLECTION_MILLIS : 0L);
        }
        finishes.put(player, new Finish(player, reason(value), elapsed));
        return true;
    }

    public synchronized Decision poll(long now) {
        if (decided || finishes.isEmpty() || now < deadline) {
            return null;
        }
        decided = true;
        Finish first = null;
        for (Finish finish : finishes.values()) {
            if (first == null || finish.elapsedNanos < first.elapsedNanos) {
                first = finish;
            }
        }
        for (Finish finish : finishes.values()) {
            if (finish != first && finish.elapsedNanos == first.elapsedNanos) {
                return new Decision("Draw", DRAW_REASON);
            }
        }
        return new Decision(first.player, first.reason);
    }

    public synchronized boolean hasPendingFinish() {
        return !decided && !finishes.isEmpty();
    }

    private static JsonObject parse(String value) {
        if (value == null || value.length() > 1024) {
            return null;
        }
        try {
            JsonElement parsed = new JsonParser().parse(value);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Long integer(JsonElement value) {
        try {
            return value == null || !value.isJsonPrimitive() ? null : Long.valueOf(value.getAsString());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static long displayIgt(JsonObject report) {
        Long igt = integer(report.get("igt"));
        return igt == null ? 0L : safeIgt(igt);
    }

    private static long safeIgt(long igt) {
        return igt > 0L && igt <= MAX_ELAPSED_NANOS / 1000000L ? igt : 0L;
    }

    public static final class Decision {
        public final String winner;
        public final String reason;

        Decision(String winner, String reason) {
            this.winner = winner;
            this.reason = reason;
        }
    }

    private static final class Finish {
        final String player;
        final String reason;
        final long elapsedNanos;

        Finish(String player, String reason, long elapsedNanos) {
            this.player = player;
            this.reason = reason;
            this.elapsedNanos = elapsedNanos;
        }
    }
}
