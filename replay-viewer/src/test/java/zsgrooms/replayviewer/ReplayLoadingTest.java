// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.google.gson.JsonParser;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplayLoadingTest {
    private ReplayLoading read(String json) throws IOException {
        return new ReplayLoading(new JsonParser().parse(json).getAsJsonArray(), 30000);
    }

    @Test void longLoadsAndBackwardsSeeksUseExactIntervals() throws Exception {
        ReplayLoading loading = read("[[0,2000],[5000,25000]]");
        assertTrue(loading.at(0));
        assertFalse(loading.at(2000));
        assertTrue(loading.at(24000));
        assertFalse(loading.at(25000));
        assertTrue(loading.at(5000));
        assertFalse(loading.at(4999));
        assertFalse(loading.at(-1));
    }

    @Test void legacyMissingDataDoesNotImplyLoading() throws Exception {
        assertFalse(new ReplayLoading(null, 30000).at(1000));
        assertFalse(read("[]").at(1000));
    }

    @Test void rejectsInvalidAndOverlappingIntervals() {
        for (String value : new String[]{"[[1,4],[3,5]]", "[[-1,2]]", "[[3,2]]", "[[0,30001]]",
                "[[0.5,2]]", "[[0,2,3]]", "[[0,999999999999]]", "[[\"0\",2]]"}) {
            assertThrows(IOException.class, () -> read(value), value);
        }
    }
}
