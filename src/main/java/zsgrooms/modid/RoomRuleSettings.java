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
    public boolean preventTempleHostileSpawns;
    public boolean spawnNearFilterStructure;
    public boolean minimumNearbyAnimals;
    public boolean netherEntryWarmup;
    public boolean disablePauseWorldSaves;
    public boolean reduceZeroCycleFlyAways;
    public boolean sharedNetherEntry;
    // Zero means unchanged, so gameplay-rule edits cannot reset the race format.
    public int seedCount;
    public int finisherLimit;
    public TournamentSettings tournament;
    public boolean resetTournament;

    public static RoomRuleSettings capture(InGame game) {
        RoomRuleSettings rules = new RoomRuleSettings();
        if (game != null) {
            rules.tournament = game.getTournamentSettings().copy();
            rules.seedCount = game.getFinishGoal();
            rules.finisherLimit = game.getFinisherLimit();
            rules.allowCheats = game.areCheatsAllowed();
            rules.rngStandardization = game.isRngStandardized();
            rules.boostedBarters = game.areBartersBoosted();
            rules.minimumBastionIron = game.hasMinimumBastionIron();
            rules.removeBastionZombifiedPiglins = game.removesBastionZombifiedPiglins();
            rules.removeNaturalStriderJockeys = game.removesNaturalStriderJockeys();
            rules.preventTempleHostileSpawns = game.preventsTempleHostileSpawns();
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
        if (resetTournament) game.setTournament(null);
        if (tournament != null) game.setTournamentSettings(tournament);
        if (seedCount > 0) game.setFinishGoal(seedCount);
        if (finisherLimit > 0) game.setFinisherLimit(finisherLimit);
        game.setCheatsAllowed(allowCheats);
        game.setRngStandardized(rngStandardization);
        game.setBoostedBarters(boostedBarters);
        game.setMinimumBastionIron(minimumBastionIron);
        game.setRemoveBastionZombifiedPiglins(removeBastionZombifiedPiglins);
        game.setRemoveNaturalStriderJockeys(removeNaturalStriderJockeys);
        game.setPreventTempleHostileSpawns(preventTempleHostileSpawns);
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
            // Older rule payloads predate this option and retain vanilla spawning.
            if (object.has("preventTempleHostileSpawns") && (!object.get("preventTempleHostileSpawns").isJsonPrimitive()
                    || !object.get("preventTempleHostileSpawns").getAsJsonPrimitive().isBoolean())) return null;
            if (!validOptionalCount(object, "seedCount", RaceSequence.MAX_SEEDS)
                    || !validOptionalCount(object, "finisherLimit", 64)) return null;
            for (String field : FIELDS) {
                JsonElement value = object.get(field);
                if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) return null;
            }
            RoomRuleSettings result = GSON.fromJson(object, RoomRuleSettings.class);
            if (object.has("resetTournament") && (!object.get("resetTournament").isJsonPrimitive()
                    || !object.get("resetTournament").getAsJsonPrimitive().isBoolean())) return null;
            return result.tournament != null && !result.tournament.valid() ? null : result;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean validOptionalCount(JsonObject object, String key, int max) {
        if (!object.has(key)) return true;
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return false;
        try {
            int number = value.getAsBigDecimal().intValueExact();
            return number >= 0 && number <= max;
        } catch (ArithmeticException exception) {
            return false;
        }
    }
}
