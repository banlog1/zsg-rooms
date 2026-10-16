// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import net.minecraft.client.gui.widget.CheckboxWidget;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ReplaySettingsCheckboxTest {
    @Test void nativeCheckboxWritesThroughAndRetainsLookupLabel() {
        AtomicBoolean value = new AtomicBoolean();
        ReplaySettingsCheckbox checkbox = new ReplaySettingsCheckbox(20, 72, 280, "Player sounds (experimental)", value::get, value::set);
        assertTrue(checkbox instanceof CheckboxWidget);
        assertEquals("Player sounds (experimental)", checkbox.getMessage().getString());
        assertFalse(checkbox.isChecked());
        checkbox.onPress();
        assertTrue(value.get());
        assertTrue(checkbox.isChecked());
        checkbox.onPress();
        assertFalse(value.get());
        assertFalse(checkbox.isChecked());
    }

    @Test void deferredOrRejectedSetterDoesNotOptimisticallyChangeCheckmark() {
        AtomicBoolean value = new AtomicBoolean();
        AtomicBoolean requested = new AtomicBoolean();
        ReplaySettingsCheckbox checkbox = new ReplaySettingsCheckbox(20, 72, 280, "Quick Mode (experimental)", value::get, requested::set);
        checkbox.onPress();
        assertTrue(requested.get());
        assertFalse(checkbox.isChecked());
        value.set(true);
        checkbox.refresh();
        assertTrue(checkbox.isChecked());
        checkbox.onPress();
        assertFalse(requested.get());
        assertTrue(checkbox.isChecked());
    }

    @Test void externalChangesRefreshWithoutCallingSetterOrReactivatingHiddenControls() {
        AtomicBoolean value = new AtomicBoolean();
        AtomicInteger writes = new AtomicInteger();
        ReplaySettingsCheckbox checkbox = new ReplaySettingsCheckbox(20, 72, 280, "Auto-hide controls", value::get, ignored -> writes.incrementAndGet());
        checkbox.visible = false;
        checkbox.active = false;
        value.set(true);
        checkbox.refresh();
        assertTrue(checkbox.isChecked());
        assertEquals(0, writes.get());
        assertFalse(checkbox.visible);
        assertFalse(checkbox.active);
        assertFalse(checkbox.changeFocus(true));
    }
}
