// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimelineCursorTest {
    @Test void progressArrowAndStemAreSymmetricAtEveryTrackPosition() {
        for (int width : new int[] {304, 411, 624, 1264}) {
            for (int time = 0; time <= 10000; time += 100) {
                int center = TimelineCursor.center(width, 10000, 1, 0, time);
                TimelineCursor.draw(center, 16, (x, y, w, h, color) -> {
                    assertEquals(center * 2, x * 2 + w - 1);
                    assertTrue(x >= 0 && x + w <= width);
                    assertTrue(y >= 0 && y + h <= 16);
                });
            }
        }
    }

    @Test void mapsToNativeTrackIncludingZoomOffsetsAndEnds() {
        assertEquals(4, TimelineCursor.center(624, 10000, 1, 0, -1));
        assertEquals(312, TimelineCursor.center(624, 10000, 1, 0, 5000));
        assertEquals(620, TimelineCursor.center(624, 10000, 1, 0, 10001));
        assertEquals(312, TimelineCursor.center(624, 10000, 0.5, 2500, 5000));
        assertEquals(4, TimelineCursor.center(624, 0, 1, 0, 0));
    }
}
