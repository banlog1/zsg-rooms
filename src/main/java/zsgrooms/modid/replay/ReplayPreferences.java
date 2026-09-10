package zsgrooms.modid.replay;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Local recording choices, never part of synchronized room state. */
public final class ReplayPreferences {
    public final boolean enabled;
    public final String libraryDirectory;
    public final boolean replayModRecordingDisabled;
    public final String soloTestGroup;
    public final boolean showRecordingHud;

    public ReplayPreferences(boolean enabled, String libraryDirectory, boolean replayModRecordingDisabled) {
        this(enabled, libraryDirectory, replayModRecordingDisabled, "");
    }

    public ReplayPreferences(boolean enabled, String libraryDirectory, boolean replayModRecordingDisabled, String soloTestGroup) {
        this(enabled, libraryDirectory, replayModRecordingDisabled, soloTestGroup, true);
    }

    public ReplayPreferences(boolean enabled, String libraryDirectory, boolean replayModRecordingDisabled, String soloTestGroup, boolean showRecordingHud) {
        this.enabled = enabled;
        this.libraryDirectory = libraryDirectory == null ? "" : libraryDirectory.trim();
        this.replayModRecordingDisabled = replayModRecordingDisabled;
        this.soloTestGroup = normalizeTestGroup(soloTestGroup);
        this.showRecordingHud = showRecordingHud;
    }

    public static String normalizeTestGroup(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String id = value.trim();
        if (!java.util.UUID.fromString(id).toString().equalsIgnoreCase(id)) throw new IllegalArgumentException("Invalid test group ID");
        return id.toLowerCase(java.util.Locale.ROOT);
    }

    static ReplayPreferences load(Path path) {
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            values.load(reader);
        } catch (IOException | IllegalArgumentException ignored) {
            return new ReplayPreferences(false, "", false);
        }
        try {
            java.nio.file.Paths.get(values.getProperty("libraryDirectory", ""));
        } catch (java.nio.file.InvalidPathException ignored) {
            return new ReplayPreferences(false, "", false);
        }
        String testGroup;
        try { testGroup = normalizeTestGroup(values.getProperty("soloTestGroup", "")); }
        catch (IllegalArgumentException ignored) { testGroup = ""; }
        return new ReplayPreferences(Boolean.parseBoolean(values.getProperty("enabled", "false")),
                values.getProperty("libraryDirectory", ""),
                Boolean.parseBoolean(values.getProperty("replayModRecordingDisabled", "false")), testGroup,
                Boolean.parseBoolean(values.getProperty("showRecordingHud", "true")));
    }

    void save(Path path) throws IOException {
        Properties values = new Properties();
        values.setProperty("enabled", Boolean.toString(enabled));
        values.setProperty("libraryDirectory", libraryDirectory);
        values.setProperty("replayModRecordingDisabled", Boolean.toString(replayModRecordingDisabled));
        values.setProperty("soloTestGroup", soloTestGroup);
        values.setProperty("showRecordingHud", Boolean.toString(showRecordingHud));
        Files.createDirectories(path.toAbsolutePath().getParent());
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            values.store(writer, "Local ZSG replay settings");
        }
    }

    Path resolveLibraries(Path gameDirectory) {
        return (libraryDirectory.isEmpty() ? gameDirectory.resolve("zsgrooms/replay-libraries")
                : gameDirectory.resolve(libraryDirectory)).toAbsolutePath().normalize();
    }
}
