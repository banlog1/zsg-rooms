// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class PlayerHudTrackTest {
    @Test void seekSelectsPastStateWithoutLeakingAcrossResetsOrUnavailableIntervals() throws Exception {
        PlayerHudTrack track = read(new int[][]{{0, 0, 1, 1}, {200, 0, -1, 0}, {400, 1, 1, 1}, {800, 1, 1, 1}});
        assertEquals(0, track.at(100, 0, 1, 0).time);
        assertNull(track.at(300, 0, 1, 0));
        assertNull(track.at(401, 0, 1, 0));
        assertNull(track.at(401, 1, 2, 0));
        assertNull(track.at(401, 1, 1, 401));
        assertEquals(400, track.at(700, 1, 1, 400).time);
        assertEquals(800, track.at(1000, 1, 1, 400).time);
        assertEquals(0, track.at(1, 0, 1, 0).time);
        assertNull(track.at(2400, 1, 1, 400));
    }
    @Test void malformedAndOversizedTracksAreRejected() {
        assertThrows(IOException.class, () -> read(new int[][]{{2,0,1,1}, {1,0,1,1}}));
        assertThrows(IOException.class, () -> read(new int[][]{{1,0,1,65537}}));
        assertThrows(IOException.class, () -> PlayerHudTrack.read(new ByteArrayInputStream(new byte[8]), 1000));
    }
    @Test void inventoryDefaultsToKeyToggleAndOptionalAlwaysModeSurvivesCopy() {
        FollowDetailOptions options = new FollowDetailOptions();
        assertFalse(options.inventoryVisible());
        options.toggleInventory();
        assertTrue(options.inventoryVisible());
        options.toggleInventory();
        assertFalse(options.inventoryVisible());
        options.alwaysInventory = true;
        options.inventoryDismissed = false;
        assertTrue(options.inventoryVisible());
        options.toggleInventory();
        assertFalse(options.inventoryVisible());
        options.toggleInventory();
        assertTrue(options.inventoryVisible());
        FollowDetailOptions copy = new FollowDetailOptions();
        copy.copyFrom(options);
        assertTrue(copy.alwaysInventory);
        assertTrue(copy.inventoryVisible());
        copy.closeInventory();
        assertFalse(copy.inventoryVisible());
        assertTrue(copy.alwaysInventory);
    }
    private static PlayerHudTrack read(int[][] rows) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        out.writeInt(0x5A485544); out.writeInt(1);
        for (int[] row : rows) {
            for (int value : row) out.writeInt(value);
            out.write(new byte[row[3]]);
        }
        return PlayerHudTrack.read(new ByteArrayInputStream(buffer.toByteArray()), 1000);
    }
}
