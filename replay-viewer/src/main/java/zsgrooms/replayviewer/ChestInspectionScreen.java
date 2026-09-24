// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;

/** Not a HandledScreen: clicking, dragging and number keys cannot move items or send inventory packets. */
final class ChestInspectionScreen extends Screen {
    private static final Identifier TEXTURE = new Identifier("minecraft", "textures/gui/container/generic_54.png");
    private final ReplayHandler handler;
    private final ChestHistory.Frame<ItemStack> frame;
    private final java.util.List<ItemStack> items;
    private final String status;
    private final int rows;
    private final net.minecraft.client.world.ClientWorld world;
    private int left, top;

    ChestInspectionScreen(ReplayHandler handler, ChestHistory.Frame<ItemStack> frame, String unavailable, int rows,
                          java.util.List<ItemStack> predicted) {
        super(new LiteralText(rows == 6 ? "Large Chest" : "Chest"));
        this.handler = handler;
        this.frame = frame;
        this.items = frame != null ? frame.items : predicted;
        this.rows = rows;
        this.world = net.minecraft.client.MinecraftClient.getInstance().world;
        this.status = frame != null && frame.items != null ? "Last recorded: " + ViewerLayout.time(frame.time)
                : predicted != null ? "Predicted vanilla loot" : unavailable.isEmpty() ? "Contents unknown" : unavailable;
    }

    @Override protected void init() {
        left = (width - 176) / 2;
        top = Math.max(4, (height - (rows * 18 + 66)) / 2);
        addButton(new ButtonWidget(left + 38, top + rows * 18 + 42, 100, 20,
                new LiteralText("Done"), button -> onClose()));
    }

    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        client.getTextureManager().bindTexture(TEXTURE);
        RenderSystem.color4f(1, 1, 1, 1);
        drawTexture(matrices, left, top, 0, 0, 176, rows * 18 + 17);
        drawTexture(matrices, left, top + rows * 18 + 17, 0, 215, 176, 7);
        textRenderer.draw(matrices, title, left + 8, top + 6, 0x404040);
        ItemStack hovered = ItemStack.EMPTY;
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                int x = left + 8 + i % 9 * 18, y = top + 18 + i / 9 * 18;
                ItemStack stack = items.get(i);
                itemRenderer.renderInGuiWithOverrides(stack, x, y);
                itemRenderer.renderGuiItemOverlay(textRenderer, stack, x, y);
                if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) hovered = stack;
            }
        }
        drawCenteredString(matrices, textRenderer, textRenderer.trimToWidth(status, width - 16), width / 2,
                top + rows * 18 + 29, 0xFFFFFF);
        super.render(matrices, mouseX, mouseY, delta);
        if (!hovered.isEmpty()) renderTooltip(matrices, hovered, mouseX, mouseY);
        else if ("Predicted vanilla loot".equals(status) && mouseY >= top + rows * 18 + 27
                && mouseY < top + rows * 18 + 39 && Math.abs(mouseX - width / 2) < textRenderer.getWidth(status) / 2) {
            renderTooltip(matrices, new LiteralText("Original vanilla loot, not recorded contents. Custom loot changes are not modeled."), mouseX, mouseY);
        }
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (client.options.keyInventory.matchesKey(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void tick() {
        if (ReplayModReplay.instance.getReplayHandler() != handler || client.world != world) onClose();
    }

    @Override public void onClose() { client.openScreen(null); }
    @Override public boolean isPauseScreen() { return false; }

}
