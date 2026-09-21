// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/** Vanilla UI geometry, rendered from snapshots without changing the camera player's inventory. */
final class DetailedFollowHud extends DrawableHelper {
    private static final Identifier ICONS = new Identifier("minecraft", "textures/gui/icons.png");
    private static final Identifier WIDGETS = new Identifier("minecraft", "textures/gui/widgets.png");
    private static final Identifier INVENTORY = new Identifier("minecraft", "textures/gui/container/inventory.png");
    private final MinecraftClient client = MinecraftClient.getInstance();
    private PlayerHudTrack.Frame last;
    private PlayerHudState state;

    int reservedHeight() {
        return state == null ? 58 : 58 + Math.max(0, heartRows() - 1) * heartSpacing();
    }

    void render(MatrixStack matrices, PlayerHudTrack.Frame frame, FollowDetailOptions options, PlayerEntity player) {
        if (last != frame) { last = frame; state = frame == null ? null : PlayerHudState.decode(frame.payload); }
        int width = client.getWindow().getScaledWidth(), height = client.getWindow().getScaledHeight();
        if (state == null) {
            drawCenteredString(matrices, client.textRenderer, "Player details unavailable", width / 2, height - 49, 0xCCCCCC);
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.color4f(1, 1, 1, 1);
        if (options.inventoryVisible()) inventory(matrices, width, height, player);
        bars(matrices, width, height, player);
        if (!options.inventoryVisible()) effects(matrices, width);
        RenderSystem.color4f(1, 1, 1, 1);
        RenderSystem.disableDepthTest();
    }

    private void bars(MatrixStack matrices, int width, int height, PlayerEntity player) {
        int center = width / 2, left = center - 91, right = center + 91;
        int baseline = height - 39;
        bind(ICONS);
        int hearts = Math.min(100, (int) Math.ceil(state.maxHealth / 2));
        int absorption = Math.min(100 - hearts, (int) Math.ceil(state.absorption / 2));
        int heartU = hasEffect("poison") ? 88 : hasEffect("wither") ? 124 : 52;
        for (int i = 0; i < hearts + absorption; i++) {
            int x = left + i % 10 * 8, y = baseline - i / 10 * heartSpacing();
            meter(matrices, x, y, i < hearts ? state.health : state.absorption,
                    i < hearts ? i : i - hearts, 16, i < hearts ? heartU : 160, i < hearts ? heartU + 9 : 169, 0);
        }
        int armorY = baseline - (heartRows() - 1) * heartSpacing() - 10;
        boolean hungry = hasEffect("hunger");
        for (int i = 0; i < 10; i++) {
            if (state.armor > 0) meter(matrices, left + i * 8, armorY, state.armor, i, 16, 34, 25, 9);
            meter(matrices, right - i * 8 - 9, baseline, state.food, i, hungry ? 133 : 16, hungry ? 88 : 52, hungry ? 97 : 61, 27);
            if (state.air < 300) drawTexture(matrices, right - i * 8 - 9, baseline - 10,
                    i < Math.ceil(Math.max(0, state.air) / 30.0) ? 16 : 25, 18, 9, 9);
        }
        drawTexture(matrices, left, height - 29, 0, 64, 182, 5);
        drawTexture(matrices, left, height - 29, 0, 69, (int) (183 * state.experience), 5);
        if (state.level > 0) {
            String level = Integer.toString(state.level);
            int x = (width - client.textRenderer.getWidth(level)) / 2, y = height - 35;
            client.textRenderer.draw(matrices, level, x + 1, y, 0);
            client.textRenderer.draw(matrices, level, x - 1, y, 0);
            client.textRenderer.draw(matrices, level, x, y + 1, 0);
            client.textRenderer.draw(matrices, level, x, y - 1, 0);
            client.textRenderer.draw(matrices, level, x, y, 0x80FF20);
        }
        bind(WIDGETS);
        drawTexture(matrices, left, height - 22, 0, 0, 182, 22);
        drawTexture(matrices, left - 1 + state.selected * 20, height - 23, 0, 22, 24, 22);
        boolean offhandRight = player != null && player.getMainArm() == Arm.LEFT;
        if (!state.items[40].isEmpty()) drawTexture(matrices, offhandRight ? right : left - 29,
                height - 23, offhandRight ? 53 : 24, 22, 29, 24);
        for (int i = 0; i < 9; i++) item(state.items[i], left + 3 + i * 20, height - 19);
        if (!state.items[40].isEmpty()) item(state.items[40], offhandRight ? right + 10 : left - 26, height - 19);
    }

    private int heartRows() {
        return Math.max(1, (int) Math.ceil(Math.min(200, state.maxHealth + state.absorption) / 20));
    }
    private int heartSpacing() { return Math.max(10 - (heartRows() - 2), 3); }
    private boolean hasEffect(String path) {
        for (PlayerHudState.Effect effect : state.effects) {
            if (effect.id.getNamespace().equals("minecraft") && effect.id.getPath().equals(path)) return true;
        }
        return false;
    }

    private void inventory(MatrixStack matrices, int width, int height, PlayerEntity player) {
        InventoryPlacement placement = new InventoryPlacement(width, height, reservedHeight());
        fill(matrices, 0, 0, width, height - reservedHeight(), 0x88000000);
        RenderSystem.pushMatrix();
        try {
            RenderSystem.translatef(placement.x, placement.y, 0);
            RenderSystem.scalef(placement.scale, placement.scale, 1);
            bind(INVENTORY);
            drawTexture(matrices, 0, 0, 0, 0, 176, 166);
            // Crafting slots are not recorded. Mark them unknown, not falsely empty.
            client.textRenderer.draw(matrices, new TranslatableText("container.crafting"), 97, 6, 0x404040);
            for (int i = 0; i < 4; i++) client.textRenderer.draw(matrices, "-", 104 + i % 2 * 18, 22 + i / 2 * 18, 0x777777);
            client.textRenderer.draw(matrices, "-", 160, 32, 0x777777);
            if (player != null) InventoryScreen.drawEntity(51, 75, 30, 0, 0, player);
            for (int i = 9; i < 36; i++) item(state.items[i], 8 + (i - 9) % 9 * 18, 84 + (i - 9) / 9 * 18);
            for (int i = 0; i < 9; i++) item(state.items[i], 8 + i * 18, 142);
            for (int i = 0; i < 4; i++) item(state.items[39 - i], 8, 8 + i * 18);
            item(state.items[40], 77, 62);
            item(state.items[41], 151, 60);
        } finally { RenderSystem.popMatrix(); }
    }

    private void effects(MatrixStack matrices, int width) {
        int positive = 0, negative = 0;
        int columns = Math.max(1, width / 25);
        for (PlayerHudState.Effect effect : state.effects) {
            StatusEffect type = Registry.STATUS_EFFECT.get(effect.id);
            if (type == null) continue;
            int index = type.isBeneficial() ? positive++ : negative++;
            int x = width - 25 * (index % columns + 1), y = 36 + (index / columns * 2 + (type.isBeneficial() ? 0 : 1)) * 26;
            bind(INVENTORY);
            drawTexture(matrices, x, y, 141, 166, 24, 24);
            net.minecraft.client.texture.Sprite sprite = client.getStatusEffectSpriteManager().getSprite(type);
            bind(sprite.getAtlas().getId());
            drawSprite(matrices, x + 3, y + 3, 0, 18, 18, sprite);
        }
    }

    private void bind(Identifier texture) {
        RenderSystem.color4f(1, 1, 1, 1);
        client.getTextureManager().bindTexture(texture);
    }
    private void meter(MatrixStack matrices, int x, int y, float value, int index, int empty, int full, int half, int v) {
        drawTexture(matrices, x, y, empty, v, 9, 9);
        if (value > index * 2) drawTexture(matrices, x, y, value >= index * 2 + 2 ? full : half, v, 9, 9);
    }
    private void item(ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        RenderSystem.enableDepthTest();
        client.getItemRenderer().renderGuiItemIcon(stack, x, y);
        client.getItemRenderer().renderGuiItemOverlay(client.textRenderer, stack, x, y);
        RenderSystem.disableDepthTest();
    }
}
