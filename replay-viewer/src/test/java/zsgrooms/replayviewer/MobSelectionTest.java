// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MobSelectionTest {
    private static final Identifier BLAZE = new Identifier("minecraft:blaze");

    @Test void revisionOnlyChangesWhenSelectionDoes() {
        MobSelection selection = new MobSelection();
        int initial = selection.revision();
        selection.clear();
        selection.set(BLAZE, false);
        assertEquals(initial, selection.revision());
        selection.set(BLAZE, true);
        assertEquals(initial + 1, selection.revision());
        selection.set(BLAZE, true);
        assertEquals(initial + 1, selection.revision());
        selection.clear();
        assertEquals(initial + 2, selection.revision());
    }

    @Test void trailSelectionsAndHighlightsDoNotShareState() {
        MobSelection trails = new MobSelection();
        MobHighlights highlights = new MobHighlights();
        trails.set(BLAZE, true);
        assertFalse(highlights.active());
        MobSelection restored = new MobSelection();
        restored.copyFrom(trails);
        trails.clear();
        assertTrue(restored.active());
        restored.copyFrom(restored);
        assertTrue(restored.selected(BLAZE));
        restored.enabled = false;
        assertTrue(restored.selected(BLAZE));
        assertFalse(restored.active());
    }
}
