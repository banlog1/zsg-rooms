// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Metadata-only scan. Packet streams are opened by ReplayMod only after selection. */
final class ReplayBrowserFiles {
    static final int MAX_FILES = 10000;
    private static final Pattern GENERATED_NAME = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private ReplayBrowserFiles() {}

    static Result scan(Path game, Path browse) throws IOException {
        Set<Path> directories = new LinkedHashSet<>();
        directories.add(game.resolve("replay_recordings").toAbsolutePath().normalize());
        String warning = "";
        String destination = "";
        try { destination = readSetting(game.resolve("config/zsgrooms-replay-storage.properties"), "recordingDirectory"); }
        catch (IOException | IllegalArgumentException error) { warning = "Could not read recording folder setting"; }
        if (!destination.isEmpty()) {
            try {
                Path path = Paths.get(destination);
                if (!path.isAbsolute()) warning = "Recording folder setting is not an absolute path";
                else directories.add(path.normalize());
            } catch (java.nio.file.InvalidPathException error) { warning = "Recording folder setting is invalid"; }
        }
        if (browse != null) directories.add(browse.toAbsolutePath().normalize());
        // Group imports live here; this is deliberately not an unrestricted recursive scan.
        for (Path directory : new ArrayList<>(directories)) directories.add(directory.resolve("zsg-shared"));
        Set<Path> seen = new LinkedHashSet<>();
        List<Entry> entries = new ArrayList<>();
        boolean limited = false;
        for (Path directory : directories) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Scan cancelled");
            if (!Files.exists(directory)) continue;
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
                for (Path file : files) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Scan cancelled");
                    if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mcpr") || !Files.isRegularFile(file)) continue;
                    Path real = file.toRealPath();
                    if (!seen.add(real)) continue;
                    if (entries.size() == MAX_FILES) { limited = true; break; }
                    entries.add(read(real));
                }
            } catch (IOException error) { warning = "Could not read folder: " + directory; }
            if (limited) break;
        }
        entries.sort(Comparator.comparingLong((Entry entry) -> entry.date).reversed().thenComparing(entry -> entry.path.toString()));
        return new Result(Collections.unmodifiableList(entries), warning, limited);
    }

    static Entry read(Path file) {
        String fallback = file.getFileName().toString();
        long date = 0, size = 0;
        BasicFileAttributes identity = null;
        try {
            identity = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            date = identity.lastModifiedTime().toMillis();
            size = identity.size();
            if (!identity.isRegularFile()) throw new IOException("Not a regular recording file");
            try (ZipFile zip = new ZipFile(file.toFile())) {
                if (zip.size() > 8192) throw new IOException("Too many archive entries");
                JsonObject metadata = json(zip, "metaData.json", 1024 * 1024);
                if (metadata == null) throw new IOException("Replay metadata is missing");
                long duration = metadata.get("duration").getAsLong();
                if (duration < 0 || duration > Integer.MAX_VALUE) throw new IOException("Invalid replay duration");
                if (metadata.has("date")) date = metadata.get("date").getAsLong();
                String name = fallback.substring(0, fallback.length() - 5);
                String server = string(metadata, "serverName");
                if (!server.isEmpty()) name = server;
                try {
                    JsonObject races = json(zip, "zsg-rooms/races.json", 2 * 1024 * 1024);
                    if (races != null && !string(races, "displayName").isEmpty()) name = string(races, "displayName") + " - ZSG replay";
                } catch (IOException | RuntimeException ignored) { /* Optional Rooms metadata never blocks legacy playback. */ }
                String stem = fallback.substring(0, fallback.length() - 5);
                if (!GENERATED_NAME.matcher(stem).matches()) name = stem;
                return new Entry(file, name, date, (int) duration, size, "", identity);
            }
        } catch (IOException | RuntimeException error) {
            return new Entry(file, fallback, date, -1, size, error.getMessage() == null ? "Unreadable recording" : error.getMessage(), identity);
        }
    }

    /** Only the confirmed archive, never sibling caches, directories, or a replaced selection. */
    static void delete(Entry entry) throws IOException {
        Files.delete(confirmedPath(entry, "deleting"));
    }

    /** Pure validation: opening or editing the rename dialog never touches the filesystem. */
    static String renameError(String name) {
        if (name == null || name.trim().isEmpty()) return "Enter a recording filename";
        if (!name.equals(name.trim())) return "Filename cannot start or end with spaces";
        if (name.length() > 255 || name.getBytes(StandardCharsets.UTF_8).length > 255) return "Filename is too long";
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isISOControl(c) || Character.getType(c) == Character.FORMAT
                    || "<>:\"/\\|?*".indexOf(c) >= 0) return "Use a single filename without paths or special characters";
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= name.length() || !Character.isLowSurrogate(name.charAt(++i))) return "Invalid filename character";
            } else if (Character.isLowSurrogate(c)) return "Invalid filename character";
        }
        if (!name.toLowerCase(Locale.ROOT).endsWith(".mcpr")) return "Filename must end in .mcpr";
        String stem = name.substring(0, name.length() - 5);
        if (stem.trim().isEmpty() || stem.endsWith(".") || stem.endsWith(" ")) return "Enter a valid name before .mcpr";
        String device = name.substring(0, name.indexOf('.')).trim().toUpperCase(Locale.ROOT);
        if (device.equals("CON") || device.equals("PRN") || device.equals("AUX") || device.equals("NUL")
                || device.equals("CONIN$") || device.equals("CONOUT$")
                || device.matches("(COM|LPT)[1-9\u00b9\u00b2\u00b3]")) return "This filename is reserved";
        return "";
    }

    /** Same-directory move only; no archive rewrite, cache move, or replacement of any target. */
    static Path rename(Entry entry, String name) throws IOException {
        String error = renameError(name);
        if (!error.isEmpty()) throw new IOException(error);
        Path source = confirmedPath(entry, "renaming");
        Path target = source.resolveSibling(name).normalize();
        if (!source.getParent().equals(target.getParent())) throw new IOException("Use a filename in the same folder");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new java.nio.file.FileAlreadyExistsException(name);
        if (Thread.currentThread().isInterrupted()) throw new IOException("Rename cancelled");
        // Do not use ATOMIC_MOVE: providers may replace an existing target with that option.
        return Files.move(source, target);
    }

    private static Path confirmedPath(Entry entry, String action) throws IOException {
        Path path = entry.path.toAbsolutePath().normalize();
        if (entry.identity == null || !entry.identity.isRegularFile()
                || !path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mcpr")
                || !path.equals(path.toRealPath())) throw new IOException("Refresh the library before " + action + " this file");
        BasicFileAttributes current = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!current.isRegularFile() || current.size() != entry.identity.size()
                || !current.lastModifiedTime().equals(entry.identity.lastModifiedTime())
                || !Objects.equals(current.fileKey(), entry.identity.fileKey())) {
            throw new IOException("Recording changed; refresh the library before " + action + " it");
        }
        return path;
    }

    private static JsonObject json(ZipFile zip, String name, int limit) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) return null;
        if (entry.getSize() > limit) throw new IOException("Replay metadata is too large");
        try (InputStream input = zip.getInputStream(entry); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > limit) throw new IOException("Replay metadata is too large");
                output.write(buffer, 0, count);
            }
            String json = new String(output.toByteArray(), StandardCharsets.UTF_8);
            // Check depth with the streaming parser before Gson's recursive tree parser.
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
                    if (depth > 16) throw new IOException("Excessive metadata nesting");
                }
            }
            return new JsonParser().parse(json).getAsJsonObject();
        }
    }

    static String readSetting(Path file, String key) throws IOException {
        if (!Files.exists(file)) return "";
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(file)) { values.load(input); }
        return values.getProperty(key, "").trim();
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    static final class Result {
        final List<Entry> entries;
        final String warning;
        final boolean limited;
        Result(List<Entry> entries, String warning, boolean limited) {
            this.entries = entries; this.warning = warning; this.limited = limited;
        }
    }

    static final class Entry {
        final Path path;
        final String name, error;
        final long date, size;
        final int duration;
        private final BasicFileAttributes identity;
        Entry(Path path, String name, long date, int duration, long size, String error, BasicFileAttributes identity) {
            this.path = path; this.name = name; this.date = date; this.duration = duration; this.size = size; this.error = error;
            this.identity = identity;
        }
        boolean playable() { return error.isEmpty(); }
        boolean matches(String query) {
            String lower = query.toLowerCase(Locale.ROOT);
            return name.toLowerCase(Locale.ROOT).contains(lower) || path.getFileName().toString().toLowerCase(Locale.ROOT).contains(lower);
        }
    }
}
