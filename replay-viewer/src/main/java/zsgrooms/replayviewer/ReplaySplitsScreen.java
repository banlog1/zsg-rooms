// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntConsumer;

/** A read-only, paged comparison. Only visible columns are indexed, off the render thread. */
final class ReplaySplitsScreen extends Screen {
    private final ReplayHandler handler;
    private final ViewerControls.State state;
    private final MilestoneIndex activeIndex;
    private final IntConsumer localJump;
    private final int sourceTime;
    private final RaceReplayLibrary library;
    private final SplitMilestoneReader reader = new SplitMilestoneReader();
    private final ExecutorService files = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ZSG replay splits"); thread.setDaemon(true); return thread;
    });
    private final List<Column> columns = new ArrayList<>();
    private final Map<ButtonWidget, String> tooltips = new LinkedHashMap<>();
    private RaceRecording source;
    private RaceRecording.Race race;
    private ReplaySplitsLayout layout;
    private Future<?> work;
    private volatile boolean closed;
    private volatile int generation;
    private boolean started, selecting, jumping;
    private int page;
    private String status = "Reading race recordings...";

    ReplaySplitsScreen(ReplayHandler handler, ViewerControls.State state,
                       MilestoneIndex activeIndex, IntConsumer localJump) {
        super(new LiteralText("Splits"));
        this.handler = handler; this.state = state; this.activeIndex = activeIndex; this.localJump = localJump;
        sourceTime = handler.getReplaySender().currentTimeStamp();
        library = ReplayViewer.library();
        handler.getReplaySender().setReplaySpeed(0);
        handler.getOverlay().setVisible(false);
    }

    @Override protected void init() {
        int previousColumns = layout == null ? 0 : layout.columns;
        layout = new ReplaySplitsLayout(width, height, columns.size());
        page = Math.min(page, layout.pages(columns.size()) - 1);
        rebuild();
        if (!started) { started = true; load(); }
        else if (!selecting && race != null && previousColumns != layout.columns) requestPage();
    }

    private void rebuild() {
        buttons.clear(); children.clear(); tooltips.clear();
        for (int i = 0; i < layout.columns && page * layout.columns + i < columns.size(); i++) {
            Column column = columns.get(page * layout.columns + i);
            int x = layout.columnX(i), cellWidth = layout.columnWidth;
            ReplaySplits data = column.data;
            int worlds = data == null ? 0 : data.worlds.size();
            button(x + 4, 66, 18, 18, "<", !selecting && worlds > 0 && column.world > 0,
                    () -> { column.world--; rebuild(); }, "Previous recorded world. Resets are kept separate.");
            button(x + cellWidth - 22, 66, 18, 18, ">", !selecting && column.world + 1 < worlds,
                    () -> { column.world++; rebuild(); }, "Next recorded world. World numbers do not identify a shared seed.");
            for (Milestones.Kind kind : Milestones.Kind.values()) {
                ReplaySplits.Split split = worlds == 0 ? null : data.worlds.get(column.world).splits.get(kind);
                String label = split == null ? "--" : ReplayTimings.clock(split.elapsed);
                String tip = split == null ? (data == null ? column.status : "No " + kind.label
                        + " split recorded in this world.") : kind.description + "\n" + column.entry.label()
                        + "\nRace elapsed " + ReplayTimings.clock(split.elapsed)
                        + "\nReplay time " + ReplayTimeInput.format(split.time)
                        + "\nRecorded IGT " + ReplayTimings.clock(split.igt)
                        + (split.jumpable ? "\nJump to this moment and follow this runner."
                        : "\nRecorded during a coverage gap. No player view is available at this exact moment.");
                button(x + 3, layout.rowY(kind.ordinal()), cellWidth - 6, layout.rowHeight,
                        label, !selecting && split != null && split.jumpable, () -> select(column, split), tip);
            }
        }
        button(layout.left, layout.footerY, 76, 20, "Players...", !selecting && race != null,
                () -> { onClose(); client.openScreen(new RaceReplayScreen(handler, state)); },
                "Import a recording from this race, or switch runners at the same elapsed race time.");
        button(width / 2 - 48, layout.footerY, 20, 20, "<", !selecting && page > 0,
                () -> { page--; requestPage(); }, "Previous runners");
        button(width / 2 + 28, layout.footerY, 20, 20, ">", !selecting && page + 1 < layout.pages(columns.size()),
                () -> { page++; requestPage(); }, "Next runners");
        button(layout.left + layout.width - 64, layout.footerY, 64, 20, "Done", true, this::onClose,
                "Return to playback. Your previous playback speed is restored.");
    }

    private void button(int x, int y, int width, int height, String text, boolean enabled, Runnable action, String tip) {
        ButtonWidget button = addButton(new FlatButton(x, y, width, height, text, action));
        button.active = enabled;
        tooltips.put(button, tip);
    }

    private boolean current(Column column) {
        return source != null && column.entry.recording.recordingId.equals(source.recordingId);
    }

    private void load() {
        File file = RecordingIndex.file(handler.getReplayFile());
        if (file == null) { status = "Save this replay before comparing splits"; return; }
        int token = ++generation;
        work = files.submit(() -> {
            try {
                CompletedIndexCache.Key key = CompletedIndexCache.Key.read(file.toPath());
                RecordingIndex.verifyIdentity(handler.getReplayFile(), key);
                RaceRecording recording = RaceRecording.read(file.toPath());
                RaceRecording.Race selected = recording.raceAt(sourceTime);
                if (selected == null) throw new IOException("No recorded race at this time");
                List<RaceReplayLibrary.Entry> entries = library.scan(recording, selected);
                if (!key.equals(CompletedIndexCache.Key.read(file.toPath()))) throw new IOException("Recording changed; reopen replay to compare splits");
                // Put the current take first without changing the library's normal ordering.
                entries.sort(java.util.Comparator.comparingInt(e -> e.recording.recordingId.equals(recording.recordingId)
                        && e.race.id.equals(selected.id) ? 0 : 1));
                apply(token, () -> {
                    source = recording; race = selected;
                    for (RaceReplayLibrary.Entry entry : entries) columns.add(new Column(entry));
                    layout = new ReplaySplitsLayout(width, height, columns.size());
                    status = "";
                    requestPage();
                });
            } catch (Exception error) { fail(token, error); }
        });
    }

    private void requestPage() {
        if (closed || selecting) return;
        if (work != null) work.cancel(true);
        int token = ++generation;
        List<Column> pending = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            if (i < page * layout.columns || i >= (page + 1) * layout.columns) {
                column.data = null; column.key = null;
            } else if (column.data == null && !current(column)) {
                column.status = "Indexing splits...";
                pending.add(column);
            }
        }
        refreshActive();
        rebuild();
        work = files.submit(() -> {
            for (Column column : pending) {
                if (cancelled(token)) return;
                try {
                    CompletedIndexCache.Key key = CompletedIndexCache.Key.read(column.entry.recording.path);
                    RaceRecording checked = RaceRecording.read(column.entry.recording.path);
                    RaceRecording.Race targetRace = checkedRace(column.entry, checked);
                    List<Milestones.Entry> events = reader.read(checked.path, () -> cancelled(token));
                    if (!key.equals(CompletedIndexCache.Key.read(checked.path))) throw new IOException("Recording changed on disk");
                    ReplaySplits data = new ReplaySplits(new RaceReplayLibrary.Entry(checked, targetRace), events);
                    apply(token, () -> { column.key = key; column.set(data, -1); rebuild(); });
                } catch (Exception error) {
                    if (cancelled(token)) return;
                    apply(token, () -> { column.status = message(error); rebuild(); });
                }
            }
        });
    }

    private RaceRecording.Race checkedRace(RaceReplayLibrary.Entry entry, RaceRecording checked) throws IOException {
        if (!checked.recordingId.equals(entry.recording.recordingId) || !checked.recorder.equals(entry.recording.recorder)) {
            throw new IOException("Recording changed on disk");
        }
        return checked.races.stream().filter(r -> r.id.equals(entry.race.id) && r.group().equals(race.group())).findFirst()
                .orElseThrow(() -> new IOException("Recording no longer belongs to this race"));
    }

    private void refreshActive() {
        if (!activeIndex.complete) return;
        boolean changed = false;
        for (int i = page * layout.columns; i < Math.min(columns.size(), (page + 1) * layout.columns); i++) {
            Column column = columns.get(i);
            if (current(column) && column.data == null && !column.failed) {
                if (activeIndex.available) {
                    column.set(new ReplaySplits(column.entry, activeIndex.entries), sourceTime);
                } else { column.failed = true; column.status = activeIndex.status; }
                changed = true;
            }
        }
        if (changed) rebuild();
    }

    @Override public void tick() { if (!closed && !selecting) refreshActive(); }

    private void select(Column column, ReplaySplits.Split split) {
        if (closed || selecting || split == null || !split.jumpable || ReplayModReplay.instance.getReplayHandler() != handler) return;
        if (current(column)) {
            // A dimension seek may temporarily replace this screen with the loading screen.
            jumping = true;
            try { localJump.accept(split.time); }
            catch (RuntimeException error) {
                org.apache.logging.log4j.LogManager.getLogger("ZSG Replay Viewer").warn("Split seek failed", error);
                client.inGameHud.setOverlayMessage(new LiteralText("Could not jump to this split"), false);
            } finally { jumping = false; onClose(); }
            return;
        }
        selecting = true; status = "Opening runner at split...";
        if (work != null) work.cancel(true);
        int token = ++generation;
        rebuild();
        work = files.submit(() -> {
            try {
                if (column.key == null || !column.key.equals(CompletedIndexCache.Key.read(column.entry.recording.path))) {
                    throw new IOException("Recording changed; reopen Splits to refresh");
                }
                RaceRecording checked = RaceRecording.read(column.entry.recording.path);
                RaceRecording.Race targetRace = checkedRace(column.entry, checked);
                if (!ReplaySplits.covered(checked, targetRace, split.time)) throw new IOException("No recorded coverage at this split");
                apply(token, () -> {
                    closed = true;
                    files.shutdownNow();
                    ReplayViewer.switchToSplit(handler, source, sourceTime, checked, split.time, state, column.key);
                });
            } catch (Exception error) { fail(token, error); }
        });
    }

    private boolean cancelled(int token) { return closed || token != generation || Thread.currentThread().isInterrupted(); }

    private void apply(int token, Runnable action) {
        MinecraftClient.getInstance().send(() -> {
            if (!closed && generation == token && client.currentScreen == this
                    && ReplayModReplay.instance.getReplayHandler() == handler) action.run();
        });
    }

    private void fail(int token, Exception error) {
        apply(token, () -> { selecting = false; status = message(error); rebuild(); });
    }

    private static String message(Exception error) {
        String message = error.getMessage();
        return message == null || message.isEmpty() ? "Splits unavailable for this recording" : message;
    }

    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        fill(matrices, 0, 0, width, height, 0xB5101316);
        drawCenteredString(matrices, textRenderer, title.getString(), width / 2, 12, 0xFFFFFF);
        String subtitle = race == null ? "" : (race.testGroup == null ? "Race elapsed" : "Test elapsed")
                + "  |  " + columns.size() + (columns.size() == 1 ? " recording" : " recordings");
        drawCenteredString(matrices, textRenderer, subtitle, width / 2, 27, 0xA9D9CD);
        for (Milestones.Kind kind : Milestones.Kind.values()) {
            int y = layout.rowY(kind.ordinal());
            fill(matrices, layout.left, y + layout.rowHeight - 1, layout.left + layout.width, y + layout.rowHeight, 0x304F6666);
            MilestoneGlyphs.draw(kind, layout.left + 2, y + (layout.rowHeight - 12) / 2, 12, 12,
                    (x, top, w, h, color) -> fill(matrices, x, top, x + w, top + h, color));
            textRenderer.drawWithShadow(matrices, textRenderer.trimToWidth(kind.description, layout.labelWidth - 22),
                    layout.left + 20, y + (layout.rowHeight - 8) / 2, 0xD5DDDB);
        }
        for (int i = 0; i < layout.columns && page * layout.columns + i < columns.size(); i++) {
            Column column = columns.get(page * layout.columns + i);
            int x = layout.columnX(i), center = x + layout.columnWidth / 2;
            drawCenteredString(matrices, textRenderer, textRenderer.trimToWidth(column.entry.recording.name, layout.columnWidth - 12),
                    center, 44, current(column) && column.entry.race.id.equals(race.id) ? 0x8FE2C8 : 0xFFFFFF);
            String take = column.entry.recording.recordingId.toString().substring(0, 8);
            if (column.entry.recording.races.size() > 1) take += " / " + (column.entry.recording.races.indexOf(column.entry.race) + 1);
            drawCenteredString(matrices, textRenderer, take, center, 56, 0x9BA6A7);
            int worlds = column.data == null ? 0 : column.data.worlds.size();
            String world = column.data == null ? (column.failed || !column.status.equals("Indexing splits...") ? "Unavailable" : "Indexing...")
                    : worlds == 0 ? "No coverage" : "World " + (column.world + 1) + " / " + worlds;
            drawCenteredString(matrices, textRenderer, textRenderer.trimToWidth(world, layout.columnWidth - 48), center, 72, 0xB3C4C0);
        }
        drawCenteredString(matrices, textRenderer, (page + 1) + " / " + layout.pages(columns.size()), width / 2, layout.footerY + 6, 0xB3C4C0);
        drawCenteredString(matrices, textRenderer, textRenderer.trimToWidth(status, layout.width), width / 2, layout.footerY - 13, 0xE3C89A);
        super.render(matrices, mouseX, mouseY, delta);
        String tip = null;
        for (Map.Entry<ButtonWidget, String> entry : tooltips.entrySet()) if (entry.getKey().isHovered()) { tip = entry.getValue(); break; }
        if (mouseY >= 40 && mouseY < 65) {
            int columnIndex = (mouseX - layout.columnX(0)) / layout.columnWidth;
            int index = page * layout.columns + columnIndex;
            if (mouseX >= layout.columnX(0) && columnIndex < layout.columns && index < columns.size()) {
                Column column = columns.get(index);
                tip = column.entry.label() + "\n" + column.entry.recording.path.getFileName();
            }
        }
        if (mouseY >= 24 && mouseY < 38) tip = "Elapsed time from each recording's race start, including loading."
                + "\nEach column keeps reset worlds separate. World numbers alone do not identify the same seed."
                + "\nOnly recorded advancement completions appear; unavailable splits are never estimated.";
        if (tip != null) renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(tip), Math.min(320, width - 24)), mouseX, mouseY);
    }

    @Override public void onClose() {
        if (closed) return;
        stop();
        client.openScreen(null);
    }

    private void stop() {
        closed = true; generation++;
        if (work != null) work.cancel(true);
        files.shutdownNow();
        if (ReplayModReplay.instance.getReplayHandler() == handler) {
            handler.getOverlay().setVisible(true);
            handler.getOverlay().setMouseVisible(state.mouseVisible);
            handler.getReplaySender().setReplaySpeed(state.speed);
        }
    }

    @Override public void removed() { if (!closed && !jumping) stop(); }
    @Override public boolean isPauseScreen() { return false; }

    private static final class Column {
        final RaceReplayLibrary.Entry entry;
        ReplaySplits data;
        CompletedIndexCache.Key key;
        String status = "Indexing splits...";
        int world;
        boolean failed, initialized;
        Column(RaceReplayLibrary.Entry entry) { this.entry = entry; }
        void set(ReplaySplits data, int time) {
            this.data = data;
            world = initialized || time < 0 ? Math.min(world, Math.max(0, data.worlds.size() - 1)) : data.worldAt(time);
            initialized = true;
            status = data.worlds.isEmpty() ? "No recorded world coverage" : "";
        }
    }

    private static final class FlatButton extends ButtonWidget {
        FlatButton(int x, int y, int width, int height, String label, Runnable action) {
            super(x, y, width, height, new LiteralText(label), button -> action.run());
        }
        @Override public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            if (active && (isHovered() || isFocused())) fill(matrices, x, y, x + width, y + height, 0x5545776D);
            if (active && isFocused()) fill(matrices, x, y + height - 1, x + width, y + height, 0xFF8FE2C8);
            net.minecraft.client.font.TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
            drawCenteredString(matrices, renderer, renderer.trimToWidth(getMessage().getString(), width - 6),
                    x + width / 2, y + (height - 8) / 2, active ? 0xEDF4F1 : 0x83918F);
        }
    }
}
