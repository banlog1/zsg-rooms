package zsgrooms.modid.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Properties;

public final class TutorialPreferences {
    public enum Choice { NONE, QUICK, FULL }

    private final Path path;
    private Choice choice;

    public TutorialPreferences(Path path) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath();
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(this.path)) {
            properties.load(input);
            if ("1".equals(properties.getProperty("version"))) {
                String value = properties.getProperty("choice");
                if (value != null) {
                    choice = Choice.valueOf(value);
                }
            }
        } catch (IOException | IllegalArgumentException ignored) {
            choice = null;
        }
    }

    public synchronized boolean shouldPrompt() {
        return choice == null;
    }

    public synchronized Choice getChoice() {
        return choice;
    }

    public synchronized void choose(Choice choice) throws IOException {
        // A failed save must not prompt again during this session.
        this.choice = Objects.requireNonNull(choice, "choice");
        Properties properties = new Properties();
        properties.setProperty("version", "1");
        properties.setProperty("choice", choice.name());

        Path parent = path.getParent();
        if (parent == null) {
            throw new IOException("Tutorial preferences path must identify a file");
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".zsg-rooms-tutorial-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "ZSG Rooms tutorial preferences");
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
