package zsgrooms.modid;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimerCategoryOverrideTest {
    private static final class Timer implements TimerCategoryOverride.Access {
        Object instance = new Object();
        Object selected = "original";
        int writes;
        public Object timer() { return instance; }
        public Object category(Object timer) { assertSame(instance, timer); return selected; }
        public void category(Object timer, Object value) { assertSame(instance, timer); selected = value; writes++; }
    }

    @Test
    void aaOrdinaryAndLeaveRestoreCategoriesWithoutRepeatedWrites() throws Exception {
        Timer timer = new Timer();
        TimerCategoryOverride override = new TimerCategoryOverride();
        override.sync(timer, null);
        assertEquals(0, timer.writes);
        override.sync(timer, "AA");
        override.sync(timer, "AA");
        assertEquals(1, timer.writes);
        override.sync(timer, "Any%");
        assertEquals("Any%", timer.selected);
        override.sync(timer, "AA");
        override.sync(timer, null);
        assertEquals("original", timer.selected);
        override.sync(timer, null);
        assertEquals(4, timer.writes);
    }

    @Test
    void newWorldTimersAndManualChangesAreCorrectedWhileOutsideTimersAreUntouched() throws Exception {
        Timer timer = new Timer();
        TimerCategoryOverride override = new TimerCategoryOverride();
        override.sync(timer, "AA");
        timer.instance = new Object();
        timer.selected = "Any%";
        override.sync(timer, "AA");
        assertEquals("AA", timer.selected);
        timer.selected = "manual-change";
        override.sync(timer, "AA");
        assertEquals("AA", timer.selected);
        override.sync(timer, null);
        assertEquals("Any%", timer.selected);
        override.sync(timer, "AA");
        timer.instance = new Object();
        timer.selected = "unrelated-world";
        override.sync(timer, null);
        assertEquals("unrelated-world", timer.selected);
    }
}
