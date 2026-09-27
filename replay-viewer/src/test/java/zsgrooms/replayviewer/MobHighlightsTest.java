// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MobHighlightsTest {
    private static final Identifier PIGLIN = new Identifier("minecraft", "piglin");
    private static final Identifier BLAZE = new Identifier("minecraft", "blaze");

    @Test void startsEmptyAndSelectsTypesIndependently() {
        MobHighlights options = new MobHighlights();
        assertFalse(options.active());
        options.set(PIGLIN, true);
        options.set(PIGLIN, true);
        assertEquals(1, options.size());
        assertTrue(options.active());
        assertFalse(options.selected(BLAZE));
        options.set(BLAZE, true);
        options.set(PIGLIN, false);
        assertTrue(options.selected(BLAZE));
        assertFalse(options.selected(PIGLIN));
    }

    @Test void masterSwitchRetainsSelectionAndClearDisablesAll() {
        MobHighlights options = new MobHighlights();
        options.set(PIGLIN, true);
        options.enabled = false;
        assertFalse(options.active());
        assertTrue(options.selected(PIGLIN));
        options.enabled = true;
        assertTrue(options.active());
        options.clear();
        assertFalse(options.active());
        assertEquals(0, options.size());
    }

    @Test void recordingSwitchCopiesSettingsWithoutSharingMutableSelections() {
        MobHighlights source = new MobHighlights();
        source.enabled = false;
        source.color = TrailStyle.Color.VIOLET;
        source.set(PIGLIN, true);
        MobHighlights copy = new MobHighlights();
        copy.set(BLAZE, true);
        copy.copyFrom(source);
        assertFalse(copy.enabled);
        assertEquals(TrailStyle.Color.VIOLET, copy.color);
        assertTrue(copy.selected(PIGLIN));
        assertFalse(copy.selected(BLAZE));
        source.clear();
        assertTrue(copy.selected(PIGLIN));
        copy.copyFrom(copy);
        assertTrue(copy.selected(PIGLIN));
    }
}
