package zsgrooms.modid.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import zsgrooms.modid.InGame;
import zsgrooms.modid.Room;
import zsgrooms.modid.ZsgRooms;

import java.util.Map;

public final class MatchHud {
    private static final int PANEL_WIDTH = 166;

    private MatchHud() {
    }

    public static void render(MatrixStack matrices, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        Room room = ZsgRooms.getActiveRoom();
        InGame game = room == null ? null : ZsgRooms.getGame(room.roomName);
        if (client == null || client.options.hudHidden || room == null || game == null || !game.getIsInGame()) {
            return;
        }

        Map<String, Integer> progress = game.getPlayerProgress();
        Map<String, String> labels = game.getPlayerProgressLabels();
        int rows = Math.max(1, room.getPlayerCount());
        int panelHeight = 31 + rows * 22;
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        RoomUiPreferences.HudPosition position = RoomUiPreferences.getHudPosition();
        boolean right = position == RoomUiPreferences.HudPosition.TOP_RIGHT
                || position == RoomUiPreferences.HudPosition.BOTTOM_RIGHT;
        boolean bottom = position == RoomUiPreferences.HudPosition.BOTTOM_LEFT
                || position == RoomUiPreferences.HudPosition.BOTTOM_RIGHT;
        int x = right ? screenWidth - PANEL_WIDTH - 8 : 8;
        int y = bottom ? Math.max(8, screenHeight - panelHeight - 34) : 8;

        DrawableHelper.fill(matrices, x, y, x + PANEL_WIDTH, y + panelHeight, 0xB0101114);
        DrawableHelper.fill(matrices, x, y, x + PANEL_WIDTH, y + 2, 0xFFE6C64A);
        drawCentered(client, matrices, "Current Match", x, y + 7, 0xFFFFE36B);
        drawCentered(client, matrices, "ZSG Room", x, y + 18, 0xFFD2D2D2);

        int rowY = y + 32;
        for (zsgrooms.modid.Player player : room.players) {
            if (player == null) {
                continue;
            }
            String name = player.getName();
            int stage = progress.containsKey(name) ? progress.get(name) : 0;
            String label = labels.containsKey(name) ? labels.get(name) : "Starting";
            int headX = x + 7;
            int headY = rowY;
            int headSize = 12;
            DrawableHelper.fill(matrices, headX - 1, headY - 1,
                    headX + headSize + 1, headY + headSize + 1, 0xCC050505);
            PlayerHeadRenderer.draw(matrices, client, name, player.getUuid(), headX, headY, headSize);

            int textX = headX + headSize + 5;
            int textWidth = x + PANEL_WIDTH - textX - 7;
            int nameColor = player.getIsHost() ? 0xFFFFFFFF : 0xFFFFD85A;
            client.textRenderer.drawWithShadow(matrices, trimToWidth(client, name, textWidth),
                    textX, rowY + 1, nameColor);
            String stageText = stage > 0 ? stage + "/8  " + label : label;
            client.textRenderer.drawWithShadow(matrices, trimToWidth(client, stageText, textWidth),
                    textX, rowY + 11, 0xFFB8B8B8);
            rowY += 22;
        }
    }

    private static void drawCentered(MinecraftClient client, MatrixStack matrices, String text, int x, int y, int color) {
        int textX = x + (PANEL_WIDTH - client.textRenderer.getWidth(text)) / 2;
        client.textRenderer.drawWithShadow(matrices, text, textX, y, color);
    }

    private static String trimToWidth(MinecraftClient client, String text, int width) {
        String safeText = text == null ? "" : text;
        if (client.textRenderer.getWidth(safeText) <= width) {
            return safeText;
        }
        String ellipsis = "...";
        while (!safeText.isEmpty() && client.textRenderer.getWidth(safeText + ellipsis) > width) {
            safeText = safeText.substring(0, safeText.length() - 1);
        }
        return safeText + ellipsis;
    }
}
