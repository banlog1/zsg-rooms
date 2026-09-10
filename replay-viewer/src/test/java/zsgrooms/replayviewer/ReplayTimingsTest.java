// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.google.gson.JsonParser;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplayTimingsTest {
    private ReplayTimings timing(String json) throws IOException {
        return new ReplayTimings(new JsonParser().parse(json).getAsJsonArray(), 10000);
    }

    @Test void legacyFilesAndUnavailableCaptureDoNotInventTimesOrPauseState() throws Exception {
        assertFalse(new ReplayTimings(null, 10000).at(500).known);
        assertFalse(timing("[[0,0,2,-1,-1]]").at(500).known);
        assertEquals("--:--", ReplayTimings.clock(-1));
        assertEquals("01:02.345", ReplayTimings.clock(62345));
    }

    @Test void interpolatesRecordedValuesAndSupportsBackwardSeeking() throws Exception {
        ReplayTimings data = timing("[[0,0,0,100,50],[1000,0,0,1100,950],[2000,0,0,2100,1950]]");
        assertEquals(1600, data.at(1500).rta);
        assertEquals(500, data.at(500).igt);
        assertFalse(data.at(500).paused);
        assertFalse(data.at(-1).known);
    }

    @Test void recordedPauseFreezesIgtButCanContinueRta() throws Exception {
        ReplayTimings data = timing("[[0,0,1,100,50],[1000,0,1,1100,50],[1100,0,0,1200,50]]");
        assertTrue(data.at(500).paused);
        assertEquals(50, data.at(500).igt);
        assertEquals(600, data.at(500).rta);
        assertFalse(data.at(1100).paused);
    }

    @Test void neverInterpolatesAcrossResetsStateChangesOrUnavailableGaps() throws Exception {
        ReplayTimings data = timing("[[0,0,0,900,800],[1000,1,0,100,50],[1100,1,2,-1,-1],[2000,1,0,300,100]]");
        assertEquals(900, data.at(500).rta);
        assertEquals(100, data.at(1050).rta);
        assertFalse(data.at(1500).known);
        assertFalse(data.at(3501).known);
    }

    @Test void missingTimerModStillAllowsRecordedPauseIndicator() throws Exception {
        ReplayTimings.Value value = timing("[[0,0,1,-1,-1],[1000,0,1,-1,-1]]").at(500);
        assertTrue(value.known);
        assertTrue(value.paused);
        assertEquals(-1, value.igt);
    }

    @Test void rejectsInvalidBoundsOrderAndSampleShape() {
        assertThrows(IOException.class, () -> timing("[[0,0,0,0,0],[0,0,0,1,1]]"));
        assertThrows(IOException.class, () -> timing("[[10001,0,0,0,0]]"));
        assertThrows(IOException.class, () -> timing("[[0,0,3,0,0]]"));
        assertThrows(IOException.class, () -> timing("[[0,0,0,-2,0]]"));
        assertThrows(IOException.class, () -> timing("[[0,0,0,0]]"));
    }
}
