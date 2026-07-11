package zsgrooms.modid.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class RoomUiPreferences {
    public enum HudPosition {
        TOP_LEFT("Top Left"),
        TOP_RIGHT("Top Right"),
        BOTTOM_LEFT("Bottom Left"),
        BOTTOM_RIGHT("Bottom Right");

        private final String label;

        HudPosition(String label) {
            this.label = label;
        }

        public String getLabel() {
            return this.label;
        }
    }

    private static final Path CONFIG_PATH = Paths.get("config", "zsg-rooms-ui.txt");
    private static HudPosition hudPosition = loadHudPosition();

    private RoomUiPreferences() {
    }

    public static HudPosition getHudPosition() {
        return hudPosition;
    }

    public static void setHudPosition(HudPosition position) {
        if (position == null) {
            return;
        }
        hudPosition = position;
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.write(CONFIG_PATH, position.name().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    private static HudPosition loadHudPosition() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String saved = new String(Files.readAllBytes(CONFIG_PATH), StandardCharsets.UTF_8).trim();
                return HudPosition.valueOf(saved);
            }
        } catch (IOException | IllegalArgumentException ignored) {
        }
        return HudPosition.TOP_RIGHT;
    }
}
