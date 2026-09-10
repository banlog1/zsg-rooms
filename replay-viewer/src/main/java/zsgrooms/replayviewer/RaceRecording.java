// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Untrusted file metadata, read without extracting archives or loading ReplayMod classes. */
final class RaceRecording {
    static final int MAX_JSON = 2 * 1024 * 1024;
    static final long MAX_FILE = 8L * 1024 * 1024 * 1024;
    final Path path;
    final UUID recordingId;
    final UUID recorder;
    final String name;
    final int duration;
    final List<Race> races;
    final List<Interval> intervals;
    final ReplayTimings timing;

    private RaceRecording(Path path, JsonObject data, JsonObject standard) throws IOException {
        this.path = path.toAbsolutePath().normalize();
        require(number(data, "schemaVersion") == 1 && number(data, "protocolVersion") == 736, "Unsupported replay schema/protocol");
        require("1.16.1".equals(string(data, "minecraftVersion")) && "zsg-packet-v1".equals(string(data, "captureFormat")), "Unsupported capture format");
        require(data.get("complete").getAsBoolean() && !data.get("truncated").getAsBoolean(), "Incomplete replay metadata");
        recordingId = uuid(string(data, "recordingId"));
        recorder = uuid(string(data, "recorderUuid"));
        name = string(data, "displayName").replaceAll("[\\p{Cntrl}\\u00a7]", "");
        long length = number(data, "durationMillis");
        require(length > 0 && length <= Integer.MAX_VALUE, "Invalid replay duration");
        duration = (int) length;
        require(number(standard, "duration") == duration && number(standard, "protocol") == 736, "Replay headers disagree");
        long end = length * 1000000L;
        List<Race> parsedRaces = new ArrayList<>();
        JsonArray array = data.getAsJsonArray("races");
        require(array.size() <= 1024, "Too many races");
        Set<String> ids = new HashSet<>();
        long previousEnd = 0L;
        for (JsonElement element : array) {
            JsonObject object = element.getAsJsonObject();
            String id = string(object, "raceId");
            long start = number(object, "startOffsetNanos");
            long finish = number(object, "endOffsetNanos");
            require(ids.add(id) && start >= previousEnd && finish >= start && finish <= end, "Invalid race boundaries");
            String testGroup = object.has("testGroupId") && !object.get("testGroupId").isJsonNull()
                    ? uuid(string(object, "testGroupId")).toString() : null;
            int firstWorld = object.has("firstWorldIndex") ? integer(object, "firstWorldIndex") : -1;
            require(firstWorld >= -1, "Invalid world index");
            parsedRaces.add(new Race(id, start, finish, testGroup, firstWorld));
            previousEnd = finish;
        }
        races = Collections.unmodifiableList(parsedRaces);
        List<Interval> parsedIntervals = new ArrayList<>();
        array = data.getAsJsonArray("intervals");
        require(array.size() <= 4096, "Too many world intervals");
        long previousTime = 0L;
        int previousWorld = -1;
        for (JsonElement element : array) {
            JsonObject object = element.getAsJsonObject();
            int start = integer(object, "startReplayMillis");
            int finish = integer(object, "endReplayMillis");
            int world = integer(object, "worldIndex");
            require(start >= previousTime && finish >= start && finish <= duration && world >= 0 && world >= previousWorld, "Invalid world coverage");
            parsedIntervals.add(new Interval(start, finish, world));
            previousTime = finish;
            previousWorld = world;
        }
        intervals = Collections.unmodifiableList(parsedIntervals);
        timing = new ReplayTimings(data.getAsJsonArray("timingSamples"), duration);
    }

    static RaceRecording read(Path path) throws IOException {
        require(Files.isRegularFile(path) && Files.size(path) <= MAX_FILE, "Replay missing or too large");
        try (ZipFile zip = new ZipFile(path.toFile())) {
            require(zip.size() <= 4096, "Too many archive entries");
            require(zip.getEntry("recording.tmcpr") != null, "Missing replay packets");
            Set<String> names = new HashSet<>();
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) require(names.add(entries.nextElement().getName()), "Duplicate archive entries");
            JsonObject data = json(zip, "zsg-rooms/races.json");
            JsonObject standard = json(zip, "metaData.json");
            return new RaceRecording(path, data, standard);
        } catch (RuntimeException error) {
            throw new IOException("Invalid replay metadata", error);
        }
    }

    private static JsonObject json(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        require(entry != null && entry.getSize() <= MAX_JSON, "Missing or oversized race metadata");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream input = zip.getInputStream(entry)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                require(bytes.size() + count <= MAX_JSON, "Oversized race metadata");
                bytes.write(buffer, 0, count);
            }
        }
        String json = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        // Bound nesting before Gson's recursive tree parser sees external input.
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            int depth = 0;
            while (reader.peek() != JsonToken.END_DOCUMENT) {
                switch (reader.peek()) {
                    case BEGIN_OBJECT: reader.beginObject(); depth++; break;
                    case BEGIN_ARRAY: reader.beginArray(); depth++; break;
                    case END_OBJECT: reader.endObject(); depth--; break;
                    case END_ARRAY: reader.endArray(); depth--; break;
                    case NAME: reader.nextName(); break;
                    default: reader.skipValue();
                }
                require(depth <= 16, "Excessive metadata nesting");
            }
        }
        return new JsonParser().parse(json).getAsJsonObject();
    }

    private static String string(JsonObject object, String key) throws IOException {
        JsonElement value = object.get(key);
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(), "Invalid " + key);
        String text = value.getAsString();
        require(!text.isEmpty() && text.length() <= 128, "Invalid " + key);
        return text;
    }

    private static long number(JsonObject object, String key) throws IOException {
        JsonElement value = object.get(key);
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(), "Invalid " + key);
        return value.getAsBigDecimal().longValueExact();
    }

    private static int integer(JsonObject object, String key) throws IOException {
        long value = number(object, key);
        require(value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE, "Invalid " + key);
        return (int) value;
    }

    private static UUID uuid(String value) throws IOException {
        UUID id = UUID.fromString(value);
        require(id.toString().equalsIgnoreCase(value), "Invalid UUID");
        return id;
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }

    Race raceAt(int timestamp) {
        for (Race race : races) if (timestamp * 1000000L >= race.start && timestamp * 1000000L < race.end) return race;
        return races.size() == 1 ? races.get(0) : null;
    }

    int targetTime(Race race, long elapsedNanos) {
        long nanos = race.start + elapsedNanos;
        if (elapsedNanos < 0 || nanos < race.start || nanos >= race.end) return -1;
        long time = nanos / 1000000L;
        int nextWorld = Integer.MAX_VALUE;
        int index = races.indexOf(race);
        if (index + 1 < races.size() && races.get(index + 1).firstWorld >= 0) nextWorld = races.get(index + 1).firstWorld;
        for (Interval interval : intervals) {
            if (time >= interval.start && time < interval.end && interval.world >= race.firstWorld && interval.world < nextWorld) return (int) time;
        }
        return -1;
    }

    static final class Race {
        final String id;
        final long start;
        final long end;
        final String testGroup;
        final int firstWorld;

        Race(String id, long start, long end, String testGroup, int firstWorld) {
            this.id = id; this.start = start; this.end = end; this.testGroup = testGroup; this.firstWorld = firstWorld;
        }

        String group() { return testGroup == null ? "race:" + id : "test:" + testGroup; }
    }

    static final class Interval {
        final int start;
        final int end;
        final int world;
        Interval(int start, int end, int world) { this.start = start; this.end = end; this.world = world; }
    }
}
