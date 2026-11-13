package zsgrooms.modid.settings;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.*;
import static zsgrooms.modid.settings.SettingsImportPlan.*;
import static zsgrooms.modid.settings.ImportSettingsSchema.*;

/** Restart-only transactions. Never touches live game settings, source files, or a shared config. */
public final class SettingsImportStore {
    private static final Gson JSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
    private static final int MAX_FILE = 1024 * 1024;
    private static final int MAX_STATE = 16 * MAX_FILE;
    private static final String DIRECTORY = "config/zsg-rooms-import";
    private static final List<String> TARGETS = Arrays.asList(OPTIONS, STANDARD, GLOBAL);
    private final Path game, directory;

    public SettingsImportStore(Path game) throws IOException {
        this.game = game.toRealPath(); this.directory = this.game.resolve(DIRECTORY);
        checkTarget(this.game); safePath(this.game, directory);
    }

    public boolean pending() { return Files.exists(directory.resolve("pending.json")); }
    public boolean canUndo() { return Files.exists(directory.resolve("last-backup.json")) && !pending(); }
    public String status() throws IOException {
        String text = readOptional(directory.resolve("result.txt"));
        return text == null ? "" : text;
    }

    public synchronized void stage(SettingsImportPlan plan, Set<Category> categories, boolean allowLocalCopy) throws IOException {
        if (pending()) throw new IOException("An import or undo is already pending. Cancel it first.");
        if (plan.count(categories) == 0) throw new IOException("No changes selected.");
        if (plan.detachesShared(categories) && !allowLocalCopy) throw new IOException("Confirm the switch to local StandardSettings first.");
        JsonObject pending = new JsonObject(); pending.addProperty("kind", "import");
        pending.addProperty("globalPointer", plan.targetPointer);
        JsonArray bindings = new JsonArray(); for (String key : plan.bindings) bindings.add(new JsonPrimitive(key));
        pending.add("bindings", bindings);
        JsonObject options = new JsonObject(), standard = new JsonObject();
        for (SettingsImportPlan.Change change : plan.changes) {
            if (categories.contains(change.category)) (change.file.equals(OPTIONS) ? options : standard).add(change.key, change.value);
        }
        pending.add("options", options); pending.add("standard", standard);
        writeState("pending.json", JSON.toJson(pending));
    }

    public synchronized void stageUndo() throws IOException {
        if (!canUndo()) throw new IOException("No completed import is available to undo.");
        String backup = readState("last-backup.json");
        validateBackup(object(backup));
        JsonObject request = new JsonObject(); request.addProperty("kind", "undo"); request.add("backup", object(backup));
        writeState("pending.json", JSON.toJson(request));
    }

    public synchronized void cancel() throws IOException {
        safePath(game, directory.resolve("pending.json"));
        Files.deleteIfExists(directory.resolve("pending.json"));
    }

    /** Called by Fabric preLaunch, before Minecraft and mod configuration objects exist. */
    public synchronized void applyAtStartup() throws IOException {
        Path journal = directory.resolve("recovery.json");
        if (Files.exists(journal)) {
            recover(object(readState("recovery.json")));
            cancel(); Files.delete(journal);
            writeState("result.txt", "An interrupted import was rolled back. Your previous settings were restored.");
            return;
        }
        if (!pending()) return;
        JsonObject recovery = null;
        try {
            checkTarget(game);
            JsonObject request = object(readState("pending.json"));
            String kind = request.get("kind").getAsString();
            Map<String, String> output;
            if (kind.equals("undo")) {
                JsonObject backup = request.getAsJsonObject("backup"); validateBackup(backup);
                output = backupFiles(backup);
            } else if (kind.equals("import")) output = prepare(request);
            else throw new IOException("Unknown import operation.");
            JsonObject originals = snapshot(output.keySet());
            JsonElement previousBackup = JsonNull.INSTANCE;
            if (Files.exists(directory.resolve("last-backup.json"))) {
                previousBackup = object(readState("last-backup.json")); validateBackup(previousBackup.getAsJsonObject());
            }
            JsonObject record = new JsonObject(); record.add("files", originals); record.add("previousBackup", previousBackup);
            writeState("recovery.json", JSON.toJson(record));
            recovery = record;
            for (Map.Entry<String, String> entry : output.entrySet()) writeTarget(entry.getKey(), entry.getValue());
            if (kind.equals("import")) writeState("last-backup.json", JSON.toJson(originals));
            else Files.deleteIfExists(directory.resolve("last-backup.json"));
            writeState("result.txt", kind.equals("undo") ? "Previous settings restored on this launch." : "Settings imported successfully on this launch.");
            cancel(); Files.delete(journal);
        } catch (Exception error) {
            // Keep recovery.json if rollback itself fails; never continue with partially applied files.
            if (recovery != null) recover(recovery);
            cancel(); Files.deleteIfExists(journal);
            writeState("result.txt", "Import not applied: " + message(error) + " Previous settings kept.");
        }
    }

