package zsgrooms.modid.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class LoadingProgressPositionTest {
    @TempDir Path directory;

    @Test void centerPreservesVanillaCoordinates() {
        assertEquals(320, LoadingProgressPosition.CENTER.mapX(640, 90));
        assertEquals(210, LoadingProgressPosition.CENTER.mapY(360, 90));
        assertEquals(146, LoadingProgressPosition.CENTER.textY(360, 90));
    }

    @Test void cornersKeepMapAndTextInsideSmallAndLargeViewports() {
        for (int width : new int[]{320, 640, 960}) {
            for (int height : new int[]{180, 240, 360, 540}) {
                for (int size : new int[]{90, 91, 94}) {
                    for (LoadingProgressPosition position : LoadingProgressPosition.values()) {
                        if (position == LoadingProgressPosition.CENTER) continue;
                        int left = position.mapX(width, size) - size / 2;
                        int top = position.mapY(height, size) - size / 2;
                        assertTrue(left >= 12 && left + size <= width - 12);
                        assertTrue(position.textY(height, size) >= 12);
                        assertTrue(position.textY(height, size) + 9 < top);
                        assertTrue(top + size <= height - 12);
                        assertEquals(position.name().endsWith("LEFT") ? 12 : width - size - 12, left);
                        assertEquals(position.name().startsWith("TOP") ? 31 : height - size - 12, top);
                    }
                }
            }
        }
    }

    @Test void preferencesLoadPresetsAndFallBackToCenter() throws Exception {
        Path path = directory.resolve("position.txt");
        assertEquals(LoadingProgressPosition.CENTER, RoomUiPreferences.loadLoadingProgressPosition(path));
        for (LoadingProgressPosition position : LoadingProgressPosition.values()) {
            Files.write(path, (position.name() + "\n").getBytes(StandardCharsets.UTF_8));
            assertEquals(position, RoomUiPreferences.loadLoadingProgressPosition(path));
        }
        Files.write(path, "INVALID".getBytes(StandardCharsets.UTF_8));
        assertEquals(LoadingProgressPosition.CENTER, RoomUiPreferences.loadLoadingProgressPosition(path));
    }
}
