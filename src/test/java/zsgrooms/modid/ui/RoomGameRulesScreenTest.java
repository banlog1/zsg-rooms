package zsgrooms.modid.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RoomGameRulesScreenTest {

    @Test
    public void rawPresetUsesTheNewDisplayName() {
        assertEquals("Raw", RoomRulePreset.REGULAR_VERIFIABLE_ZSG.getLabel());
    }

    @Test
    public void lobbyRecognizesPresetsButRequiresExactVerifiableDefaults() {
        for (RoomRulePreset preset : RoomRulePreset.values()) {
            if (preset.isCustom()) continue;
            zsgrooms.modid.RoomRuleSettings rules = new zsgrooms.modid.RoomRuleSettings();
            rules.allowCheats = preset.allowsCheats();
            rules.rngStandardization = preset.standardizesRng();
            rules.boostedBarters = preset.boostsBarters();
            rules.minimumBastionIron = preset.guaranteesBastionIron();
            rules.removeBastionZombifiedPiglins = preset.removesBastionZombifiedPiglins();
            rules.removeNaturalStriderJockeys = preset.removesNaturalStriderJockeys();
            rules.spawnNearFilterStructure = preset.spawnsNearFilterStructure();
            rules.minimumNearbyAnimals = preset.guaranteesNearbyAnimals();
            rules.netherEntryWarmup = preset.warmsNetherEntry();
            rules.disablePauseWorldSaves = preset.disablesPauseWorldSaves();
            rules.reduceZeroCycleFlyAways = preset.reducesZeroCycleFlyAways();
            rules.sharedNetherEntry = preset.sharesNetherEntry();
            assertEquals(preset, RoomRulePreset.matching(rules));
            rules.removeNaturalStriderJockeys = !rules.removeNaturalStriderJockeys;
            rules.disablePauseWorldSaves = !rules.disablePauseWorldSaves;
            assertEquals(preset == RoomRulePreset.REGULAR_VERIFIABLE_ZSG ? RoomRulePreset.CUSTOM : preset,
                    RoomRulePreset.matching(rules));
            rules.sharedNetherEntry = !rules.sharedNetherEntry;
            assertEquals(RoomRulePreset.CUSTOM, RoomRulePreset.matching(rules));
        }
    }

    @Test
    public void wideScreensUseTwoReadableColumns() {
        assertTrue(RoomGameRulesScreen.useTwoColumns(700, 300));
    }

    @Test
    public void narrowScreensStackRuleGroups() {
        assertFalse(RoomGameRulesScreen.useTwoColumns(420, 450));
    }

    @Test
    public void shortNarrowScreensUseTabbedGroupsInsteadOfCrampedColumns() {
        assertFalse(RoomGameRulesScreen.useTwoColumns(420, 180));
        assertFalse(RoomGameRulesScreen.useTwoColumns(420, 240));
        assertFalse(RoomGameRulesScreen.useTwoColumns(304, 300));
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
