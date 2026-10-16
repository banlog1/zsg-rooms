// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.AbstractGuiSlider;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.advanced.AbstractGuiTimeline;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;

/** Rendering only: the original widgets retain all native input and marker handling. */
public final class ViewerChrome {
    private ViewerChrome() { }

    public static boolean transport(Object widget, GuiRenderer renderer, ReadableDimension size, RenderInfo info) {
        int kind = ReplayViewer.compactWidget(widget);
        if (kind != 1 && kind != 2) return false;
        Point mouse = new Point(info.mouseX, info.mouseY);
        int w = size.getWidth(), h = size.getHeight();
        if (mouse.getX() >= 0 && mouse.getX() < w && mouse.getY() >= 0 && mouse.getY() < h)
            renderer.drawRect(0, 0, w, h, 0x66334444);
        if (kind == 1) {
            ViewerIconButton.Icon icon = ReplayViewer.playbackPaused() ? ViewerIconButton.Icon.PLAY : ViewerIconButton.Icon.PAUSE;
            ViewerIconButton.drawIcon(renderer, icon, (w - 14) / 2 + 1, (h - 14) / 2 + 1, 0xCC000000);
            ViewerIconButton.drawIcon(renderer, icon, (w - 14) / 2, (h - 14) / 2, 0xFFE8EEED);
        } else {
            AbstractGuiSlider<?> slider = (AbstractGuiSlider<?>) widget;
            int knob = 4 + (int) Math.round((w - 8) * slider.getValue() / (double) Math.max(1, slider.getSteps()));
            renderer.drawRect(3, h - 5, w - 6, 3, 0xBB000000);
            renderer.drawRect(4, h - 4, w - 8, 1, 0xFFB8C1C0);
            renderer.drawRect(knob - 1, h - 6, 3, 5, 0xFF77DCC8);
            renderer.drawCenteredString(w / 2, 2, 0xFFE8EEED, ReplayViewer.playbackSliderSpeed() + "x", true);
        }
        return true;
    }

    public static void timeline(AbstractGuiTimeline<?> timeline, GuiRenderer renderer, ReadableDimension size) {
        int w = Math.max(1, size.getWidth() - 8), y = size.getHeight() / 2;
        int center = TimelineCursor.center(size.getWidth(), timeline.getLength(), timeline.getZoom(),
                timeline.getOffset(), timeline.getCursorPosition());
        renderer.drawRect(3, y - 2, w + 2, 6, 0xAA000000);
        renderer.drawRect(4, y, w, 2, 0xFF9CA7A5);
        renderer.drawRect(4, y, center - 4, 2, 0xFF77DCC8);
        if (timeline.getCursor()) {
            TimelineCursor.draw(center, size.getHeight(), renderer::drawRect);
        }
    }
}
