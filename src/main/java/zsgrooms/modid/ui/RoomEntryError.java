package zsgrooms.modid.ui;

import java.util.Locale;

/** Friendly entry errors without exposing a relay's raw response or room code. */
final class RoomEntryError {
    final String title, detail;
    final boolean connectionProblem;

    private RoomEntryError(String title, String detail, boolean connectionProblem) {
        this.title = title; this.detail = detail; this.connectionProblem = connectionProblem;
    }

    static RoomEntryError from(String reason, boolean creating) {
        String s = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        if (s.matches("(?s).*\\b(?:5[0-9]{2}|429)\\b.*")) return unavailable();
        if (s.contains("room is full") || s.contains("room is full or unavailable"))
            return new RoomEntryError("Room unavailable", "The room is full or a race has started. Ask the host to check the lobby.", false);
        if (s.contains("404") || s.contains("host is offline"))
            return new RoomEntryError("Room not found", "Check the code with your host and make sure their room is still open. Custom relays must match.", false);
        if (s.contains("player is already connected"))
            return new RoomEntryError("Already connected", "Another connection is using your player name. Close it before trying again.", false);
        if (creating && (s.contains("already owned") || s.contains("409")))
            return new RoomEntryError("Code already in use", "Retry to create a room with a fresh code.", false);
        if (s.contains("409") || s.contains("rejected") || s.contains("removed from room"))
            return new RoomEntryError("Could not join this room", "Ask your host to check the room code, available places and whether a race is running.", false);
        return unavailable();
    }

    private static RoomEntryError unavailable() {
        return new RoomEntryError("Cannot reach the relay", "The service or your connection may be unavailable. Wait and retry, or choose a custom relay in Connection settings.", true);
    }

    static RoomEntryError cancelled() {
        return new RoomEntryError("Connection cancelled", "The room connection was cancelled when you left this screen. Retry when you are ready.", false);
    }
}
