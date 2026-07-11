package zsgrooms.modid;

public class Player {
    public String name;
    public boolean isInRoom;
    public boolean isRequestingSeedChange;
    public boolean isHost;

    public Player(String name, boolean isInRoom, boolean isHost) {
        this.name = name;
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
