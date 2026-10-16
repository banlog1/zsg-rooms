// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;

/** Presentation only. No live screen handler is constructed or clicked. */
final class RecordedScreenView extends DrawableHelper {
    private static final Identifier INVENTORY = new Identifier("minecraft", "textures/gui/container/inventory.png");
    private static final Identifier CRAFTING = new Identifier("minecraft", "textures/gui/container/crafting_table.png");
    private static final Identifier CHEST = new Identifier("minecraft", "textures/gui/container/generic_54.png");
    private final MinecraftClient client = MinecraftClient.getInstance();

    ItemStack draw(MatrixStack matrices, int kind, String title, ItemStack[] slots, ItemStack cursor,
                   int width, int height, int bottomInset, PlayerEntity player, int mouseX, int mouseY,
                   boolean craftingKnown) {
        RecordedScreenLayout layout = RecordedScreenLayout.create(kind, slots.length);
        if (layout == null) return ItemStack.EMPTY;
        float scale = Math.min(1, Math.min(Math.max(1, width - 16) / 176F,
                Math.max(1, height - bottomInset - 40) / (float) layout.height));
        float left = (width - 176 * scale) / 2, top = 24 + (height - bottomInset - 24 - layout.height * scale) / 2;
        fill(matrices, 0, 0, width, height - bottomInset, 0x88000000);
        RenderSystem.pushMatrix();
        ItemStack hovered = ItemStack.EMPTY;
        try {
            RenderSystem.translatef(left, top, 0);
            RenderSystem.scalef(scale, scale, 1);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.color4f(1, 1, 1, 1);
            client.getTextureManager().bindTexture(kind == 1 ? INVENTORY : kind == 2 ? CRAFTING : CHEST);
            if (kind == 3) {
                drawTexture(matrices, 0, 0, 0, 0, 176, layout.rows * 18 + 17);
                drawTexture(matrices, 0, layout.rows * 18 + 17, 0, 126, 176, 96);
                client.textRenderer.draw(matrices, client.textRenderer.trimToWidth(title, 160), 8, 6, 0x404040);
                client.textRenderer.draw(matrices, new TranslatableText("container.inventory"), 8, layout.rows * 18 + 19, 0x404040);
            } else {
                drawTexture(matrices, 0, 0, 0, 0, 176, 166);
                client.textRenderer.draw(matrices, new TranslatableText("container.crafting"), kind == 1 ? 97 : 28, 6, 0x404040);
                if (kind == 2) client.textRenderer.draw(matrices, new TranslatableText("container.inventory"), 8, 72, 0x404040);
                if (kind == 1 && player != null) InventoryScreen.drawEntity(51, 75, 30, 0, 0, player);
            }
            float mx = (mouseX - left) / scale, my = (mouseY - top) / scale;
            for (int i = 0; i < slots.length; i++) {
                item(slots[i], layout.x[i], layout.y[i]);
                if (kind == 1 && !craftingKnown && i < 5)
                    client.textRenderer.draw(matrices, "-", layout.x[i] + 5, layout.y[i] + 4, 0x777777);
                if (mx >= layout.x[i] && mx < layout.x[i] + 16 && my >= layout.y[i] && my < layout.y[i] + 16) {
                    hovered = slots[i];
                    fill(matrices, layout.x[i], layout.y[i], layout.x[i] + 16, layout.y[i] + 16, 0x66FFFFFF);
                }
            }
            // Cursor motion is not captured; a fixed inset keeps its stack visible without inventing clicks.
            if (!cursor.isEmpty()) {
                fill(matrices, 151, -24, 172, -3, 0xEE202328);
                item(cursor, 154, -22);
                if (mx >= 151 && mx < 172 && my >= -24 && my < -3) hovered = cursor;
            }
        } finally {
            RenderSystem.popMatrix();
            RenderSystem.color4f(1, 1, 1, 1);
            RenderSystem.disableDepthTest();
        }
        return hovered;
    }

    private void item(ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        RenderSystem.enableDepthTest();
        client.getItemRenderer().renderGuiItemIcon(stack, x, y);
        client.getItemRenderer().renderGuiItemOverlay(client.textRenderer, stack, x, y);
        RenderSystem.disableDepthTest();
    }
}
