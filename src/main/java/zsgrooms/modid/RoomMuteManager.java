package zsgrooms.modid;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RoomMuteManager {
    private static final Map<String, Set<String>> MUTED_PLAYERS = new HashMap<String, Set<String>>();

    private RoomMuteManager() {
    }

    public static synchronized boolean toggle(String roomName, String playerName) {
        String room = normalize(roomName);
        String player = normalize(playerName);
        if (room.isEmpty() || player.isEmpty()) {
            return false;
        }
        Set<String> muted = MUTED_PLAYERS.computeIfAbsent(room, ignored -> new HashSet<String>());
        if (!muted.add(player)) {
            muted.remove(player);
            if (muted.isEmpty()) {
                MUTED_PLAYERS.remove(room);
            }
            return false;
        }
        return true;
    }

    public static synchronized boolean isMuted(String roomName, String playerName) {
        Set<String> muted = MUTED_PLAYERS.get(normalize(roomName));
        return muted != null && muted.contains(normalize(playerName));
    }

    public static synchronized boolean shouldHideRoomMessage(String roomName, String message) {
        Set<String> muted = MUTED_PLAYERS.get(normalize(roomName));
        if (muted == null || muted.isEmpty() || message == null) {
            return false;
        }
        String normalizedMessage = message.trim().toLowerCase(Locale.ROOT);
        for (String player : muted) {
            if (normalizedMessage.startsWith("<" + player + "> ")
                    || normalizedMessage.startsWith(player + " made the advancement [")) {
                return true;
            }
        }
        return false;
    }

    public static synchronized void clearRoom(String roomName) {
        MUTED_PLAYERS.remove(normalize(roomName));
    }

    static synchronized void clearAll() {
        MUTED_PLAYERS.clear();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
