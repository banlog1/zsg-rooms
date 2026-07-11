package zsgrooms.modid.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void comparesReleaseVersions() {
        assertTrue(UpdateManager.isNewer("1.1.0", "1.0.9"));
        assertTrue(UpdateManager.isNewer("v1.0.0", "1.0.0-test"));
        assertFalse(UpdateManager.isNewer("1.0.0", "1.0.0"));
        assertFalse(UpdateManager.isNewer("1.0.0-test", "1.0.0"));
    }

    @Test
    void helperReplacesJarAndClearsMarker() throws Exception {
        Path target = this.temporaryDirectory.resolve("zsg-rooms.jar");
        Path pending = this.temporaryDirectory.resolve("update.jar.pending");
        Path marker = this.temporaryDirectory.resolve("pending.properties");
        Files.write(target, "old".getBytes(StandardCharsets.UTF_8));
        Files.write(pending, "new".getBytes(StandardCharsets.UTF_8));
        Files.write(marker, "pending".getBytes(StandardCharsets.UTF_8));

        UpdaterHelper.main(new String[]{target.toString(), pending.toString(), marker.toString()});

        assertTrue(new String(Files.readAllBytes(target), StandardCharsets.UTF_8).equals("new"));
        assertFalse(Files.exists(pending));
        assertFalse(Files.exists(marker));
    }
}
