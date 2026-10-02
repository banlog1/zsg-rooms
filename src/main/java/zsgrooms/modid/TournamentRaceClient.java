package zsgrooms.modid;

import net.minecraft.client.MinecraftClient;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import zsgrooms.modid.net.RoomSnapshot;

/** Tournament launch snapshots arrive before launch; waiting players never load the race world. */
public final class TournamentRaceClient {
    private static String launchedRace = "";
    private static String acceptedRace = "";

    public static boolean isLaunchPayload(String value) { return value != null && value.startsWith("{"); }

    public static String launchPayload(String roomName) {
        JsonObject payload = new JsonObject();
        payload.addProperty("tournamentSnapshot", ZsgRooms.createRoomSnapshot(roomName));
        return payload.toString();
    }

    public static boolean acceptLaunch(String roomName, String value) {
        try {
            String json = new JsonParser().parse(value).getAsJsonObject().get("tournamentSnapshot").getAsString();
            RoomSnapshot snapshot = RoomSnapshot.fromJson(json);
            if (snapshot == null || !roomName.equals(snapshot.roomName) || snapshot.tournament == null
                    || snapshot.sequence == null || !snapshot.inGame) return false;
            if (snapshot.raceId.equals(acceptedRace) || snapshot.raceId.equals(launchedRace)) return true;
            if (!ZsgRooms.applyRoomSnapshot(json)) return false;
            acceptedRace = snapshot.raceId;
            return launch(roomName, snapshot.seed);
        } catch (RuntimeException invalid) { return false; }
    }

    public static boolean participates(String roomName) {
        InGame game = ZsgRooms.getGame(roomName);
        return game == null || game.getTournament() == null || game.getSequence() != null
                && game.getSequence().active(ZsgRoomsClient.localPlayerName(MinecraftClient.getInstance()));
    }

    public static boolean launch(String roomName, String seed) {
        InGame game = ZsgRooms.getGame(roomName);
        if (game == null || !game.getIsInGame() || !seed.equals(game.getSeed()) || !participates(roomName)) return false;
        if (game.getRaceId().equals(launchedRace)) return true;
        ZsgRoomsClient.beginSynchronizedStart(roomName, seed);
        if (!ZsgSeedBridge.launchSeedWithAtum(seed)) {
            ZsgRoomsClient.cancelSynchronizedStart(roomName, seed);
            RaceSequenceClient.report(game, "dnf", -1);
            return false;
        }
        launchedRace = game.getRaceId();
        return true;
    }
}
