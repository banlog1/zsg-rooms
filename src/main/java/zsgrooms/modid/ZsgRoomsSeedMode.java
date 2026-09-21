package zsgrooms.modid;

public final class ZsgRoomsSeedMode {
    public static final String SPECIFICATION = "rooms-mix";
    public static final String LABEL = "ZSG Rooms Mode";
    public static final String DESCRIPTION = "Temple 20%, Village 20%, Shipwreck 20%, Ruined Portal 20%, Mapless 10%, Mapless OP 10%";

    private ZsgRoomsSeedMode() {}

    public static String filterForRoll(int roll) {
        if (roll < 0 || roll >= 100) throw new IllegalArgumentException("Filter roll must be in [0, 100)");
        if (roll < 20) return "rooms-temple-v5";
        if (roll < 40) return "rooms-village-v5";
        if (roll < 60) return "rooms-shipwreck-v5";
        if (roll < 80) return "rpseedbank";
        if (roll < 90) return "zsg";
        return "zsgop";
    }
}
