package zsgrooms.modid.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RoomGameRulesScreenTest {
    @Test
    public void wideScreensUseTwoReadableColumns() {
        assertTrue(RoomGameRulesScreen.useTwoColumns(700, 300));
    }

    @Test
    public void narrowScreensStackRuleGroups() {
        assertFalse(RoomGameRulesScreen.useTwoColumns(420, 300));
    }

    @Test
    public void veryShortScreensPreferColumnsToAvoidVerticalOverflow() {
        assertTrue(RoomGameRulesScreen.useTwoColumns(420, 180));
    }

    @Test
    public void performanceOverridesPreserveStandardPresetSelection() {
        assertEquals(
                RoomRulePreset.STANDARD_ZSG_ROOMS,
                RoomGameRulesScreen.presetAfterRuleToggle(
                        RoomRulePreset.STANDARD_ZSG_ROOMS, true));
        assertEquals(
                RoomRulePreset.STANDARD_ZSG_VANILLA_BARTERS,
                RoomGameRulesScreen.presetAfterRuleToggle(
                        RoomRulePreset.STANDARD_ZSG_VANILLA_BARTERS, true));
    }

    @Test
    public void gameplayRuleChangesStillSelectCustomPreset() {
        assertEquals(
                RoomRulePreset.CUSTOM,
                RoomGameRulesScreen.presetAfterRuleToggle(
                        RoomRulePreset.STANDARD_ZSG_ROOMS, false));
    }

    @Test
    public void verifiablePresetDoesNotAllowPerformanceOverrides() {
        assertEquals(
                RoomRulePreset.CUSTOM,
                RoomGameRulesScreen.presetAfterRuleToggle(
                        RoomRulePreset.REGULAR_VERIFIABLE_ZSG, true));
    }
}
