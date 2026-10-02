package zsgrooms.modid;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Host-owned standings. Stage numbers are zero-based; elapsed time never resets. */
public final class RaceSequence {
    public static final int VERSION = 1;
    public static final int MAX_SEEDS = 20;
    public static final long SKIP_NANOS = 30L * 60 * 1_000_000_000;
    private static final long MAX_TIME = 7L * 24 * 60 * 60 * 1_000_000_000;
    private static final Gson GSON = new Gson();

    public int version = VERSION;
    public String raceId;
    public int goal;
    public int finisherLimit = 1;
    private transient long closeAfterNanos;
    private transient boolean closing;
    private transient String closingCutoff;
    private List<String> seeds = new ArrayList<>();
    private Map<String, Runner> runners = new LinkedHashMap<>();

    public RaceSequence(String raceId, List<String> seeds, Collection<String> players) {
        this(raceId, seeds, players, 1);
    }

    public RaceSequence(String raceId, List<String> seeds, Collection<String> players, int finisherLimit) {
        if (raceId == null || raceId.isEmpty() || seeds == null || seeds.isEmpty() || seeds.size() > MAX_SEEDS
                || seeds.stream().anyMatch(s -> s == null || s.isEmpty()) || players == null || players.isEmpty()) {
            throw new IllegalArgumentException("Invalid race sequence");
        }
        this.raceId = raceId;
        this.goal = seeds.size();
        this.seeds.addAll(seeds);
        for (String name : players) runners.put(name, new Runner(name));
        this.finisherLimit = Math.max(1, Math.min(finisherLimit, runners.size()));
    }

    public Runner runner(String name) { return runners.get(name); }
    public String seed(int stage) { return stage >= 0 && stage < seeds.size() ? seeds.get(stage) : null; }
    public boolean active(String name) {
        Runner runner = runner(name);
        return runner != null && !runner.done();
    }
    public boolean complete() { return runners.values().stream().allMatch(Runner::done); }

    /** The host settles close finishes before stopping runners outside the requested places. */
    public boolean closeAtLimit(long elapsedNanos, long nowNanos, boolean closeContender) {
        List<Runner> placed = standings();
        placed.removeIf(r -> !r.placed());
        if (placed.size() < finisherLimit) return complete();
        Runner cutoff = placed.get(finisherLimit - 1);
        if (!closing || !cutoff.name.equals(closingCutoff)) {
            closing = true;
            closingCutoff = cutoff.name;
            closeAfterNanos = nowNanos + (closeContender ? 2_000_000_000L : 0L);
        }
        if (!complete() && nowNanos - closeAfterNanos < 0) return false;
        // A fast skip must not beat a runner who can still win on penalty-adjusted time.
        if (cutoff.skipped > 0 && runners.values().stream().anyMatch(r -> !r.done()
                && (elapsedNanos < 0 || elapsedNanos + r.penaltyNanos() <= cutoff.adjustedNanos() + 2_000_000_000L))) return false;
        // Keep completion data, but only award the requested places (including exact ties).
        for (Runner runner : runners.values()) {
            if (!runner.done() || runner.placed() && runner.adjustedNanos() > cutoff.adjustedNanos()) runner.stopped = true;
        }
        return true;
    }

    public void awardSingleSeedForfeitWin() {
        if (goal == 1) awardForfeitWin();
    }

    public void awardForfeitWin() {
        if (finisherLimit != 1 || runners.size() < 2
                || runners.values().stream().anyMatch(Runner::placed)) return;
        List<Runner> active = new ArrayList<>();
        for (Runner runner : runners.values()) if (!runner.done()) active.add(runner);
        if (active.size() == 1) active.get(0).wonByForfeit = true;
    }

    public boolean submit(String player, String payload) {
        return submit(player, payload, true);
    }

