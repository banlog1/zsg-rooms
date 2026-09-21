// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Matrix4f;

/** A depth-tested, camera-facing pause symbol; never visible through terrain. */
final class PauseIndicator extends RenderPhase {
    private static final int OUTLINE = 0xFF201B13;
    private static final int AMBER = 0xFFFFD080;
    private static final int[] RING_X = {0, 4, 6, 4, 0, -4, -6, -4};
    private static final int[] RING_Y = {-6, -4, 0, 4, 6, 4, 0, -4};
    private static final RenderLayer LAYER = RenderLayer.of("zsg_replay_pause", VertexFormats.POSITION_COLOR,
            7, 256, false, true, RenderLayer.MultiPhaseParameters.builder()
                    .texture(NO_TEXTURE).transparency(TRANSLUCENT_TRANSPARENCY).cull(DISABLE_CULLING)
                    .depthTest(LEQUAL_DEPTH_TEST).writeMaskState(COLOR_MASK).fog(NO_FOG).build(false));

    private PauseIndicator() { super("zsg_replay_pause", () -> {}, () -> {}); }

    static void renderAbove(PlayerEntity player, MatrixStack matrices, VertexConsumerProvider consumers) {
        renderAbove(player, matrices, consumers, false, 0);
    }

    static void renderAbove(PlayerEntity player, MatrixStack matrices, VertexConsumerProvider consumers,
                            boolean loading, int time) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (player.isInvisible() || client.getEntityRenderManager().getSquaredDistanceToCamera(player) > 4096) return;
        matrices.push();
        try {
            // Clear the vanilla nameplate instead of drawing over the player's name.
            matrices.translate(0, player.getHeight() + 1.05, 0);
            matrices.multiply(client.getEntityRenderManager().getRotation());
            matrices.scale(-0.025F, -0.025F, 0.025F);
            Matrix4f transform = matrices.peek().getModel();
            VertexConsumer vertices = consumers.getBuffer(LAYER);
            Rectangle rectangle = (left, top, right, bottom, color) -> quad(vertices, transform, left, top, right, bottom, color);
            if (loading) ring(rectangle, time); else bars(rectangle);
        } finally { matrices.pop(); }
    }

    static void renderHud(MatrixStack matrices, int x, int y) {
        bars((left, top, right, bottom, color) -> DrawableHelper.fill(matrices,
                x + left, y + top, x + right, y + bottom, color));
    }

    static void renderLoadingHud(MatrixStack matrices, int x, int y, int time) {
        ring((left, top, right, bottom, color) -> DrawableHelper.fill(matrices,
                x + left, y + top, x + right, y + bottom, color), time);
    }

    private static void ring(Rectangle rectangle, int time) {
        // Replay time keeps the animation stable when paused or seeking backwards.
        int head = Math.floorMod(time / 100, 8);
        for (int i = 0; i < 8; i++) {
            int x = RING_X[i], y = RING_Y[i];
            int age = Math.floorMod(head - i, 8);
            int r = 150 - age * 14, g = 235 - age * 20, b = 255 - age * 20;
            int color = 0xFF000000 | r << 16 | g << 8 | b;
            rectangle.draw(x - 2, y - 2, x + 2, y - 1, OUTLINE);
            rectangle.draw(x - 2, y + 1, x + 2, y + 2, OUTLINE);
            rectangle.draw(x - 2, y - 1, x - 1, y + 1, OUTLINE);
            rectangle.draw(x + 1, y - 1, x + 2, y + 1, OUTLINE);
            rectangle.draw(x - 1, y - 1, x + 1, y + 1, color);
        }
    }

    private static void bars(Rectangle rectangle) {
        // Each outline is four strips, keeping the colored faces coplanar without z-fighting.
        for (int x = -6; x <= 2; x += 8) {
            rectangle.draw(x - 1, -7, x + 5, -6, OUTLINE);
            rectangle.draw(x - 1, 6, x + 5, 7, OUTLINE);
            rectangle.draw(x - 1, -6, x, 6, OUTLINE);
            rectangle.draw(x + 4, -6, x + 5, 6, OUTLINE);
            rectangle.draw(x, -6, x + 4, 6, AMBER);
        }
    }

    private static void quad(VertexConsumer vertices, Matrix4f matrix, int left, int top, int right, int bottom, int color) {
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255, a = color >>> 24;
        vertices.vertex(matrix, left, top, 0).color(r, g, b, a).next();
        vertices.vertex(matrix, left, bottom, 0).color(r, g, b, a).next();
        vertices.vertex(matrix, right, bottom, 0).color(r, g, b, a).next();
        vertices.vertex(matrix, right, top, 0).color(r, g, b, a).next();
    }

    private interface Rectangle { void draw(int left, int top, int right, int bottom, int color); }
}
