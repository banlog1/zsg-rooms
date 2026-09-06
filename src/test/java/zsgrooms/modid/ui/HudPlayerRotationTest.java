package zsgrooms.modid.ui;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class HudPlayerRotationTest {
    @Test
    public void selfIsPinnedAndOpponentChangesOnlyAtInterval() {
        assertEquals(2, index(5, 2, 2, true, 0, 0));
        assertEquals(2, index(5, 2, 2, true, 15000, 0));
        assertEquals(0, index(5, 2, 2, true, 4999, 1));
        assertEquals(1, index(5, 2, 2, true, 5000, 1));
        assertEquals(3, index(5, 2, 2, true, 10000, 1));
        assertEquals(4, index(5, 2, 2, true, 15000, 1));
        assertEquals(0, index(5, 2, 2, true, 20000, 1));
    }

    @Test
    public void fittingRoomsStayStillAndEmptyRowsAreRejected() {
        for (int row = 0; row < 3; row++) {
            assertEquals(index(3, 1, 4, true, 0, row), index(3, 1, 4, true, 15000, row));
        }
        assertEquals(-1, index(0, -1, 2, true, 0, 0));
        assertEquals(-1, index(1, 0, 2, true, 0, 1));
        assertEquals(0, index(1, 0, 2, true, 10000, 0));
    }

    @Test
    public void allPlayersAreReachableWithoutDuplicateRowsAcrossRoomSizes() {
        for (int count = 1; count <= 20; count++) {
            for (int local = -1; local < count; local++) {
                for (boolean pinned : new boolean[]{false, true}) {
                    for (int rows = 1; rows <= 4; rows++) {
                        Set<Integer> seen = new HashSet<Integer>();
                        for (int step = 0; step < count; step++) {
                            Set<Integer> displayed = new HashSet<Integer>();
                            for (int row = 0; row < Math.min(rows, count); row++) {
                                int index = index(count, local, rows, pinned, step * 5000L, row);
                                assertTrue(index >= 0 && index < count);
                                assertTrue(displayed.add(index));
                                seen.add(index);
                            }
                        }
                        assertEquals(count, seen.size());
                    }
                }
            }
        }
    }

    @Test
    public void unpinnedSingleRowIncludesSelfAndUsesConfiguredSpeed() {
        assertEquals(0, HudPlayerRotation.playerIndex(3, 1, 1, false, 1999, 2, 0));
        assertEquals(1, HudPlayerRotation.playerIndex(3, 1, 1, false, 2000, 2, 0));
        assertEquals(2, HudPlayerRotation.playerIndex(3, 1, 1, false, 4000, 2, 0));
        assertEquals(0, HudPlayerRotation.playerIndex(3, 1, 1, false, 6000, 2, 0));
    }

    private static int index(int count, int local, int rows, boolean pin, long elapsed, int row) {
        return HudPlayerRotation.playerIndex(count, local, rows, pin, elapsed, 5, row);
    }
}
