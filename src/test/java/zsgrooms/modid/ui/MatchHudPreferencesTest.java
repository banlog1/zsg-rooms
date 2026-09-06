package zsgrooms.modid.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class MatchHudPreferencesTest {
    @TempDir
    Path directory;

    @Test
    public void missingConfigDefaultsToSelfAndOneRotatingOpponent() {
        MatchHudPreferences settings = MatchHudPreferences.load(directory.resolve("missing.properties"));
        assertTrue(settings.visible);
        assertTrue(settings.pinSelf);
        assertEquals(2, settings.rows);
        assertEquals(5, settings.rotationSeconds);
        assertEquals(70, settings.opacity);
        assertEquals(100, settings.scale);
    }

    @Test
    public void allSettingsRoundTripIncludingTransparentBackground() {
        Path path = directory.resolve("nested/hud.properties");
        MatchHudPreferences settings = new MatchHudPreferences();
        settings.visible = false;
        settings.header = false;
        settings.heads = false;
        settings.progressNumbers = false;
        settings.pinSelf = false;
        settings.opacity = 0;
        settings.scale = 125;
        settings.rows = 1;
        settings.rotationSeconds = 8;
        settings.save(path);
        MatchHudPreferences restored = MatchHudPreferences.load(path);
        assertFalse(restored.visible);
        assertFalse(restored.header);
        assertFalse(restored.heads);
        assertFalse(restored.progressNumbers);
        assertFalse(restored.pinSelf);
        assertEquals(0, restored.opacity);
        assertEquals(125, restored.scale);
        assertEquals(1, restored.rows);
        assertEquals(8, restored.rotationSeconds);
    }

    @Test
    public void invalidValuesFallbackOrClampAndPinningLeavesRoomForOpponents() throws Exception {
        Path path = directory.resolve("hud.properties");
        Files.write(path, ("visible=oops\nopacity=-4\nscale=999\nrows=0\nrotationSeconds=nope\n")
                .getBytes(StandardCharsets.UTF_8));
        MatchHudPreferences restored = MatchHudPreferences.load(path);
        assertTrue(restored.visible);
        assertEquals(0, restored.opacity);
        assertEquals(150, restored.scale);
        assertEquals(2, restored.rows);
        assertEquals(5, restored.rotationSeconds);
    }

    @Test
    public void malformedPropertiesDoNotPreventStartup() throws Exception {
        Path path = directory.resolve("hud.properties");
        Files.write(path, "visible=\\uINVALID".getBytes(StandardCharsets.UTF_8));
        assertTrue(MatchHudPreferences.load(path).visible);
    }
}
