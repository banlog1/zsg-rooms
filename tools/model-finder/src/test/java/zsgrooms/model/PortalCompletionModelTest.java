package zsgrooms.model;

import com.seedfinding.mccore.block.Block;
import com.seedfinding.mccore.block.Blocks;
import com.seedfinding.mccore.util.data.Pair;
import com.seedfinding.mccore.util.pos.BPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static zsgrooms.model.PortalCompletionModel.*;

class PortalCompletionModelTest {
    @Test
    void realSeedLayoutSupportsBothObsidianAndLavaCompletion() {
        long seed = -6539493275060482115L;
        Result direct = evaluate(seed, 1, 7, 2, 0, 0, 1, 0);
        assertEquals(4, direct.status());
        assertEquals(7, direct.template());
        assertEquals(1, direct.missing());
        Result cast = evaluate(seed, 1, 7, 0, 27, 0, 1, 0);
        assertEquals(3, cast.status()); // Native water check still required.
        assertEquals(1, cast.cast());
        assertTrue(cast.lava() >= cast.cast());
        assertEquals(cast, evaluate(seed, 1, 7, 0, 27, 0, 1, 0));
        assertEquals(2, evaluate(seed, 1, 7, 0, 26, 0, 1, 0).status());
        assertEquals(2, evaluate(seed, 1, 7, 0, 27, 1, 0, 0).status());
    }

    @Test
    void materialsAndIgnitionAreBudgetedSeparately() {
        assertTrue(resources(2, 2, 0, 0, 0, 1, 0));
        assertTrue(resources(0, 0, 0, 0, 0, 0, 5));
        assertFalse(resources(2, 2, 0, 0, 0, 0, 0));
        assertTrue(resources(5, 2, 3, 27, 0, 1, 0));
        assertFalse(resources(5, 2, 2, 27, 0, 1, 0));
        assertFalse(resources(5, 2, 20, 26, 0, 1, 0));
        assertFalse(resources(5, 2, 20, 27, 1, 0, 0));
        assertTrue(resources(5, 2, 20, 36, 1, 0, 0));
        assertTrue(resources(2, 2, 0, 9, 1, 0, 0));
    }

    @Test
    void ignitionRequiresReusableFlintAndSteelOrAtLeastFiveCharges() {
        for (int charges = 0; charges < 5; charges++) {
            assertFalse(resources(2, 2, 0, 0, 0, 0, charges));
            assertFalse(resources(2, 2, 0, 8, 1, 0, charges));
            assertFalse(resources(2, 2, 0, 9, 0, 0, charges));
            assertTrue(resources(2, 2, 0, 0, 0, 1, charges));
            assertTrue(resources(2, 2, 0, 9, 1, 0, charges));
            // A small charge stack must not let bucket iron also pay for ignition.
            assertFalse(resources(5, 2, 3, 27, 1, 0, charges));
            assertFalse(resources(5, 2, 3, 35, 1, 0, charges));
            assertTrue(resources(5, 2, 3, 36, 1, 0, charges));
        }
        for (int charges : new int[]{5, 6}) {
            assertTrue(resources(2, 2, 0, 0, 0, 0, charges));
            assertTrue(resources(5, 2, 3, 27, 0, 0, charges));
            assertFalse(resources(5, 2, 3, 26, 0, 0, charges));
            assertFalse(resources(5, 2, 2, 27, 0, 0, charges));
        }
    }

    @Test
    void cryingObsidianNeverCountsAndCornersAreOptional() {
        BPos start = new BPos(-7, 65, -12);
        for (boolean alongX : new boolean[]{false, true}) {
            Map<BPos, Block> blocks = frame(start, alongX, 2, 3);
            assertEquals(0, missingBlocks(blocks, start, alongX, 2, 3));
            BPos side = start.add(0, 1, 0);
            blocks.remove(side);
            assertEquals(1, missingBlocks(blocks, start, alongX, 2, 3));
            blocks.put(side, Blocks.CRYING_OBSIDIAN);
            assertEquals(-1, missingBlocks(blocks, start, alongX, 2, 3));
            blocks.remove(side);
            blocks.put(start, Blocks.CRYING_OBSIDIAN); // Corner does not matter.
            assertEquals(1, missingBlocks(blocks, start, alongX, 2, 3));
            blocks.put(start.add(alongX ? 1 : 0, 1, alongX ? 0 : 1), Blocks.OBSIDIAN);
            assertEquals(-1, missingBlocks(blocks, start, alongX, 2, 3));
        }
    }

    @Test
    void searchKeepsUsableFramesAndDoesNotInventUnrelatedPortals() {
        BPos start = new BPos(100, 66, 80);
        Map<BPos, Block> blocks = frame(start, true, 3, 3);
        blocks.remove(start.add(0, 1, 0));
        ArrayList<Pair<Block, BPos>> original = new ArrayList<>();
        blocks.forEach((p, b) -> original.add(new Pair<>(b, p)));
        Frame result = bestFrame(original, blocks);
        assertNotNull(result);
        assertEquals(1, result.missing());
        assertEquals(result, bestFrame(original, blocks));
        assertEquals(-1, missingBlocks(Map.of(), start, true, 2, 3));
        assertNull(bestFrame(java.util.List.of(), Map.of()));
    }

    @Test
    void allTemplateSourceTablesAreBounded() {
        int[] sizes = {0,26,2,3,1,0,21,26,0,19,10,19,30};
        assertEquals(13, PortalLavaTemplates.COORDINATES.length);
        for (int i = 0; i < sizes.length; i++) {
            assertEquals(sizes[i] * 3, PortalLavaTemplates.COORDINATES[i].length);
            assertEquals(i + 1, templateId(i < 10 ? "portal_" + (i + 1) : "giant_portal_" + (i - 9)));
        }
    }

    private static Map<BPos, Block> frame(BPos start, boolean alongX, int width, int height) {
        Map<BPos, Block> result = new HashMap<>();
        for (int u = 0; u <= width + 1; u++) for (int y = 0; y <= height + 1; y++) {
            boolean side = u == 0 || u == width + 1, cap = y == 0 || y == height + 1;
            if (side != cap) result.put(start.add(alongX ? u : 0, y, alongX ? 0 : u), Blocks.OBSIDIAN);
        }
        return result;
    }
}
