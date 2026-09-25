package zsgrooms.modid.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RuinedPortalRepairPreferenceTest {
    @TempDir Path config;

    @Test void newTestingPreferenceDoesNotInheritOldDefaultOnFile() throws Exception {
        Files.write(config.resolve("zsg-rooms-rp-repair.txt"), "true".getBytes(StandardCharsets.UTF_8));
        Path testing = config.resolve("zsg-rooms-rp-repair-testing.txt");
        assertFalse(RoomUiPreferences.loadRuinedPortalRepairTesting(testing));
        Files.write(testing, "true".getBytes(StandardCharsets.UTF_8));
        assertTrue(RoomUiPreferences.loadRuinedPortalRepairTesting(testing));
        Files.write(testing, "false".getBytes(StandardCharsets.UTF_8));
        assertFalse(RoomUiPreferences.loadRuinedPortalRepairTesting(testing));
        Files.write(testing, "invalid".getBytes(StandardCharsets.UTF_8));
        assertFalse(RoomUiPreferences.loadRuinedPortalRepairTesting(testing));
    }
}
