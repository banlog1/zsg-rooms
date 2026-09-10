// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class RaceRecordingTest {
    @TempDir Path temp;
    private static final String RECORDER = "00000000-0000-0000-0000-000000000001";
    private static final String GROUP = "00000000-0000-0000-0000-000000000002";

    static JsonObject data(String id, int start, String group) {
        JsonObject data = new JsonObject();
        data.addProperty("schemaVersion", 1); data.addProperty("protocolVersion", 736);
        data.addProperty("minecraftVersion", "1.16.1"); data.addProperty("captureFormat", "zsg-packet-v1");
        data.addProperty("recordingId", id); data.addProperty("recorderUuid", RECORDER);
        data.addProperty("displayName", "Same Player"); data.addProperty("durationMillis", 100000);
        data.addProperty("complete", true); data.addProperty("truncated", false);
        JsonArray races = new JsonArray();
        JsonObject race = new JsonObject();
        race.addProperty("raceId", "actual-" + id); race.addProperty("startOffsetNanos", start * 1000000L);
        race.addProperty("endOffsetNanos", 100000L * 1000000L);
        if (group != null) race.addProperty("testGroupId", group);
        races.add(race); data.add("races", races);
        JsonArray intervals = new JsonArray();
        intervals.add(interval(1000, 30000, 0)); intervals.add(interval(40000, 100000, 1));
        data.add("intervals", intervals);
        return data;
    }

    private static JsonObject interval(int start, int end, int world) {
        JsonObject interval = new JsonObject();
        interval.addProperty("startReplayMillis", start); interval.addProperty("endReplayMillis", end); interval.addProperty("worldIndex", world);
        return interval;
    }

    private Path write(JsonObject data) throws IOException {
        Path path = temp.resolve(UUID.randomUUID() + ".mcpr");
        zip(path, data.toString(), 100000);
        return path;
    }

    static void zip(Path path, String json, int duration) throws IOException {
        Files.createDirectories(path.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("zsg-rooms/races.json")); zip.write(json.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("metaData.json")); zip.write(("{\"protocol\":736,\"duration\":" + duration + "}").getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("recording.tmcpr")); zip.write(0); zip.closeEntry();
        }
    }

    @Test void alignsUnequalLoadsAndRejectsGapsOrEndedFilesWithoutClamping() throws Exception {
        RaceRecording a = RaceRecording.read(write(data(UUID.randomUUID().toString(), 8000, GROUP)));
        RaceRecording b = RaceRecording.read(write(data(UUID.randomUUID().toString(), 15000, GROUP)));
        long elapsed = 25000L * 1000000L - a.races.get(0).start;
        assertEquals(-1, b.targetTime(b.races.get(0), elapsed)); // maps to 32000: reset gap
        assertEquals(50000, b.targetTime(b.races.get(0), 35000L * 1000000L));
        assertEquals(-1, b.targetTime(b.races.get(0), -1L));
        assertEquals(-1, b.targetTime(b.races.get(0), 200000L * 1000000L));
        assertEquals(-1, b.targetTime(b.races.get(0), Long.MAX_VALUE));
    }

    @Test void distinguishesRealRacesAndAllowsSameRecorderSeparateTestTakes() throws Exception {
        Path directory = temp.resolve("game/replay_recordings"); Files.createDirectories(directory);
        String aId = UUID.randomUUID().toString(); String bId = UUID.randomUUID().toString();
        zip(directory.resolve("a.mcpr"), data(aId, 8000, GROUP).toString(), 100000);
        zip(directory.resolve("b.mcpr"), data(bId, 15000, GROUP).toString(), 100000);
        zip(directory.resolve("duplicate.mcpr"), data(aId, 8000, GROUP).toString(), 100000);
        zip(directory.resolve("normal.mcpr"), data(UUID.randomUUID().toString(), 8000, null).toString(), 100000);
        RaceRecording a = RaceRecording.read(directory.resolve("a.mcpr"));
        assertEquals(2, new RaceReplayLibrary(temp.resolve("game")).scan(a, a.races.get(0)).size());
        assertTrue(a.races.get(0).group().startsWith("test:"));
        RaceRecording normal = RaceRecording.read(directory.resolve("normal.mcpr"));
        assertEquals(1, new RaceReplayLibrary(temp.resolve("game")).scan(normal, normal.races.get(0)).size());
    }

    @Test void groupsDifferentRecordersOnlyByTheExactRealRaceId() throws Exception {
        Path directory = temp.resolve("game/replay_recordings");
        for (int i = 0; i < 3; i++) {
            JsonObject value = data(UUID.randomUUID().toString(), 8000 + i * 1000, null);
            value.addProperty("recorderUuid", UUID.randomUUID().toString());
            value.getAsJsonArray("races").get(0).getAsJsonObject().addProperty("raceId", i < 2 ? "shared-race" : "other-race");
            zip(directory.resolve(i + ".mcpr"), value.toString(), 100000);
        }
        RaceRecording current = RaceRecording.read(directory.resolve("0.mcpr"));
        java.util.List<RaceReplayLibrary.Entry> entries = new RaceReplayLibrary(temp.resolve("game")).scan(current, current.races.get(0));
        assertEquals(2, entries.size());
        assertNotEquals(entries.get(0).recording.recorder, entries.get(1).recording.recorder);
        RaceReplayLibrary.Entry other = entries.stream().filter(entry -> !entry.recording.recordingId.equals(current.recordingId)).findFirst().get();
        assertEquals(19000, other.recording.targetTime(other.race, 10000L * 1000000L));
    }

    @Test void importsCopyFilesAndNeverOverwriteDuplicateOrMismatchedRecordings() throws Exception {
        Path source = write(data(UUID.randomUUID().toString(), 8000, GROUP));
        byte[] original = Files.readAllBytes(source);
        RaceReplayLibrary library = new RaceReplayLibrary(temp.resolve("game"));
        Path copy = library.importReplay(source, "test:" + GROUP);
        assertNotEquals(source, copy);
        assertArrayEquals(original, Files.readAllBytes(copy));
        assertEquals(copy, library.importReplay(source, "test:" + GROUP));
        assertThrows(IOException.class, () -> library.importReplay(source, "race:different"));
        assertArrayEquals(original, Files.readAllBytes(source));
    }

    @Test void remembersTheOriginalCustomDirectoryWhenSwitchingBackFromAnImport() throws Exception {
        Path aPath = temp.resolve("custom/a.mcpr");
        zip(aPath, data(UUID.randomUUID().toString(), 8000, GROUP).toString(), 100000);
        RaceRecording a = RaceRecording.read(aPath);
        RaceReplayLibrary library = new RaceReplayLibrary(temp.resolve("game"));
        assertEquals(1, library.scan(a, a.races.get(0)).size());
        RaceRecording b = RaceRecording.read(library.importReplay(write(data(UUID.randomUUID().toString(), 15000, GROUP)), "test:" + GROUP));
        assertEquals(2, library.scan(b, b.races.get(0)).size());
    }

    @Test void rejectsMalformedUnsupportedIncompleteAndOversizedMetadata() throws Exception {
        for (String key : new String[] {"schemaVersion", "protocolVersion", "durationMillis"}) {
            JsonObject data = data(UUID.randomUUID().toString(), 8000, GROUP);
            data.addProperty(key, -1);
            assertThrows(IOException.class, () -> RaceRecording.read(write(data)));
        }
        JsonObject data = data(UUID.randomUUID().toString(), 8000, GROUP); data.addProperty("complete", false);
        assertThrows(IOException.class, () -> RaceRecording.read(write(data)));
        data.addProperty("complete", true); data.addProperty("truncated", true);
        assertThrows(IOException.class, () -> RaceRecording.read(write(data)));
        Path oversized = temp.resolve("oversized.mcpr");
        zip(oversized, new String(new char[RaceRecording.MAX_JSON + 1]).replace('\0', ' '), 100000);
        assertThrows(IOException.class, () -> RaceRecording.read(oversized));
        Path nested = temp.resolve("nested.mcpr");
        zip(nested, new String(new char[100]).replace('\0', '['), 100000);
        assertThrows(IOException.class, () -> RaceRecording.read(nested));
    }

    @Test void rejectsOverlappingRacesAndCoverageAndDuplicateRaceIds() throws Exception {
        JsonObject data = data(UUID.randomUUID().toString(), 8000, GROUP);
        data.getAsJsonArray("races").add(data.getAsJsonArray("races").get(0));
        assertThrows(IOException.class, () -> RaceRecording.read(write(data)));
        JsonObject badCoverage = data(UUID.randomUUID().toString(), 8000, GROUP);
        badCoverage.getAsJsonArray("intervals").get(1).getAsJsonObject().addProperty("startReplayMillis", 20000);
        assertThrows(IOException.class, () -> RaceRecording.read(write(badCoverage)));
    }

    @Test void nextRoundWorldIsNotCoverageOfPreviousRound() throws Exception {
        JsonObject data = data(UUID.randomUUID().toString(), 8000, GROUP);
        JsonObject first = data.getAsJsonArray("races").get(0).getAsJsonObject();
        first.addProperty("firstWorldIndex", 0); first.addProperty("endOffsetNanos", 50000L * 1000000L);
        JsonObject second = new JsonObject(); second.addProperty("raceId", "next");
        second.addProperty("startOffsetNanos", 50000L * 1000000L); second.addProperty("endOffsetNanos", 100000L * 1000000L);
        second.addProperty("firstWorldIndex", 1); data.getAsJsonArray("races").add(second);
        RaceRecording recording = RaceRecording.read(write(data));
        assertEquals(-1, recording.targetTime(recording.races.get(0), 35000L * 1000000L));
        assertEquals(60000, recording.targetTime(recording.races.get(1), 10000L * 1000000L));
    }
}
