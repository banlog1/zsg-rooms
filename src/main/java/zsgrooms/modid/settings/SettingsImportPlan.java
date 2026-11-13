package zsgrooms.modid.settings;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.*;
import java.util.*;
import static zsgrooms.modid.settings.ImportSettingsSchema.*;

public final class SettingsImportPlan {
    public static final String OPTIONS = "options.txt";
    public static final String STANDARD = "config/mcsr/standardsettings.json";
    public static final String GLOBAL = "config/mcsr/standardsettings.global";
    public final Path source;
    public final SettingsImportInstance instance;
    public final String sourceName, sourceDetails;
    public final List<Change> changes = new ArrayList<>();
    public final List<String> skipped = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();
    final String targetPointer;
    final Set<String> bindings;

    public static final class Change {
        public final Category category;
        public final String file, key, before;
        public final JsonElement value;
        Change(Category category, String file, String key, JsonElement before, JsonElement value) {
            this.category = category; this.file = file; this.key = key;
            this.before = before == null ? "(default)" : before.toString(); this.value = value;
        }
        public String description() {
            return (file.equals(STANDARD) ? "StandardSettings: " : "Minecraft: ") + key + "  " + before + " -> " + value;
        }
    }

    private SettingsImportPlan(Path source, String pointer, Set<String> bindings) throws IOException {
        this.source = source; this.targetPointer = pointer; this.bindings = new HashSet<>(bindings);
        this.instance = SettingsImportInstance.read(source);
        this.sourceName = instance.name; this.sourceDetails = instance.details();
    }

    private SettingsImportPlan(SettingsSnapshotStore.Snapshot snapshot, String pointer, Set<String> bindings) {
        this.source = snapshot.file; this.targetPointer = pointer; this.bindings = new HashSet<>(bindings);
        this.instance = null; this.sourceName = snapshot.name; this.sourceDetails = "Saved settings | Minecraft " + snapshot.minecraftVersion;
    }

    public static Path gameDirectory(Path folder) throws IOException {
        Path real = folder.toRealPath();
        for (String child : new String[]{".minecraft", "minecraft", ""}) {
            Path path = real.resolve(child);
            if (Files.isRegularFile(path.resolve(OPTIONS)) || Files.isRegularFile(path.resolve(STANDARD))) return path.toRealPath();
        }
        throw new IOException("Choose an instance or Minecraft folder containing options.txt.");
    }

