package zsgrooms.modid;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.network.ClientSidePacketRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import zsgrooms.modid.net.RoomSocketTransport;
import zsgrooms.modid.net.RoomWebSocketTransport;
import zsgrooms.modid.ui.MatchHud;
import zsgrooms.modid.ui.SynchronizedStartScreen;
import zsgrooms.modid.ui.ZsgInGameActions;
import zsgrooms.modid.update.UpdateManager;

import java.util.HashSet;
import java.util.Set;

public class ZsgRoomsClient implements ClientModInitializer {
    private static final Set<String> REPORTED_ADVANCEMENTS = new HashSet<String>();
    private static String advancementRaceKey = "";
    private static String completedRaceKey = "";
    private static boolean wasInGame;
    private static String synchronizedRoomName = "";
    private static String synchronizedSeed = "";
    private static Object worldBeforeLaunch;
    private static boolean awaitingSynchronizedStart;
    private static boolean worldReadySent;

    @Override
    public void onInitializeClient() {
        ClientSidePacketRegistry.INSTANCE.register(ZsgRoomNetworking.ROOM_ACTION, (context, buffer) -> {
            String action = buffer.readString(64);
            String roomName = buffer.readString(64);
            String playerName = buffer.readString(64);
            String value = buffer.readString(256);
            context.getTaskQueue().execute(() -> {
                if ("launch".equals(action)) {
                    beginSynchronizedStart(roomName, value);
                }
                ZsgRooms.applyRoomAction(action, roomName, playerName, value);
                if ("race_start".equals(action)) {
                    releaseSynchronizedStart(roomName, value);
                }
            });
        });
        HudRenderCallback.EVENT.register(MatchHud::render);
        ClientTickEvents.END_CLIENT_TICK.register(ZsgRoomsClient::tickClient);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> UpdateManager.installOnExit());
    }

    public static void onAdvancementCompleted(String advancementId, String title) {
        String roomName = ZsgRooms.getActiveRoomName();
        InGame game = roomName == null ? null : ZsgRooms.getGame(roomName);
        if (game == null || !game.getIsInGame() || !game.isSynchronizedStartReleased()) {
            return;
        }
        String raceKey = roomName + "|" + game.getSeed();
        if (!raceKey.equals(advancementRaceKey)) {
            REPORTED_ADVANCEMENTS.clear();
            advancementRaceKey = raceKey;
        }
        if (REPORTED_ADVANCEMENTS.add(advancementId)) {
            sendRoomAction("advancement", roomName, advancementId + "\t" + title);
        }
    }

    public static void onEndExitPortalEntered() {
        String roomName = ZsgRooms.getActiveRoomName();
        InGame game = roomName == null ? null : ZsgRooms.getGame(roomName);
        if (game == null || !game.getIsInGame() || !game.isSynchronizedStartReleased()) {
            return;
        }
        String raceKey = roomName + "|" + game.getSeed();
        if (raceKey.equals(completedRaceKey)) {
            return;
        }
        completedRaceKey = raceKey;
        String time = SpeedRunIgtBridge.completedInGameTime();
        String result = time.isEmpty() ? "Beat the seed" : "Beat the seed in " + time + " IGT";
        sendRoomAction("complete_run", roomName, result);
    }

    public static void resetLocalAdvancementTracking() {
        REPORTED_ADVANCEMENTS.clear();
        advancementRaceKey = "";
        completedRaceKey = "";
    }

    private static void tickClient(MinecraftClient client) {
        String roomName = ZsgRooms.getActiveRoomName();
        InGame game = roomName == null ? null : ZsgRooms.getGame(roomName);
        boolean inGame = game != null && game.getIsInGame();
        if (inGame && !wasInGame) {
            resetLocalAdvancementTracking();
        }
        wasInGame = inGame;
        tickSynchronizedStart(client, roomName, game);
        ZsgInGameActions.tick(client);
    }

    public static void beginSynchronizedStart(String roomName, String seed) {
        MinecraftClient client = MinecraftClient.getInstance();
        synchronizedRoomName = roomName == null ? "" : roomName;
        synchronizedSeed = seed == null ? "" : seed;
        worldBeforeLaunch = client == null ? null : client.world;
        awaitingSynchronizedStart = !synchronizedRoomName.isEmpty() && !synchronizedSeed.isEmpty();
        worldReadySent = false;
    }

    public static void releaseSynchronizedStart(String roomName, String seed) {
        if (!awaitingSynchronizedStart || !synchronizedRoomName.equals(roomName)
                || !synchronizedSeed.equals(seed)) {
            return;
        }
        awaitingSynchronizedStart = false;
        worldReadySent = false;
        worldBeforeLaunch = null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }
        client.execute(() -> {
            if (client.currentScreen instanceof SynchronizedStartScreen) {
                client.openScreen(null);
                if (client.mouse != null) {
                    client.mouse.lockCursor();
                }
            }
            if (client.inGameHud != null) {
                client.inGameHud.setOverlayMessage(new LiteralText("GO!").formatted(Formatting.GREEN), false);
                if (client.inGameHud.getChatHud() != null) {
                    client.inGameHud.getChatHud().addMessage(new LiteralText("[ZSG Room] ").formatted(Formatting.AQUA)
                            .append(new LiteralText("All players loaded. The race has started!").formatted(Formatting.GREEN)));
                }
            }
        });
    }

    private static void tickSynchronizedStart(MinecraftClient client, String roomName, InGame game) {
        if (!awaitingSynchronizedStart) {
            return;
        }
        if (game == null || !game.getIsInGame() || !synchronizedRoomName.equals(roomName)
                || !synchronizedSeed.equals(game.getSeed())) {
            cancelSynchronizedStart(client);
            return;
        }
        if (game.isSynchronizedStartReleased()) {
            releaseSynchronizedStart(synchronizedRoomName, synchronizedSeed);
            return;
        }
        if (!worldReadySent && isNewWorldPlayable(client)) {
            worldReadySent = true;
            client.openScreen(new SynchronizedStartScreen(synchronizedRoomName));
            sendRoomAction("world_ready", synchronizedRoomName, synchronizedSeed);
            if (game.isSynchronizedStartReleased()) {
                releaseSynchronizedStart(synchronizedRoomName, synchronizedSeed);
            }
        }
    }

    private static boolean isNewWorldPlayable(MinecraftClient client) {
        return client != null && client.world != null && client.player != null
                && client.world != worldBeforeLaunch
                && client.currentScreen == null;
    }

    private static void cancelSynchronizedStart(MinecraftClient client) {
        awaitingSynchronizedStart = false;
        worldReadySent = false;
        worldBeforeLaunch = null;
        if (client != null && client.currentScreen instanceof SynchronizedStartScreen) {
            client.openScreen(null);
        }
    }

    public static void sendRoomAction(String action, String roomName, String value) {
        MinecraftClient client = MinecraftClient.getInstance();
        String playerName = localPlayerName(client);
        if (RoomWebSocketTransport.sendAction(action, roomName, playerName, value)) {
            return;
        }
        if (RoomWebSocketTransport.hasRelayRoom(roomName)) {
            return;
        }
        if (RoomSocketTransport.sendAction(action, roomName, playerName, value)) {
            return;
        }
        if (client != null && client.getNetworkHandler() != null && ClientSidePacketRegistry.INSTANCE.canServerReceive(ZsgRoomNetworking.ROOM_ACTION)) {
            ClientSidePacketRegistry.INSTANCE.sendToServer(ZsgRoomNetworking.ROOM_ACTION, ZsgRoomNetworking.packet(action, roomName, playerName, value));
        } else {
            ZsgRooms.applyRoomAction(action, roomName, playerName, value);
        }
    }

    public static String localPlayerName(MinecraftClient client) {
        if (client != null && client.player != null && client.player.getEntityName() != null) {
            return client.player.getEntityName();
        }
        if (client != null && client.getSession() != null && client.getSession().getUsername() != null) {
            return client.getSession().getUsername();
        }
        return "host";
    }

    public static String localPlayerUuid(MinecraftClient client) {
        if (client != null && client.getSession() != null && client.getSession().getProfile() != null
                && client.getSession().getProfile().getId() != null) {
            return client.getSession().getProfile().getId().toString();
        }
        return "";
    }
}
