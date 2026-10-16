// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/** Paused, read-only snapshot of observed trades through the moment this screen was opened. */
final class BarterScreen extends Screen {
    private final Screen parent;
    private final ReplayAnalysis analysis;
    private final RecordingIndex index;
    private final ReplayHandler handler;
    private final IntConsumer seek;
    private final int time;
    private final double speed;
    private final Map<AbstractButtonWidget, String> tips = new LinkedHashMap<>();
    private final List<BarterHistory.Event> trades = new ArrayList<>();
    private final List<BarterStats.Total> totals = new ArrayList<>();
    private BarterScreenLayout layout;
    private BarterHistory loaded;
    private BarterStats stats;
    private TextFieldWidget search;
    private String query = "";
    private String status = "Reading barter history...";
    private int page;
    private int world = -1;
    private boolean items, oldest, closed, jumping;

    BarterScreen(Screen parent, ReplayAnalysis analysis, RecordingIndex index, ReplayHandler handler, IntConsumer seek) {
        super(new LiteralText("Piglin Barters"));
        this.parent = parent; this.analysis = analysis; this.index = index; this.handler = handler; this.seek = seek;
        speed = handler.getReplaySender().paused() ? 0 : handler.getReplaySender().getReplaySpeed();
        handler.getReplaySender().setReplaySpeed(0);
        time = handler.getReplaySender().currentTimeStamp();
        handler.getOverlay().setVisible(false);
    }

    @Override protected void init() {
        layout = new BarterScreenLayout(width, height);
        page = Math.min(page, layout.pages(size()) - 1);
        rebuild();
        refresh();
    }

    private void refresh() {
        if (closed) return;
        status = index.barterStatus;
        BarterHistory history = index.barters;
        if (history == loaded) return;
        loaded = history;
        RaceRecording recording = index.recording;
        RaceRecording.Interval interval = recording == null ? null : recording.intervalAt(time);
        world = interval == null ? -1 : interval.world;
        stats = new BarterStats(history, world, time);
        if (interval == null && history != null && history.available) status = "No recorded world at this moment";
        filter();
    }

    private void filter() {
        trades.clear(); totals.clear();
        if (stats != null) {
            for (BarterHistory.Event event : stats.events) {
                if (query.trim().isEmpty() || event.items.stream().anyMatch(item -> BarterStats.matches(item, query))) trades.add(event);
            }
            if (!oldest) Collections.reverse(trades);
            for (BarterStats.Total total : stats.items) if (BarterStats.matches(total.item, query)) totals.add(total);
        }
        page = Math.min(page, layout.pages(size()) - 1);
        rebuild();
    }

    private int size() { return items ? totals.size() : trades.size(); }

