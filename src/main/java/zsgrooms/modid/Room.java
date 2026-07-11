package zsgrooms.modid;

import java.util.ArrayList;
import java.util.List;

public class Room {
    public String roomName;
    public String seed;
    public Player host;
    public Player[] players;
    public int maxPlayers;

    private final List<String> roomMessages;

    public Room(String roomName, String seed, Player host, int maxPlayers) {
        this.roomName = roomName;
        this.seed = seed;
        this.host = host;
        this.players = new Player[maxPlayers];
        this.maxPlayers = maxPlayers;
        this.roomMessages = new ArrayList<String>();
        this.players[0] = host;
        if (host != null) {
            roomMessages.add(host.getName() + " joined " + roomName);
        }
    }

    public void addPlayer(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }
        for (int i = 0; i < maxPlayers; i++) {
            if (players[i] == null) {
                players[i] = player;
                roomMessages.add(player.getName() + " joined " + roomName);
                return;
            }
        }
        throw new IllegalStateException("Room is full");
    }

    public void removePlayer(Player player) {
        for (int i = 0; i < maxPlayers; i++) {
            if (players[i] != null && players[i].equals(player)) {
                players[i] = null;
                roomMessages.add(player.getName() + " left " + roomName);
                return;
            }
        }
        throw new IllegalArgumentException("Player not found in room");
    }

    public boolean isFull() {
        return countPlayers() >= maxPlayers;
    }

    public boolean isEmpty() {
        return countPlayers() == 0;
    }

    public void closeRoom() {
        for (int i = 0; i < maxPlayers; i++) {
            players[i] = null;
        }
        roomMessages.add("Room closed: " + roomName);
    }

    public int getPlayerCount() {
        return countPlayers();
    }

    public List<String> getPlayerNames() {
        List<String> names = new ArrayList<String>();
        for (Player player : players) {
            if (player != null && player.getName() != null) {
                names.add(player.getName());
            }
        }
        return names;
    }

    public Player getPlayer(String name) {
        if (name == null) {
            return null;
        }
        for (Player player : players) {
            if (player != null && name.equals(player.getName())) {
                return player;
            }
        }
        return null;
    }

    public String findFirstOtherPlayerName(String name) {
        for (Player player : players) {
            if (player != null && player.getName() != null && !player.getName().equals(name)) {
                return player.getName();
            }
        }
        return null;
    }

    public void displayRoomInfo() {
        System.out.println("Room Name: " + roomName);
        System.out.println("Seed: " + getSeed());
        System.out.println("Host: " + host.getName());
        System.out.println("Players in Room:");
        for (Player player : players) {
            if (player != null) {
                System.out.println("- " + player.getName());
            }
        }
    }

    public String getSeed() {
        if (this.seed == null || this.seed.trim().isEmpty()) {
            this.seed = "room-seed-" + System.nanoTime();
        }
        return this.seed;
    }

    public void setSeed(String seed) {
        this.seed = seed;
    }

    public void addRoomMessage(String message) {
        if (message != null && !message.trim().isEmpty()) {
            roomMessages.add(message.trim());
            while (roomMessages.size() > 50) {
                roomMessages.remove(0);
            }
        }
    }

    public List<String> getRoomMessages() {
        return new ArrayList<String>(roomMessages);
    }

    public void replacePlayers(List<Player> replacement, String hostName) {
        int requiredSize = replacement == null ? 0 : replacement.size();
        if (requiredSize > this.maxPlayers) {
            this.maxPlayers = requiredSize;
            this.players = new Player[this.maxPlayers];
        } else {
            for (int i = 0; i < this.players.length; i++) {
                this.players[i] = null;
            }
        }

        this.host = null;
        if (replacement != null) {
            int index = 0;
            for (Player player : replacement) {
                if (player == null || index >= this.players.length) {
                    continue;
                }
                this.players[index++] = player;
                if (player.getIsHost() || player.getName().equals(hostName)) {
                    player.setHost(true);
                    this.host = player;
                }
            }
        }
        if (this.host == null && this.players.length > 0 && this.players[0] != null) {
            this.players[0].setHost(true);
            this.host = this.players[0];
        }
    }

    public void replaceRoomMessages(List<String> messages) {
        this.roomMessages.clear();
        if (messages != null) {
            int start = Math.max(0, messages.size() - 50);
            this.roomMessages.addAll(messages.subList(start, messages.size()));
        }
    }

    private int countPlayers() {
        int count = 0;
        for (Player player : players) {
            if (player != null) {
                count += 1;
            }
        }
        return count;
    }
}
