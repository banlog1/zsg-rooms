package zsgrooms.modid.replay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class ReplayStoragePreferencesTest {
    @TempDir Path temp;

    @Test void defaultsToInstanceFolderAndDoesNotCreateItUntilRequested() throws Exception {
        assertEquals(temp.resolve("replay_recordings"), ReplayStoragePreferences.resolveDirectory(temp));
        assertFalse(Files.exists(temp.resolve("replay_recordings")));
        assertTrue(Files.isDirectory(ReplayStoragePreferences.prepareDirectory(temp)));
    }

    @Test void customDestinationPersistsAndResetLeavesFilesUntouched() throws Exception {
        Path custom = temp.resolve("external recordings");
        ReplayStoragePreferences.save(temp, custom.toString());
        assertEquals(custom, ReplayStoragePreferences.resolveDirectory(temp));
        Path recording = custom.resolve("existing.mcpr");
        Files.write(recording, new byte[] {1, 2, 3});
        ReplayStoragePreferences.save(temp, "");
        assertEquals(temp.resolve("replay_recordings"), ReplayStoragePreferences.resolveDirectory(temp));
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(recording));
    }

    @Test void invalidSelectionDoesNotReplacePreviousSettingOrFiles() throws Exception {
        Path good = temp.resolve("good");
        ReplayStoragePreferences.save(temp, good.toString());
        assertThrows(IOException.class, () -> ReplayStoragePreferences.save(temp, "relative-folder"));
        Path conflict = temp.resolve("file");
        Files.write(conflict, new byte[] {4});
        assertThrows(IOException.class, () -> ReplayStoragePreferences.save(temp, conflict.toString()));
        assertEquals(good, ReplayStoragePreferences.resolveDirectory(temp));
        assertArrayEquals(new byte[] {4}, Files.readAllBytes(conflict));
    }

    @Test void corruptSettingIsReportedRatherThanSilentlyChangingDestination() throws Exception {
        Path config = temp.resolve("config/zsgrooms-replay-storage.properties");
        Files.createDirectories(config.getParent());
        Files.write(config, "recordingDirectory=\\uZZZZ".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> ReplayStoragePreferences.resolveDirectory(temp));
    }

    @Test void resolvedSessionDestinationDoesNotChangeWhenTheNextDestinationIsSaved() throws Exception {
        Path first = temp.resolve("first"), second = temp.resolve("second");
        ReplayStoragePreferences.save(temp, first.toString());
        Path sessionFile = ReplayStoragePreferences.resolveDirectory(temp).resolve("session.mcpr");
        ReplayStoragePreferences.save(temp, second.toString());
        assertEquals(first.resolve("session.mcpr"), sessionFile);
        assertEquals(second, ReplayStoragePreferences.resolveDirectory(temp));
    }
}
