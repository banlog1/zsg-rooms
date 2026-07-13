package zsgrooms.modid.net;

import net.minecraft.client.MinecraftClient;
import zsgrooms.modid.InGame;
import zsgrooms.modid.Room;
import zsgrooms.modid.ZsgRooms;
import zsgrooms.modid.ZsgRoomsClient;
import zsgrooms.modid.ZsgSeedBridge;
import zsgrooms.modid.ui.ZsgInGameActions;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RoomWebSocketTransport {
    public static final String DEFAULT_RELAY_URL = "zsg-rooms-relay.sashko-ato.workers.dev";
    private static final String RELAY_PROPERTY = "zsgrooms.relay";
    private static final String RELAY_ENVIRONMENT = "ZSG_ROOMS_RELAY";
    private static final Path RELAY_CONFIG = Paths.get("config", "zsg-rooms-relay.txt");

    private static RelayConnection connection;
    private static String relayUrl = configuredRelayUrl();
    private static String status = "Room relay offline";

    private RoomWebSocketTransport() {
    }

    public static synchronized boolean host(String endpoint, String roomName, String playerName) {
        stop();
        try {
            String normalizedEndpoint = normalizeRelayUrl(endpoint);
            relayUrl = displayRelayUrl(normalizedEndpoint);
            connection = new RelayConnection(normalizedEndpoint, roomName, playerName, true);
            if (!connection.open()) {
                status = connection.getJoinError();
                connection.close();
                connection = null;
                return false;
            }
            connection.sendSnapshot();
            saveRelayUrl(relayUrl);
            status = "Hosting through secure room relay";
            return true;
        } catch (Exception exception) {
            status = "Could not connect to relay: " + usefulMessage(exception);
            connection = null;
            return false;
        }
    }

    public static synchronized boolean connect(String endpoint, String roomName, String playerName) {
        stop();
        try {
            String normalizedEndpoint = normalizeRelayUrl(endpoint);
            relayUrl = displayRelayUrl(normalizedEndpoint);
            connection = new RelayConnection(normalizedEndpoint, roomName, playerName, false);
            if (!connection.open()) {
                status = connection.getJoinError();
                connection.close();
                connection = null;
                return false;
            }
            saveRelayUrl(relayUrl);
            status = "Connected through secure room relay";
            return true;
        } catch (Exception exception) {
            status = "Could not connect to relay: " + usefulMessage(exception);
            connection = null;
            return false;
        }
    }

    public static synchronized boolean sendAction(String type, String roomName, String playerName, String value) {
        if (connection == null || !connection.isOpen() || !connection.roomName.equals(roomName)) {
            return false;
        }
        if (connection.host) {
            connection.handleHostAction(type, playerName, value);
        } else {
            connection.send(type, playerName, value);
        }
        return true;
    }

    public static synchronized boolean isActive() {
        return connection != null && connection.isOpen();
    }

    public static synchronized boolean hasRelayRoom(String roomName) {
        return connection != null && connection.roomName.equals(roomName);
    }

    public static synchronized boolean isHosting() {
        return isActive() && connection.host;
    }

    public static synchronized String getStatus() {
        return status;
    }

    public static synchronized String getRelayUrl() {
        return relayUrl;
    }

    public static synchronized void stop() {
        RelayConnection current = connection;
        connection = null;
        if (current != null) {
            current.close();
        }
        status = "Room relay offline";
    }

    private static void runOnClientThread(Runnable action) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft != null) {
            minecraft.execute(action);
        } else {
            action.run();
        }
    }

    private static String configuredRelayUrl() {
        String property = System.getProperty(RELAY_PROPERTY);
        if (property != null && !property.trim().isEmpty()) {
            return displayRelayUrl(property);
        }
        String environment = System.getenv(RELAY_ENVIRONMENT);
        if (environment != null && !environment.trim().isEmpty()) {
            return displayRelayUrl(environment);
        }
        try {
            if (Files.exists(RELAY_CONFIG)) {
                return displayRelayUrl(new String(Files.readAllBytes(RELAY_CONFIG), StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
        }
        return DEFAULT_RELAY_URL;
    }

    private static void saveRelayUrl(String endpoint) {
        try {
            Files.createDirectories(RELAY_CONFIG.getParent());
            Files.write(RELAY_CONFIG, endpoint.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    private static String normalizeRelayUrl(String endpoint) {
        if (endpoint == null || endpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter the deployed relay URL");
        }
        String normalized = endpoint.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!normalized.startsWith("https://")
                && !normalized.startsWith("http://")
                && !normalized.startsWith("wss://")
                && !normalized.startsWith("ws://")) {
            normalized = "https://" + normalized;
        }
        return normalized;
    }

    private static String displayRelayUrl(String endpoint) {
        if (endpoint == null) {
            return "";
        }
        String display = endpoint.trim();
        if (display.startsWith("https://")) display = display.substring(8);
        else if (display.startsWith("http://")) display = display.substring(7);
        else if (display.startsWith("wss://")) display = display.substring(6);
        else if (display.startsWith("ws://")) display = display.substring(5);
        while (display.endsWith("/")) {
            display = display.substring(0, display.length() - 1);
        }
        return display;
    }

    private static URI roomUri(String endpoint, String roomName, String playerName, boolean host) throws URISyntaxException {
        String websocketEndpoint = endpoint;
        if (websocketEndpoint.startsWith("https://")) {
            websocketEndpoint = "wss://" + websocketEndpoint.substring(8);
        } else if (websocketEndpoint.startsWith("http://")) {
            websocketEndpoint = "ws://" + websocketEndpoint.substring(7);
        }
        String encodedRoom = urlEncode(roomName);
        String encodedPlayer = urlEncode(playerName);
        return new URI(websocketEndpoint + "/room/" + encodedRoom + "?role=" + (host ? "host" : "guest") + "&player=" + encodedPlayer);
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8").replace("+", "%20");
        } catch (Exception exception) {
            return "";
        }
    }

    private static String hostToken() {
        byte[] token = new byte[32];
        new SecureRandom().nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private static String usefulMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static class RelayConnection implements SimpleWebSocketClient.Listener {
        private final String roomName;
        private final String playerName;
        private final boolean host;
        private final CountDownLatch welcomeLatch;
        private final SimpleWebSocketClient socket;
        private volatile boolean welcomed;
        private volatile boolean closing;
        private volatile boolean preparingSeed;
        private volatile String joinError;

        private RelayConnection(String endpoint, String roomName, String playerName, boolean host) throws URISyntaxException {
            this.roomName = roomName;
            this.playerName = playerName;
            this.host = host;
            this.welcomeLatch = new CountDownLatch(1);
            this.welcomed = false;
            this.closing = false;
            this.preparingSeed = false;
            this.joinError = "Relay did not accept the room connection";
            String authorization = host ? "Bearer " + hostToken() : "";
            this.socket = new SimpleWebSocketClient(roomUri(endpoint, roomName, playerName, host), authorization, this);
        }

        private boolean open() throws IOException {
            this.socket.connect(8000);
            try {
                if (!this.welcomeLatch.await(8, TimeUnit.SECONDS)) {
                    this.joinError = "Relay room handshake timed out";
                    return false;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                this.joinError = "Relay connection was interrupted";
                return false;
            }
            return this.welcomed && this.socket.isOpen();
        }

        private boolean isOpen() {
            return this.welcomed && this.socket.isOpen();
        }

        private String getJoinError() {
            return this.joinError;
        }

        private void send(String type, String player, String value) {
            sendRaw(RoomProtocol.encode(type, this.roomName, player, value));
        }

        private void sendRaw(String message) {
            try {
                this.socket.sendText(message);
            } catch (IOException exception) {
                status = "Room relay send failed: " + usefulMessage(exception);
            }
        }

        private void sendSnapshot() {
            String snapshot = ZsgRooms.createRoomSnapshot(this.roomName);
            if (!snapshot.isEmpty()) {
                send("snapshot", this.playerName, snapshot);
            }
        }

        private void handleHostAction(String type, String player, String value) {
            if (!this.playerName.equals(player)) {
                return;
            }
            if ("start".equals(type)) {
                requestAndLaunchExactSeed();
            } else if ("filter".equals(type)) {
                ZsgRooms.applyRoomAction(type, this.roomName, player, value);
                sendSnapshot();
            } else if ("seed_change".equals(type)) {
                boolean newRequest = isNewSeedChangeRequest(player);
                boolean ready = ZsgRooms.registerSeedChangeRequest(player);
                if (newRequest) {
                    announceSeedChangeRequest(player);
                }
                sendSnapshot();
                if (ready) {
                    announceSeedChangeAgreement();
                    requestAndLaunchExactSeed();
                }
            } else if ("advancement".equals(type)) {
                handleAdvancement(player, value);
            } else if ("complete_run".equals(type)) {
                finishAndBroadcast(player, ZsgRooms.completionReason(value));
            } else if ("forfeit".equals(type)) {
                finishForfeit(player);
            } else if ("leave_room".equals(type)) {
                handlePlayerLeave(player);
            } else if ("world_ready".equals(type)) {
                handleWorldReady(player, value);
            } else {
                ZsgRooms.applyRoomAction(type, this.roomName, player, value);
                send(type, player, value);
                if ("progress".equals(type) || "reset_run".equals(type)) {
                    sendSnapshot();
                }
            }
        }

        private void handleGuestAction(String type, String player, String value) {
            if ("join_room".equals(type)) {
                Room room = ZsgRooms.getRoom(this.roomName);
                if (room == null || room.isFull() || room.getPlayer(player) != null) {
                    send("error", player, room != null && room.getPlayer(player) != null
                            ? "That player name is already connected"
                            : "The room is full or unavailable");
                    send("kick", player, "Room join was rejected");
                    return;
                }
                ZsgRooms.applyRoomAction(type, this.roomName, player, value);
                sendSnapshot();
                return;
            }
            if ("start".equals(type) || "filter".equals(type)) {
                send("error", player, "Only the host can use that control");
                return;
            }
            if ("seed_change".equals(type)) {
                boolean newRequest = isNewSeedChangeRequest(player);
                boolean ready = ZsgRooms.registerSeedChangeRequest(player);
                if (newRequest) {
                    announceSeedChangeRequest(player);
                }
                sendSnapshot();
                if (ready) {
                    announceSeedChangeAgreement();
                    requestAndLaunchExactSeed();
                }
                return;
            }
            if ("advancement".equals(type)) {
                handleAdvancement(player, value);
                return;
            }
            if ("complete_run".equals(type)) {
                finishAndBroadcast(player, ZsgRooms.completionReason(value));
                return;
            }
            if ("forfeit".equals(type)) {
                finishForfeit(player);
                return;
            }
            if ("leave_room".equals(type)) {
                handlePlayerLeave(player);
                return;
            }
            if ("world_ready".equals(type)) {
                handleWorldReady(player, value);
                return;
            }
            if (!"chat".equals(type)
                    && !"profile".equals(type)
                    && !"share_seed".equals(type)
                    && !"reset_run".equals(type)
                    && !"progress".equals(type)) {
                return;
            }

            ZsgRooms.applyRoomAction(type, this.roomName, player, value);
            if ("reset_run".equals(type)) {
                ZsgInGameActions.showRunReset(MinecraftClient.getInstance(), player);
            }
            send(type, player, value);
            if ("progress".equals(type) || "profile".equals(type) || "reset_run".equals(type)) {
                sendSnapshot();
            }
        }

        private void handleAdvancement(String player, String value) {
            boolean won = ZsgRooms.trackAdvancement(this.roomName, player, value);
            ZsgInGameActions.showRemoteAdvancement(MinecraftClient.getInstance(), player, value);
            send("advancement", player, value);
            if (won) {
                finishAndBroadcast(player, "Completed the run");
            } else {
                sendSnapshot();
            }
        }

        private void finishForfeit(String player) {
            Room room = ZsgRooms.getRoom(this.roomName);
            String winner = room == null ? null : room.findFirstOtherPlayerName(player);
            finishAndBroadcast(winner == null ? "No winner" : winner, player + " forfeited");
        }

        private void handlePlayerLeave(String player) {
            Room room = ZsgRooms.getRoom(this.roomName);
            InGame game = ZsgRooms.getGame(this.roomName);
            String winner = room == null ? null : room.findFirstOtherPlayerName(player);
            boolean decidesMatch = game != null && game.getIsInGame() && room != null && room.getPlayerCount() == 2 && winner != null;
            ZsgRooms.applyRoomAction("leave_room", this.roomName, player, "");
            if (decidesMatch) {
                finishAndBroadcast(winner, player + " left the match");
            } else {
                releaseStartIfReady();
                sendSnapshot();
            }
        }

        private void handleWorldReady(String player, String seed) {
            if (!ZsgRooms.markPlayerWorldReady(this.roomName, player, seed)) {
                return;
            }
            sendSnapshot();
            releaseStartIfReady();
        }

        private void releaseStartIfReady() {
            InGame game = ZsgRooms.getGame(this.roomName);
            if (game == null || !ZsgRooms.areAllPlayersWorldReady(this.roomName)
                    || !ZsgRooms.releaseSynchronizedStart(this.roomName, game.getSeed())) {
                return;
            }
            send("race_start", this.playerName, game.getSeed());
            ZsgRoomsClient.releaseSynchronizedStart(this.roomName, game.getSeed());
            status = "All players loaded - race started";
            sendSnapshot();
        }

        private void finishAndBroadcast(String winner, String reason) {
            String safeWinner = winner == null || winner.trim().isEmpty() ? "No winner" : winner.trim();
            if (!ZsgRooms.finishMatch(this.roomName, safeWinner, reason)) {
                return;
            }
            String value = safeWinner + "\t" + reason;
            send("match_result", this.playerName, value);
            ZsgInGameActions.showMatchResult(MinecraftClient.getInstance(), safeWinner, reason);
            sendSnapshot();
        }

        private void announceSeedChangeAgreement() {
            ZsgInGameActions.showSeedChangeAgreement(MinecraftClient.getInstance());
            send("seed_change_ready", this.playerName, "All players agreed. Preparing a new seed...");
        }

        private boolean isNewSeedChangeRequest(String player) {
            Room room = ZsgRooms.getRoom(this.roomName);
            return room != null && room.getPlayer(player) != null
                    && !room.getPlayer(player).getIsRequestingSeedChange();
        }

        private void announceSeedChangeRequest(String player) {
            if (!this.playerName.equals(player)) {
                ZsgInGameActions.showSeedChangeRequest(MinecraftClient.getInstance(), player);
            }
            send("seed_change_vote", player, player + " requested a seed change");
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
            sendSnapshot();

            ZsgSeedBridge.requestExactSeedForRoom(this.roomName, filter).whenComplete((seed, error) -> {
                this.preparingSeed = false;
                runOnClientThread(() -> {
                    if (error != null || seed == null || seed.trim().isEmpty()) {
                        String reason = error == null ? "empty seed" : usefulMessage(error);
                        status = "Seed request failed: " + reason;
                        ZsgRooms.shareChat(this.roomName, "Could not get a shared seed: " + reason);
                        sendSnapshot();
                        return;
                    }

                    ZsgRooms.prepareRoomSeed(this.roomName, seed, filter);
                    sendSnapshot();
                    ZsgInGameActions.showSeedReady(MinecraftClient.getInstance());
                    send("seed_ready", this.playerName, "New seed ready. Starting now!");
                    send("launch", this.playerName, seed);
                    ZsgRoomsClient.beginSynchronizedStart(this.roomName, seed);
                    ZsgRooms.launchRoomWithSeed(this.roomName, seed);
                    status = "Waiting for every player to load";
                    sendSnapshot();
                });
            });
        }

        @Override
        public void onText(String message) {
            Map<String, String> decoded = RoomProtocol.decode(message);
            String type = decoded.get("type");
            if ("welcome".equals(type)) {
                this.welcomed = true;
                this.welcomeLatch.countDown();
                return;
            }
            if ("error".equals(type)) {
                this.joinError = decoded.get("value");
                status = this.joinError;
                this.welcomeLatch.countDown();
                return;
            }

            if (this.host) {
                runOnClientThread(() -> handleGuestAction(type, decoded.get("player"), decoded.get("value")));
            } else {
                runOnClientThread(() -> {
                    String value = decoded.get("value");
                    if ("launch".equals(type)) {
                        ZsgRoomsClient.beginSynchronizedStart(decoded.get("room"), value);
                    }
                    ZsgRooms.applyRoomAction(type, decoded.get("room"), decoded.get("player"), value);
                    if ("race_start".equals(type)) {
                        ZsgRoomsClient.releaseSynchronizedStart(decoded.get("room"), value);
                    }
                    if ("advancement".equals(type)) {
                        ZsgInGameActions.showRemoteAdvancement(MinecraftClient.getInstance(), decoded.get("player"), value);
                    }
                    if ("seed_change_ready".equals(type)) {
                        ZsgInGameActions.showSeedChangeAgreement(MinecraftClient.getInstance());
                    }
                    if ("seed_change_vote".equals(type)
                            && !ZsgRoomsClient.localPlayerName(MinecraftClient.getInstance()).equals(decoded.get("player"))) {
                        ZsgInGameActions.showSeedChangeRequest(MinecraftClient.getInstance(), decoded.get("player"));
                    }
                    if ("reset_run".equals(type)
                            && !ZsgRoomsClient.localPlayerName(MinecraftClient.getInstance()).equals(decoded.get("player"))) {
                        ZsgInGameActions.showRunReset(MinecraftClient.getInstance(), decoded.get("player"));
                    }
                    if ("seed_ready".equals(type)) {
                        ZsgInGameActions.showSeedReady(MinecraftClient.getInstance());
                    }
                    if ("match_result".equals(type)) {
                        String[] result = value == null ? new String[0] : value.split("\\t", 2);
                        ZsgInGameActions.showMatchResult(MinecraftClient.getInstance(),
                                result.length > 0 ? result[0] : "No winner",
                                result.length > 1 ? result[1] : "Match finished");
                    }
                });
            }
        }

        @Override
        public void onClosed(String reason) {
            this.preparingSeed = false;
            this.welcomeLatch.countDown();
            if (!this.closing) {
                status = reason == null || reason.trim().isEmpty() ? "Room relay disconnected" : reason;
            }
        }

        private void close() {
            this.closing = true;
            this.socket.close();
        }
    }
}
