// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.lib.de.johni0702.minecraft.gui.GuiRenderer;
import com.replaymod.lib.de.johni0702.minecraft.gui.RenderInfo;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiContainer;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiTooltip;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.KeyHandler;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.KeyInput;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.popup.AbstractGuiPopup;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Color;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Dimension;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Point;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.ReadableDimension;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.IntConsumer;

/** A bounded native overlay popup; it never replaces the replay's screen or sender. */
final class MilestoneChooser extends AbstractGuiPopup<MilestoneChooser> implements KeyHandler {
    private final MilestonePlacement.Group group;
    private final IntConsumer seek;
    private final Choice[] rows = new Choice[6];
    private final Choice previous = new Choice(-1, "Previous milestones");
    private final Choice next = new Choice(1, "Next milestones");
    private final Choice cancel = new Choice(0, "Close milestones");
    private int page, pageSize;
    private boolean closed;

    MilestoneChooser(GuiContainer container, MilestonePlacement.Group group, IntConsumer seek) {
        super(container);
        this.group = group;
        this.seek = seek;
        disablePopupBackground();
        popup.setBackgroundColor(new Color(0, 0, 0, 180));
        for (int i = 0; i < rows.length; i++) rows[i] = new Choice(2, "Milestone");
        popup.addElements(null, rows);
        popup.addElements(null, previous, next, cancel);
        popup.setLayout(new CustomLayout<GuiPanel>() {
            @Override public ReadableDimension calcMinSize(GuiContainer<?> container) {
                return new Dimension(Math.max(100, Math.min(340, getMinecraft().getWindow().getScaledWidth() - 40)),
                        48 + capacity() * 22);
            }

            @Override protected void layout(GuiPanel container, int width, int height) {
                int count = capacity();
                if (pageSize != count) {
                    int first = page * Math.max(1, pageSize);
                    pageSize = count;
                    page = first / pageSize;
                    refresh();
                }
                for (int i = 0; i < rows.length; i++) set(rows[i], 6, 22 + i * 22,
                        width - 12, i < pageSize ? 20 : 0);
                set(previous, 6, height - 24, 20, 20);
                set(next, 30, height - 24, 20, 20);
                set(cancel, width - 26, height - 24, 20, 20);
            }
        });
    }

    private int capacity() { return Math.max(1, Math.min(rows.length, (getMinecraft().getWindow().getScaledHeight() - 80) / 22)); }

    void show() { open(); }
    @Override protected MilestoneChooser getThis() { return this; }

    private void refresh() {
        page = Math.max(0, Math.min(page, group.pages(pageSize) - 1));
        List<Milestones.Entry> entries = group.page(page, pageSize);
        for (int i = 0; i < rows.length; i++) rows[i].bind(i < entries.size() ? entries.get(i) : null);
        previous.setEnabled(page > 0);
        next.setEnabled(page + 1 < group.pages(pageSize));
    }

    private void changePage(int direction) {
        if (closed || pageSize == 0) return;
        page += direction;
        refresh();
    }

    @Override protected void close() {
        if (closed) return;
        closed = true;
        super.close();
    }

    @Override public boolean handleKey(KeyInput input) {
        if (input.isEscape()) { close(); return true; }
        if (input.key == GLFW.GLFW_KEY_LEFT || input.key == GLFW.GLFW_KEY_PAGE_UP) { changePage(-1); return true; }
        if (input.key == GLFW.GLFW_KEY_RIGHT || input.key == GLFW.GLFW_KEY_PAGE_DOWN) { changePage(1); return true; }
        return false;
    }

    private final class Choice extends GuiButton {
        private final int action;
        private Milestones.Entry entry;

        Choice(int action, String name) {
            this.action = action;
            setLabel(name);
            if (action != 2) setTooltip(new GuiTooltip().setText(new String[] {name}));
            onClick(() -> {
                if (closed) return;
                if (action == 0) close();
                else if (action != 2) changePage(action);
                else if (entry != null) {
                    int time = entry.time;
                    close();
                    seek.accept(time);
                }
            });
        }

        void bind(Milestones.Entry entry) {
            this.entry = entry;
            setEnabled(entry != null);
            setLabel(entry == null ? "" : MilestonePlacement.eventText(entry));
            setTooltip(entry == null ? null : new GuiTooltip().setText(new String[] {getLabel()}));
        }

        @Override public ReadableDimension calcMinSize() { return new Dimension(1, 1); }

        @Override public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo info) {
            if ((action == 2 && entry == null) || size.getHeight() == 0) return;
            Point mouse = new Point(info.mouseX, info.mouseY);
            boolean hover = isEnabled() && isMouseHovering(mouse);
            int width = size.getWidth(), height = size.getHeight();
            renderer.drawRect(0, 0, width, height, hover ? 0xA0666666 : 0x50333333);
            if (action == 2) {
                MilestoneGlyphs.draw(entry.kind, 3, 4, Math.min(12, width - 3), 12, renderer::drawRect);
                String text = getMinecraft().textRenderer.trimToWidth(getLabel(), Math.max(0, width - 23));
                renderer.drawString(19, 6, 0xFFEEEEEE, text);
            } else {
                int color = isEnabled() ? 0xFFEEEEEE : 0xFF888888;
                for (int i = 0; i < 7; i++) {
                    if (action == 0) {
                        renderer.drawRect(7 + i, 7 + i, 1, 1, color);
                        renderer.drawRect(13 - i, 7 + i, 1, 1, color);
                    } else {
                        int offset = Math.abs(3 - i);
                        renderer.drawRect(action < 0 ? 7 + offset : 12 - offset, 7 + i, 1, 1, color);
                    }
                }
            }
        }
    }

    @Override public void draw(GuiRenderer renderer, ReadableDimension size, RenderInfo info) {
        super.draw(renderer, size, info);
        if (info.getLayer() != 0 || pageSize == 0) return;
        int panelWidth = popup.getMinSize().getWidth(), panelHeight = popup.getMinSize().getHeight();
        int left = (size.getWidth() - panelWidth) / 2, top = (size.getHeight() - panelHeight) / 2;
        String title = getMinecraft().textRenderer.trimToWidth(group.entries.size() + " milestones", panelWidth - 12);
        String pagination = getMinecraft().textRenderer.trimToWidth(
                (page + 1) + " / " + group.pages(pageSize), Math.max(0, panelWidth - 90));
        renderer.drawString(left + 6, top + 6, 0xFFEEEEEE, title);
        renderer.drawString(left + 58, top + panelHeight - 18, 0xFFCCCCCC,
                pagination);
    }
}
