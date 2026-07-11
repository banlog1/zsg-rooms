package zsgrooms.modid;

public class Player {
    public String name;
    private String uuid;
    public boolean isInRoom;
    public boolean isRequestingSeedChange;
    public boolean isHost;

    public Player(String name, boolean isInRoom, boolean isHost) {
        this(name, "", isInRoom, isHost);
    }

    public Player(String name, String uuid, boolean isInRoom, boolean isHost) {
        this.name = name;
        this.uuid = uuid == null ? "" : uuid.trim();
        this.isInRoom = isInRoom;
        this.isRequestingSeedChange = false;
        this.isHost = isHost;
    }

    public void setRequestingSeedChange(boolean isRequestingSeedChange) {
        this.isRequestingSeedChange = isRequestingSeedChange;
    }

    public void setInRoom(boolean isInRoom) {
        this.isInRoom = isInRoom;
    }

    public void setHost(boolean isHost) {
        this.isHost = isHost;
    }

    public String getName() {
        return name;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid == null ? "" : uuid.trim();
    }

    public boolean getIsInRoom() {
        return isInRoom;
    }

    public boolean getIsRequestingSeedChange() {
        return isRequestingSeedChange;
    }

    public boolean getIsHost() {
        return isHost;
    }

    public boolean equals(Player other) {
        return other != null && this.name != null && this.name.equals(other.name);
    }
}
