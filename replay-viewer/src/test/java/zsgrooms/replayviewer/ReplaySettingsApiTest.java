// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class ReplaySettingsApiTest {
    @Test void originalReflectionFieldsKeepTheirTypes() throws Exception {
        for (String name : new String[]{"quick", "fastSeeking", "sounds", "inventory", "pigs", "dragon", "piglinTrail"}) {
            assertEquals(CheckboxWidget.class, AnalysisScreen.class.getDeclaredField(name).getType());
        }
        assertEquals(ButtonWidget.class, AnalysisScreen.class.getDeclaredField("highlights").getType());
        assertEquals(ButtonWidget.class, AnalysisScreen.class.getDeclaredField("pathEditor").getType());
    }

    @Test void legacyAndEightArgumentConstructorsRemainAvailable() throws Exception {
        assertNotNull(AnalysisScreen.class.getDeclaredConstructor(ReplayAnalysis.class, FollowDetailOptions.class, PlaybackMode.class));
        assertNotNull(AnalysisScreen.class.getDeclaredConstructor(ReplayAnalysis.class, FollowDetailOptions.class, PlaybackMode.class,
                BooleanSupplier.class, BooleanSupplier.class, Consumer.class, Consumer.class, Runnable.class));
        assertEquals(void.class, AnalysisScreen.class.getDeclaredMethod("openDisplayTab").getReturnType());
    }
}