    public static List<Path> discover(Path destination) throws IOException {
        Path current = destination.toRealPath();
        Set<Path> roots = new LinkedHashSet<>();
        if (current.getParent() != null && current.getParent().getParent() != null
                && (Files.exists(current.getParent().resolve("instance.cfg")) || Files.exists(current.getParent().resolve("mmc-pack.json"))))
            roots.add(current.getParent().getParent());
        String roaming = System.getenv("APPDATA");
        if (roaming != null) {
            roots.add(Paths.get(roaming, "PrismLauncher", "instances"));
            roots.add(Paths.get(roaming, "MultiMC", "instances"));
        }
        Set<Path> found = new LinkedHashSet<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
                int scanned = 0;
                for (Path entry : stream) {
                    if (++scanned > 256) break;
                    addSource(found, entry, current);
                }
            } catch (IOException ignored) { /* A manually chosen folder remains available. */ }
        }
        if (roaming != null) addSource(found, Paths.get(roaming, ".minecraft"), current);
        addSource(found, Paths.get(System.getProperty("user.home"), ".minecraft"), current);
        List<Path> result = new ArrayList<>(found);
        result.sort(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static void addSource(Set<Path> found, Path entry, Path current) {
        try { Path path = gameDirectory(entry); if (!path.equals(current)) found.add(path); }
        catch (IOException ignored) { }
    }

    public static SettingsImportPlan preview(Path sourceFolder, Path target, Set<String> bindings, boolean standardInstalled) throws IOException {
        Path source = gameDirectory(sourceFolder);
        if (Files.isSameFile(source, target)) throw new IOException("Choose a different instance, not this one.");
        SettingsImportStore.checkTarget(target);
        String pointer = SettingsImportStore.readOptional(target.resolve(GLOBAL));
        SettingsImportPlan plan = new SettingsImportPlan(source, pointer, bindings);
        Map<String, String> incoming = options(SettingsImportStore.readOptional(source.resolve(OPTIONS)));
        JsonObject sourceStandard = effectiveStandard(source);
        if (Files.exists(source.resolve(GLOBAL))) plan.warnings.add("Source uses shared StandardSettings. Its settings are read only; the shared file is never changed.");
        if (Files.exists(source.resolve("config/standardoptions.txt")))
            plan.warnings.add("Legacy standardoptions.txt is not imported. Only the supported StandardSettings JSON format is migrated.");
        return compare(plan, incoming, sourceStandard, target, standardInstalled);
    }

    public static SettingsImportPlan previewSnapshot(SettingsSnapshotStore.Snapshot snapshot, Path target, Set<String> bindings, boolean standardInstalled) throws IOException {
        SettingsImportStore.checkTarget(target);
        SettingsImportPlan plan = new SettingsImportPlan(snapshot, SettingsImportStore.readOptional(target.resolve(GLOBAL)), bindings);
        return compare(plan, snapshot.options, snapshot.standard, target, standardInstalled);
    }

    private static SettingsImportPlan compare(SettingsImportPlan plan, Map<String, String> incoming, JsonObject sourceStandard,
                                              Path target, boolean standardInstalled) throws IOException {
        Set<String> bindings = plan.bindings;
        Map<String, String> existing = options(SettingsImportStore.readOptional(target.resolve(OPTIONS)));
        JsonObject targetStandard = standardInstalled ? effectiveStandard(target) : new JsonObject();
        if (plan.targetPointer != null && standardInstalled) plan.warnings.add("This instance uses shared StandardSettings. Import will switch THIS instance to a local copy. Other instances stay unchanged.");
        if (!standardInstalled && sourceStandard.size() > 0) plan.warnings.add("StandardSettings is not installed here; its overrides will be skipped.");
        if (standardInstalled) {
            for (Map.Entry<String, JsonElement> entry : sourceStandard.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(".") || key.equals("toggleAll") || key.equals("showIntroductionScreen")) continue;
                JsonElement value = standardValue(key, entry.getValue(), bindings);
                if (value == null) { plan.skipped.add("StandardSettings: " + key + " (unsupported or incompatible)"); continue; }
                plan.add(STANDARD, key, targetStandard.get(key), value);
            }
        }
        for (Map.Entry<String, String> entry : incoming.entrySet()) {
            String key = entry.getKey();
            String value = vanillaValue(key, entry.getValue(), bindings);
            if (value == null) { plan.skipped.add("Minecraft: " + key + " (unsupported or incompatible)"); continue; }
            plan.add(OPTIONS, key, existing.containsKey(key) ? new JsonPrimitive(existing.get(key)) : null, new JsonPrimitive(value));
            if (standardInstalled) {
                // Existing enabled reset overrides must not silently undo an imported vanilla option.
                for (String standardKey : new String[]{standardKey(key), standardKey(key) + "OnWorldJoin"}) {
                    if (category(standardKey, true, bindings) == null || standardValue(standardKey, sourceStandard.get(standardKey), bindings) != null) continue;
                    JsonElement current = targetStandard.get(standardKey);
                    if (current != null && enabled(current)) plan.add(STANDARD, standardKey, current, fromVanilla(key, value));
                }
            }
        }
        if (plan.changes.stream().anyMatch(c -> c.file.equals(OPTIONS)))
            SettingsImportStore.ensureCurrentOptionsVersion(existing, new LinkedHashMap<>());
        return plan;
    }

    private void add(String file, String key, JsonElement before, JsonElement after) {
        if (!after.equals(before)) changes.add(new Change(category(key, file.equals(STANDARD), bindings), file, key, before, after));
    }

    public int count(Set<Category> selected) { return (int) changes.stream().filter(c -> selected.contains(c.category)).count(); }
    public boolean detachesShared(Set<Category> selected) {
        return targetPointer != null && changes.stream().anyMatch(c -> c.file.equals(STANDARD) && selected.contains(c.category));
    }

    static JsonObject effectiveStandard(Path game) throws IOException {
        String pointer = SettingsImportStore.readOptional(game.resolve(GLOBAL));
        if (pointer == null) return object(SettingsImportStore.readOptional(game.resolve(STANDARD)));
        String name = pointer.trim();
        if (name.isEmpty() || name.contains("\n") || name.contains("\r")) throw new IOException("Invalid StandardSettings shared-file pointer.");
        Path shared;
        try { shared = Paths.get(name); }
        catch (InvalidPathException e) { throw new IOException("Invalid StandardSettings shared-file path.", e); }
        // Relative global pointers depend on the launcher's working directory, so do not guess.
        if (!shared.isAbsolute()) throw new IOException("StandardSettings uses a relative shared-file path. Change it to an absolute path before importing.");
        if (!Files.isRegularFile(shared)) throw new IOException("The StandardSettings shared file is missing.");
        return object(SettingsImportStore.readOptional(shared));
    }

    static JsonObject object(String text) throws IOException {
        if (text == null) return new JsonObject();
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            JsonElement json = readJson(reader, 0);
            if (!json.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Unsupported settings JSON.");
            return json.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) { throw new IOException("Invalid settings JSON.", e); }
    }

    private static JsonElement readJson(JsonReader reader, int depth) throws IOException {
        if (depth > 16) throw new IOException("Settings JSON is nested too deeply.");
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                JsonObject object = new JsonObject(); reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (object.has(key) || object.size() >= 4096) throw new IOException("Duplicate or excessive JSON settings.");
                    object.add(key, readJson(reader, depth + 1));
                }
                reader.endObject(); return object;
            case BEGIN_ARRAY:
                JsonArray array = new JsonArray(); reader.beginArray();
                while (reader.hasNext()) {
                    if (array.size() >= 4096) throw new IOException("Settings array is too large.");
                    array.add(readJson(reader, depth + 1));
                }
                reader.endArray(); return array;
            case STRING: return new JsonPrimitive(reader.nextString());
            case NUMBER:
                try { return new JsonPrimitive(new java.math.BigDecimal(reader.nextString())); }
                catch (NumberFormatException e) { throw new IOException("Invalid settings number.", e); }
            case BOOLEAN: return new JsonPrimitive(reader.nextBoolean());
            case NULL: reader.nextNull(); return JsonNull.INSTANCE;
            default: throw new IOException("Invalid settings JSON.");
        }
    }

    static Map<String, String> options(String text) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        if (text == null) return values;
        for (String line : text.split("\\r?\\n")) {
            int colon = line.indexOf(':');
            if (colon < 1) continue;
            String key = line.substring(0, colon);
            if (values.containsKey(key)) throw new IOException("Duplicate option: " + key);
            values.put(key, line.substring(colon + 1).trim());
            if (values.size() > 4096) throw new IOException("Too many settings in options.txt.");
        }
        return values;
    }
}
