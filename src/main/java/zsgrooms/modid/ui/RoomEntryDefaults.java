package zsgrooms.modid.ui;

import zsgrooms.modid.ZsgRooms;

import java.security.SecureRandom;

/** Shared defaults for the one-click flow; no world or connection is started here. */
public final class RoomEntryDefaults {
    public static final int CAPACITY = 10;
    public static final String FILTER = "rooms-mix";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private RoomEntryDefaults() { }

    public static String newCode() {
        StringBuilder code = new StringBuilder(8);
        for (int i = 0; i < 8; i++) code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return code.toString();
    }

    public static boolean validCode(String code) {
        return code != null && code.matches("[A-Za-z0-9_-]{4,64}");
    }

    public static void create(String code, String player, String uuid) {
        RoomRulePreset p = RoomRulePreset.STANDARD_ZSG_ROOMS;
        ZsgRooms.createRoom(code, CAPACITY, 1, FILTER, player, p.allowsCheats(), p.standardizesRng(),
                p.boostsBarters(), p.guaranteesBastionIron(), p.removesBastionZombifiedPiglins(),
                p.spawnsNearFilterStructure(), p.guaranteesNearbyAnimals(), p.warmsNetherEntry(),
                p.removesNaturalStriderJockeys(), p.disablesPauseWorldSaves(), p.reducesZeroCycleFlyAways(), p.sharesNetherEntry());
        ZsgRooms.getGame(code).setFinisherLimit(1);
        ZsgRooms.getGame(code).setPreventTempleHostileSpawns(p.preventsTempleHostileSpawns());
        ZsgRooms.setPlayerUuid(code, player, uuid);
    }
}
