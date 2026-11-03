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
    public final boolean performanceMode;
    public final boolean keepSeedChanges;
    public final boolean detailedCapture;
    public final RecordingDetail recordingDetail;
    public final CameraRate cameraRate;
    public final boolean showCaptureHealth;

    public enum RecordingDetail {
        STANDARD("Standard"), DETAILED("Detailed"), SUPER_DETAILED("Super Detailed");
        public final String label;
        RecordingDetail(String label) { this.label = label; }
        static RecordingDetail parse(String value, boolean legacyDetailed) {
            try { return valueOf(value); }
            catch (IllegalArgumentException | NullPointerException ignored) {
                return legacyDetailed ? DETAILED : STANDARD;
            }
        }
    }

    public enum CameraRate {
        STANDARD(0, "Standard"), HZ_60(60, "60 Hz"), HZ_120(120, "120 Hz"), HZ_240(240, "240 Hz (advanced)");
        public final int hz;
        public final String label;
        CameraRate(int hz, String label) { this.hz = hz; this.label = label; }
        static CameraRate parse(String value) {
            try { return valueOf(value); }
            catch (IllegalArgumentException | NullPointerException ignored) { return STANDARD; }
        }
    }

    public ReplayPreferences(boolean enabled, String libraryDirectory, boolean replayModRecordingDisabled) {
        this(enabled, libraryDirectory, replayModRecordingDisabled, "");
    }

    public ReplayPreferences(boolean enabled, String libraryDirectory, boolean replayModRecordingDisabled, String soloTestGroup) {
        this(enabled, libraryDirectory, replayModRecordingDisabled, soloTestGroup, true);
    }

    public ReplayPreferences(boolean enabled, String libraryDirectory, boolean replayModRecordingDisabled, String soloTestGroup, boolean showRecordingHud) {
        this(enabled, libraryDirectory, replayModRecordingDisabled, soloTestGroup, showRecordingHud, false);
    }

    public ReplayPreferences(boolean enabled, String libraryDirectory, boolean replayModRecordingDisabled, String soloTestGroup, boolean showRecordingHud, boolean performanceMode) {
        this(enabled, libraryDirectory, replayModRecordingDisabled, soloTestGroup, showRecordingHud, performanceMode, false);
    }

    public ReplayPreferences(boolean enabled, String libraryDirectory, boolean replayModRecordingDisabled, String soloTestGroup, boolean showRecordingHud, boolean performanceMode, boolean keepSeedChanges) {
        this(enabled, libraryDirectory, replayModRecordingDisabled, soloTestGroup, showRecordingHud, performanceMode, keepSeedChanges, false);
    }

    public ReplayPreferences(boolean enabled, String libraryDirectory, boolean replayModRecordingDisabled, String soloTestGroup, boolean showRecordingHud, boolean performanceMode, boolean keepSeedChanges, boolean detailedCapture) {
        this(enabled, libraryDirectory, replayModRecordingDisabled, soloTestGroup, showRecordingHud,
                performanceMode, keepSeedChanges, detailedCapture, CameraRate.STANDARD);
    }

    public ReplayPreferences(boolean enabled, String libraryDirectory, boolean replayModRecordingDisabled, String soloTestGroup, boolean showRecordingHud, boolean performanceMode, boolean keepSeedChanges, boolean detailedCapture, CameraRate cameraRate) {
        this(enabled, libraryDirectory, replayModRecordingDisabled, soloTestGroup, showRecordingHud,
                performanceMode, keepSeedChanges, detailedCapture ? RecordingDetail.DETAILED : RecordingDetail.STANDARD,
                cameraRate, false);
    }

    public ReplayPreferences(boolean enabled, String libraryDirectory, boolean replayModRecordingDisabled, String soloTestGroup, boolean showRecordingHud, boolean performanceMode, boolean keepSeedChanges, RecordingDetail recordingDetail, CameraRate cameraRate, boolean showCaptureHealth) {
        this.enabled = enabled;
        this.libraryDirectory = libraryDirectory == null ? "" : libraryDirectory.trim();
        this.replayModRecordingDisabled = replayModRecordingDisabled;
        this.soloTestGroup = normalizeTestGroup(soloTestGroup);
        this.showRecordingHud = showRecordingHud;
        this.performanceMode = performanceMode;
        this.keepSeedChanges = keepSeedChanges;
        this.recordingDetail = recordingDetail == null ? RecordingDetail.STANDARD : recordingDetail;
        this.detailedCapture = this.recordingDetail != RecordingDetail.STANDARD;
        this.cameraRate = cameraRate == null ? CameraRate.STANDARD : cameraRate;
        this.showCaptureHealth = showCaptureHealth;
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
                Boolean.parseBoolean(values.getProperty("showRecordingHud", "true")),
                Boolean.parseBoolean(values.getProperty("performanceMode", "false")),
                Boolean.parseBoolean(values.getProperty("keepSeedChanges", "false")),
                RecordingDetail.parse(values.getProperty("recordingDetail"),
                        Boolean.parseBoolean(values.getProperty("detailedCapture", "false"))),
                CameraRate.parse(values.getProperty("cameraRate", "STANDARD")),
                Boolean.parseBoolean(values.getProperty("showCaptureHealth", "false")));
    }

    void save(Path path) throws IOException {
        Properties values = new Properties();
        values.setProperty("enabled", Boolean.toString(enabled));
        values.setProperty("libraryDirectory", libraryDirectory);
        values.setProperty("replayModRecordingDisabled", Boolean.toString(replayModRecordingDisabled));
        values.setProperty("soloTestGroup", soloTestGroup);
        values.setProperty("showRecordingHud", Boolean.toString(showRecordingHud));
        values.setProperty("performanceMode", Boolean.toString(performanceMode));
        values.setProperty("keepSeedChanges", Boolean.toString(keepSeedChanges));
        values.setProperty("detailedCapture", Boolean.toString(detailedCapture));
        values.setProperty("recordingDetail", recordingDetail.name());
        values.setProperty("cameraRate", cameraRate.name());
        values.setProperty("showCaptureHealth", Boolean.toString(showCaptureHealth));
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
