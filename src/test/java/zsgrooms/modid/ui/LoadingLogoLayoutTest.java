package zsgrooms.modid.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class LoadingLogoLayoutTest {
    @TempDir Path directory;

    @Test void progressIsClampedAndRevealsLogoWithoutGoingBackwards() {
        int previous = 0;
        for (int percent = -1; percent <= 101; percent++) {
            int filled = LoadingLogoLayout.filledHeight(percent, 74);
            assertEquals(Math.max(0, Math.min(100, percent)) * 74 / 100, filled);
            assertTrue(filled >= previous);
            previous = filled;
        }
    }

    @Test void logoFitsInsideChunkMapForEveryPreset() {
        for (int mapSize : new int[]{46, 82, 90}) {
            for (LoadingProgressPosition position : LoadingProgressPosition.values()) {
                int size = LoadingLogoLayout.logoSize(mapSize);
                int x = position.mapX(320, mapSize);
                int y = position.mapY(240, mapSize);
                assertEquals(4, (mapSize - size) / 2);
                assertTrue(x - size / 2 >= 0 && x + size / 2 <= 320);
                assertTrue(y - size / 2 >= 0 && y + size / 2 <= 240);
            }
        }
    }

    @Test void indicatorPreferenceDefaultsToZsgAndLoadsEitherMode() throws Exception {
        Path path = directory.resolve("indicator.txt");
        assertEquals(LoadingIndicatorStyle.ZSG, RoomUiPreferences.loadLoadingIndicatorStyle(path));
        for (LoadingIndicatorStyle style : LoadingIndicatorStyle.values()) {
            Files.write(path, (style.name() + "\n").getBytes(StandardCharsets.UTF_8));
            assertEquals(style, RoomUiPreferences.loadLoadingIndicatorStyle(path));
        }
        Files.write(path, "invalid".getBytes(StandardCharsets.UTF_8));
        assertEquals(LoadingIndicatorStyle.ZSG, RoomUiPreferences.loadLoadingIndicatorStyle(path));
    }
}
