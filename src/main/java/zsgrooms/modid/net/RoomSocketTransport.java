package zsgrooms.modid.net;

import net.minecraft.client.MinecraftClient;
import zsgrooms.modid.InGame;
import zsgrooms.modid.Room;
import zsgrooms.modid.ZsgRooms;
import zsgrooms.modid.ZsgSeedBridge;
import zsgrooms.modid.ui.ZsgInGameActions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RoomSocketTransport {
    public static final int DEFAULT_PORT = 27181;

    private static RoomServer server;
    private static RoomClient client;
    private static String status = "Socket room offline";

    private RoomSocketTransport() {
    }

    public static synchronized boolean host(String roomName, String playerName, int port) {
        stop();
        try {
            server = new RoomServer(roomName, playerName, port);
            server.start();
            status = "Hosting room data on port " + port;
            return true;
        } catch (IOException exception) {
            status = "Could not host room data: " + exception.getMessage();
            return false;
        }
    }

    public static synchronized boolean connect(String address, String roomName, String playerName) {
        closeClient();
        HostPort hostPort = parseAddress(address);
        if (hostPort == null) {
            status = "Enter a server address like host:27181";
            return false;
        }
        try {
            client = new RoomClient(hostPort.host, hostPort.port, roomName, playerName);
            if (!client.startAndAwaitWelcome()) {
                status = client.getJoinError();
                client.close();
                client = null;
                return false;
            }
            status = "Connected to " + hostPort.host + ":" + hostPort.port;
            return true;
        } catch (IOException exception) {
            status = "Could not connect: " + exception.getMessage();
            return false;
        }
    }

    public static synchronized boolean sendAction(String type, String roomName, String playerName, String value) {
        if (client != null && client.isRunning()) {
            client.send(type, roomName, playerName, value);
            return true;
        }
        if (server != null && server.isRunning()) {
            server.handleHostAction(type, roomName, playerName, value);
            return true;
        }
        return false;
    }

    public static synchronized boolean isHosting() {
        return server != null && server.isRunning();
    }

    public static synchronized boolean isConnected() {
        return client != null && client.isRunning();
    }

    public static synchronized String getStatus() {
        return status;
    }

    public static synchronized void stop() {
        closeClient();
        if (server != null) {
            server.close();
            server = null;
        }
        status = "Socket room offline";
    }

    private static synchronized void closeClient() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    private static void applyLine(String line) {
        Map<String, String> message = RoomProtocol.decode(line);
        if ("error".equals(message.get("type"))) {
            status = message.get("value");
            return;
        }
        runOnClientThread(() -> {
            String type = message.get("type");
            ZsgRooms.applyRoomAction(type, message.get("room"), message.get("player"), message.get("value"));
            if ("seed_change_ready".equals(type)) {
                ZsgInGameActions.showSeedChangeAgreement(MinecraftClient.getInstance());
            } else if ("seed_change_vote".equals(type)
                    && !zsgrooms.modid.ZsgRoomsClient.localPlayerName(MinecraftClient.getInstance()).equals(message.get("player"))) {
                ZsgInGameActions.showSeedChangeRequest(MinecraftClient.getInstance(), message.get("player"));
            } else if ("seed_ready".equals(type)) {
                ZsgInGameActions.showSeedReady(MinecraftClient.getInstance());
            } else if ("reset_run".equals(type)
                    && !zsgrooms.modid.ZsgRoomsClient.localPlayerName(MinecraftClient.getInstance()).equals(message.get("player"))) {
                ZsgInGameActions.showRunReset(MinecraftClient.getInstance(), message.get("player"));
            }
        });
    }

    private static void runOnClientThread(Runnable action) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft != null) {
            minecraft.execute(action);
        } else {
            action.run();
        }
    }

    private static HostPort parseAddress(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String text = raw.trim();
        int colon = text.lastIndexOf(':');
        if (colon < 1 || colon == text.length() - 1) {
            return new HostPort(text, DEFAULT_PORT);
        }
        try {
            return new HostPort(text.substring(0, colon), Integer.parseInt(text.substring(colon + 1)));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static class RoomServer {
        private final String roomName;
        private final String hostName;
        private final ServerSocket serverSocket;
        private final List<ClientHandler> clients;
        private volatile boolean running;
        private volatile boolean preparingSeed;

        private RoomServer(String roomName, String hostName, int port) throws IOException {
            this.roomName = roomName;
            this.hostName = hostName;
            this.serverSocket = new ServerSocket(port);
            this.clients = Collections.synchronizedList(new ArrayList<ClientHandler>());
            this.preparingSeed = false;
        }

        private void start() {
            this.running = true;
            Thread thread = new Thread(this::acceptLoop, "ZSG Room Socket Host");
            thread.setDaemon(true);
            thread.start();
        }

        private boolean isRunning() {
            return this.running && !this.serverSocket.isClosed();
        }

        private void acceptLoop() {
            while (this.running) {
                try {
                    Socket socket = this.serverSocket.accept();
                    ClientHandler handler = new ClientHandler(this, socket);
                    this.clients.add(handler);
                    handler.start();
                    runOnClientThread(handler::sendSnapshot);
                } catch (IOException exception) {
                    if (this.running) {
                        status = "Room host accept failed: " + exception.getMessage();
                    }
                }
            }
        }

        private void handle(ClientHandler sender, String line) {
            Map<String, String> message = RoomProtocol.decode(line);
            String type = message.get("type");
            String room = message.get("room");
            String value = message.get("value");
            if (!this.roomName.equals(room) || type == null || type.trim().isEmpty()) {
                return;
            }

            if (sender.getPlayerName() == null) {
                if (!"join_room".equals(type) || !sender.claimPlayer(message.get("player"))) {
                    sender.close();
                    return;
                }
            }

            String player = sender.getPlayerName();
            runOnClientThread(() -> handleClientAction(sender, type, player, value));
        }

        private void handleClientAction(ClientHandler sender, String type, String player, String value) {
            if ("join_room".equals(type)) {
                Room room = ZsgRooms.getRoom(this.roomName);
                if (room == null || room.isFull() || room.getPlayer(player) != null) {
                    String reason = room != null && room.getPlayer(player) != null
                            ? "That player name is already connected"
                            : "The room is full or unavailable";
                    sender.send(RoomProtocol.encode("error", this.roomName, this.hostName, reason));
                    sender.close();
                    return;
                }
                ZsgRooms.applyRoomAction(type, this.roomName, player, value);
                sender.markAccepted();
                sender.send(RoomProtocol.encode("welcome", this.roomName, this.hostName, player));
                broadcastSnapshot();
                return;
            }
            if ("start".equals(type) || "filter".equals(type)) {
                sendToPlayer(player, "chat", this.roomName, this.hostName, "Only the host can use that control");
                return;
            }
            if ("seed_change".equals(type)) {
                boolean newRequest = isNewSeedChangeRequest(player);
                boolean ready = ZsgRooms.registerSeedChangeRequest(player);
                if (newRequest) {
                    announceSeedChangeRequest(player);
                }
                broadcastSnapshot();
                if (ready) {
                    announceSeedChangeAgreement();
                    requestAndLaunchExactSeed();
                }
                return;
            }
            if (!"chat".equals(type)
                    && !"profile".equals(type)
                    && !"share_seed".equals(type)
                    && !"forfeit".equals(type)
                    && !"leave_room".equals(type)
                    && !"reset_run".equals(type)
                    && !"progress".equals(type)) {
                return;
            }

            ZsgRooms.applyRoomAction(type, this.roomName, player, value);
            if ("reset_run".equals(type)) {
                ZsgInGameActions.showRunReset(MinecraftClient.getInstance(), player);
            }
            broadcast(type, this.roomName, player, value);
            if ("forfeit".equals(type) || "leave_room".equals(type) || "progress".equals(type)
                    || "profile".equals(type) || "reset_run".equals(type)) {
                broadcastSnapshot();
            }
        }

        private void handleHostAction(String type, String room, String player, String value) {
            if (!this.roomName.equals(room) || !this.hostName.equals(player)) {
                return;
            }
            if ("start".equals(type)) {
                requestAndLaunchExactSeed();
            } else if ("filter".equals(type)) {
                ZsgRooms.applyRoomAction(type, room, player, value);
                broadcastSnapshot();
            } else if ("seed_change".equals(type)) {
                boolean newRequest = isNewSeedChangeRequest(player);
                boolean ready = ZsgRooms.registerSeedChangeRequest(player);
                if (newRequest) {
                    announceSeedChangeRequest(player);
                }
                broadcastSnapshot();
                if (ready) {
                    announceSeedChangeAgreement();
                    requestAndLaunchExactSeed();
                }
            } else {
                ZsgRooms.applyRoomAction(type, room, player, value);
                broadcast(type, room, player, value);
                if ("forfeit".equals(type) || "leave_room".equals(type) || "progress".equals(type)
                        || "profile".equals(type) || "reset_run".equals(type)) {
                    broadcastSnapshot();
                }
            }
        }

        private void requestAndLaunchExactSeed() {
            if (this.preparingSeed) {
                return;
            }
            Room room = ZsgRooms.getRoom(this.roomName);
            InGame game = ZsgRooms.getGame(this.roomName);
            if (room == null || game == null) {
                return;
            }

            this.preparingSeed = true;
            status = "Requesting one shared seed from FSG...";
            String filter = game.targetStructure;
            ZsgRooms.shareChat(this.roomName, "Host is requesting a shared " + ZsgSeedBridge.seedTypeLabel(filter) + " seed...");
            broadcastSnapshot();

            ZsgSeedBridge.requestExactSeedForRoom(this.roomName, filter).whenComplete((seed, error) -> {
                this.preparingSeed = false;
                runOnClientThread(() -> {
                    if (error != null || seed == null || seed.trim().isEmpty()) {
                        String reason = error == null ? "empty seed" : error.getMessage();
                        status = "Seed request failed: " + reason;
                        ZsgRooms.shareChat(this.roomName, "Could not get a shared seed: " + reason);
                        broadcastSnapshot();
                        return;
                    }

                    ZsgRooms.prepareRoomSeed(this.roomName, seed, filter);
                    broadcastSnapshot();
                    ZsgInGameActions.showSeedReady(MinecraftClient.getInstance());
                    broadcast("seed_ready", this.roomName, this.hostName, "New seed ready. Starting now!");
                    broadcast("launch", this.roomName, this.hostName, seed);
                    ZsgRooms.launchRoomWithSeed(this.roomName, seed);
                    status = "Race launched with a synchronized seed";
                    broadcastSnapshot();
                });
            });
        }

        private void announceSeedChangeAgreement() {
            ZsgInGameActions.showSeedChangeAgreement(MinecraftClient.getInstance());
            broadcast("seed_change_ready", this.roomName, this.hostName,
                    "All players agreed. Preparing a new seed...");
        }

        private boolean isNewSeedChangeRequest(String player) {
            Room room = ZsgRooms.getRoom(this.roomName);
            return room != null && room.getPlayer(player) != null
                    && !room.getPlayer(player).getIsRequestingSeedChange();
        }

        private void announceSeedChangeRequest(String player) {
            if (!this.hostName.equals(player)) {
                ZsgInGameActions.showSeedChangeRequest(MinecraftClient.getInstance(), player);
            }
            broadcast("seed_change_vote", this.roomName, player, player + " requested a seed change");
        }

        private void broadcast(String type, String room, String player, String value) {
            String line = RoomProtocol.encode(type, room, player, value);
            synchronized (this.clients) {
                for (ClientHandler client : new ArrayList<ClientHandler>(this.clients)) {
                    client.send(line);
                }
            }
        }

        private void broadcastSnapshot() {
            String snapshot = ZsgRooms.createRoomSnapshot(this.roomName);
            if (!snapshot.isEmpty()) {
                broadcast("snapshot", this.roomName, this.hostName, snapshot);
            }
        }

        private void sendToPlayer(String playerName, String type, String room, String player, String value) {
            synchronized (this.clients) {
                for (ClientHandler client : new ArrayList<ClientHandler>(this.clients)) {
                    if (playerName.equals(client.getPlayerName())) {
                        client.send(RoomProtocol.encode(type, room, player, value));
                    }
                }
            }
        }

        private void remove(ClientHandler handler) {
            this.clients.remove(handler);
            String playerName = handler.getPlayerName();
            if (playerName != null && handler.isAccepted()) {
                runOnClientThread(() -> {
                    ZsgRooms.removeRoomPlayer(this.roomName, playerName);
                    broadcastSnapshot();
                });
            }
        }

        private void close() {
            this.running = false;
            try {
                this.serverSocket.close();
            } catch (IOException ignored) {
            }
            synchronized (this.clients) {
                for (ClientHandler client : new ArrayList<ClientHandler>(this.clients)) {
                    client.close();
                }
                this.clients.clear();
            }
        }
    }

    private static class ClientHandler {
        private final RoomServer server;
        private final Socket socket;
        private final BufferedReader reader;
        private final PrintWriter writer;
        private volatile String playerName;
        private volatile boolean accepted;

        private ClientHandler(RoomServer server, Socket socket) throws IOException {
            this.server = server;
            this.socket = socket;
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            this.writer = new PrintWriter(socket.getOutputStream(), true);
            this.playerName = null;
            this.accepted = false;
        }

        private void start() {
            Thread thread = new Thread(this::readLoop, "ZSG Room Socket Client");
            thread.setDaemon(true);
            thread.start();
        }

        private void readLoop() {
            try {
                String line;
                while ((line = this.reader.readLine()) != null) {
                    this.server.handle(this, line);
                }
            } catch (IOException ignored) {
            } finally {
                close();
                this.server.remove(this);
            }
        }

        private void sendSnapshot() {
            String snapshot = ZsgRooms.createRoomSnapshot(this.server.roomName);
            if (!snapshot.isEmpty()) {
                send(RoomProtocol.encode("snapshot", this.server.roomName, this.server.hostName, snapshot));
            }
        }

        private synchronized void send(String line) {
            this.writer.println(line);
        }

        private boolean claimPlayer(String requestedName) {
            if (requestedName == null) {
                return false;
            }
            String cleanName = requestedName.trim();
            if (cleanName.isEmpty() || cleanName.length() > 32 || cleanName.equals(this.server.hostName)) {
                return false;
            }
            this.playerName = cleanName;
            return true;
        }

        private String getPlayerName() {
            return this.playerName;
        }

        private void markAccepted() {
            this.accepted = true;
        }

        private boolean isAccepted() {
            return this.accepted;
        }

        private void close() {
            try {
                this.socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static class RoomClient {
        private final Socket socket;
        private final BufferedReader reader;
        private final PrintWriter writer;
        private final String roomName;
        private final String playerName;
        private final CountDownLatch welcomeLatch;
        private volatile boolean running;
        private volatile boolean welcomed;
        private volatile String joinError;

        private RoomClient(String host, int port, String roomName, String playerName) throws IOException {
            this.socket = new Socket();
            this.socket.connect(new InetSocketAddress(host, port), 5000);
            this.reader = new BufferedReader(new InputStreamReader(this.socket.getInputStream(), "UTF-8"));
            this.writer = new PrintWriter(this.socket.getOutputStream(), true);
            this.roomName = roomName;
            this.playerName = playerName;
            this.welcomeLatch = new CountDownLatch(1);
            this.welcomed = false;
            this.joinError = "Room did not accept the connection";
        }

        private boolean startAndAwaitWelcome() {
            this.running = true;
            Thread thread = new Thread(this::readLoop, "ZSG Room Socket Guest");
            thread.setDaemon(true);
            thread.start();
            send("join_room", this.roomName, this.playerName, "");
            try {
                if (!this.welcomeLatch.await(6, TimeUnit.SECONDS)) {
                    this.joinError = "Room handshake timed out";
                    return false;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                this.joinError = "Room connection was interrupted";
                return false;
            }
            return this.welcomed && isRunning();
        }

        private boolean isRunning() {
            return this.running && !this.socket.isClosed();
        }

        private synchronized void send(String type, String roomName, String playerName, String value) {
            this.writer.println(RoomProtocol.encode(type, roomName, playerName, value));
        }

        private void readLoop() {
            try {
                String line;
                while ((line = this.reader.readLine()) != null) {
                    Map<String, String> message = RoomProtocol.decode(line);
                    String type = message.get("type");
                    if ("welcome".equals(type)) {
                        this.welcomed = true;
                        this.welcomeLatch.countDown();
                    } else if ("error".equals(type)) {
                        this.joinError = message.get("value");
                        status = this.joinError;
                        this.welcomeLatch.countDown();
                    } else {
                        applyLine(line);
                    }
                }
            } catch (IOException exception) {
                if (this.running) {
                    status = "Room socket disconnected: " + exception.getMessage();
                }
            } finally {
                this.welcomeLatch.countDown();
                close();
            }
        }

        private String getJoinError() {
            return this.joinError;
        }

        private void close() {
            this.running = false;
            try {
                this.socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static class HostPort {
        private final String host;
        private final int port;

        private HostPort(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }
}
