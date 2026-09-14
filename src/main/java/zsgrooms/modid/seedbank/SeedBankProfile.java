package zsgrooms.modid.seedbank;

import java.util.Locale;

public enum SeedBankProfile {
    TEMPLE("rooms-temple-v5", "ZSG Rooms Desert Temple", "temple", "desert_pyramid"),
    VILLAGE("rooms-village-v5", "ZSG Rooms Village", "village", "village"),
    SHIPWRECK("rooms-shipwreck-v5", "ZSG Rooms Shipwreck", "shipwreck", "shipwreck");

    public static final String MODEL_PROFILE = "zsg-model-only-v5";
    public final String specification;
    public final String label;
    public final String type;
    public final String structure;

    SeedBankProfile(String specification, String label, String type, String structure) {
        this.specification = specification;
        this.label = label;
        this.type = type;
        this.structure = structure;
    }

    public static SeedBankProfile find(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (SeedBankProfile profile : values()) {
            if (profile.specification.equals(normalized) || profile.label.toLowerCase(Locale.ROOT).equals(normalized)) {
                return profile;
            }
        }
        return null;
    }
}
