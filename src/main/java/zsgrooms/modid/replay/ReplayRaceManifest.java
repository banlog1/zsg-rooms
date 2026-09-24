package zsgrooms.modid.replay;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Bounded, process-local capture state. Only offsets, never absolute nanoTime, leave this class. */
final class ReplayRaceManifest {
    static final int MAX_RACES = 1024;
    static final int MAX_INTERVALS = 4096;
    static final int MAX_TIMING_SAMPLES = 12000;
    private final long originNanos;
    private final Data data;
    private Race currentRace;
    private Interval currentInterval;
    private boolean sealed;
    private long[] loading;
    private long[] screen;

    synchronized void allowTemplePrediction(Long seed) {
        if (!sealed) data.templePredictionSeed = seed;
    }

    synchronized void recordStewOrder(List<String> order) {
        if (!sealed && data.stewOrder == null) data.stewOrder = new ArrayList<>(order);
    }

    synchronized void recordChest(long time, int opening, int world, int syncId, String dimension,
                                  long first, long second, int stateFirst, int stateSecond) {
        if (sealed || data.chestOpenings.size() >= MAX_INTERVALS) return;
        data.chestOpenings.add(new ChestOpening(time, opening, world, syncId, dimension, first, second, stateFirst, stateSecond));
    }

    synchronized void recordChestLoot(long time, int chunk, int world, List<ReplayChestLootPacket.Loot> loot) {
        if (sealed || world < 0 || loot == null) return;
        for (ReplayChestLootPacket.Loot entry : loot) {
            if (data.chestLoot.size() >= 2048) break;
            data.chestLoot.add(new ChestLoot(time, chunk, world, entry));
        }
    }

    /** Screen kind: 0 = none, 1 = inventory, 2 = crafting table. Only transitions allocate. */
    synchronized void recordScreen(long time, int kind) {
        if (sealed || time < 0 || kind < 0 || kind > 2) return;
        if (screen != null) {
            if (time < screen[0] || screen[2] == kind) return;
            screen[1] = time;
            screen = null;
        }
        if (kind != 0 && data.screenIntervals.size() < MAX_INTERVALS) {
            screen = new long[]{time, time, kind};
            data.screenIntervals.add(screen);
        }
    }

    synchronized void recordLoading(long time, boolean active) {
        if (sealed || time < 0) return;
        if (active) recordScreen(time, 0);
        if (active && loading == null && data.loadingIntervals.size() < MAX_INTERVALS) {
            loading = new long[]{time, time};
            data.loadingIntervals.add(loading);
        } else if (!active && loading != null) {
            loading[1] = Math.max(loading[0], time);
            loading = null;
        }
    }

    ReplayRaceManifest(UUID recordingId, UUID recorder, String displayName, long originNanos) {
        this.originNanos = originNanos;
        data = new Data(recordingId, recorder, displayName);
    }

    synchronized void startRace(String raceId, UUID player, long startNanos) {
        startRace(raceId, player, startNanos, "", -1);
    }

    synchronized void startRace(String raceId, UUID player, long startNanos, String testGroup, int firstWorldIndex) {
        if (sealed || raceId == null || raceId.isEmpty() || player == null
                || !data.recorderUuid.equals(player.toString())) return;
        for (Race race : data.races) {
            if (race.raceId.equals(raceId)) return;
        }
        long offset = startNanos - originNanos;
        if (currentRace != null) currentRace.endOffsetNanos = offset;
        if (data.races.size() == MAX_RACES) {
            data.truncated = true;
            currentRace = null;
            return;
        }
        currentRace = new Race(raceId, offset, testGroup, firstWorldIndex);
        data.races.add(currentRace);
    }

    synchronized void finishRace(String raceId, long elapsedNanos, long igtMillis) {
        if (!sealed && currentRace != null && currentRace.raceId.equals(raceId)
                && currentRace.finishElapsedNanos == null && elapsedNanos >= 0L) {
            currentRace.finishElapsedNanos = elapsedNanos;
            if (igtMillis >= 0L) currentRace.finishIgtMillis = igtMillis;
        }
    }

    synchronized void openInterval(long replayMillis, int worldIndex, int entityId, String dimension) {
        if (sealed || currentInterval != null) return;
        if (data.intervals.size() == MAX_INTERVALS) {
            data.truncated = true;
            return;
        }
        currentInterval = new Interval(replayMillis, worldIndex, entityId, dimension);
        data.intervals.add(currentInterval);
    }

    synchronized void closeInterval(long replayMillis) {
        recordScreen(replayMillis, 0);
        if (sealed || currentInterval == null) return;
        currentInterval.endReplayMillis = Math.max(currentInterval.startReplayMillis, replayMillis);
        currentInterval = null;
    }

    synchronized boolean needsTiming(long time, int world, int state) {
        if (sealed || data.timingSamples.size() >= MAX_TIMING_SAMPLES) return false;
        if (data.timingSamples.isEmpty()) return true;
        long[] last = data.timingSamples.get(data.timingSamples.size() - 1);
        return time > last[0] && (time - last[0] >= 1000L || world != last[1] || state != last[2]);
    }

