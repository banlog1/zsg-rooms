package zsgrooms.modid.net;

import com.google.gson.Gson;
import zsgrooms.modid.SpeedRunIgtBridge;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Host-clock finish ordering. All durations are monotonic milliseconds, never wall-clock dates. */
public final class RaceFinishArbiter {
    public static final long COLLECTION_MILLIS = 2000L;
    public static final long DRAW_MARGIN_MILLIS = 50L;
    public static final String UNRESOLVED_REASON = "Finish too close to call (network timing)";
    private static final long SAMPLE_LIFETIME = 60000L;
    private static final long MAX_TRANSIT = 10000L;
    private static final Gson GSON = new Gson();
    private final Map<Long, Long> probes = new LinkedHashMap<Long, Long>();
    private final Map<String, Deque<Sample>> samples = new HashMap<String, Deque<Sample>>();
    private final Map<String, Finish> finishes = new LinkedHashMap<String, Finish>();
    private String raceId = "";
    private long raceStarted;
    private long nonce;
    private long deadline;
    private boolean decided;

    public static long now() {
        return System.nanoTime() / 1000000L;
    }

    public synchronized void beginRace(String id, long now) {
        if (!raceId.equals(id)) {
            raceId = id;
            raceStarted = now;
            finishes.clear();
            decided = false;
        }
    }

    public synchronized String probe(long now) {
        Probe probe = new Probe();
        probe.nonce = ++nonce;
        probes.put(probe.nonce, now);
        while (probes.size() > 8) {
            probes.remove(probes.keySet().iterator().next());
        }
        return GSON.toJson(probe);
    }

    public static String reply(String value, long received, long sent) {
        Probe probe = parse(value, Probe.class);
        if (probe == null || probe.nonce <= 0) {
            return null;
        }
        Reply reply = new Reply();
        reply.nonce = probe.nonce;
        reply.received = received;
        reply.sent = sent;
        return GSON.toJson(reply);
    }

    public synchronized void resetClocks() {
        samples.clear();
        probes.clear();
    }

    public synchronized void forgetPlayer(String player) {
        samples.remove(player);
    }

    public synchronized void receiveReply(String player, String value, long received) {
        Reply reply = parse(value, Reply.class);
        Long sent = reply == null ? null : probes.get(reply.nonce);
        if (sent == null || received < sent || received - sent > MAX_TRANSIT
                || reply.sent < reply.received || reply.sent - reply.received > received - sent) {
            return;
        }
        Deque<Sample> history = samples.computeIfAbsent(player, ignored -> new ArrayDeque<Sample>());
        for (Sample sample : history) {
            if (sample.nonce == reply.nonce) {
                return;
            }
        }
        // Four timestamps exclude time spent handling the probe on the guest.
        double rtt = (received - sent) - (reply.sent - reply.received);
        double offset = (((double) reply.received - sent) + ((double) reply.sent - received)) / 2.0D;
        history.addLast(new Sample(reply.nonce, received, offset, rtt));
        while (history.size() > 8) {
            history.removeFirst();
        }
    }

    public static String completion(String raceId, long entered, long igt) {
        Completion report = new Completion();
        report.version = 2;
        report.raceId = raceId;
        report.entered = entered;
        report.igt = Math.max(0L, igt);
        return GSON.toJson(report);
    }

    public static String reason(String value) {
        Completion report = parse(value, Completion.class);
        if (report != null && report.version == 2) {
            return report.igt > 0L ? "Beat the seed in " + SpeedRunIgtBridge.formatMilliseconds(report.igt) + " IGT"
                    : "Beat the seed";
        }
        return value == null || value.trim().isEmpty() ? "Beat the seed" : value.trim();
    }

    public synchronized boolean submit(String player, String value, boolean host, long received) {
        return submit(player, value, host, received, true);
    }

    public synchronized boolean submit(String player, String value, boolean host, long received, boolean contested) {
        if (decided || raceId.isEmpty() || finishes.containsKey(player)
                || !finishes.isEmpty() && received >= deadline) {
            return false;
        }
        Completion report = parse(value, Completion.class);
        boolean legacy = value != null && (value.equals("Beat the seed")
                || value.startsWith("Beat the seed in ") && value.endsWith(" IGT"));
        if (!legacy && (report == null || report.version != 2 || !raceId.equals(report.raceId)
                || report.igt < 0L || report.igt > 7L * 24L * 3600000L)) {
            return false;
        }
        Sample sample = bestSample(player, received);
        boolean timed = !legacy && (host || sample != null);
        double entered = timed ? report.entered - (host ? 0.0D : sample.offset) : received;
        // Half the RTT bounds unknown directional asymmetry; allow timestamp quantization and clock drift.
        double uncertainty = !timed ? Double.POSITIVE_INFINITY
                : host ? 2.0D : sample.rtt / 2.0D + 2.0D + (received - sample.measured) * 0.0001D;
        if (timed && (entered + uncertainty < raceStarted || entered - uncertainty > received
                || entered < received - MAX_TRANSIT)) {
            return false;
        }
        if (finishes.isEmpty()) {
            deadline = received + (contested ? COLLECTION_MILLIS : 0L);
        }
        finishes.put(player, new Finish(player, reason(value), entered, timed));
        return true;
    }

    public synchronized Decision poll(long now) {
        if (decided || finishes.isEmpty() || now < deadline) {
            return null;
        }
        decided = true;
        Finish first = null;
        for (Finish finish : finishes.values()) {
            if (first == null || finish.entered < first.entered) {
                first = finish;
            }
        }
        for (Finish finish : finishes.values()) {
            if (finish != first && (!first.timed || !finish.timed
                    || finish.entered - first.entered < DRAW_MARGIN_MILLIS)) {
                return new Decision("Draw", UNRESOLVED_REASON);
            }
        }
        return new Decision(first.player, first.reason);
    }

    public synchronized boolean hasPendingFinish() {
        return !decided && !finishes.isEmpty();
    }

    private Sample bestSample(String player, long now) {
        Deque<Sample> history = samples.get(player);
        Sample best = null;
        if (history != null) {
            for (Sample sample : history) {
                if (now >= sample.measured && now - sample.measured <= SAMPLE_LIFETIME
                        && (best == null || sample.rtt < best.rtt)) {
                    best = sample;
                }
            }
        }
        return best;
    }

    private static <T> T parse(String value, Class<T> type) {
        if (value == null || value.length() > 1024) {
            return null;
        }
        try {
            return GSON.fromJson(value, type);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static final class Decision {
        public final String winner;
        public final String reason;

        Decision(String winner, String reason) {
            this.winner = winner;
            this.reason = reason;
        }
    }

    private static class Probe {
        long nonce;
    }

    private static final class Reply extends Probe {
        long received;
        long sent;
    }

    private static final class Completion {
        int version;
        String raceId;
        long entered;
        long igt;
    }

    private static final class Sample {
        final long nonce;
        final long measured;
        final double offset;
        final double rtt;

        Sample(long nonce, long measured, double offset, double rtt) {
            this.nonce = nonce;
            this.measured = measured;
            this.offset = offset;
            this.rtt = rtt;
        }
    }

    private static final class Finish {
        final String player;
        final String reason;
        final double entered;
        final boolean timed;

        Finish(String player, String reason, double entered, boolean timed) {
            this.player = player;
            this.reason = reason;
            this.entered = entered;
            this.timed = timed;
        }
    }
}
