package zsgrooms.modid.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.WorldGenerationProgressTracker;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public final class RoomLoadingIndicator extends DrawableHelper {
    private static final Identifier LOGO = new Identifier("zsg-rooms", "textures/gui/loading_logo.png");

    private RoomLoadingIndicator() { }

    public static void render(MatrixStack matrices, MinecraftClient client, int width, int height,
                              WorldGenerationProgressTracker tracker, Object2IntMap<ChunkStatus> colors) {
        LoadingProgressPosition position = RoomUiPreferences.getLoadingProgressPosition();
        int mapSize = tracker.getSize() * 2;
        int centerX = position.mapX(width, mapSize);
        int centerY = position.mapY(height, mapSize);
        int size = LoadingLogoLayout.logoSize(mapSize);
        int x = centerX - size / 2;
        int y = centerY - size / 2;
        int percent = MathHelper.clamp(tracker.getProgressPercentage(), 0, 100);
        RenderSystem.disableDepthTest();
        drawBacking(matrices, tracker, colors, centerX, centerY);

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

    static void drawBacking(MatrixStack matrices, WorldGenerationProgressTracker tracker,
                            Object2IntMap<ChunkStatus> colors, int centerX, int centerY) {
        int count = tracker.getSize(), mapSize = count * 2;
        int size = LoadingLogoLayout.logoSize(mapSize);
        int left = centerX - mapSize / 2, top = centerY - mapSize / 2;
        int x = centerX - size / 2, y = centerY - size / 2;
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        Matrix4f matrix = matrices.peek().getModel();
        RenderSystem.enableBlend();
        RenderSystem.disableTexture();
        RenderSystem.defaultBlendFunc();
        buffer.begin(7, VertexFormats.POSITION_COLOR);
        for (int cx = 0; cx < count; cx++) for (int cz = 0; cz < count; cz++) {
            int px = left + cx * 2, py = top + cz * 2;
            // Omit only tiles completely hidden by the opaque logo backing.
            if (px >= x && py >= y && px + 2 <= x + size && py + 2 <= y + size) continue;
            quad(buffer, matrix, px, py, px + 2, py + 2, colors.getInt(tracker.getChunkStatus(cx, cz)));
        }
        quad(buffer, matrix, x, y, x + size, y + size, 0x101010);
        buffer.end();
        BufferRenderer.draw(buffer);
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
    }

    private static void quad(BufferBuilder buffer, Matrix4f matrix, int x, int y, int right, int bottom, int color) {
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        buffer.vertex(matrix, right, y, 0).color(r, g, b, 255).next();
        buffer.vertex(matrix, x, y, 0).color(r, g, b, 255).next();
        buffer.vertex(matrix, x, bottom, 0).color(r, g, b, 255).next();
        buffer.vertex(matrix, right, bottom, 0).color(r, g, b, 255).next();
    }
}
