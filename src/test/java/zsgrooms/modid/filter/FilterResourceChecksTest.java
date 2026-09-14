package zsgrooms.modid.filter;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FilterResourceChecksTest {
    @Test
    void villageCountsOnlyMinimumGolemDrop() {
        assertTrue(FilterResourceChecks.villageIron(4, 0, 0, true));
        assertFalse(FilterResourceChecks.villageIron(3, 0, 0, true));
        assertFalse(FilterResourceChecks.villageIron(4, 0, 0, false));
        assertFalse(FilterResourceChecks.villageIron(20, 0, 0, false));
    }

    @Test
    void villageCountsNuggetsAndBlocksWithoutRoundingUp() {
        assertTrue(FilterResourceChecks.villageIron(3, 9, 0, true));
        assertFalse(FilterResourceChecks.villageIron(3, 8, 0, true));
        assertTrue(FilterResourceChecks.villageIron(0, 36, 0, true));
        assertTrue(FilterResourceChecks.villageIron(0, 0, 1, true));
        assertFalse(FilterResourceChecks.villageIron(-1, 100, 0, true));
    }

    @Test
    void shipwreckReservesResourcesForAllTools() {
        assertTrue(FilterResourceChecks.shipwreckTools(11, 0, 0));
        assertFalse(FilterResourceChecks.shipwreckTools(10, 0, 0));
        assertTrue(FilterResourceChecks.shipwreckTools(4, 3, 4));
        assertFalse(FilterResourceChecks.shipwreckTools(4, 3, 3));
        assertTrue(FilterResourceChecks.shipwreckTools(4, 7, 0));
        assertFalse(FilterResourceChecks.shipwreckTools(3, 100, 100));
    }

    @Test
    void foodRetainsUpstreamWheatCarrotWeighting() {
        assertTrue(FilterResourceChecks.shipwreckFood(30, 0));
        assertTrue(FilterResourceChecks.shipwreckFood(0, 5));
        assertTrue(FilterResourceChecks.shipwreckFood(6, 4));
        assertFalse(FilterResourceChecks.shipwreckFood(5, 4));
        assertFalse(FilterResourceChecks.shipwreckFood(-1, 20));
    }
}
