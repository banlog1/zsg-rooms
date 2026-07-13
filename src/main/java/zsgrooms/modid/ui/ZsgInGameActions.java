package zsgrooms.modid.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.SaveLevelScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.CreditsScreen;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import zsgrooms.modid.Room;
import zsgrooms.modid.InGame;
import zsgrooms.modid.ZsgRooms;
import zsgrooms.modid.ZsgRoomsClient;
import zsgrooms.modid.ZsgSeedBridge;

public class ZsgInGameActions {
    private static final String EXIT_PORTAL_RESULT = "Beat the seed";
    private static final String EXIT_PORTAL_TIME_PREFIX = EXIT_PORTAL_RESULT + " in ";
    private static final String EXIT_PORTAL_TIME_SUFFIX = " IGT";
    private static int returnTicks = -1;
    private static String pendingWinner;
    private static String pendingReason;
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
        if (client == null || client.inGameHud == null) {
            return;
        }

        String result = reason == null || reason.trim().isEmpty() ? "Match finished" : reason.trim();
        if (localPlayerName(client).equals(winner) && isExitPortalResult(result)
                && !isReadyForOverworldResult(client)) {
            pendingWinner = winner;
            pendingReason = result;
            if (client.currentScreen instanceof CreditsScreen) {
                client.currentScreen.onClose();
            }
            return;
        }

        displayMatchResult(client, winner, result);
    }

    static String matchResultSubtitle(String result) {
        if (result.startsWith(EXIT_PORTAL_TIME_PREFIX) && result.endsWith(EXIT_PORTAL_TIME_SUFFIX)) {
            int timeEnd = result.length() - EXIT_PORTAL_TIME_SUFFIX.length();
            String time = result.substring(EXIT_PORTAL_TIME_PREFIX.length(), timeEnd).trim();
            if (!time.isEmpty()) {
                return "Final IGT: " + time;
            }
        }
        if (EXIT_PORTAL_RESULT.equals(result)) {
            return "Seed completed";
        }
        return result;
    }

    private static boolean isExitPortalResult(String result) {
        return EXIT_PORTAL_RESULT.equals(result)
                || result.startsWith(EXIT_PORTAL_TIME_PREFIX) && result.endsWith(EXIT_PORTAL_TIME_SUFFIX);
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
        if (pendingWinner != null && isReadyForOverworldResult(client)) {
            String winner = pendingWinner;
            String reason = pendingReason;
            pendingWinner = null;
            pendingReason = null;
            displayMatchResult(client, winner, reason);
        }
        if (returnTicks < 0) {
            return;
        }
        returnTicks--;
        if (returnTicks == 0) {
            returnTicks = -1;
            returnToRoom(client);
        }
    }

    private static void displayMatchResult(MinecraftClient client, String winner, String result) {
        String localName = localPlayerName(client);
        boolean localVictory = localName.equals(winner);
        String title = localVictory ? "Victory!" : winner + " Wins!";
        MutableText titleText = new LiteralText(title).formatted(
                localVictory ? Formatting.GOLD : Formatting.RED);
        MutableText subtitleText = new LiteralText(matchResultSubtitle(result)).formatted(
                isExitPortalResult(result) ? Formatting.GREEN : Formatting.YELLOW);
        MutableText winnerText = new LiteralText("Winner: ").formatted(Formatting.GRAY)
                .append(new LiteralText(winner).formatted(Formatting.GOLD));
        client.inGameHud.setTitles(null, null, 5, 80, 15);
        client.inGameHud.setTitles(titleText, null, -1, -1, -1);
        client.inGameHud.setTitles(null, subtitleText, -1, -1, -1);
        client.inGameHud.setOverlayMessage(winnerText, false);
        returnTicks = 90;
    }

    private static boolean isReadyForOverworldResult(MinecraftClient client) {
        return client != null
                && client.world != null
                && client.player != null
                && World.OVERWORLD.equals(client.world.getRegistryKey())
                && !(client.currentScreen instanceof CreditsScreen)
                && !(client.currentScreen instanceof DownloadingTerrainScreen);
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
