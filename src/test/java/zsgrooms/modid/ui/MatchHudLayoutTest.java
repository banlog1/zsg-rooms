package zsgrooms.modid.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MatchHudLayoutTest {
    @Test
    public void enlargedHudFitsSmallViewports() {
        MatchHudPreferences settings = new MatchHudPreferences();
        for (int width : new int[]{240, 320, 427, 960}) {
            for (int height : new int[]{120, 180, 240, 540}) {
                int panel = MatchHud.panelHeight(settings, 4);
                float scale = MatchHud.fitScale(150, width - 16, height - 42, panel);
                assertTrue(166 * scale <= width - 16 + 0.001F);
                assertTrue(panel * scale <= height - 42 + 0.001F);
            }
        }
    }

    @Test
    public void removingHeaderReclaimsItsSpaceWithoutChangingRowHeight() {
        MatchHudPreferences settings = new MatchHudPreferences();
        int withHeader = MatchHud.panelHeight(settings, 2);
        settings.header = false;
        assertEquals(withHeader - 25, MatchHud.panelHeight(settings, 2));
        assertEquals(22, MatchHud.panelHeight(settings, 3) - MatchHud.panelHeight(settings, 2));
    }

    @Test
    public void settingsPagesLeaveSpaceForFooterAtDifferentGuiScales() {
        for (int height : new int[]{120, 180, 240, 360, 540}) {
            int lastBottom = 58 + (MatchHudSettingsScreen.capacity(height) - 1) * 24 + 20;
            assertTrue(lastBottom <= height - 28);
        }
        assertFalse(MatchHudSettingsScreen.hasPreview(427));
        assertTrue(MatchHudSettingsScreen.hasPreview(500));
    }
}
