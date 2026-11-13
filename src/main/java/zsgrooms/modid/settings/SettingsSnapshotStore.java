package zsgrooms.modid.settings;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static zsgrooms.modid.settings.ImportSettingsSchema.*;
import static zsgrooms.modid.settings.SettingsImportPlan.*;

/** Portable, allowlisted settings snapshots, separate from restart transactions and undo. */
public final class SettingsSnapshotStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FORMAT = "zsg-rooms-settings";
    private static final int LIMIT = 128;
    private final Path game, directory;

    public static final class Snapshot {
        public final Path file;
        public final String name, minecraftVersion;
        public final Instant savedAt;
        final Map<String, String> options;
        final JsonObject standard;
        private Snapshot(Path file, String name, String version, Instant savedAt, Map<String, String> options, JsonObject standard) {
            this.file = file; this.name = name; this.minecraftVersion = version; this.savedAt = savedAt;
            this.options = Collections.unmodifiableMap(new LinkedHashMap<>(options)); this.standard = standard;
        }
        public int count() { return options.size() + standard.size(); }
    }

    public static final class Listing {
        public final List<Snapshot> snapshots = new ArrayList<>();
        public int unreadable;
    }

    public SettingsSnapshotStore(Path game) throws IOException {
        this.game = game.toRealPath(); this.directory = this.game.resolve("config/zsg-rooms-import/snapshots");
        SettingsImportStore.safePath(this.game, directory);
    }

    public Listing list() throws IOException {
        SettingsImportStore.safePath(game, directory);
        Listing result = new Listing();
        if (!Files.exists(directory)) return result;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json")) {
            int count = 0;
            for (Path file : files) {
                if (++count > LIMIT) throw new IOException("Too many saved settings files. Move some out of the snapshots folder first.");
                try { checkOwned(file); result.snapshots.add(read(file)); }
                catch (IOException e) { result.unreadable++; }
            }
        }
        result.snapshots.sort(Comparator.comparing((Snapshot item) -> item.savedAt).reversed().thenComparing(item -> item.name));
        return result;
    }

    /** The caller flushes live Minecraft options first, on the client thread. */
    public Snapshot save(String name, String minecraftVersion, Set<String> bindings, boolean standardInstalled) throws IOException {
        if (!Files.isRegularFile(game.resolve(OPTIONS))) throw new IOException("Save Minecraft Options before taking a snapshot.");
        Map<String, String> values = options(SettingsImportStore.readOptional(game.resolve(OPTIONS)));
        Map<String, String> safe = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String value = vanillaValue(entry.getKey(), entry.getValue(), bindings);
            if (value != null) safe.put(entry.getKey(), value);
        }
        JsonObject standard = new JsonObject();
        if (standardInstalled) for (Map.Entry<String, JsonElement> entry : effectiveStandard(game).entrySet()) {
            JsonElement value = standardValue(entry.getKey(), entry.getValue(), bindings);
            if (value != null) standard.add(entry.getKey(), value);
        }
        if (safe.isEmpty() && standard.size() == 0) throw new IOException("No compatible settings are available to save.");
        return create(new Snapshot(null, validateName(name), validateText(minecraftVersion, 64), Instant.now(), safe, standard));
    }

    public Snapshot importFile(Path file) throws IOException { return create(read(file)); }

    private synchronized Snapshot create(Snapshot snapshot) throws IOException {
        Listing listing = list();
        if (listing.snapshots.size() + listing.unreadable >= LIMIT) throw new IOException("Saved settings limit reached (128). Delete or export an older snapshot first.");
        Path file = directory.resolve(UUID.randomUUID() + ".json");
        Snapshot saved = new Snapshot(file, snapshot.name, snapshot.minecraftVersion, snapshot.savedAt, snapshot.options, snapshot.standard);
        writeOwned(saved); return saved;
    }

    public synchronized Snapshot rename(Snapshot snapshot, String name) throws IOException {
        checkOwned(snapshot.file);
        Snapshot current = read(snapshot.file);
        Snapshot renamed = new Snapshot(current.file, validateName(name), current.minecraftVersion, current.savedAt, current.options, current.standard);
        writeOwned(renamed); return renamed;
    }

    public synchronized void delete(Snapshot snapshot) throws IOException {
        checkOwned(snapshot.file); Files.delete(snapshot.file);
    }

    public void exportFile(Snapshot snapshot, Path destination, boolean overwrite) throws IOException {
        checkOwned(snapshot.file);
        Path target = destination.toAbsolutePath().normalize();
        if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) throw new IOException("Choose a .json file for saved settings.");
        if (Files.isSymbolicLink(target)) throw new IOException("Choose a regular file, not a link.");
        if (!overwrite && Files.exists(target)) throw new IOException("That file already exists. Confirm before replacing it.");
        SettingsImportStore.atomicWrite(target, serialize(read(snapshot.file)));
    }

    private void checkOwned(Path file) throws IOException {
        if (file == null || !directory.equals(file.toAbsolutePath().normalize().getParent())) throw new IOException("This saved settings file is outside the snapshots folder.");
        SettingsImportStore.safePath(game, file);
    }

    private void writeOwned(Snapshot snapshot) throws IOException {
        checkOwned(snapshot.file); SettingsImportStore.atomicWrite(snapshot.file, serialize(snapshot));
    }

    private static String serialize(Snapshot snapshot) throws IOException {
        JsonObject object = new JsonObject(); object.addProperty("format", FORMAT); object.addProperty("version", 1);
        object.addProperty("name", snapshot.name); object.addProperty("minecraftVersion", snapshot.minecraftVersion);
        object.addProperty("savedAt", snapshot.savedAt.toString());
        JsonObject options = new JsonObject(); snapshot.options.forEach(options::addProperty);
        object.add("options", options); object.add("standard", snapshot.standard);
        String text = JSON.toJson(object) + "\n";
        if (text.getBytes(StandardCharsets.UTF_8).length > 1024 * 1024) throw new IOException("Saved settings file is too large.");
        return text;
    }

    private static Snapshot read(Path file) throws IOException {
        try {
            String text = SettingsImportStore.readOptional(file);
            if (text == null) throw new IOException("Saved settings file no longer exists.");
            JsonObject data = object(text);
            if (!FORMAT.equals(string(data, "format")) || !data.has("version") || !data.get("version").equals(new JsonPrimitive(1)))
                throw new IOException("Unsupported saved settings file or version.");
            String name = validateName(string(data, "name")), version = validateText(string(data, "minecraftVersion"), 64);
            Instant time = Instant.parse(string(data, "savedAt"));
            if (time.isBefore(Instant.EPOCH) || time.isAfter(Instant.parse("9999-12-30T00:00:00Z"))) throw new IOException("Invalid saved settings date.");
            Map<String, String> options = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : data.getAsJsonObject("options").entrySet()) {
                if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) throw new IOException("Invalid saved option value.");
                options.put(entry.getKey(), entry.getValue().getAsString());
            }
            JsonObject standard = data.getAsJsonObject("standard");
            if (options.isEmpty() && standard.size() == 0) throw new IOException("The saved settings file is empty.");
            return new Snapshot(file.toAbsolutePath().normalize(), name, version, time, options, standard);
        } catch (RuntimeException e) { throw new IOException("Invalid saved settings file.", e); }
    }

    private static String string(JsonObject data, String key) throws IOException {
        JsonElement value = data.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IOException("Missing saved settings field: " + key);
        return value.getAsString();
    }

    public static String validateName(String name) throws IOException { return validateText(name == null ? "" : name.trim(), 64); }
    private static String validateText(String value, int maximum) throws IOException {
        if (value == null || value.isEmpty() || value.length() > maximum) throw new IOException("Enter a name between 1 and " + maximum + " characters.");
        for (char c : value.toCharArray()) if (Character.isISOControl(c) || Character.getType(c) == Character.FORMAT || c == '\u00a7')
            throw new IOException("Names cannot contain formatting or control characters.");
        return value;
    }
}
