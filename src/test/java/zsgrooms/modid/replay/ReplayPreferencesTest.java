package zsgrooms.modid.replay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReplayPreferencesTest {
    @TempDir Path temp;

    @Test void detailLevelsPersistIndependentlyOfCameraPerformanceAndHealth() throws Exception {
        Path file = temp.resolve("levels.properties");
        for (ReplayPreferences.RecordingDetail detail : ReplayPreferences.RecordingDetail.values()) {
            for (ReplayPreferences.CameraRate rate : ReplayPreferences.CameraRate.values()) {
                for (boolean performance : new boolean[]{false, true}) {
                    for (boolean health : new boolean[]{false, true}) {
                        new ReplayPreferences(true, "", true, "", false, performance, true, detail, rate, health).save(file);
                        ReplayPreferences loaded = ReplayPreferences.load(file);
                        assertEquals(detail, loaded.recordingDetail);
                        assertEquals(detail != ReplayPreferences.RecordingDetail.STANDARD, loaded.detailedCapture);
                        assertEquals(rate, loaded.cameraRate);
                        assertEquals(performance, loaded.performanceMode);
                        assertEquals(health, loaded.showCaptureHealth);
                    }
                }
            }
        }
    }

    @Test void legacyDetailedSettingsMigrateWithoutEnablingSuperDetailed() throws Exception {
        Path file = temp.resolve("legacy.properties");
        assertEquals(ReplayPreferences.RecordingDetail.STANDARD, ReplayPreferences.load(file).recordingDetail);
        assertFalse(ReplayPreferences.load(file).showCaptureHealth);
        for (boolean legacy : new boolean[]{false, true}) {
            for (String value : new String[]{"", "recordingDetail=unknown\n"}) {
                Files.write(file, ("detailedCapture=" + legacy + "\n" + value).getBytes(StandardCharsets.UTF_8));
                assertEquals(legacy ? ReplayPreferences.RecordingDetail.DETAILED : ReplayPreferences.RecordingDetail.STANDARD,
                        ReplayPreferences.load(file).recordingDetail);
            }
        }
        Files.write(file, "detailedCapture=true\nrecordingDetail=STANDARD\n".getBytes(StandardCharsets.UTF_8));
        assertFalse(ReplayPreferences.load(file).detailedCapture);
        Files.write(file, "detailedCapture=false\nrecordingDetail=SUPER_DETAILED\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(ReplayPreferences.RecordingDetail.SUPER_DETAILED, ReplayPreferences.load(file).recordingDetail);
        assertTrue(ReplayPreferences.load(file).detailedCapture);
    }

    @Test void cameraRateDefaultsToStandardAndPersistsIndependentlyOfPresets() throws Exception {
        Path file = temp.resolve("camera.properties");
        assertEquals(ReplayPreferences.CameraRate.STANDARD, ReplayPreferences.load(file).cameraRate);
        for (ReplayPreferences.CameraRate rate : ReplayPreferences.CameraRate.values()) {
            for (boolean detail : new boolean[]{false, true}) {
                for (boolean performance : new boolean[]{false, true}) {
                    new ReplayPreferences(true, "", true, "", false, performance, true, detail, rate).save(file);
                    ReplayPreferences loaded = ReplayPreferences.load(file);
                    assertEquals(rate, loaded.cameraRate);
                    assertEquals(detail, loaded.detailedCapture);
                    assertEquals(performance, loaded.performanceMode);
                    assertTrue(loaded.enabled);
                    assertTrue(loaded.keepSeedChanges);
                    assertFalse(loaded.showRecordingHud);
                }
            }
        }
        for (String setting : new String[]{"", "cameraRate=bogus\n", "cameraRate=HZ_480\n"}) {
            Files.write(file, ("enabled=true\n" + setting).getBytes(StandardCharsets.UTF_8));
            assertEquals(ReplayPreferences.CameraRate.STANDARD, ReplayPreferences.load(file).cameraRate);
            assertTrue(ReplayPreferences.load(file).enabled);
        }
    }

    @Test void detailedCaptureIsOptInAndPersistsWithoutChangingNormalMode() throws Exception {
        Path file = temp.resolve("detail.properties");
        assertFalse(ReplayPreferences.load(file).detailedCapture);
        Files.write(file, "enabled=true\nperformanceMode=true\n".getBytes(StandardCharsets.UTF_8));
        assertFalse(ReplayPreferences.load(file).detailedCapture);
        new ReplayPreferences(true, "", true, "", false, true, true, true).save(file);
        ReplayPreferences loaded = ReplayPreferences.load(file);
        assertTrue(loaded.detailedCapture);
        assertTrue(loaded.enabled);
        assertTrue(loaded.performanceMode);
        assertTrue(loaded.keepSeedChanges);
        assertFalse(loaded.showRecordingHud);
        new ReplayPreferences(true, "", true, "", true, true, true, false).save(file);
        assertFalse(ReplayPreferences.load(file).detailedCapture);
    }

    @Test void seedRetentionDefaultsOffForNewAndExistingSettingsAndPersists() throws Exception {
        Path file = temp.resolve("retention.properties");
        assertFalse(ReplayPreferences.load(file).keepSeedChanges);
        Files.write(file, "enabled=true\nperformanceMode=true\n".getBytes(StandardCharsets.UTF_8));
        assertFalse(ReplayPreferences.load(file).keepSeedChanges);
        new ReplayPreferences(true, "", true, "", false, true, true).save(file);
        ReplayPreferences loaded = ReplayPreferences.load(file);
        assertTrue(loaded.keepSeedChanges);
        assertTrue(loaded.performanceMode);
        assertFalse(loaded.showRecordingHud);
        assertTrue(loaded.enabled);
        new ReplayPreferences(true, "", true, "", true, true, false).save(file);
        assertFalse(ReplayPreferences.load(file).keepSeedChanges);
    }

    @Test void performanceModeDefaultsOffAndPersistsIndependently() throws Exception {
        Path file = temp.resolve("performance.properties");
        assertFalse(ReplayPreferences.load(file).performanceMode);
        new ReplayPreferences(true, "", true, "", false, true).save(file);
        ReplayPreferences loaded = ReplayPreferences.load(file);
        assertTrue(loaded.performanceMode);
        assertTrue(loaded.enabled);
        assertFalse(loaded.showRecordingHud);
        new ReplayPreferences(true, "", true, "", true, false).save(file);
        assertFalse(ReplayPreferences.load(file).performanceMode);
        Files.write(file, "enabled=true\n".getBytes(StandardCharsets.UTF_8));
        assertFalse(ReplayPreferences.load(file).performanceMode);
    }

    @Test void recordingBadgeDefaultsVisibleAndCanBeHiddenWithoutDisablingCapture() throws Exception {
        Path file = temp.resolve("badge.properties");
        assertTrue(ReplayPreferences.load(file).showRecordingHud);
        new ReplayPreferences(true, "", true, "", false).save(file);
        ReplayPreferences preferences = ReplayPreferences.load(file);
        assertTrue(preferences.enabled);
        assertTrue(preferences.replayModRecordingDisabled);
        assertFalse(preferences.showRecordingHud);
    }

    @Test void soloTestGroupIsOptInPersistentAndRequiresACanonicalUuid() throws Exception {
        Path file = temp.resolve("replay.properties");
        assertEquals("", ReplayPreferences.load(file).soloTestGroup);
        String id = java.util.UUID.randomUUID().toString();
        new ReplayPreferences(true, "", true, id).save(file);
        assertEquals(id, ReplayPreferences.load(file).soloTestGroup);
        assertThrows(IllegalArgumentException.class, () -> new ReplayPreferences(true, "", true, "1-1-1-1-1"));
        Files.write(file, "enabled=true\nsoloTestGroup=bad\n".getBytes(StandardCharsets.UTF_8));
        assertTrue(ReplayPreferences.load(file).enabled);
        assertEquals("", ReplayPreferences.load(file).soloTestGroup);
    }

    @Test
    void missingSettingsAreOffAndDoNotConfirmAnotherRecorder() {
        ReplayPreferences preferences = ReplayPreferences.load(temp.resolve("missing.properties"));
        assertFalse(preferences.enabled);
        assertFalse(preferences.replayModRecordingDisabled);
        assertEquals(temp.resolve("zsgrooms/replay-libraries").toAbsolutePath(), preferences.resolveLibraries(temp));
    }

    @Test
    void choicesPersistWithoutJvmArgumentsIncludingPathsWithSpaces() throws Exception {
        Path file = temp.resolve("config/replay.properties");
        String directory = temp.resolve("libraries with spaces").toString();
        new ReplayPreferences(true, directory, true).save(file);
        ReplayPreferences loaded = ReplayPreferences.load(file);
        assertTrue(loaded.enabled);
        assertTrue(loaded.replayModRecordingDisabled);
        assertEquals(directory, loaded.libraryDirectory);
        assertEquals(temp.resolve("libraries with spaces"), loaded.resolveLibraries(temp));
        new ReplayPreferences(false, directory, false).save(file);
        assertFalse(ReplayPreferences.load(file).enabled);
        assertFalse(ReplayPreferences.load(file).replayModRecordingDisabled);
    }

    @Test
    void defaultSetupRemainsAutomaticAfterSavingAndReplacingOverride() throws Exception {
        Path file = temp.resolve("config/replay.properties");
        new ReplayPreferences(true, "some-custom-folder", true).save(file);
        new ReplayPreferences(true, "", true).save(file);
        ReplayPreferences loaded = ReplayPreferences.load(file);
        assertTrue(loaded.enabled);
        assertEquals("", loaded.libraryDirectory);
        assertEquals(temp.resolve("zsgrooms/replay-libraries"), loaded.resolveLibraries(temp));
    }

    @Test
    void invalidConfigurationFailsClosed() throws Exception {
        Path file = temp.resolve("replay.properties");
        Files.write(file, "enabled=perhaps\nreplayModRecordingDisabled=yes\n".getBytes(StandardCharsets.UTF_8));
        assertFalse(ReplayPreferences.load(file).enabled);
        assertFalse(ReplayPreferences.load(file).replayModRecordingDisabled);
        Files.write(file, "enabled=true\nlibraryDirectory=\\uNOTHEX".getBytes(StandardCharsets.UTF_8));
        assertFalse(ReplayPreferences.load(file).enabled);
    }
}