    public boolean submit(String player, String payload, boolean started) {
        try {
            Report report = GSON.fromJson(payload, Report.class);
            Runner runner = runner(player);
            if (report == null || report.version != VERSION || !raceId.equals(report.raceId)
                    || runner == null || runner.done() || report.stage != runner.stage) return false;
            if ("dnf".equals(report.action)) return withdraw(player);
            if (!started) return false;
            if (!"finish".equals(report.action) && !"skip".equals(report.action)) return false;
            long elapsed = Long.parseLong(report.elapsedNanos);
            if (elapsed < runner.elapsedNanos || elapsed < 0 || elapsed > MAX_TIME) return false;
            if ("skip".equals(report.action) && goal == 1) return withdraw(player);
            runner.elapsedNanos = elapsed;
            if ("skip".equals(report.action)) runner.skipped++;
            runner.stage++;
            runner.finished = runner.stage == goal;
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public boolean withdraw(String name) {
        Runner runner = runner(name);
        if (runner == null || runner.done()) return false;
        runner.dnf = true;
        return true;
    }

    public List<Runner> standings() {
        List<Runner> result = new ArrayList<>(runners.values());
        result.sort(Comparator.comparingInt((Runner r) -> r.placed() ? 0 : r.done() ? 2 : 1)
                .thenComparingLong(r -> r.placed() ? r.adjustedNanos() : -r.stage)
                .thenComparing(r -> r.name));
        return result;
    }

    public int place(String name) {
        Runner runner = runner(name);
        if (runner == null || !runner.placed()) return 0;
        return 1 + (int) runners.values().stream().filter(r -> r.placed()
                && r.adjustedNanos() < runner.adjustedNanos()).count();
    }

    /** Snapshots reveal only stages somebody has reached, never the future sequence. */
    public RaceSequence publicCopy() {
        RaceSequence copy = GSON.fromJson(GSON.toJson(this), RaceSequence.class);
        int revealed = Math.min(goal, 1 + runners.values().stream().mapToInt(r -> r.stage).max().orElse(0));
        copy.seeds = new ArrayList<>(seeds.subList(0, Math.min(revealed, seeds.size())));
        return copy;
    }

    public boolean valid(String expectedRaceId) {
        return version == VERSION && raceId != null && raceId.equals(expectedRaceId) && goal >= 1 && goal <= MAX_SEEDS
                && finisherLimit >= 1 && finisherLimit <= 64
                && seeds != null && !seeds.isEmpty() && seeds.size() <= goal
                && seeds.stream().allMatch(s -> s != null && !s.isEmpty())
                && runners != null && !runners.isEmpty() && runners.size() <= 64
                && runners.entrySet().stream().allMatch(e -> e.getValue() != null && e.getKey().equals(e.getValue().name)
                && e.getValue().stage >= 0 && e.getValue().stage <= goal && e.getValue().skipped >= 0
                && e.getValue().skipped <= e.getValue().stage && e.getValue().elapsedNanos >= 0
                && e.getValue().elapsedNanos <= MAX_TIME && e.getValue().finished == (e.getValue().stage == goal)
                && (e.getValue().finished || e.getValue().stopped ? 1 : 0) + (e.getValue().dnf ? 1 : 0)
                    + (e.getValue().wonByForfeit ? 1 : 0) <= 1
                && (!e.getValue().wonByForfeit || finisherLimit == 1));
    }

    public static String report(String raceId, int stage, long elapsed, String action) {
        Report report = new Report();
        report.raceId = raceId;
        report.stage = stage;
        report.elapsedNanos = Long.toString(elapsed);
        report.action = action;
        return GSON.toJson(report);
    }

    public static String time(long nanos) {
        long seconds = Math.max(0, nanos / 1_000_000_000);
        return String.format(java.util.Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    public static final class Runner {
        public String name;
        public int stage;
        public int skipped;
        public long elapsedNanos;
        public boolean finished;
        public boolean dnf;
        public boolean stopped;
        public boolean wonByForfeit;
        private Runner(String name) { this.name = name; }
        public boolean placed() { return (finished || wonByForfeit) && !stopped; }
        public boolean done() { return placed() || dnf || stopped; }
        public long penaltyNanos() { return skipped * SKIP_NANOS; }
        public long adjustedNanos() { return elapsedNanos + penaltyNanos(); }
    }

    private static final class Report {
        int version = VERSION;
        String raceId;
        int stage;
        String elapsedNanos;
        String action;
    }
}
