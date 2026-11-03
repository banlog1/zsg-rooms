package zsgrooms.modid.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import zsgrooms.modid.replay.ReplayPrototype;

public final class ReplayHud {
    private static String healthText;
    private static int healthWidth;
    private static java.util.List<net.minecraft.text.StringRenderable> healthLines = java.util.Collections.emptyList();
    private ReplayHud() {
    }

    public static void render(MatrixStack matrices, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        // Playback has a client world but no integrated server, just like a remote server connection.
        if (client.world == null || client.getServer() == null || client.options.hudHidden) return;
        boolean showBadge = ReplayPrototype.getPreferences().showRecordingHud;
        if (!showBadge && !ReplayPrototype.getPreferences().showCaptureHealth) return;
        String status = showBadge ? ReplayPrototype.getHudStatus() : null;
        String health = ReplayPrototype.getCaptureHealthStatus();
        if (health != null) {
            int available = Math.max(32, Math.min(260, client.getWindow().getScaledWidth() / 2 - 16));
            if (!health.equals(healthText) || available != healthWidth) {
                healthText = health;
                healthWidth = available;
                healthLines = client.textRenderer.wrapLines(new net.minecraft.text.LiteralText(health), available);
            }
            int y = status == null ? 8 : 25;
            for (net.minecraft.text.StringRenderable line : healthLines) {
                int lineWidth = client.textRenderer.getWidth(line.getString());
                int lineX = RoomUiPreferences.getHudPosition() == RoomUiPreferences.HudPosition.TOP_RIGHT
                        ? 8 : client.getWindow().getScaledWidth() - lineWidth - 8;
                DrawableHelper.fill(matrices, lineX - 2, y - 2, lineX + lineWidth + 2, y + 10, 0xAA101114);
                client.textRenderer.drawWithShadow(matrices, line.getString(), lineX, y, 0xBBDEDB);
                y += 12;
            }
        }
        if (status == null) return;
        int width = client.textRenderer.getWidth(status) + 16;
        // Keep the recorder badge away from the selected top match-HUD corner.
        int x = RoomUiPreferences.getHudPosition() == RoomUiPreferences.HudPosition.TOP_RIGHT
                ? 8 : client.getWindow().getScaledWidth() - width - 8;
        DrawableHelper.fill(matrices, x, 6, x + width, 21, 0xAA101114);
        DrawableHelper.fill(matrices, x + 4, 11, x + 8, 15,
                ReplayPrototype.isRecording() ? 0xFFFF5555 : 0xFFFFCC66);
        client.textRenderer.drawWithShadow(matrices, status, x + 12, 10, 0xFFFFFF);
    }
}
