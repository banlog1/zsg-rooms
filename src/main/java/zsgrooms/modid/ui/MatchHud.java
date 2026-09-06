package zsgrooms.modid.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import zsgrooms.modid.InGame;
import zsgrooms.modid.Player;
import zsgrooms.modid.Room;
import zsgrooms.modid.ZsgRooms;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;

public final class MatchHud {
    private static final int PANEL_WIDTH = 166;
    private static WeakReference<Object> lastWorld = new WeakReference<Object>(null);
    private static String lastRoom;
    private static int lastRoster;
    private static long rotationStart;

    private MatchHud() {
    }

    public static void render(MatrixStack matrices, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        Room room = ZsgRooms.getActiveRoom();
        InGame game = room == null ? null : ZsgRooms.getGame(room.roomName);
        MatchHudPreferences settings = RoomUiPreferences.getMatchHud();
        if (client == null || client.world == null || client.options.hudHidden || !settings.visible
                || room == null || game == null || !game.getIsInGame()) {
            lastWorld.clear();
            return;
        }
        int roster = 1;
        for (Player player : room.players) {
            if (player != null) {
                roster = 31 * roster + Objects.hashCode(player.getName());
            }
        }
        long now = System.nanoTime() / 1_000_000L;
        if (lastWorld.get() != client.world || !Objects.equals(lastRoom, room.roomName) || lastRoster != roster) {
            rotationStart = now;
            lastWorld = new WeakReference<Object>(client.world);
            lastRoom = room.roomName;
            lastRoster = roster;
        }

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        int count = room.getPlayerCount();
        if (count == 0) {
            return;
        }
        int rows = Math.min(count, settings.rows);
        int height = panelHeight(settings, rows);
        float scale = fitScale(settings.scale, screenWidth - 16, screenHeight - 42, height);
        RoomUiPreferences.HudPosition position = RoomUiPreferences.getHudPosition();
        boolean right = position == RoomUiPreferences.HudPosition.TOP_RIGHT
                || position == RoomUiPreferences.HudPosition.BOTTOM_RIGHT;
        boolean bottom = position == RoomUiPreferences.HudPosition.BOTTOM_LEFT
                || position == RoomUiPreferences.HudPosition.BOTTOM_RIGHT;
        float x = right ? screenWidth - PANEL_WIDTH * scale - 8 : 8;
        float y = bottom ? Math.max(8, screenHeight - height * scale - 34) : 8;
        drawPanel(matrices, client, settings, room.players, game.getPlayerProgress(),
                game.getPlayerProgressLabels(), client.getSession().getUsername(),
                x, y, scale, now - rotationStart);
    }

    static int panelHeight(MatchHudPreferences settings, int rows) {
        return (settings.header ? 32 : 7) + rows * 22 + 3;
    }

    static float fitScale(int percent, int width, int height, int panelHeight) {
        return Math.max(0.01F, Math.min(percent / 100.0F,
                Math.min(width / (float) PANEL_WIDTH, height / (float) panelHeight)));
    }

    static void drawPanel(MatrixStack matrices, MinecraftClient client, MatchHudPreferences settings,
                          Player[] players, Map<String, Integer> progress, Map<String, String> labels,
                          String localName, float x, float y, float scale, long elapsedMillis) {
        int count = 0;
        int localIndex = -1;
        for (Player player : players) {
            if (player != null) {
                if (Objects.equals(localName, player.getName())) {
                    localIndex = count;
                }
                count++;
            }
        }
        int rows = Math.min(count, settings.rows);
        int height = panelHeight(settings, rows);
        matrices.push();
        matrices.translate(x, y, 0);
        matrices.scale(scale, scale, 1.0F);
        try {
            if (settings.opacity > 0) {
                int alpha = settings.opacity * 255 / 100;
                DrawableHelper.fill(matrices, 0, 0, PANEL_WIDTH, height, (alpha << 24) | 0x101114);
                if (settings.header) {
                    DrawableHelper.fill(matrices, 0, 0, PANEL_WIDTH, 2, (alpha << 24) | 0xE6C64A);
                }
            }
            if (settings.header) {
                drawCentered(client, matrices, "Current Match", 7, 0xFFFFE36B);
                drawCentered(client, matrices, "ZSG Room (" + count + ")", 18, 0xFFD2D2D2);
            }
            int rowY = settings.header ? 32 : 7;
            for (int row = 0; row < rows; row++) {
                int index = HudPlayerRotation.playerIndex(count, localIndex, rows, settings.pinSelf,
                        elapsedMillis, settings.rotationSeconds, row);
                Player player = occupiedPlayer(players, index);
                if (player == null) {
                    continue;
                }
                String name = player.getName();
                int stage = progress.getOrDefault(name, 0);
                String label = labels.getOrDefault(name, "Starting");
                if (settings.heads) {
                    PlayerHeadRenderer.draw(matrices, client, name, player.getUuid(), 7, rowY, 12);
                }
                int textX = settings.heads ? 24 : 7;
                int textWidth = PANEL_WIDTH - textX - 7;
                int nameColor = player.getIsHost() ? 0xFFFFFFFF : 0xFFFFD85A;
                client.textRenderer.drawWithShadow(matrices, trimToWidth(client, name, textWidth),
                        textX, rowY + 1, nameColor);
                String stageText = settings.progressNumbers && stage > 0 ? stage + "/8  " + label : label;
                client.textRenderer.drawWithShadow(matrices, trimToWidth(client, stageText, textWidth),
                        textX, rowY + 11, 0xFFB8B8B8);
                rowY += 22;
            }
        } finally {
            matrices.pop();
        }
    }

    private static Player occupiedPlayer(Player[] players, int index) {
        for (Player player : players) {
            if (player != null && index-- == 0) {
                return player;
            }
        }
        return null;
    }

    private static void drawCentered(MinecraftClient client, MatrixStack matrices, String text, int y, int color) {
        client.textRenderer.drawWithShadow(matrices, text, (PANEL_WIDTH - client.textRenderer.getWidth(text)) / 2, y, color);
    }

    private static String trimToWidth(MinecraftClient client, String text, int width) {
        String safeText = text == null ? "" : text;
        if (client.textRenderer.getWidth(safeText) <= width) {
            return safeText;
        }
        return client.textRenderer.trimToWidth(safeText, Math.max(0, width - client.textRenderer.getWidth("..."))) + "...";
    }
}
