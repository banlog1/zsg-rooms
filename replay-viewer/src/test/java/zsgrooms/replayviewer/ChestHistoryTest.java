// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class ChestHistoryTest {
    private ChestOpening opening(int world, String dimension, long first, long second) {
        return new ChestOpening(10, 1, world, 1, dimension, first, second, 5, 6);
    }
    private void contents(ChestHistory<String> history, int slots, int time) throws IOException {
        ArrayList<String> items = new ArrayList<>(Collections.nCopies(slots + 36, "empty"));
        items.set(0, "iron"); history.inventory(1, items, time);
    }

    @Test void seeksNeverRevealFutureContentsAndSnapshotsDoNotMutate() throws Exception {
        ChestHistory<String> history = new ChestHistory<>();
        history.open(opening(0, "overworld", 0, 0), 10);
        contents(history, 27, 20);
        history.slot(1, 0, "empty", 30);
        assertNull(history.at(0, "overworld", 0, 9));
        assertNull(history.at(0, "overworld", 0, 15).items);
        assertEquals("iron", history.at(0, "overworld", 0, 25).items.get(0));
        assertEquals("empty", history.at(0, "overworld", 0, 40).items.get(0));
        assertEquals("iron", history.at(0, "overworld", 0, 20).items.get(0));
    }

    @Test void doubleChestAliasesShareContentsButDimensionsAndResetsDoNot() throws Exception {
        ChestHistory<String> history = new ChestHistory<>();
        long second = 1L << 38;
        history.open(opening(0, "overworld", 0, second), 10);
        contents(history, 54, 20);
        assertSame(history.at(0, "overworld", 0, 30), history.at(0, "overworld", second, 30));
        assertNull(history.at(0, "nether", 0, 30));
        assertNull(history.at(1, "overworld", 0, 30));
        history.close();
        assertEquals("iron", history.at(0, "overworld", 0, 100).items.get(0));
    }

    @Test void reusedWindowIdsAndPlayerSlotsCannotChangeAnOldChest() throws Exception {
        ChestHistory<String> history = new ChestHistory<>();
        history.open(opening(0, "overworld", 0, 0), 10); contents(history, 27, 20);
        history.slot(-1, -1, "cursor", 21); history.slot(1, 27, "player", 22); history.slot(0, 0, "inventory", 23);
        history.open(null, 30); history.slot(1, 0, "wrong chest", 40);
        assertEquals("iron", history.at(0, "overworld", 0, 50).items.get(0));
        history.open(opening(0, "overworld", 0, 0), 60);
        history.slot(1, 0, "no baseline", 61);
        assertNull(history.at(0, "overworld", 0, 61).items);
    }

    @Test void breakingEitherHalfOrReplacingChunkInvalidatesUntilReopened() throws Exception {
        ChestHistory<String> history = new ChestHistory<>();
        long second = 1L << 38;
        history.open(opening(0, "overworld", 0, second), 10); contents(history, 54, 20);
        history.block(0, "overworld", second, 6, 25);
        assertNotNull(history.at(0, "overworld", 0, 25).items);
        history.block(0, "overworld", second, 0, 30);
        assertNull(history.at(0, "overworld", 0, 30).items);
        history.slot(1, 0, "stale", 31);
        assertNull(history.at(0, "overworld", second, 31).items);
        history.open(opening(0, "overworld", 0, 0), 40); contents(history, 27, 50);
        history.chunk(0, "overworld", 1, 1, 55);
        assertNotNull(history.at(0, "overworld", 0, 55).items);
        history.chunk(0, "overworld", 0, 0, 60);
        assertNull(history.at(0, "overworld", 0, 60).items);
        assertEquals("iron", history.at(0, "overworld", 0, 20).items.get(0));
    }

    @Test void metadataIsOptionalAndRejectsInvalidPositionsAndBounds() throws Exception {
        assertTrue(ChestOpening.read(null, 100).isEmpty());
        String valid = "[{\"time\":10,\"opening\":1,\"world\":0,\"syncId\":1,\"dimension\":\"minecraft:overworld\","
                + "\"first\":0,\"second\":274877906944,\"stateFirst\":5,\"stateSecond\":6}]";
        assertEquals(54, ChestOpening.read(new JsonParser().parse(valid).getAsJsonArray(), 100).get(0).slots());
        assertThrows(IOException.class, () -> ChestOpening.read(new JsonParser().parse(valid).getAsJsonArray(), 9));
        assertThrows(IOException.class, () -> ChestOpening.read(new JsonParser().parse(valid.replace("274877906944", "1")).getAsJsonArray(), 100));
        assertThrows(IOException.class, () -> ChestOpening.read(new JsonParser().parse(valid.replace("\"syncId\":1", "\"syncId\":200")).getAsJsonArray(), 100));
    }
}
