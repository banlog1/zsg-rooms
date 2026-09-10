package zsgrooms.modid.replay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReplayPreferencesTest {
    @TempDir Path temp;

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
