package zsgrooms.modid.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.SaveLevelScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import zsgrooms.modid.Room;
import zsgrooms.modid.InGame;
import zsgrooms.modid.ZsgRooms;
import zsgrooms.modid.ZsgRoomsClient;
import zsgrooms.modid.ZsgSeedBridge;

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
            showSeedChangeRequest(client, localPlayerName(client));
            client.openScreen(null);
            client.mouse.lockCursor();
        }
    }

    public static void resetCurrentRun(MinecraftClient client) {
        String roomName = ZsgRooms.getActiveRoomName();
        InGame game = roomName == null ? null : ZsgRooms.getGame(roomName);
        if (client == null || game == null || !game.getIsInGame()) {
            return;
        }

        String playerName = localPlayerName(client);
        ZsgRoomsClient.sendRoomAction("reset_run", roomName, "");
        ZsgRoomsClient.resetLocalAdvancementTracking();
        showRunReset(client, playerName);
        client.openScreen(null);
        client.mouse.lockCursor();

        if (!ZsgSeedBridge.launchSeedWithAtum(game.getSeed())) {
            client.inGameHud.getChatHud().addMessage(roomMessage(
                    "Could not reset the current seed.", Formatting.RED));
        }
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
        MutableText message = new LiteralText(player + " made the advancement ").formatted(Formatting.WHITE)
                .append(new LiteralText("[" + title + "]").formatted(Formatting.GREEN));
        client.inGameHud.getChatHud().addMessage(message);
    }

    public static void showSeedChangeAgreement(MinecraftClient client) {
        if (client != null && client.inGameHud != null) {
            client.inGameHud.getChatHud().addMessage(roomMessage(
                    "All players agreed. Preparing a new seed...", Formatting.GOLD));
        }
    }

    public static void showSeedChangeRequest(MinecraftClient client, String player) {
        if (client != null && client.inGameHud != null) {
            String name = player == null || player.trim().isEmpty() ? "A player" : player.trim();
            client.inGameHud.getChatHud().addMessage(roomMessage(
                    name + " requested a seed change.", Formatting.YELLOW));
        }
    }

    public static void showRunReset(MinecraftClient client, String player) {
        if (client != null && client.inGameHud != null) {
            String name = player == null || player.trim().isEmpty() ? "A player" : player.trim();
            client.inGameHud.getChatHud().addMessage(roomMessage(
                    name + " reset their run on the current seed.", Formatting.GOLD));
        }
    }

    public static void showSeedReady(MinecraftClient client) {
        if (client != null && client.inGameHud != null) {
            client.inGameHud.getChatHud().addMessage(roomMessage(
                    "New seed ready. Starting now!", Formatting.GREEN));
        }
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

    private static MutableText roomMessage(String message, Formatting color) {
        return new LiteralText("[ZSG Room] ").formatted(Formatting.AQUA)
                .append(new LiteralText(message).formatted(color));
    }
}
