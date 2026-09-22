// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.util.math.Vector4f;
import net.minecraft.world.RayTraceContext;

/** Per-frame hit areas for the same status icons in the HUD and above the player. */
final class IndicatorTooltips extends Screen {
    static final int PAUSED = 3, LOADING = 4;
    private static final IndicatorTooltips TOOLTIP = new IndicatorTooltips();
    private static final Region[] world = {new Region(), new Region()};
    private static int count;
    private static boolean enabled;
    static Matrix4f projection;

    private IndicatorTooltips() { super(LiteralText.EMPTY); }

    static void beginFrame(boolean visibleCursor) {
        enabled = visibleCursor;
        count = 0;
        if (!visibleCursor) for (Region region : world) region.player = null;
    }

    static String label(int kind) {
        switch (kind) {
            case ReplayScreens.INVENTORY: return "Recorded player has their inventory open.";
            case ReplayScreens.CRAFTING: return "Recorded player is using a crafting table.";
            case PAUSED: return "Recorded player has paused the game.";
            case LOADING: return "Recorded player is loading a world.";
            default: return null;
        }
    }

    static void worldIcon(Matrix4f model, int offset, int kind, PlayerEntity player) {
        if (!enabled || projection == null || count == world.length) return;
        MinecraftClient client = MinecraftClient.getInstance();
        Region region = world[count];
        region.left = region.top = Float.POSITIVE_INFINITY;
        region.right = region.bottom = Float.NEGATIVE_INFINITY;
        for (int x = offset - 8; x <= offset + 8; x += 16) {
            for (int y = -8; y <= 8; y += 16) {
                Vector4f point = new Vector4f(x, y, 0, 1);
                point.transform(model);
                point.transform(projection);
                if (point.getW() <= 0 || Math.abs(point.getZ()) > point.getW()) return;
                float sx = (point.getX() / point.getW() + 1) * client.getWindow().getScaledWidth() / 2;
                float sy = (1 - point.getY() / point.getW()) * client.getWindow().getScaledHeight() / 2;
                region.left = Math.min(region.left, sx); region.right = Math.max(region.right, sx);
                region.top = Math.min(region.top, sy); region.bottom = Math.max(region.bottom, sy);
            }
        }
        region.kind = kind;
        region.player = player;
        count++;
    }

    static String hudLabel(double mx, double my, int x, boolean loading, boolean paused, int screen) {
        if (my < 10 || my >= 26) return null;
        if ((loading || paused) && mx >= x - 24 && mx < x - 8) return label(loading ? LOADING : PAUSED);
        int center = x - (loading || paused ? 38 : 16);
        return screen != ReplayScreens.NONE && mx >= center - 8 && mx < center + 8 ? label(screen) : null;
    }

    static void render(MatrixStack matrices, int x, boolean loading, boolean paused, int screen) {
        if (!enabled) return;
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getScaledWidth(), height = client.getWindow().getScaledHeight();
        int mx = (int) (client.mouse.getX() * width / client.getWindow().getWidth());
        int my = (int) (client.mouse.getY() * height / client.getWindow().getHeight());
        String text = hudLabel(mx, my, x, loading, paused, screen);
        for (int i = 0; text == null && i < count; i++) {
            Region region = world[i];
            if (mx < region.left || mx >= region.right || my < region.top || my >= region.bottom) continue;
            PlayerEntity player = region.player;
            Vec3d start = client.gameRenderer.getCamera().getPos();
            Vec3d end = player.getCameraPosVec(client.getTickDelta()).add(0, player.getHeight() + 1.05 - player.getStandingEyeHeight(), 0);
            if (client.world.rayTrace(new RayTraceContext(start, end, RayTraceContext.ShapeType.COLLIDER,
                    RayTraceContext.FluidHandling.NONE, player)).getType() == HitResult.Type.MISS) text = label(region.kind);
        }
        if (text == null) return;
        if (TOOLTIP.client != client || TOOLTIP.width != width || TOOLTIP.height != height) TOOLTIP.init(client, width, height);
        TOOLTIP.renderTooltip(matrices, client.textRenderer.wrapLines(new LiteralText(text), Math.min(240, width - 24)), mx, my);
    }

    private static final class Region {
        float left, top, right, bottom;
        int kind;
        PlayerEntity player;
    }
}
