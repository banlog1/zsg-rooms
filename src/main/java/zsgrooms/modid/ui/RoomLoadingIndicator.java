package zsgrooms.modid.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.WorldGenerationProgressTracker;
import net.minecraft.client.gui.screen.LevelLoadingScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public final class RoomLoadingIndicator extends DrawableHelper {
    private static final Identifier LOGO = new Identifier("zsg-rooms", "textures/gui/loading_logo.png");

    private RoomLoadingIndicator() { }

    public static void render(MatrixStack matrices, MinecraftClient client, int width, int height,
                              WorldGenerationProgressTracker tracker) {
        LoadingProgressPosition position = RoomUiPreferences.getLoadingProgressPosition();
        int mapSize = tracker.getSize() * 2;
        int centerX = position.mapX(width, mapSize);
        int centerY = position.mapY(height, mapSize);
        int size = LoadingLogoLayout.logoSize(mapSize);
        int x = centerX - size / 2;
        int y = centerY - size / 2;
        int percent = MathHelper.clamp(tracker.getProgressPercentage(), 0, 100);
        RenderSystem.disableDepthTest();
        LevelLoadingScreen.drawChunkMap(matrices, tracker, centerX, centerY, 2, 0);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        client.getTextureManager().bindTexture(LOGO);
        RenderSystem.color4f(1, 1, 1, 0.25F);
        drawTexture(matrices, x, y, size, size, 0, 0, 512, 512, 512, 512);
        RenderSystem.color4f(1, 1, 1, 1);
        int filled = LoadingLogoLayout.filledHeight(percent, size);
        if (filled > 0) {
            int offset = size - filled;
            float sourceY = offset * 512.0F / size;
            // Crop the reveal without stretching or moving the logo as progress changes.
            drawTexture(matrices, x, y + offset, size, filled, 0, sourceY,
                    512, Math.round(filled * 512.0F / size), 512, 512);
        }
        RenderSystem.disableBlend();
        String label = percent + "%";
        client.textRenderer.drawWithShadow(matrices, label, centerX - client.textRenderer.getWidth(label) / 2,
                position.textY(height, mapSize), 0xFFFFFF);
    }
}
