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

import java.util.List;
import java.util.function.IntConsumer;

final class MilestoneBar extends AbstractGuiElement<MilestoneBar> implements Clickable {
    private final MilestoneIndex index;
    private final int duration;
    private final IntConsumer seek;
    private List<Milestones.Entry> source;
    private MilestonePlacement placement;
    private int width, height;

    MilestoneBar(MilestoneIndex index, int duration, IntConsumer seek) {
        this.index = index; this.duration = Math.max(1, duration); this.seek = seek;
    }

    @Override protected MilestoneBar getThis() { return this; }
    @Override protected ReadableDimension calcMinSize() { return new Dimension(1, MilestonePlacement.HEIGHT); }

    @Override public void layout(ReadableDimension size, RenderInfo info) {
        super.layout(size, info);
        if (size != null) updatePlacement(size);
    }

    private void updatePlacement(ReadableDimension size) {
        List<Milestones.Entry> published = index.entries;
        height = Math.max(0, Math.min(MilestonePlacement.HEIGHT, size.getHeight()));
        int nextWidth = Math.max(0, size.getWidth());
        if (placement == null || source != published || width != nextWidth) {
            width = nextWidth;
            source = published;
            placement = new MilestonePlacement(published, width, duration);
        }
    }

    private Point local(int x, int y) {
        Point point = new Point(x, y);
        if (getContainer() != null) getContainer().convertFor(this, point);
        return point;
    }

    @Override
    public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo info) {
        super.draw(renderer, size, info);
        updatePlacement(size);
        if (height == 0 || width == 0) return;
        // ReplayMod offsets draw-time mouse coordinates; click/tooltip coordinates are global.
        MilestonePlacement.Group hover = placement.hit(info.mouseX, info.mouseY, height);
        for (MilestonePlacement.Group group : placement.groups) {
            int markerWidth = group.right - group.left;
            if (group == hover && isEnabled()) renderer.drawRect(group.left, 0, markerWidth, height, 0x70464646);
            int glyphX = group.left + Math.max(0, (markerWidth - MilestoneGlyphs.SIZE) / 2);
            int glyphY = height >= MilestonePlacement.HEIGHT ? 1 : 0;
            MilestoneGlyphs.draw(group.entries.get(0).kind, glyphX, glyphY,
                    group.right - glyphX, Math.min(MilestoneGlyphs.SIZE, height - glyphY), renderer::drawRect);
            if (height >= 14) {
                int center = group.left + markerWidth / 2;
                if (group.grouped()) {
                    for (int offset = -3; offset <= 3; offset += 3) {
                        int dot = center + offset;
                        if (dot >= group.left && dot < group.right) renderer.drawRect(dot, height - 2, 1, 1, 0xFFDDDDDD);
                    }
                } else if (center < group.right) renderer.drawRect(center, height - 2, 1, 2, group.entries.get(0).kind.color);
            }
        }
        if (placement.groups.isEmpty()) {
            for (int x = 3; x < Math.min(12, width); x += 3) renderer.drawRect(x, height / 2, 1, 1, 0xFFAAAAAA);
        }
    }

    @Override
    public GuiElement getTooltip(RenderInfo info) {
        if (placement == null || !isEnabled()) return null;
        Point point = local(info.mouseX, info.mouseY);
        if (placement.groups.isEmpty() && point.getX() >= 0 && point.getX() < Math.min(16, width)
                && point.getY() >= 0 && point.getY() < height) return new GuiTooltip().setText(new String[] {index.status});
        MilestonePlacement.Group group = placement.hit(point.getX(), point.getY(), height);
        return group == null ? null : new GuiTooltip().setText(group.tooltip());
    }

    @Override
    public boolean mouseClick(Click click) {
        if (click.button != 0 || !isEnabled() || placement == null) return false;
        Point point = local(click.x, click.y);
        MilestonePlacement.Group group = placement.hit(point.getX(), point.getY(), height);
        if (group == null) return false;
        if (group.grouped()) {
            if (getContainer() == null) return false;
            new MilestoneChooser(getContainer(), group, seek).show();
        } else seek.accept(group.entries.get(0).time);
        return true;
    }
}
