package zsgrooms.modid.ui;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class MatchHudPreferences {
    boolean visible = true;
    boolean header = true;
    boolean heads = true;
    boolean progressNumbers = true;
    boolean pinSelf = true;
    int opacity = 70;
    int scale = 100;
    int rows = 2;
    int rotationSeconds = 5;

    static MatchHudPreferences load(Path path) {
        MatchHudPreferences settings = new MatchHudPreferences();
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            values.load(reader);
        } catch (IOException | IllegalArgumentException ignored) {
            return settings;
        }
        settings.visible = bool(values, "visible", settings.visible);
        settings.header = bool(values, "header", settings.header);
        settings.heads = bool(values, "heads", settings.heads);
        settings.progressNumbers = bool(values, "progressNumbers", settings.progressNumbers);
        settings.pinSelf = bool(values, "pinSelf", settings.pinSelf);
        settings.opacity = number(values, "opacity", settings.opacity, 0, 100);
        settings.scale = number(values, "scale", settings.scale, 75, 150);
        settings.rows = number(values, "rows", settings.rows, 1, 4);
        settings.rotationSeconds = number(values, "rotationSeconds", settings.rotationSeconds, 2, 10);
        if (settings.pinSelf) {
            settings.rows = Math.max(2, settings.rows);
        }
        return settings;
    }

    void save(Path path) {
        Properties values = new Properties();
        values.setProperty("visible", Boolean.toString(visible));
        values.setProperty("header", Boolean.toString(header));
        values.setProperty("heads", Boolean.toString(heads));
        values.setProperty("progressNumbers", Boolean.toString(progressNumbers));
        values.setProperty("pinSelf", Boolean.toString(pinSelf));
        values.setProperty("opacity", Integer.toString(opacity));
        values.setProperty("scale", Integer.toString(scale));
        values.setProperty("rows", Integer.toString(rows));
        values.setProperty("rotationSeconds", Integer.toString(rotationSeconds));
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                values.store(writer, "Local match HUD preferences");
            }
        } catch (IOException ignored) {
        }
    }

    private static boolean bool(Properties values, String key, boolean fallback) {
        String value = values.getProperty(key, "").trim();
        return "true".equalsIgnoreCase(value) ? true : "false".equalsIgnoreCase(value) ? false : fallback;
    }

    private static int number(Properties values, String key, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(values.getProperty(key, "").trim())));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
