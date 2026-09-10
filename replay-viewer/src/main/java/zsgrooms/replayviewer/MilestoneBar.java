// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.AbstractGuiElement;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiElement;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiTooltip;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Click;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Clickable;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

final class MilestoneBar extends AbstractGuiElement<MilestoneBar> implements Clickable {
    private final MilestoneIndex index;
    private final int duration;
    private final IntConsumer seek;
    private int width;

    MilestoneBar(MilestoneIndex index, int duration, IntConsumer seek) {
        this.index = index; this.duration = Math.max(1, duration); this.seek = seek;
    }

    @Override protected MilestoneBar getThis() { return this; }
    @Override protected ReadableDimension calcMinSize() { return new Dimension(1, 14); }

    private int x(Milestones.Entry entry) {
        return 4 + (int) ((long) entry.time * Math.max(0, width - 8) / duration);
    }

    @Override
    public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo info) {
        super.draw(renderer, size, info);
        width = size.getWidth();
        int labelEnd = 0;
        for (Milestones.Entry entry : index.entries) {
            int x = x(entry);
            renderer.drawRect(x, 10, 2, 4, entry.kind.color);
            int labelWidth = getMinecraft().textRenderer.getWidth(entry.kind.label);
            int left = Math.max(0, Math.min(width - labelWidth, x - labelWidth / 2));
            if (left >= labelEnd) {
                renderer.drawString(left, 0, entry.kind.color, entry.kind.label);
                labelEnd = left + labelWidth + 4;
            }
        }
        if (index.entries.isEmpty()) renderer.drawString(0, 0, 0xFFAAAAAA, index.status);
    }

    private List<Milestones.Entry> near(int x) {
        List<Milestones.Entry> hits = new ArrayList<>();
        for (Milestones.Entry entry : index.entries) if (Math.abs(x(entry) - x) <= 5) hits.add(entry);
        return hits;
    }

    @Override
    public GuiElement getTooltip(RenderInfo info) {
        Point point = new Point(info.mouseX, info.mouseY);
        getContainer().convertFor(this, point);
        if (point.getY() < 0 || point.getY() >= 14 || point.getX() < 0 || point.getX() >= width) return null;
        List<Milestones.Entry> hits = near(point.getX());
        if (hits.isEmpty()) return null;
        List<String> lines = new ArrayList<>();
        for (Milestones.Entry entry : hits) {
            if (lines.size() == 8) { lines.add("More milestones at this position"); break; }
            lines.add(entry.tooltip());
        }
        return new GuiTooltip().setText(lines.toArray(new String[0]));
    }

    @Override
    public boolean mouseClick(Click click) {
        if (click.button != 0) return false;
        Point point = new Point(click.x, click.y);
        getContainer().convertFor(this, point);
        if (point.getY() < 0 || point.getY() >= 14) return false;
        List<Milestones.Entry> hits = near(point.getX());
        if (hits.isEmpty()) return false;
        Milestones.Entry nearest = hits.get(0);
        for (Milestones.Entry entry : hits) if (Math.abs(x(entry) - point.getX()) < Math.abs(x(nearest) - point.getX())) nearest = entry;
        seek.accept(nearest.time);
        return true;
    }
}
