package zsgrooms.modid.settings;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Read-only launcher metadata. Missing or damaged labels never block a settings import. */
public final class SettingsImportInstance {
    public final Path directory;
    public final String name, minecraftVersion, launcher;

    private SettingsImportInstance(Path directory, String name, String version, String launcher) {
        this.directory = directory; this.name = name; this.minecraftVersion = version; this.launcher = launcher;
    }

    public String details() {
        return (minecraftVersion.isEmpty() ? "Minecraft version unknown" : "Minecraft " + minecraftVersion) + " | " + launcher;
    }

    public static List<SettingsImportInstance> discover(Path current) throws IOException {
        List<SettingsImportInstance> result = new ArrayList<>();
        for (Path directory : SettingsImportPlan.discover(current)) {
            try { result.add(read(directory)); }
            catch (IOException ignored) { /* An instance may disappear while the list is loading. */ }
        }
        result.sort(Comparator.comparing((SettingsImportInstance item) -> item.name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(item -> item.directory.toString(), String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public static SettingsImportInstance read(Path folder) throws IOException {
        Path game = SettingsImportPlan.gameDirectory(folder);
        Path instance = game;
        String leaf = game.getFileName() == null ? "" : game.getFileName().toString();
        if ((leaf.equals(".minecraft") || leaf.equals("minecraft")) && game.getParent() != null
                && hasMetadata(game.getParent())) instance = game.getParent();
        Map<String, String> config = config(instance.resolve("instance.cfg"));
        String name = clean(config.get("name"));
        if (name.isEmpty()) {
            Path fallback = instance;
            if (instance.equals(game) && (leaf.equals(".minecraft") || leaf.equals("minecraft")) && game.getParent() != null)
                fallback = game.getParent();
            name = clean(fallback.getFileName() == null ? "Minecraft" : fallback.getFileName().toString());
            if (isDefaultGame(game)) name = "Default Minecraft";
            if (name.isEmpty()) name = "Minecraft";
        }
        String version = version(instance.resolve("mmc-pack.json"));
        if (version.isEmpty()) version = clean(config.get("IntendedVersion"));
        return new SettingsImportInstance(game, name, version, launcher(instance));
    }

    private static boolean hasMetadata(Path instance) {
        return Files.isRegularFile(instance.resolve("instance.cfg")) || Files.isRegularFile(instance.resolve("mmc-pack.json"));
    }

    private static boolean isDefaultGame(Path game) {
        for (String root : new String[]{System.getenv("APPDATA"), System.getProperty("user.home")}) {
            if (root == null) continue;
            try { if (game.equals(Paths.get(root, ".minecraft").toRealPath())) return true; }
            catch (IOException | InvalidPathException ignored) { }
        }
        return false;
    }

    private static String launcher(Path instance) {
        if (hasMetadata(instance) && instance.getParent() != null && instance.getParent().getParent() != null) {
            Path root = instance.getParent().getParent();
            boolean prism = Files.isRegularFile(root.resolve("prismlauncher.cfg"));
            boolean multi = Files.isRegularFile(root.resolve("multimc.cfg"));
            if (prism != multi) return prism ? "Prism Launcher" : "MultiMC";
        }
        return "Unknown launcher";
    }

    private static String version(Path pack) {
        try {
            JsonElement components = SettingsImportPlan.object(SettingsImportStore.readOptional(pack)).get("components");
            if (components == null || !components.isJsonArray()) return "";
            for (JsonElement element : components.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject component = element.getAsJsonObject();
                if (string(component.get("uid")).equals("net.minecraft")) return clean(string(component.get("version")));
            }
        } catch (IOException | RuntimeException ignored) { }
        return "";
    }

    private static String string(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }

    private static Map<String, String> config(Path file) {
        Map<String, String> result = new HashMap<>();
        try {
            String text = SettingsImportStore.readOptional(file);
            if (text == null) return result;
            boolean general = true;
            // Only these top-level strings are needed, in both legacy and Qt [General] INI files.
            for (String line : text.replace("\uFEFF", "").split("\\r?\\n")) {
                line = line.trim();
                if (line.startsWith("[")) { general = line.equals("[General]"); continue; }
                int equal = line.indexOf('=');
                if (!general || equal < 1) continue;
                String key = line.substring(0, equal).trim();
                if (key.equals("name") || key.equals("IntendedVersion") || key.equals("ConfigVersion"))
                    result.put(key, line.substring(equal + 1).trim());
            }
            boolean qt = result.containsKey("ConfigVersion");
            result.replaceAll((key, value) -> decode(value, qt));
        } catch (IOException | RuntimeException ignored) { result.clear(); }
        return result;
    }

    private static String decode(String value, boolean qt) {
        if (!qt && value.length() > 1 && value.startsWith("\"") && value.endsWith("\"")
                && (value.contains(";") || value.contains("=") || value.contains(",")))
            value = value.substring(1, value.length() - 1);
        StringBuilder decoded = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (qt && c == '"') { quoted = !quoted; continue; }
            if (!quoted && (qt ? c == ';' : c == '#')) break;
            if (c == '\\' && i + 1 < value.length()) {
                c = value.charAt(++i);
                if (c == 'n' || c == 'r' || c == 't') c = ' ';
                else if (qt && c == 'x') {
                    int number = 0, digits = 0;
                    while (i + 1 < value.length() && digits < 4 && Character.digit(value.charAt(i + 1), 16) >= 0) {
                        number = number * 16 + Character.digit(value.charAt(++i), 16); digits++;
                    }
                    if (digits > 0) c = (char) number;
                }
            }
            decoded.append(c);
        }
        String result = decoded.toString().trim();
        return qt && result.startsWith("@@") ? result.substring(1) : result;
    }

    private static String clean(String value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length() && result.length() < 256; i++) {
            char c = value.charAt(i);
            if (c == '\u00A7') { i++; continue; }
            if (Character.isISOControl(c) || Character.getType(c) == Character.FORMAT || Character.isWhitespace(c)) c = ' ';
            result.append(c);
        }
        return result.toString().trim();
    }
}