    synchronized void recordTiming(long time, int world, int state, long rta, long igt) {
        if (needsTiming(time, world, state)) data.timingSamples.add(new long[] {time, world, state, rta, igt});
    }

    /** Writer-thread finalization clamps coverage to the last packet actually written. */
    synchronized String finish(long durationMillis, boolean complete) {
        closeInterval(durationMillis);
        recordLoading(durationMillis, false);
        sealed = true;
        data.durationMillis = durationMillis;
        data.complete = complete;
        if (!complete || data.truncated) data.templePredictionSeed = null;
        if (data.templePredictionSeed == null) { data.chestLoot.clear(); data.stewOrder = null; }
        else data.chestLoot.removeIf(chest -> chest.time > durationMillis);
        data.timingSamples.removeIf(sample -> sample[0] > durationMillis);
        data.chestOpenings.removeIf(chest -> chest.time > durationMillis);
        data.loadingIntervals.removeIf(interval -> interval[0] >= durationMillis);
        for (long[] interval : data.loadingIntervals) interval[1] = Math.min(interval[1], durationMillis);
        data.screenIntervals.removeIf(interval -> interval[0] >= durationMillis);
        for (long[] interval : data.screenIntervals) interval[1] = Math.min(interval[1], durationMillis);
        for (Interval interval : data.intervals) {
            interval.startReplayMillis = Math.min(interval.startReplayMillis, durationMillis);
            interval.endReplayMillis = Math.min(interval.endReplayMillis, durationMillis);
        }
        long endNanos = durationMillis * 1000000L;
        for (Race race : data.races) {
            if (race.endOffsetNanos == null || race.endOffsetNanos > endNanos) race.endOffsetNanos = endNanos;
        }
        return new Gson().toJson(data);
    }

    private static final class Data {
        final int schemaVersion = 1;
        final String captureFormat = "zsg-packet-v1";
        final String minecraftVersion = "1.16.1";
        final int protocolVersion = 736;
        final String recordingId;
        final String recorderUuid;
        final String displayName;
        final List<Race> races = new ArrayList<>();
        final List<Interval> intervals = new ArrayList<>();
        // [replay ms, world index, active/paused/unavailable (0/1/2), RTA ms, IGT ms]
        final List<long[]> timingSamples = new ArrayList<>();
        final List<long[]> loadingIntervals = new ArrayList<>();
        // [start replay ms, end replay ms, inventory/crafting table (1/2)]
        final List<long[]> screenIntervals = new ArrayList<>();
        final List<ChestOpening> chestOpenings = new ArrayList<>();
        final List<ChestLoot> chestLoot = new ArrayList<>();
        Long templePredictionSeed;
        List<String> stewOrder;
        long durationMillis;
        boolean complete;
        boolean truncated;

        Data(UUID recordingId, UUID recorder, String displayName) {
            this.recordingId = recordingId.toString();
            this.recorderUuid = recorder.toString();
            this.displayName = displayName;
        }
    }

    private static final class ChestOpening {
        final long time, first, second;
        final int opening, world, syncId, stateFirst, stateSecond;
        final String dimension;

        ChestOpening(long time, int opening, int world, int syncId, String dimension,
                     long first, long second, int stateFirst, int stateSecond) {
            this.time = time;
            this.opening = opening;
            this.world = world;
            this.syncId = syncId;
            this.dimension = dimension;
            this.first = first;
            this.second = second;
            this.stateFirst = stateFirst;
            this.stateSecond = stateSecond;
        }
    }

    private static final class ChestLoot {
        final long time;
        final int chunk, world;
        final ReplayChestLootPacket.Loot loot;

        ChestLoot(long time, int chunk, int world, ReplayChestLootPacket.Loot loot) {
            this.time = time;
            this.chunk = chunk;
            this.world = world;
            this.loot = loot;
        }
    }

    private static final class Race {
        final String raceId;
        final long startOffsetNanos;
        final String testGroupId;
        final int firstWorldIndex;
        Long endOffsetNanos;
        Long finishElapsedNanos;
        Long finishIgtMillis;

        Race(String raceId, long startOffsetNanos, String testGroup, int firstWorldIndex) {
            this.raceId = raceId;
            this.startOffsetNanos = startOffsetNanos;
            this.testGroupId = testGroup.isEmpty() ? null : testGroup;
            this.firstWorldIndex = firstWorldIndex;
        }
    }

    private static final class Interval {
        long startReplayMillis;
        long endReplayMillis;
        final int worldIndex;
        final int localEntityId;
        final String dimension;

        Interval(long start, int worldIndex, int entityId, String dimension) {
            startReplayMillis = start;
            this.worldIndex = worldIndex;
            localEntityId = entityId;
            this.dimension = dimension;
        }
    }
}
