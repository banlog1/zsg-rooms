package zsgrooms.modid.ui;

/** Display categories only; never used to select a seed or advance game RNG. */
enum SeedVisualType {
    TREASURE("Buried Treasure", "bt"), TEMPLE("Desert Temple", "dt", "dt2"),
    VILLAGE("Village", "village", "village2"), SHIPWRECK("Shipwreck", "shipwreck1", "shipwreck2"),
    PORTAL("Ruined Portal", "rp"), JUNGLE("Jungle Temple"), RANDOM("Random Seed"),
    MANUAL("Manual Seed"), ROOM("Room Seed"), MIXED("ZSG Rooms Mode"), UNKNOWN("Seed");

    final String label;
    final String[] images;

    SeedVisualType(String label, String... images) {
        this.label = label;
        this.images = images;
    }

    static SeedVisualType forFilter(String filter) {
        if (filter == null) return UNKNOWN;
        if (filter.startsWith("manual:")) return MANUAL;
        switch (filter) {
            case "rooms-buried-treasure-v5": case "zsg": case "zsgop": return TREASURE;
            case "rooms-temple-v5": case "zsgtemple": case "zsgtempleop": return TEMPLE;
            case "rooms-village-v5": case "zsgvillage": case "zsgvillageop": return VILLAGE;
            case "rooms-shipwreck-v5": case "zsgshipwreck": case "zsgshipwreckop": return SHIPWRECK;
            case "rooms-ruined-portal-v5": case "rpseedbank": return PORTAL;
            case "zsgjungletemple": case "zsgjungletempleop": return JUNGLE;
            case "random": return RANDOM;
            case "manual": return MANUAL;
            case "room": return ROOM;
            case "rooms-mix": return MIXED;
            default: return UNKNOWN;
        }
    }

    String labelFor(String filter) {
        return label + (filter != null && filter.endsWith("op") ? " OP" : "");
    }
}
