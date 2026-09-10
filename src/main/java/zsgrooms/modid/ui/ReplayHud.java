package zsgrooms.modid.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import zsgrooms.modid.replay.ReplayPrototype;

public final class ReplayHud {
    private ReplayHud() {
    }

    public static void render(MatrixStack matrices, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        // Playback has a client world but no integrated server, just like a remote server connection.
        if (client.world == null || client.getServer() == null || client.options.hudHidden
                || !ReplayPrototype.getPreferences().showRecordingHud) return;
        String status = ReplayPrototype.getHudStatus();
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
