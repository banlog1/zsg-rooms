// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplayTimeInputTest {
    @Test void acceptsSecondsAndClockTimesIncludingMilliseconds() {
        assertEquals(0, ReplayTimeInput.parse("0"));
        assertEquals(90500, ReplayTimeInput.parse(" 90.5 "));
        assertEquals(90500, ReplayTimeInput.parse("1:30.500"));
        assertEquals(3723250, ReplayTimeInput.parse("1:02:03.25"));
        assertEquals(7200000, ReplayTimeInput.parse("120:00"));
        assertEquals(Integer.MAX_VALUE, ReplayTimeInput.parse("2147483.647"));
    }

    @Test void rejectsMalformedNegativeAndOverflowingTimes() {
        assertEquals(-1, ReplayTimeInput.parse(null));
        for (String value : new String[] {"", " ", "-1", "+2", "NaN", "Infinity", "1e3", "1:", ":01",
                "1:60", "1:60:00", "1:02:60", "1:2:3:4", "1.2345", "1: 02", "2147483.648",
                "999999999:59:59", "999999999999999999999", "0.0001"}) {
            assertEquals(-1, ReplayTimeInput.parse(value), value);
        }
    }

    @Test void formatsLongRecordingsWithoutLosingHours() {
        assertEquals("0:00", ReplayTimeInput.format(0));
        assertEquals("59:59", ReplayTimeInput.format(3599000));
        assertEquals("1:00:00", ReplayTimeInput.format(3600000));
        assertEquals("12:34:56", ReplayTimeInput.format(45296000));
        assertEquals(45296000, ReplayTimeInput.parse(ReplayTimeInput.format(45296000)));
    }
}
