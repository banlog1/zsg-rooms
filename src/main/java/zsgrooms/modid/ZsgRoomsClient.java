package zsgrooms.modid;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.network.ClientSidePacketRegistry;
import net.minecraft.client.MinecraftClient;
import zsgrooms.modid.net.RoomSocketTransport;
import zsgrooms.modid.net.RoomWebSocketTransport;
import zsgrooms.modid.ui.MatchHud;
import zsgrooms.modid.ui.ZsgInGameActions;
import zsgrooms.modid.update.UpdateManager;

import java.util.HashSet;
import java.util.Set;

public class ZsgRoomsClient implements ClientModInitializer {
    private static final Set<String> REPORTED_ADVANCEMENTS = new HashSet<String>();
    private static String advancementRaceKey = "";

    @Override
    public void onInitializeClient() {
        ClientSidePacketRegistry.INSTANCE.register(ZsgRoomNetworking.ROOM_ACTION, (context, buffer) -> {
            String action = buffer.readString(64);
            String roomName = buffer.readString(64);
            String playerName = buffer.readString(64);
            String value = buffer.readString(256);
            context.getTaskQueue().execute(() -> ZsgRooms.applyRoomAction(action, roomName, playerName, value));
        });
        HudRenderCallback.EVENT.register(MatchHud::render);
        ClientTickEvents.END_CLIENT_TICK.register(ZsgInGameActions::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> UpdateManager.installOnExit());
    }

    public static void onAdvancementCompleted(String advancementId, String title) {
        String roomName = ZsgRooms.getActiveRoomName();
        InGame game = roomName == null ? null : ZsgRooms.getGame(roomName);
        if (game == null || !game.getIsInGame()) {
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

    public static void resetLocalAdvancementTracking() {
        REPORTED_ADVANCEMENTS.clear();
        advancementRaceKey = "";
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
