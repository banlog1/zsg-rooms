// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.Matrix4f;

/** Stateless HUD and world-space piglin visuals. Replay/settings gating belongs to the caller. */
public final class PiglinVisuals {
    static final int FACE_WIDTH = 16;
    static final int FACE_HEIGHT = 12;
    private static final int GAP = 4;
    private static final int AMBER = 0xFFFFBF47;
    private static final String[] FACE = {
            "....00000000....",
            "...0333333330...",
            "0003222222223000",
            "0343222222223430",
            "0343212222123430",
            ".00322222222300.",
            "...3254444523...",
            "...3254646523...",
            "...3254444523...",
            "...0324444230...",
            "....03222230....",
            ".....000000....."
    };
    private static final int[] PALETTE = {
            0xFF503A32, 0xFF241C18, 0xFFD39A7D, 0xFFA96F55,
            0xFFE8B392, 0xFFFFE8BC, 0xFF9B6254
    };

    interface Rects { void draw(int x, int y, int width, int height, int color); }

    private PiglinVisuals() { }

    /** The right edge is exclusive, including the count's one-pixel text shadow. */
    public static void renderCounter(MatrixStack matrices, int right, int top, int count, boolean available) {
        TextRenderer text = MinecraftClient.getInstance().textRenderer;
        String value = counterText(count, available);
        int textWidth = text.getWidth(value);
        int left = right - counterWidth(textWidth);
        renderFace(matrices, left, top);
        text.drawWithShadow(matrices, value, left + FACE_WIDTH + GAP,
                top + (FACE_HEIGHT - text.fontHeight) / 2, counterColor(count, available));
    }

    public static int counterWidth(int count, boolean available) {
        return counterWidth(MinecraftClient.getInstance().textRenderer.getWidth(counterText(count, available)));
    }

    static int counterWidth(int textWidth) {
        return FACE_WIDTH + GAP + textWidth + 1;
    }

    static String counterText(int count, boolean available) {
        return available ? Integer.toString(Math.max(0, count)) : "--";
    }

    static int counterColor(int count, boolean available) {
        return !available ? 0xFFABB2B8 : count > 0 ? 0xFF80E080 : 0xFFFF7777;
    }

    private static void renderFace(MatrixStack matrices, int left, int top) {
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        Matrix4f matrix = matrices.peek().getModel();
        RenderSystem.enableBlend();
        RenderSystem.disableTexture();
        RenderSystem.defaultBlendFunc();
        try {
            // Submit all pixel runs together instead of flushing once per colored rectangle.
            buffer.begin(7, VertexFormats.POSITION_COLOR);
            drawFace(left, top, (x, y, width, height, color) -> {
                int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
                buffer.vertex(matrix, x + width, y, 0).color(r, g, b, 255).next();
                buffer.vertex(matrix, x, y, 0).color(r, g, b, 255).next();
                buffer.vertex(matrix, x, y + height, 0).color(r, g, b, 255).next();
                buffer.vertex(matrix, x + width, y + height, 0).color(r, g, b, 255).next();
            });
            buffer.end();
            BufferRenderer.draw(buffer);
        } finally {
            RenderSystem.enableTexture();
            RenderSystem.disableBlend();
        }
    }

    static void drawFace(int left, int top, Rects rects) {
        for (int y = 0; y < FACE_HEIGHT; y++) {
            String row = FACE[y];
            for (int x = 0; x < FACE_WIDTH;) {
                char pixel = row.charAt(x);
                int end = x + 1;
                while (end < FACE_WIDTH && row.charAt(end) == pixel) end++;
                if (pixel != '.') rects.draw(left + x, top + y, end - x, 1, PALETTE[pixel - '0']);
                x = end;
            }
        }
    }

    public static boolean bartering(PiglinEntity piglin) {
        if (piglin == null) return false;
        // 1.16.1 getActivity() uses visible equipment/tracked flags, not server brain memories.
        return bartering(piglin.isAlive(), piglin.isBaby(),
                piglin.getOffHandStack().getItem() == Items.GOLD_INGOT, piglin.getActivity());
    }

    static boolean bartering(boolean alive, boolean baby, boolean goldInOffHand, PiglinEntity.Activity activity) {
        return alive && !baby && goldInOffHand && activity == PiglinEntity.Activity.ADMIRING_ITEM;
    }

    public static void renderAbove(PiglinEntity piglin, MatrixStack matrices,
                                   VertexConsumerProvider consumers, int light) {
        if (!bartering(piglin) || piglin.isInvisible()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && piglin.isInvisibleTo(client.player)) return;
        EntityRenderDispatcher dispatcher = client.getEntityRenderManager();
        if (dispatcher.getSquaredDistanceToCamera(piglin) > 64.0 * 64.0) return;
        matrices.push();
        try {
            // Vanilla names sit at height + 0.5; leave a separate row above them.
            matrices.translate(0, piglin.getHeight() + 0.85, 0);
            matrices.multiply(dispatcher.getRotation());
            matrices.scale(-0.025F, -0.025F, 0.025F);
            TextRenderer text = client.textRenderer;
            String label = "Bartering";
            text.draw(label, -text.getWidth(label) / 2.0F, 0, AMBER, true,
                    matrices.peek().getModel(), consumers, false, 0, light);
        } finally {
            matrices.pop();
        }
    }
}
