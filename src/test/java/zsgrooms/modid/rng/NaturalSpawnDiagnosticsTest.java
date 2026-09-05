package zsgrooms.modid.rng;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NaturalSpawnDiagnosticsTest {
    @Test
    public void capSummaryCountsEveryCheckButLimitsRepeatedReports() {
        NaturalSpawnDiagnostics.CapWindow window = new NaturalSpawnDiagnostics.CapWindow();
        assertTrue(window.record(500L, false));
        assertEquals(1, window.blocked);
        assertEquals(500L, window.sinceTick);
        window.clear(500L);

        for (long tick = 501L; tick < 600L; tick++) {
            assertFalse(window.record(tick, false));
        }
        assertTrue(window.record(600L, false));
        assertEquals(100, window.blocked);
        assertEquals(0, window.admitted);
    }

    @Test
    public void capTransitionsReportImmediatelyAndKeepWindowTotals() {
        NaturalSpawnDiagnostics.CapWindow window = new NaturalSpawnDiagnostics.CapWindow();
        assertTrue(window.record(10L, true));
        window.clear(10L);
        assertFalse(window.record(11L, true));
        assertTrue(window.record(12L, false));
        assertEquals(1, window.admitted);
        assertEquals(1, window.blocked);
        window.clear(12L);
        assertTrue(window.record(13L, true));
        assertEquals(1, window.admitted);
        assertEquals(0, window.blocked);
    }

    @Test
    public void summariesClearIntervalCountsButKeepLastObservedCycle() {
        NaturalSpawnDiagnostics.CapWindow window = new NaturalSpawnDiagnostics.CapWindow();
        window.initialSolid = 9;
        window.initialBelowWorld = 2;
        window.lastCycle = 79L;
        window.record(200L, true);
        window.clear(200L);

        assertEquals(0, window.initialSolid);
        assertEquals(0, window.initialBelowWorld);
        assertEquals(0, window.admitted);
        assertEquals(79L, window.lastCycle);
        assertEquals(200L, window.sinceTick);
    }

    @Test
    public void clockMovingBackwardsDoesNotSuppressReports() {
        NaturalSpawnDiagnostics.CapWindow window = new NaturalSpawnDiagnostics.CapWindow();
        window.record(200L, false);
        window.clear(200L);
        assertTrue(window.record(100L, false));
    }
}