    private Map<String, String> prepare(JsonObject request) throws IOException {
        Set<String> bindings = new HashSet<>();
        JsonArray keys = request.getAsJsonArray("bindings");
        if (keys.size() > 4096) throw new IOException("Too many key bindings.");
        for (JsonElement key : keys) {
            String name = key.getAsString();
            if (!name.startsWith("key_") || name.length() > 256 || name.contains("\n") || name.contains(":")) throw new IOException("Invalid key binding.");
            bindings.add(name);
        }
        JsonObject optionPatch = request.getAsJsonObject("options"), standardPatch = request.getAsJsonObject("standard");
        Map<String, String> output = new LinkedHashMap<>();
        if (optionPatch.size() > 0) {
            String original = readOptional(game.resolve(OPTIONS));
            Map<String, String> delta = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : optionPatch.entrySet()) {
                String value = vanillaValue(entry.getKey(), entry.getValue().getAsString(), bindings);
                if (value == null) throw new IOException("Incompatible option: " + entry.getKey());
                delta.put(entry.getKey(), value);
            }
            // Retain unselected options verbatim, including mod-owned lines and metadata.
            Map<String, String> current = options(original);
            ensureCurrentOptionsVersion(current, delta);
            StringBuilder merged = new StringBuilder();
            if (original != null) for (String line : original.split("\\r?\\n")) {
                int colon = line.indexOf(':');
                String key = colon > 0 ? line.substring(0, colon) : "";
                merged.append(delta.containsKey(key) ? key + ":" + delta.remove(key) : line).append('\n');
            }
            for (Map.Entry<String, String> entry : delta.entrySet()) merged.append(entry.getKey()).append(':').append(entry.getValue()).append('\n');
            output.put(OPTIONS, merged.toString());
        }
        if (standardPatch.size() > 0) {
            String expectedPointer = request.get("globalPointer").isJsonNull() ? null : request.get("globalPointer").getAsString();
            if (!Objects.equals(expectedPointer, readOptional(game.resolve(GLOBAL)))) throw new IOException("StandardSettings shared-file selection changed. Preview the import again.");
            JsonObject standard = effectiveStandard(game);
            for (Map.Entry<String, JsonElement> entry : standardPatch.entrySet()) {
                JsonElement value = standardValue(entry.getKey(), entry.getValue(), bindings);
                if (value == null) throw new IOException("Incompatible StandardSettings option: " + entry.getKey());
                standard.add(entry.getKey(), value);
            }
            output.put(STANDARD, JSON.toJson(standard) + "\n");
            if (expectedPointer != null) output.put(GLOBAL, null);
        }
        if (output.isEmpty()) throw new IOException("No settings to import.");
        return output;
    }

    static void ensureCurrentOptionsVersion(Map<String, String> current, Map<String, String> delta) throws IOException {
        String version = current.get("version");
        if (version != null) {
            try { if (Integer.parseInt(version) >= 2567) return; }
            catch (NumberFormatException ignored) { }
            throw new IOException("This instance's options use an older format. Open Minecraft Options, click Done, then preview the import again.");
        }
        for (Map.Entry<String, String> entry : current.entrySet()) {
            if (entry.getKey().equals("fancyGraphics") || (entry.getKey().startsWith("key_") && !entry.getValue().startsWith("key.") && !entry.getValue().startsWith("scancode.")))
                throw new IOException("Save this instance's Minecraft Options before importing legacy settings.");
        }
        // Fresh install packs may have no version. Otherwise vanilla treats modern key names as LWJGL2 integers.
        delta.put("version", "2567");
    }

    private JsonObject snapshot(Set<String> files) throws IOException {
        JsonObject backup = new JsonObject();
        for (String file : files) backup.addProperty(file, readOptional(game.resolve(file)));
        return backup;
    }

    private static void validateBackup(JsonObject backup) throws IOException {
        if (backup == null || backup.size() == 0 || backup.size() > TARGETS.size()) throw new IOException("Invalid import backup.");
        for (Map.Entry<String, JsonElement> entry : backup.entrySet()) {
            if (!TARGETS.contains(entry.getKey()) || (!entry.getValue().isJsonNull()
                    && (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()
                    || entry.getValue().getAsString().getBytes(StandardCharsets.UTF_8).length > MAX_FILE))) throw new IOException("Invalid backup entry.");
        }
    }

    private static Map<String, String> backupFiles(JsonObject backup) throws IOException {
        validateBackup(backup);
        Map<String, String> files = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : backup.entrySet()) files.put(entry.getKey(), entry.getValue().isJsonNull() ? null : entry.getValue().getAsString());
        return files;
    }

    private void restore(JsonObject backup) throws IOException {
        IOException failure = null;
        for (Map.Entry<String, String> entry : backupFiles(backup).entrySet()) {
            try { writeTarget(entry.getKey(), entry.getValue()); }
            catch (IOException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        if (failure != null) throw failure;
    }

    private void recover(JsonObject recovery) throws IOException {
        JsonElement previous = recovery.get("previousBackup");
        if (previous == null) throw new IOException("Incomplete settings recovery record.");
        if (!previous.isJsonNull()) validateBackup(previous.getAsJsonObject());
        restore(recovery.getAsJsonObject("files"));
        if (previous.isJsonNull()) {
            safePath(game, directory.resolve("last-backup.json"));
            Files.deleteIfExists(directory.resolve("last-backup.json"));
        } else writeState("last-backup.json", JSON.toJson(previous));
    }

    private void writeTarget(String name, String text) throws IOException {
        if (!TARGETS.contains(name)) throw new IOException("Unsupported settings destination.");
        Path path = game.resolve(name); safePath(game, path);
        if (text == null) Files.deleteIfExists(path); else atomicWrite(path, text);
    }

    private void writeState(String name, String text) throws IOException {
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_STATE) throw new IOException("Settings transaction is too large.");
        Path path = directory.resolve(name); safePath(game, path); atomicWrite(path, text);
    }
    private String readState(String name) throws IOException {
        Path path = directory.resolve(name); safePath(game, path);
        return readBounded(path, MAX_STATE);
    }

    static void checkTarget(Path game) throws IOException {
        for (String name : TARGETS) safePath(game.toRealPath(), game.toRealPath().resolve(name));
    }

    static void safePath(Path root, Path path) throws IOException {
        if (!path.normalize().startsWith(root)) throw new IOException("Settings path is outside this instance.");
        for (Path cursor = path; cursor != null && !cursor.equals(root); cursor = cursor.getParent()) {
            if (Files.isSymbolicLink(cursor) || (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && !cursor.toRealPath().startsWith(root)))
                throw new IOException("Linked settings folders are not supported. No files were changed.");
        }
    }

    static String readOptional(Path file) throws IOException {
        if (!Files.exists(file)) return null;
        return readBounded(file, MAX_FILE);
    }

    private static String readBounded(Path file, int limit) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) > limit) throw new IOException("Settings file is missing or too large: " + file.getFileName());
        try (InputStream input = Files.newInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[8192]; int count;
            while ((count = input.read(bytes)) != -1) {
                if (output.size() + count > limit) throw new IOException("Settings file grew too large.");
                output.write(bytes, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static void atomicWrite(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), ".import-", ".tmp");
        try {
            Files.write(temp, text.getBytes(StandardCharsets.UTF_8));
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
            try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { throw new IOException("This filesystem cannot safely replace settings atomically.", e); }
        } finally { Files.deleteIfExists(temp); }
    }

    private static String message(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