    private void rebuild() {
        buttons.clear(); children.clear(); tips.clear();
        int half = (layout.width - 8) / 2;
        tips.put(addButton(new ReplaySettingsCheckbox(layout.left, 29, half, "Bartering labels", () -> analysis.barteringLabels,
                value -> analysis.barteringLabels = value)),
                "Label adult piglins inspecting a gold ingot. Other admired items are excluded. Recorded visual state, not a guarantee that the trade will complete. Quick Mode may omit state changes.");
        tips.put(addButton(new ReplaySettingsCheckbox(layout.left + half + 8, 29, half, "Cluster counter", () -> analysis.piglinCounter,
                value -> { analysis.piglinCounter = value; analysis.clear(); })),
                "Largest one-block-radius piglin cluster within 64 blocks of the recorded player. Not a count of all loaded piglins or trades.");
        boolean focus = search != null && search.isFocused();
        int cursor = search == null ? query.length() : search.getCursor();
        search = new TextFieldWidget(textRenderer, layout.left, 72, layout.width - 86, 18, new LiteralText("Search barter items"));
        search.setMaxLength(96);
        search.setText(query);
        search.setCursor(cursor);
        search.setSuggestion(query.isEmpty() ? "Search items" : null);
        search.setChangedListener(value -> {
            if (value.equals(query)) return;
            query = value; page = 0; filter();
        });
        children.add(search);
        if (focus) { search.setSelected(true); setFocused(search); }
        button(layout.left + layout.width - 80, 71, 80, 20, oldest ? "Oldest first" : "Newest first", !items,
                () -> { oldest = !oldest; page = 0; filter(); }, "Trade order. Only completed trades up to this replay moment are included.");
        button(layout.left, 96, half, 20, "History", true, () -> { items = false; page = 0; filter(); },
                "Completed barters in this recorded world, up to " + ReplayTimeInput.format(time) + ". Click a trade to jump to it.");
        button(layout.left + half + 8, 96, half, 20, "Items", true, () -> { items = true; page = 0; filter(); },
                "Item totals for this recorded world, ordered by quantity. Click an item to filter trade history.");
        for (int row = 0; row < layout.rows && page * layout.rows + row < size(); row++) {
            int position = page * layout.rows + row;
            if (items) {
                BarterStats.Total total = totals.get(position);
                row(row, total.item, "", "x" + total.count, () -> {
                    query = total.item.getName().getString(); items = false; page = 0; filter();
                }, total.item.getName().getString() + " x" + total.count + "\nShow trades containing this item.");
            } else {
                BarterHistory.Event event = trades.get(position);
                ItemStack icon = event.items.stream().filter(item -> BarterStats.matches(item, query)).findFirst()
                        .orElse(event.items.isEmpty() ? ItemStack.EMPTY : event.items.get(0));
                StringBuilder tip = new StringBuilder("Replay ").append(ReplayTimings.clock(event.time));
                for (int i = 0; i < Math.min(8, event.items.size()); i++) {
                    ItemStack item = event.items.get(i);
                    tip.append('\n').append(item.getName().getString()).append(" x").append(item.getCount());
                }
                if (event.items.size() > 8) tip.append("\n+").append(event.items.size() - 8).append(" more stacks");
                tip.append("\n").append(event.dimension).append(" | ").append((int) Math.floor(event.x)).append(", ")
                        .append((int) Math.floor(event.y)).append(", ").append((int) Math.floor(event.z));
                row(row, icon, ReplayTimings.clock(event.time), icon.isEmpty() ? "" : "x" + icon.getCount()
                        + (event.items.size() > 1 ? " +" + (event.items.size() - 1) : ""), () -> jump(event.time), tip.toString());
            }
        }
        button(layout.left, layout.footer, 20, 20, "<", page > 0, () -> { page--; rebuild(); }, "Previous page");
        button(layout.left + 64, layout.footer, 20, 20, ">", page + 1 < layout.pages(size()), () -> { page++; rebuild(); }, "Next page");
        button(layout.left + layout.width - 72, layout.footer, 72, 20, "Done", true, this::onClose, "Return to replay settings.");
    }

    private void row(int row, ItemStack item, String prefix, String count, Runnable action, String tooltip) {
        int y = layout.rowY(row);
        ButtonWidget button = new ButtonWidget(layout.left, y, layout.width, 19, LiteralText.EMPTY, b -> action.run()) {
            @Override public void renderButton(MatrixStack matrices, int mx, int my, float delta) {
                if (isHovered() || isFocused()) fill(matrices, x, y, x + width, y + height, 0x35FFFFFF);
                int cursor = x + 3;
                if (!prefix.isEmpty()) {
                    drawStringWithShadow(matrices, textRenderer, prefix, cursor, y + 6, 0xAAAAAA);
                    cursor += Math.max(56, textRenderer.getWidth(prefix) + 8);
                }
                renderItem(matrices, item, cursor, y + 1);
                cursor += 20;
                int countWidth = textRenderer.getWidth(count);
                String name = item.isEmpty() ? "No items" : item.getName().getString();
                drawStringWithShadow(matrices, textRenderer, textRenderer.trimToWidth(name, Math.max(0, x + width - 12 - countWidth - cursor)), cursor, y + 6, 0xEEEEEE);
                drawStringWithShadow(matrices, textRenderer, count, x + width - countWidth - 5, y + 6, 0xA9D9CD);
            }
        };
        tips.put(addButton(button), tooltip);
    }

