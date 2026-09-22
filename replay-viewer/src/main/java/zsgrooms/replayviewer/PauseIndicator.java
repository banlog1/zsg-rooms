// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Matrix4f;

/** Depth-tested, camera-facing player status symbols; never visible through terrain. */
final class PauseIndicator extends RenderPhase {
    private static final int OUTLINE = 0xFF201B13;
    private static final int AMBER = 0xFFFFD080;
    private static final int[] RING_X = {0, 4, 6, 4, 0, -4, -6, -4};
    private static final int[] RING_Y = {-6, -4, 0, 4, 6, 4, 0, -4};
    private static final ItemStack CRAFTING_TABLE = new ItemStack(Items.CRAFTING_TABLE);
    private static final String[] POUCH = {
            ".....######.....",
            "....#hhmmms#....",
            ".....#mms#......",
            "......#s#.......",
            ".....#rrr##.....",
            ".....#mms#r#....",
            "....#hmms#r#....",
            "...#hhmmss#r#...",
            "..#hhmmmsss##...",
            "..#hhmmmssss#...",
            ".#hhmmmmsssss#..",
            ".#hhmmmmsssss#..",
            ".#hmmmmssssss#..",
            "..#mmmmsssss#...",
            "...##sssss##....",
            ".....#####......"
    };
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
        renderAbove(player, matrices, consumers, loading, true, time, ReplayScreens.NONE);
    }

    static void renderAbove(PlayerEntity player, MatrixStack matrices, VertexConsumerProvider consumers,
                            boolean loading, boolean paused, int time, int screen) {
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
            boolean status = loading || paused;
            if (status) {
                IndicatorTooltips.worldIcon(transform, screen == ReplayScreens.NONE ? 0 : -11,
                        loading ? IndicatorTooltips.LOADING : IndicatorTooltips.PAUSED, player);
                Rectangle shifted = offset(rectangle, screen == ReplayScreens.NONE ? 0 : -11);
                if (loading) ring(shifted, time); else bars(shifted);
            }
            if (screen != ReplayScreens.NONE) IndicatorTooltips.worldIcon(transform, status ? 11 : 0, screen, player);
            if (screen == ReplayScreens.INVENTORY) pouch(offset(rectangle, status ? 11 : 0));
            if (screen == ReplayScreens.CRAFTING) {
                matrices.translate(status ? 11 : 0, 0, 0);
                // Match the GUI model orientation in the camera-facing, downward-Y label space.
                matrices.scale(16, -16, -16);
                client.getItemRenderer().renderItem(CRAFTING_TABLE, ModelTransformation.Mode.GUI,
                        15728880, OverlayTexture.DEFAULT_UV, matrices, consumers);
            }
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

    static void renderScreenHud(MatrixStack matrices, int x, int y, int kind) {
        if (kind == ReplayScreens.INVENTORY) {
            pouch((left, top, right, bottom, color) -> DrawableHelper.fill(matrices,
                    x + left, y + top, x + right, y + bottom, color));
        } else if (kind == ReplayScreens.CRAFTING) {
            // Minecraft 1.16's GUI item renderer uses the global model-view matrix.
            RenderSystem.pushMatrix();
            RenderSystem.multMatrix(matrices.peek().getModel());
            try {
                RenderSystem.enableDepthTest();
                MinecraftClient.getInstance().getItemRenderer().renderGuiItemIcon(CRAFTING_TABLE, x - 8, y - 8);
            } finally {
                RenderSystem.popMatrix();
                RenderSystem.disableDepthTest();
            }
        }
    }

    private static Rectangle offset(Rectangle rectangle, int x) {
        return (left, top, right, bottom, color) -> rectangle.draw(left + x, top, right + x, bottom, color);
    }

    private static void pouch(Rectangle rectangle) {
        // Merge adjacent pixels, sharing one crisp silhouette between the HUD and nameplate.
        for (int y = 0; y < POUCH.length; y++) {
            String row = POUCH[y];
            for (int x = 0; x < row.length();) {
                char pixel = row.charAt(x);
                int end = x + 1;
                while (end < row.length() && row.charAt(end) == pixel) end++;
                int color = pixel == '#' ? 0xFF3C2814 : pixel == 'h' ? 0xFFF0CD7A
                        : pixel == 'm' ? 0xFFD49B43 : pixel == 's' ? 0xFFA16A25 : 0xFF79502C;
                if (pixel != '.') rectangle.draw(x - 8, y - 8, end - 8, y - 7, color);
                x = end;
            }
        }
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
