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
        sealed = true;
        data.durationMillis = durationMillis;
        data.complete = complete;
        data.timingSamples.removeIf(sample -> sample[0] > durationMillis);
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
        long durationMillis;
        boolean complete;
        boolean truncated;

        Data(UUID recordingId, UUID recorder, String displayName) {
            this.recordingId = recordingId.toString();
            this.recorderUuid = recorder.toString();
            this.displayName = displayName;
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
