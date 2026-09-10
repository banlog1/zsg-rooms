package zsgrooms.modid.replay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ReplayRaceManifestTest {
    private static final UUID PLAYER = new UUID(1L, 2L);
    private static final UUID RECORDING = new UUID(3L, 4L);
    private static final long MS = 1000000L;

    @Test void timingSamplesAreRateLimitedButPauseAndWorldChangesAreImmediate() {
        ReplayRaceManifest manifest = manifest(0L);
        manifest.recordTiming(10, 0, 0, 0, 0);
        assertFalse(manifest.needsTiming(1009, 0, 0));
        assertTrue(manifest.needsTiming(1010, 0, 0));
        manifest.recordTiming(20, 0, 1, 10, 5);
        manifest.recordTiming(21, 1, 2, -1, -1);
        manifest.recordTiming(22, 1, 0, 0, 0);
        manifest.recordTiming(22, 1, 1, 0, 0);
        JsonArray rows = finish(manifest, 100).getAsJsonArray("timingSamples");
        assertEquals(4, rows.size());
        assertEquals(1, rows.get(1).getAsJsonArray().get(2).getAsInt());
        assertEquals(-1, rows.get(2).getAsJsonArray().get(3).getAsInt());
        assertFalse(manifest.needsTiming(2000, 1, 0));
    }

    @Test void timingCaptureIsBoundedWithoutInvalidatingRaceCoordination() {
        ReplayRaceManifest manifest = manifest(0L);
        for (int i = 0; i <= ReplayRaceManifest.MAX_TIMING_SAMPLES; i++) {
            manifest.recordTiming(i * 1000L, 0, 0, i * 1000L, i * 900L);
        }
        JsonObject result = finish(manifest, 20000000);
        assertEquals(ReplayRaceManifest.MAX_TIMING_SAMPLES, result.getAsJsonArray("timingSamples").size());
        assertFalse(result.get("truncated").getAsBoolean());
    }

    @Test void timingSamplesBeyondTheWrittenPacketPrefixAreDropped() {
        ReplayRaceManifest manifest = manifest(0L);
        manifest.recordTiming(0, 0, 0, 0, 0);
        manifest.recordTiming(1000, 0, 0, 1000, 900);
        assertEquals(1, finish(manifest, 500).getAsJsonArray("timingSamples").size());
    }

    @Test void testGroupingPreservesTheRealRaceIdAndMarksOnlyItsOwnRace() {
        ReplayRaceManifest manifest = manifest(0L);
        String group = UUID.randomUUID().toString();
        manifest.startRace("real-first", PLAYER, 100L, group, 0);
        manifest.startRace("real-next", PLAYER, 1000L, "", 1);
        JsonObject data = finish(manifest, 10000L);
        assertEquals("real-first", race(data, 0).get("raceId").getAsString());
        assertEquals(group, race(data, 0).get("testGroupId").getAsString());
        assertFalse(race(data, 1).has("testGroupId"));
        assertEquals(1, race(data, 1).get("firstWorldIndex").getAsInt());
    }

    private ReplayRaceManifest manifest(long origin) {
        return new ReplayRaceManifest(RECORDING, PLAYER, "Recorder", origin);
    }

    private JsonObject finish(ReplayRaceManifest manifest, long duration) {
        return new JsonParser().parse(manifest.finish(duration, true)).getAsJsonObject();
    }

    private JsonObject race(JsonObject data, int index) {
        return data.getAsJsonArray("races").get(index).getAsJsonObject();
    }

    @Test
    void differentLoadTimesAndProcessOriginsAlignAtTheSameRaceTime() {
        ReplayRaceManifest a = manifest(-9000000000000L);
        ReplayRaceManifest b = manifest(7000000000000L);
        a.startRace("shared", PLAYER, -9000000000000L + 8000L * MS);
        b.startRace("shared", PLAYER, 7000000000000L + 15000L * MS);
        JsonObject first = race(finish(a, 200000L), 0);
        JsonObject second = race(finish(b, 200000L), 0);
        long elapsed = 128000L * MS - first.get("startOffsetNanos").getAsLong();
        assertEquals(120000L * MS, elapsed);
        assertEquals(135000L, (elapsed + second.get("startOffsetNanos").getAsLong()) / MS);
        assertEquals(first.get("raceId"), second.get("raceId"));
    }

    @Test
    void localResetKeepsRaceOriginAndRecordsAnUnavailableGapAndNewEntityId() {
        ReplayRaceManifest manifest = manifest(100L);
        manifest.openInterval(1000L, 0, 42, "minecraft:overworld");
        manifest.startRace("race", PLAYER, 100L + 1500L * MS);
        manifest.closeInterval(10000L);
        manifest.openInterval(15000L, 1, 99, "minecraft:overworld");
        manifest.startRace("race", PLAYER, 999999999L);
        JsonObject data = finish(manifest, 20000L);
        assertEquals(1, data.getAsJsonArray("races").size());
        assertEquals(1500L * MS, race(data, 0).get("startOffsetNanos").getAsLong());
        JsonArray intervals = data.getAsJsonArray("intervals");
        assertEquals(2, intervals.size());
        assertEquals(10000L, intervals.get(0).getAsJsonObject().get("endReplayMillis").getAsLong());
        assertEquals(15000L, intervals.get(1).getAsJsonObject().get("startReplayMillis").getAsLong());
        assertEquals(1, intervals.get(1).getAsJsonObject().get("worldIndex").getAsInt());
        assertEquals(99, intervals.get(1).getAsJsonObject().get("localEntityId").getAsInt());
    }

    @Test
    void multipleRoundsStayDistinctAndFinishIgtDoesNotReplaceElapsedTime() {
        ReplayRaceManifest manifest = manifest(0L);
        manifest.startRace("first", PLAYER, 100L * MS);
        manifest.finishRace("wrong", 1L, 1L);
        manifest.finishRace("first", 1000L * MS, 600L);
        manifest.finishRace("first", 9999L, 9999L);
        manifest.startRace("second", PLAYER, 2000L * MS);
        JsonObject data = finish(manifest, 3000L);
        assertEquals(2, data.getAsJsonArray("races").size());
        assertEquals(2000L * MS, race(data, 0).get("endOffsetNanos").getAsLong());
        assertEquals(1000L * MS, race(data, 0).get("finishElapsedNanos").getAsLong());
        assertEquals(600L, race(data, 0).get("finishIgtMillis").getAsLong());
        assertFalse(race(data, 1).has("finishElapsedNanos"));
    }

    @Test
    void unarmedOrWrongIdentityNeverFabricatesRaceZero() {
        ReplayRaceManifest manifest = manifest(0L);
        manifest.startRace("race", UUID.randomUUID(), 100L);
        manifest.startRace("race", null, 100L);
        manifest.finishRace("race", 100L, 50L);
        assertEquals(0, finish(manifest, 1000L).getAsJsonArray("races").size());
    }

    @Test
    void dimensionsHaveSeparateCoverageWithinTheSameWorld() {
        ReplayRaceManifest manifest = manifest(0L);
        manifest.openInterval(1L, 0, 42, "minecraft:overworld");
        manifest.closeInterval(100L);
        manifest.openInterval(120L, 0, 42, "minecraft:the_nether");
        JsonArray intervals = finish(manifest, 200L).getAsJsonArray("intervals");
        assertEquals("minecraft:the_nether", intervals.get(1).getAsJsonObject().get("dimension").getAsString());
        assertEquals(0, intervals.get(1).getAsJsonObject().get("worldIndex").getAsInt());
    }

    @Test
    void sealedMetadataIsStableAndClampsToTheWrittenPrefix() {
        ReplayRaceManifest manifest = manifest(0L);
        manifest.startRace("race", PLAYER, 10L * MS);
        manifest.openInterval(20L, 0, 1, "minecraft:overworld");
        manifest.closeInterval(500L);
        String json = manifest.finish(100L, false);
        manifest.startRace("late", PLAYER, 200L * MS);
        manifest.openInterval(200L, 1, 2, "minecraft:the_end");
        JsonObject data = new JsonParser().parse(json).getAsJsonObject();
        assertFalse(data.get("complete").getAsBoolean());
        assertEquals(100L, data.getAsJsonArray("intervals").get(0).getAsJsonObject()
                .get("endReplayMillis").getAsLong());
        assertEquals(json, manifest.finish(100L, false));
        assertFalse(json.contains("originNanos"));
        assertFalse(json.contains("seed"));
        assertFalse(json.contains("roomCode"));
        assertEquals(RECORDING.toString(), data.get("recordingId").getAsString());
    }

    @Test
    void excessiveEventsAreBoundedAndFlaggedInsteadOfGrowingIndefinitely() {
        ReplayRaceManifest manifest = manifest(0L);
        for (int i = 0; i <= ReplayRaceManifest.MAX_RACES; i++) manifest.startRace("r" + i, PLAYER, i * MS);
        for (int i = 0; i <= ReplayRaceManifest.MAX_INTERVALS; i++) {
            manifest.openInterval(i, i, i, "minecraft:overworld");
            manifest.closeInterval(i + 1L);
        }
        JsonObject data = finish(manifest, 10000L);
        assertTrue(data.get("truncated").getAsBoolean());
        assertEquals(ReplayRaceManifest.MAX_RACES, data.getAsJsonArray("races").size());
        assertEquals(ReplayRaceManifest.MAX_INTERVALS, data.getAsJsonArray("intervals").size());
    }

    @Test
    void concurrentStartCallbacksProduceOneMarker() throws Exception {
        ReplayRaceManifest manifest = manifest(0L);
        CountDownLatch gate = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Runnable start = () -> {
            try {
                gate.await();
                for (int i = 0; i < 100; i++) manifest.startRace("race", PLAYER, 100L);
            } catch (Throwable error) {
                failure.set(error);
            }
        };
        Thread a = new Thread(start);
        Thread b = new Thread(start);
        a.start();
        b.start();
        gate.countDown();
        a.join();
        b.join();
        assertNull(failure.get());
        assertEquals(1, finish(manifest, 1000L).getAsJsonArray("races").size());
    }
}
