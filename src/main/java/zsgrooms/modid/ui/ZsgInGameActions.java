package zsgrooms.modid.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.SaveLevelScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.LiteralText;
import zsgrooms.modid.Room;
import zsgrooms.modid.ZsgRooms;
import zsgrooms.modid.ZsgRoomsClient;

public class ZsgInGameActions {
    private static int returnTicks = -1;
    private ZsgInGameActions() {
    }

    public static void requestSeedChange(MinecraftClient client) {
        String roomName = ZsgRooms.getActiveRoomName();
        if (roomName != null) {
            ZsgRoomsClient.sendRoomAction("seed_change", roomName, "");
        } else {
            ZsgRooms.requestSeedChange(localPlayerName(client));
        }
        if (client != null && client.inGameHud != null) {
            client.inGameHud.setOverlayMessage(new LiteralText("Seed change requested"), false);
        }
        returnToRoom(client);
    }

    public static void forfeit(MinecraftClient client) {
        String roomName = ZsgRooms.getActiveRoomName();
        if (roomName != null) {
            ZsgRoomsClient.sendRoomAction("forfeit", roomName, "");
        } else {
            showMatchResult(client, ZsgRooms.forfeitActiveRoom(localPlayerName(client)), "Forfeit");
        }
    }

    public static void showMatchResult(MinecraftClient client, String winner, String reason) {
        if (client != null && client.inGameHud != null) {
            String localName = localPlayerName(client);
            String title = localName.equals(winner) ? "Victory!" : "Match Over";
            client.inGameHud.setTitles(new LiteralText(title), new LiteralText("Winner: " + winner), 5, 60, 10);
            client.inGameHud.setOverlayMessage(new LiteralText(reason), false);
            returnTicks = 70;
        }
    }

    public static void showRemoteAdvancement(MinecraftClient client, String player, String value) {
        if (client == null || client.inGameHud == null || player == null || player.equals(localPlayerName(client))) {
            return;
        }
        String[] parts = value == null ? new String[0] : value.split("\\t", 2);
        String title = parts.length > 1 ? parts[1] : (parts.length > 0 ? parts[0] : "advancement");
        client.inGameHud.getChatHud().addMessage(new LiteralText(player + " made the advancement [" + title + "]"));
    }

    public static void tick(MinecraftClient client) {
        if (returnTicks < 0) {
            return;
        }
        returnTicks--;
        if (returnTicks == 0) {
            returnTicks = -1;
            returnToRoom(client);
        }
    }

    public static void returnToRoom(MinecraftClient client) {
        String roomName = ZsgRooms.getActiveRoomName();
        if (client == null || roomName == null) {
            return;
        }
        if (client.world != null) {
            client.world.disconnect();
            client.disconnect(new SaveLevelScreen(new LiteralText("Returning to room")));
        }
        client.openScreen(new RoomLobbyScreen(new RoomMenuScreen(new TitleScreen()), roomName));
    }

    public static boolean hasActiveRoom() {
        return ZsgRooms.getActiveRoom() != null;
    }

    public static boolean isSoloRoom() {
        Room room = ZsgRooms.getActiveRoom();
        return room != null && room.getPlayerCount() <= 1;
    }

    private static String localPlayerName(MinecraftClient client) {
        return ZsgRoomsClient.localPlayerName(client);
    }
}