    private void button(int x, int y, int width, int height, String label, boolean active, Runnable action, String tooltip) {
        ButtonWidget button = addButton(new ButtonWidget(x, y, width, height, new LiteralText(label), b -> action.run()) {
            @Override public void renderButton(MatrixStack matrices, int mx, int my, float delta) {
                if (isHovered() || isFocused()) fill(matrices, x, y, x + width, y + height, 0x30FFFFFF);
                boolean selected = label.equals(items ? "Items" : "History");
                drawCenteredString(matrices, textRenderer, label, x + width / 2, y + 6, active ? 0xFFFFFF : 0x777777);
                if (selected) fill(matrices, x, y + height - 1, x + width, y + height, 0xFFA9D9CD);
            }
        });
        button.active = active;
        tips.put(button, tooltip);
    }

    private void renderItem(MatrixStack matrices, ItemStack item, int x, int y) {
        RenderSystem.pushMatrix();
        RenderSystem.multMatrix(matrices.peek().getModel());
        try {
            RenderSystem.enableDepthTest();
            client.getItemRenderer().renderGuiItemIcon(item, x, y);
        } finally { RenderSystem.popMatrix(); RenderSystem.disableDepthTest(); }
    }

    private void jump(int target) {
        if (closed || ReplayModReplay.instance.getReplayHandler() != handler) return;
        jumping = true;
        try { seek.accept(target); }
        catch (RuntimeException error) {
            org.apache.logging.log4j.LogManager.getLogger("ZSG Replay Viewer").warn("Barter seek failed", error);
            client.inGameHud.setOverlayMessage(new LiteralText("Could not jump to this barter"), false);
        }
        finally { jumping = false; finish(null); }
    }

    private void finish(Screen destination) {
        if (release()) client.openScreen(destination);
    }

    private boolean release() {
        if (closed) return false;
        closed = true;
        if (ReplayModReplay.instance.getReplayHandler() != handler) return false;
        handler.getReplaySender().setReplaySpeed(speed);
        handler.getOverlay().setVisible(true);
        return true;
    }

    @Override public void tick() { search.tick(); refresh(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { finish(parent); }
    @Override public void removed() { if (!jumping) release(); }

    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (amount == 0) return false;
        page = Math.max(0, Math.min(layout.pages(size()) - 1, page + (amount < 0 ? 1 : -1)));
        rebuild(); return true;
    }

    @Override public void render(MatrixStack matrices, int mx, int my, float delta) {
        fill(matrices, 0, 0, width, height, 0xB0000000);
        drawCenteredString(matrices, textRenderer, title.getString(), width / 2, 12, 0xFFFFFF);
        String summary = loaded == null || !loaded.available ? status : world < 0 ? "No recorded world at this moment" : "Gold " + stats.events.size() + " | Piglins " + stats.piglins
                + " | " + ReplayTimeInput.format(time) + (loaded.truncated ? " | Partial" : "");
        drawStringWithShadow(matrices, textRenderer, textRenderer.trimToWidth(summary, layout.width), layout.left, 56, 0xE8C785);
        if (size() == 0) drawCenteredString(matrices, textRenderer,
                textRenderer.trimToWidth(!status.isEmpty() ? status : "No matching trades", layout.width), width / 2, layout.rowY(0) + 6, 0xAAAAAA);
        drawCenteredString(matrices, textRenderer, (page + 1) + "/" + layout.pages(size()), layout.left + 42, layout.footer + 6, 0xAAAAAA);
        search.render(matrices, mx, my, delta);
        super.render(matrices, mx, my, delta);
        for (Map.Entry<AbstractButtonWidget, String> entry : tips.entrySet()) if (entry.getKey().isHovered()) {
            int max = Math.min(240, Math.max(80, Math.max(mx - 24, width - mx - 24)));
            renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(entry.getValue()), max), mx, my); break;
        }
    }
}
