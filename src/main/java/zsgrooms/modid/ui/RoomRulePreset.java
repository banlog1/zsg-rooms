package zsgrooms.modid.ui;

public enum RoomRulePreset {
    STANDARD_ZSG_ROOMS("Standard ZSG Rooms", false, true, true, true, true, true, true, true, true, true),
    STANDARD_ZSG_VANILLA_BARTERS(
            "Standard - Vanilla Barters", false, true, false, true, true, true, true, true, true, true),
    REGULAR_VERIFIABLE_ZSG(
            "Regular Verifiable ZSG", false, false, false, false, false, false, false, false, false, false),
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
        return false;
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

    public boolean disablesPauseWorldSaves() {
        return this.disablePauseWorldSaves;
    }

    public boolean isCustom() {
        return this == CUSTOM;
    }

    public RoomRulePreset next() {
        RoomRulePreset[] presets = values();
        return presets[(this.ordinal() + 1) % presets.length];
    }
}
