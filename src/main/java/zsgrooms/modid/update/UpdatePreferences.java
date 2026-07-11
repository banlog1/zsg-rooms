package zsgrooms.modid.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class UpdatePreferences {
    private static final Path CONFIG_PATH = Paths.get("config", "zsg-rooms-update.properties");
    private static boolean checksEnabled = true;
    private static String skippedVersion = "";

    static {
        load();
    }

    private UpdatePreferences() {
    }

    public static synchronized boolean areChecksEnabled() {
        return checksEnabled;
    }

    public static synchronized void setChecksEnabled(boolean enabled) {
        checksEnabled = enabled;
        save();
    }

    public static synchronized boolean isSkipped(String version) {
        return version != null && version.equals(skippedVersion);
    }

    public static synchronized void skipVersion(String version) {
        skippedVersion = version == null ? "" : version;
        save();
    }

    private static void load() {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(CONFIG_PATH)) {
            properties.load(input);
            checksEnabled = Boolean.parseBoolean(properties.getProperty("checksEnabled", "true"));
            skippedVersion = properties.getProperty("skippedVersion", "").trim();
        } catch (IOException ignored) {
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("checksEnabled", Boolean.toString(checksEnabled));
        properties.setProperty("skippedVersion", skippedVersion);
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream output = Files.newOutputStream(CONFIG_PATH)) {
                properties.store(output, "ZSG Rooms update preferences");
            }
        } catch (IOException ignored) {
        }
    }
}
