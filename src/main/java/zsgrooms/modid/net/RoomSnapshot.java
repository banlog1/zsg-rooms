package zsgrooms.modid.net;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import zsgrooms.modid.InGame;
import zsgrooms.modid.Player;
import zsgrooms.modid.Room;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RoomSnapshot {
    private static final Gson GSON = new Gson();

    public int protocolVersion = 1;
    public String roomName = "";
    public String seed = "";
    public String hostName = "";
    public String filter = "room";
    public int maxPlayers = 8;
    public int finishGoal = 1;
    public boolean inGame;
    public boolean cheatsAllowed;
    public boolean rngStandardized;
    public boolean boostedBarters;
    public boolean minimumBastionIron;
    public boolean removeBastionZombifiedPiglins;
    public boolean synchronizedStartReleased;
    public List<String> readyPlayers = new ArrayList<String>();
    public List<PlayerState> players = new ArrayList<PlayerState>();
    public List<String> messages = new ArrayList<String>();
    public Map<String, Integer> progress = new LinkedHashMap<String, Integer>();
    public Map<String, String> progressLabels = new LinkedHashMap<String, String>();

    public static RoomSnapshot capture(Room room, InGame game) {
        RoomSnapshot snapshot = new RoomSnapshot();
        if (room == null) {
            return snapshot;
        }
        snapshot.roomName = room.roomName;
        snapshot.seed = room.getSeed();
        snapshot.hostName = room.host == null ? "host" : room.host.getName();
        snapshot.maxPlayers = room.maxPlayers;
        snapshot.messages = room.getRoomMessages();
        for (Player player : room.players) {
            if (player != null) {
                snapshot.players.add(PlayerState.capture(player));
            }
        }
        if (game != null) {
            snapshot.filter = game.targetStructure;
            snapshot.finishGoal = game.getFinishGoal();
            snapshot.inGame = game.getIsInGame();
            snapshot.cheatsAllowed = game.areCheatsAllowed();
            snapshot.rngStandardized = game.isRngStandardized();
            snapshot.boostedBarters = game.areBartersBoosted();
            snapshot.minimumBastionIron = game.hasMinimumBastionIron();
            snapshot.removeBastionZombifiedPiglins = game.removesBastionZombifiedPiglins();
            snapshot.synchronizedStartReleased = game.isSynchronizedStartReleased();
            snapshot.readyPlayers = game.getReadyPlayers();
            snapshot.progress = game.getPlayerProgress();
            snapshot.progressLabels = game.getPlayerProgressLabels();
        }
        return snapshot;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static RoomSnapshot fromJson(String json) {
        try {
            RoomSnapshot snapshot = GSON.fromJson(json, RoomSnapshot.class);
            if (snapshot == null || snapshot.protocolVersion != 1 || snapshot.roomName == null || snapshot.roomName.trim().isEmpty()) {
                return null;
            }
            if (snapshot.players == null) {
                snapshot.players = new ArrayList<PlayerState>();
            }
            if (snapshot.messages == null) {
                snapshot.messages = new ArrayList<String>();
            }
            if (snapshot.progress == null) {
                snapshot.progress = new LinkedHashMap<String, Integer>();
            }
            if (snapshot.progressLabels == null) {
                snapshot.progressLabels = new LinkedHashMap<String, String>();
            }
            if (snapshot.readyPlayers == null) {
                snapshot.readyPlayers = new ArrayList<String>();
            }
            return snapshot;
        } catch (JsonSyntaxException exception) {
            return null;
        }
    }

    public static class PlayerState {
        public String name = "player";
        public String uuid = "";
        public boolean inRoom = true;
        public boolean requestingSeedChange;
        public boolean host;

        private static PlayerState capture(Player player) {
            PlayerState state = new PlayerState();
            state.name = player.getName();
            state.uuid = player.getUuid();
            state.inRoom = player.getIsInRoom();
            state.requestingSeedChange = player.getIsRequestingSeedChange();
            state.host = player.getIsHost();
            return state;
        }

        public Player toPlayer() {
            Player player = new Player(this.name, this.uuid, this.inRoom, this.host);
            player.setRequestingSeedChange(this.requestingSeedChange);
            return player;
        }
    }
}
