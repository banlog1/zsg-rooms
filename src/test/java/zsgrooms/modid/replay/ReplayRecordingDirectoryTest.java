package zsgrooms.modid.replay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReplayRecordingDirectoryTest {
    @TempDir
    Path temp;

    @Test
    void createsReplayModsDefaultFolderInsideTheGameDirectory() throws Exception {
        Path game = temp.resolve("instance with spaces").resolve(".minecraft");
        Path directory = ReplayPrototype.prepareRecordingDirectory(game);
        assertEquals(game.resolve("replay_recordings"), directory);
        assertTrue(Files.isDirectory(directory));
        assertFalse(Files.exists(game.resolve("zsgrooms/replays/prototype")));
    }

    @Test
    void reusesTheFolderWithoutChangingExistingRecordings() throws Exception {
        Path directory = ReplayPrototype.prepareRecordingDirectory(temp);
        Path existing = directory.resolve("existing.mcpr");
        byte[] contents = {1, 2, 3};
        Files.write(existing, contents);
        assertEquals(directory, ReplayPrototype.prepareRecordingDirectory(temp));
        assertArrayEquals(contents, Files.readAllBytes(existing));
    }

    @Test
    void reportsAnOutputPathConflictWithoutOverwritingIt() throws Exception {
        Path conflict = temp.resolve("replay_recordings");
        byte[] contents = {4, 5, 6};
        Files.write(conflict, contents);
        assertThrows(IOException.class, () -> ReplayPrototype.prepareRecordingDirectory(temp));
        assertArrayEquals(contents, Files.readAllBytes(conflict));
    }
}
