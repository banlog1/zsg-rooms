package zsgrooms.modid.ui;

import java.util.Objects;

/** A result's delayed return belongs to one race, not to the next loaded world. */
final class RaceReturnTimer {
    private String roomName;
    private String raceId;
    private int ticks = -1;

    void bind(String roomName, String raceId) {
        this.roomName = roomName;
        this.raceId = raceId;
        ticks = -1;
    }

    void start() { ticks = 90; }

    boolean retain(String roomName, String raceId) {
        if (this.roomName == null || !Objects.equals(this.roomName, roomName)
                || !Objects.equals(this.raceId, raceId)) {
            clear();
            return false;
        }
        return true;
    }

    boolean tick(String roomName, String raceId) {
        if (!retain(roomName, raceId) || ticks < 0) return false;
        if (--ticks != 0) return false;
        clear();
        return true;
    }

    void clear() {
        roomName = null;
        raceId = null;
        ticks = -1;
    }
}
