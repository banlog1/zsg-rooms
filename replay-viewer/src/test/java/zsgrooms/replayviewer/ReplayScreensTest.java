// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplayScreensTest {
    private ReplayScreens read(String value) throws IOException {
        return new ReplayScreens(new JsonParser().parse(value).getAsJsonArray(), 10000);
    }

    @Test void exactBoundariesAndSeekingDistinguishInventoryFromCrafting() throws Exception {
        ReplayScreens screens = read("[[100,200,1],[200,500,2],[700,900,1]]");
        assertEquals(0, screens.at(99));
        assertEquals(1, screens.at(100));
        assertEquals(2, screens.at(200));
        assertEquals(0, screens.at(500));
        assertEquals(1, screens.at(800));
        assertEquals(0, screens.at(900));
        assertEquals(2, screens.at(400));
        assertEquals(1, screens.at(150));
        assertEquals(0, screens.at(-1));
    }

    @Test void legacyRecordingsDoNotGuessTheOpenScreen() throws Exception {
        assertEquals(0, new ReplayScreens(null, 10000).at(500));
        assertEquals(0, read("[]").at(500));
    }

    @Test void rejectsMalformedOverlappingOrUnknownScreenIntervals() {
        for (String value : new String[]{"[[1,4,1],[3,5,2]]", "[[-1,2,1]]", "[[3,2,1]]", "[[0,10001,1]]",
                "[[0.5,2,1]]", "[[0,2]]", "[[0,2,3]]", "[[0,2,0]]", "[[0,999999999999,1]]", "[[\"0\",2,1]]"}) {
            assertThrows(IOException.class, () -> read(value), value);
        }
        JsonArray excessive = new JsonArray();
        for (int i = 0; i < 4097; i++) excessive.add(new JsonArray());
        assertThrows(IOException.class, () -> new ReplayScreens(excessive, 10000));
    }
}
