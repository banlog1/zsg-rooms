// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiTooltip;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;

/** Small pixel glyphs drawn directly, with stable hit targets and native button input. */
public final class ViewerIconButton extends GuiButton {
    enum Icon { PLAY, PAUSE, BACK, FORWARD, EDITOR, PLAYERS, SPLITS, ANALYSIS, CLOCK, HIDE, REVEAL }

    private final Icon icon;

    ViewerIconButton(Icon icon, String name, String tooltip, Runnable action) {
        this.icon = icon;
        // Keep semantic labels for accessibility and the existing smoke-test widget lookup.
        setLabel(name);
        setTooltip(new GuiTooltip().setText(tooltip.split("\n")));
        onClick(action);
    }

    @Override public ReadableDimension calcMinSize() { return new Dimension(20, 20); }

    @Override public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo info) {
        Point mouse = new Point(info.mouseX, info.mouseY);
        boolean hover = isMouseHovering(mouse);
        int width = size.getWidth(), height = size.getHeight();
        if (hover && isEnabled()) renderer.drawRect(0, 0, width, height, 0x66334444);
        int color = isEnabled() ? 0xFFE8EEED : 0xFF6C7478;
        int x = (width - 14) / 2, y = (height - 14) / 2;
        drawIcon(renderer, icon, x + 1, y + 1, 0xCC000000);
        drawIcon(renderer, icon, x, y, color);
    }

    static void drawIcon(GuiRenderer renderer, Icon icon, int x, int y, int color) {
        switch (icon) {
            case PLAY:
                for (int row = 0; row < 11; row++) renderer.drawRect(x + 4, y + 2 + row,
                        row < 6 ? row + 1 : 11 - row, 1, color);
                break;
            case PAUSE:
                renderer.drawRect(x + 3, y + 2, 3, 11, color);
                renderer.drawRect(x + 9, y + 2, 3, 11, color);
                break;
            case BACK: case FORWARD:
                for (int row = 0; row < 9; row++) {
                    int length = row < 5 ? row + 1 : 9 - row;
                    int left = icon == Icon.BACK ? x + 9 - length : x + 4;
                    renderer.drawRect(left, y + 3 + row, length, 1, color);
                }
                renderer.drawRect(icon == Icon.BACK ? x + 2 : x + 11, y + 3, 2, 9, color);
                break;
            case CLOCK:
                renderer.drawRect(x + 4, y + 1, 6, 1, color);
                renderer.drawRect(x + 4, y + 12, 6, 1, color);
                renderer.drawRect(x + 1, y + 4, 1, 6, color);
                renderer.drawRect(x + 12, y + 4, 1, 6, color);
                renderer.drawRect(x + 2, y + 2, 2, 2, color);
                renderer.drawRect(x + 10, y + 2, 2, 2, color);
                renderer.drawRect(x + 2, y + 10, 2, 2, color);
                renderer.drawRect(x + 10, y + 10, 2, 2, color);
                renderer.drawRect(x + 6, y + 3, 1, 5, color);
                renderer.drawRect(x + 6, y + 7, 4, 1, color);
                break;
            case EDITOR:
                renderer.drawRect(x + 1, y + 2, 3, 3, color);
                renderer.drawRect(x + 10, y + 9, 3, 3, color);
                for (int n = 0; n < 7; n++) renderer.drawRect(x + 3 + n, y + 4 + n, 2, 1, color);
                break;
            case PLAYERS:
                renderer.drawRect(x + 2, y + 2, 4, 4, color);
                renderer.drawRect(x + 8, y + 2, 4, 4, color);
                renderer.drawRect(x + 1, y + 8, 6, 4, color);
                renderer.drawRect(x + 8, y + 8, 5, 4, color);
                break;
            case ANALYSIS:
                for (int row = 3; row <= 11; row += 4) renderer.drawRect(x + 1, y + row, 12, 1, color);
                renderer.drawRect(x + 4, y + 1, 2, 5, color);
                renderer.drawRect(x + 9, y + 5, 2, 5, color);
                renderer.drawRect(x + 3, y + 9, 2, 5, color);
                break;
            case SPLITS:
                for (int row = 2; row <= 10; row += 4) {
                    renderer.drawRect(x + 1, y + row, 3, 2, color);
                    renderer.drawRect(x + 6, y + row, 3, 2, color);
                    renderer.drawRect(x + 11, y + row, 2, 2, color);
                }
                break;
            case HIDE: case REVEAL:
                renderer.drawRect(x + 4, y + 3, 6, 1, color);
                renderer.drawRect(x + 4, y + 10, 6, 1, color);
                renderer.drawRect(x + 2, y + 4, 2, 1, color);
                renderer.drawRect(x + 10, y + 4, 2, 1, color);
                renderer.drawRect(x + 2, y + 9, 2, 1, color);
                renderer.drawRect(x + 10, y + 9, 2, 1, color);
                renderer.drawRect(x + 1, y + 5, 1, 4, color);
                renderer.drawRect(x + 12, y + 5, 1, 4, color);
                renderer.drawRect(x + 6, y + 5, 2, 4, color);
                if (icon == Icon.HIDE) {
                    for (int n = 0; n < 12; n++) renderer.drawRect(x + 1 + n, y + 1 + n, 2, 1, color);
                }
                break;
            default: break;
        }
    }
}
