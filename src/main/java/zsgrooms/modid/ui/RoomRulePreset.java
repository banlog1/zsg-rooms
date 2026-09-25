package zsgrooms.modid.ui;

import zsgrooms.modid.RoomRuleSettings;

public enum RoomRulePreset {
    STANDARD_ZSG_ROOMS("Standard ZSG Rooms", false, true, true, true, true, true, true, true, true, true),
    STANDARD_ZSG_VANILLA_BARTERS(
            "Standard - Vanilla Barters", false, true, false, true, true, true, true, true, true, true),
    REGULAR_VERIFIABLE_ZSG(
            "Raw", false, false, false, false, false, false, false, false, false, false),
    CUSTOM("Custom", false, false, false, false, false, false, false, false, false, false);

    private final String label;
    private final boolean allowCheats;
    private final boolean rngStandardization;
    private final boolean boostedBarters;
    private final boolean minimumBastionIron;
    private final boolean removeBastionZombifiedPiglins;
    private final boolean removeNaturalStriderJockeys;
    private final boolean spawnNearFilterStructure;
    private final boolean minimumNearbyAnimals;
    private final boolean netherEntryWarmup;
    private final boolean disablePauseWorldSaves;

    RoomRulePreset(String label, boolean allowCheats, boolean rngStandardization, boolean boostedBarters,
            boolean minimumBastionIron, boolean removeBastionZombifiedPiglins,
            boolean removeNaturalStriderJockeys, boolean spawnNearFilterStructure,
            boolean minimumNearbyAnimals, boolean netherEntryWarmup, boolean disablePauseWorldSaves) {
        this.label = label;
        this.allowCheats = allowCheats;
        this.rngStandardization = rngStandardization;
        this.boostedBarters = boostedBarters;
        this.minimumBastionIron = minimumBastionIron;
        this.removeBastionZombifiedPiglins = removeBastionZombifiedPiglins;
        this.removeNaturalStriderJockeys = removeNaturalStriderJockeys;
        this.spawnNearFilterStructure = spawnNearFilterStructure;
        this.minimumNearbyAnimals = minimumNearbyAnimals;
        this.netherEntryWarmup = netherEntryWarmup;
        this.disablePauseWorldSaves = disablePauseWorldSaves;
    }

    public String getLabel() {
        return this.label;
    }

    public boolean allowsCheats() {
        return this.allowCheats;
    }

    public boolean standardizesRng() {
        return this.rngStandardization;
    }

    public boolean reducesZeroCycleFlyAways() {
        return this == STANDARD_ZSG_ROOMS || this == STANDARD_ZSG_VANILLA_BARTERS;
    }

    public boolean boostsBarters() {
        return this.boostedBarters;
    }

    public boolean guaranteesBastionIron() {
        return this.minimumBastionIron;
    }

    public boolean removesBastionZombifiedPiglins() {
        return this.removeBastionZombifiedPiglins;
    }

    public boolean removesNaturalStriderJockeys() {
        return this.removeNaturalStriderJockeys;
    }

    public boolean spawnsNearFilterStructure() {
        return this.spawnNearFilterStructure;
    }

    public boolean guaranteesNearbyAnimals() {
        return this.minimumNearbyAnimals;
    }

    public boolean warmsNetherEntry() {
        return this.netherEntryWarmup;
    }

    public boolean sharesNetherEntry() {
        return this == STANDARD_ZSG_ROOMS || this == STANDARD_ZSG_VANILLA_BARTERS;
    }

    public boolean disablesPauseWorldSaves() {
        return this.disablePauseWorldSaves;
    }

    public boolean isCustom() {
        return this == CUSTOM;
    }

    static RoomRulePreset matching(RoomRuleSettings rules) {
        for (RoomRulePreset preset : values()) {
            if (preset.isCustom()) continue;
            boolean performanceMatches = preset != REGULAR_VERIFIABLE_ZSG
                    || rules.removeNaturalStriderJockeys == preset.removesNaturalStriderJockeys()
                    && rules.disablePauseWorldSaves == preset.disablesPauseWorldSaves();
            if (performanceMatches && rules.allowCheats == preset.allowsCheats()
                    && rules.rngStandardization == preset.standardizesRng()
                    && rules.boostedBarters == preset.boostsBarters()
                    && rules.minimumBastionIron == preset.guaranteesBastionIron()
                    && rules.removeBastionZombifiedPiglins == preset.removesBastionZombifiedPiglins()
                    && rules.spawnNearFilterStructure == preset.spawnsNearFilterStructure()
                    && rules.minimumNearbyAnimals == preset.guaranteesNearbyAnimals()
                    && rules.netherEntryWarmup == preset.warmsNetherEntry()
                    && rules.reduceZeroCycleFlyAways == preset.reducesZeroCycleFlyAways()
                    && rules.sharedNetherEntry == preset.sharesNetherEntry()) return preset;
        }
        return CUSTOM;
    }


    public RoomRulePreset next() {
        RoomRulePreset[] presets = values();
        return presets[(this.ordinal() + 1) % presets.length];
    }
}
