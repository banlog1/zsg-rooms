package zsgrooms.modid.replay;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Recording storage is independent of the libraries used by the writer. */
public final class ReplayStoragePreferences {
    private ReplayStoragePreferences() {}

    public static String load(Path gameDirectory) throws IOException {
        Path file = settingsFile(gameDirectory);
        if (!Files.exists(file)) return "";
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(file)) { values.load(input); }
        catch (IllegalArgumentException error) { throw new IOException("Invalid recording storage settings", error); }
        return values.getProperty("recordingDirectory", "").trim();
    }

    public static Path resolveDirectory(Path gameDirectory) throws IOException {
        String value = load(gameDirectory);
        if (value.isEmpty()) return gameDirectory.resolve("replay_recordings");
        try {
            Path path = Paths.get(value);
            if (!path.isAbsolute()) throw new IOException("Recording folder must be an absolute path");
            return path.normalize();
        } catch (java.nio.file.InvalidPathException error) {
            throw new IOException("Invalid recording folder", error);
        }
    }

    public static Path prepareDirectory(Path gameDirectory) throws IOException {
        Path directory = resolveDirectory(gameDirectory);
        Files.createDirectories(directory);
        return directory;
    }

    public static void save(Path gameDirectory, String destination) throws IOException {
        String value = destination == null ? "" : destination.trim();
        if (!value.isEmpty()) {
            try {
                Path directory = Paths.get(value);
                if (!directory.isAbsolute()) throw new IOException("Recording folder must be an absolute path");
                directory = directory.normalize();
                Files.createDirectories(directory);
                if (!Files.isWritable(directory)) throw new IOException("Recording folder is not writable");
                value = directory.toString();
            } catch (java.nio.file.InvalidPathException error) {
                throw new IOException("Invalid recording folder", error);
            }
        }
        Path file = settingsFile(gameDirectory);
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "replay-storage-", ".tmp");
        try {
            Properties values = new Properties();
            values.setProperty("recordingDirectory", value);
            try (OutputStream output = Files.newOutputStream(temporary)) {
                values.store(output, "ZSG recording storage; applies to new recordings");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static Path settingsFile(Path gameDirectory) {
        return gameDirectory.resolve("config/zsgrooms-replay-storage.properties");
    }
}
