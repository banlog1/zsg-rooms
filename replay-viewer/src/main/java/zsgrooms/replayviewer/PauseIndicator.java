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
    private static final RenderLayer LAYER = RenderLayer.of("zsg_replay_pause", VertexFormats.POSITION_COLOR,
            7, 256, false, true, RenderLayer.MultiPhaseParameters.builder()
                    .texture(NO_TEXTURE).transparency(TRANSLUCENT_TRANSPARENCY).cull(DISABLE_CULLING)
                    .depthTest(LEQUAL_DEPTH_TEST).writeMaskState(COLOR_MASK).fog(NO_FOG).build(false));

    private PauseIndicator() { super("zsg_replay_pause", () -> {}, () -> {}); }

    static void renderAbove(PlayerEntity player, MatrixStack matrices, VertexConsumerProvider consumers) {
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
            bars((left, top, right, bottom, color) -> quad(vertices, transform, left, top, right, bottom, color));
        } finally { matrices.pop(); }
    }

    static void renderHud(MatrixStack matrices, int x, int y) {
        bars((left, top, right, bottom, color) -> DrawableHelper.fill(matrices,
                x + left, y + top, x + right, y + bottom, color));
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
