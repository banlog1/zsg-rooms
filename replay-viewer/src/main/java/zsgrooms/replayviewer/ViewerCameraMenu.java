// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.advanced.GuiDropdownMenu;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

/** A bottom-toolbar menu must not expand below the window or over the player's HUD. */
public final class ViewerCameraMenu extends GuiDropdownMenu<String> {
    private ModeScreen menu;

    @Override public GuiDropdownMenu<String> setOpened(boolean opened) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (opened && !isOpened()) {
            menu = new ModeScreen();
            client.openScreen(menu);
        } else if (!opened && isOpened()) menu.onClose();
        return this;
    }

    @Override public boolean isOpened() {
        return menu != null && MinecraftClient.getInstance().currentScreen == menu;
    }

    @Override public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo info) {
        if (info.layer != 0) return;
        Point mouse = new Point(info.mouseX, info.mouseY);
        if (isMouseHovering(mouse)) renderer.drawRect(0, 0, size.getWidth(), size.getHeight(), 0x66334444);
        String label = MinecraftClient.getInstance().textRenderer.trimToWidth(getSelectedValue(), size.getWidth() - 16);
        renderer.drawString(3, (size.getHeight() - 8) / 2, 0xFFE8EEED, label, true);
        for (int row = 0; row < 3; row++) {
            renderer.drawRect(size.getWidth() - 9 + row, 8 + row, 5 - row * 2, 1, 0xFFE8EEED);
        }
    }

    private final class ModeScreen extends Screen {
        ModeScreen() { super(new LiteralText("Camera")); }

        @Override protected void init() {
            int w = Math.min(240, width - 32);
            int top = Math.max(28, (height - 180) / 2);
            String[] values = getValues();
            for (int index = 0; index < values.length; index++) {
                final int mode = index;
                addButton(new ButtonWidget((width - w) / 2, top + index * 24, w, 22,
                        new LiteralText(values[index]), button -> {
                            setSelected(mode);
                            onSelection(mode);
                            onClose();
                        }) {
                    @Override public void renderButton(MatrixStack matrices, int mx, int my, float delta) {
                        if (isHovered()) fill(matrices, x, y, x + this.width, y + this.height, 0x66334444);
                        if (getSelected() == mode) fill(matrices, x, y + 3, x + 2, y + this.height - 3, 0xFF77DCC8);
                        textRenderer.drawWithShadow(matrices, getMessage(), x + 10, y + 7,
                                getSelected() == mode ? 0x77DCC8 : 0xEEEEEE);
                    }
                });
            }
            addButton(new ButtonWidget((width - 100) / 2, top + 150, 100, 20, new LiteralText("Done"), b -> onClose()));
        }

        @Override public void render(MatrixStack matrices, int mx, int my, float delta) {
            fill(matrices, 0, 0, width, height, 0xB0000000);
            drawCenteredString(matrices, textRenderer, title.getString(), width / 2, Math.max(10, (height - 180) / 2 - 18), 0xFFFFFF);
            super.render(matrices, mx, my, delta);
        }

        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() { client.openScreen(null); menu = null; }
    }
}
