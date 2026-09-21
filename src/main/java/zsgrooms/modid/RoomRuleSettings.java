package zsgrooms.modid;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Lobby rule edits only; seed, race state and player progress are never part of this payload. */
public final class RoomRuleSettings {
    private static final Gson GSON = new Gson();
    private static final String[] FIELDS = {
            "allowCheats",
            "rngStandardization",
            "boostedBarters",
            "minimumBastionIron",
            "removeBastionZombifiedPiglins",
            "removeNaturalStriderJockeys",
            "spawnNearFilterStructure",
            "minimumNearbyAnimals",
            "netherEntryWarmup",
            "disablePauseWorldSaves",
            "reduceZeroCycleFlyAways",
            "sharedNetherEntry"
    };

    public boolean allowCheats;
    public boolean rngStandardization;
    public boolean boostedBarters;
    public boolean minimumBastionIron;
    public boolean removeBastionZombifiedPiglins;
    public boolean removeNaturalStriderJockeys;
    public boolean spawnNearFilterStructure;
    public boolean minimumNearbyAnimals;
    public boolean netherEntryWarmup;
    public boolean disablePauseWorldSaves;
    public boolean reduceZeroCycleFlyAways;
    public boolean sharedNetherEntry;

    public static RoomRuleSettings capture(InGame game) {
        RoomRuleSettings rules = new RoomRuleSettings();
        if (game != null) {
            rules.allowCheats = game.areCheatsAllowed();
            rules.rngStandardization = game.isRngStandardized();
            rules.boostedBarters = game.areBartersBoosted();
            rules.minimumBastionIron = game.hasMinimumBastionIron();
            rules.removeBastionZombifiedPiglins = game.removesBastionZombifiedPiglins();
            rules.removeNaturalStriderJockeys = game.removesNaturalStriderJockeys();
            rules.spawnNearFilterStructure = game.spawnsNearFilterStructure();
            rules.minimumNearbyAnimals = game.hasMinimumNearbyAnimals();
            rules.netherEntryWarmup = game.hasNetherEntryWarmup();
            rules.disablePauseWorldSaves = game.disablesPauseWorldSaves();
            rules.reduceZeroCycleFlyAways = game.reducesZeroCycleFlyAways();
            rules.sharedNetherEntry = game.hasSharedNetherEntry();
        }
        return rules;
    }

    public static boolean canEdit(Room room, InGame game, String player) {
        return room != null && game != null && !game.getIsInGame()
                && room.host != null && room.host.getName().equals(player);
    }

    public void applyTo(InGame game) {
        game.setCheatsAllowed(allowCheats);
        game.setRngStandardized(rngStandardization);
        game.setBoostedBarters(boostedBarters);
        game.setMinimumBastionIron(minimumBastionIron);
        game.setRemoveBastionZombifiedPiglins(removeBastionZombifiedPiglins);
        game.setRemoveNaturalStriderJockeys(removeNaturalStriderJockeys);
        game.setSpawnNearFilterStructure(spawnNearFilterStructure);
        game.setMinimumNearbyAnimals(minimumNearbyAnimals);
        game.setNetherEntryWarmup(netherEntryWarmup);
        game.setDisablePauseWorldSaves(disablePauseWorldSaves);
        game.setReduceZeroCycleFlyAways(reduceZeroCycleFlyAways);
        game.setSharedNetherEntry(sharedNetherEntry);
    }

    public String toJson() { return GSON.toJson(this); }

    public static RoomRuleSettings fromJson(String json) {
        if (json == null || json.length() > 4096) return null;
        try {
            JsonElement element = new JsonParser().parse(json);
            if (!element.isJsonObject()) return null;
            JsonObject object = element.getAsJsonObject();
            for (String field : FIELDS) {
                JsonElement value = object.get(field);
                if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) return null;
            }
            return GSON.fromJson(object, RoomRuleSettings.class);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
