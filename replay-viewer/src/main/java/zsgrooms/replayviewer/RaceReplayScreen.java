// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import org.lwjgl.PointerBuffer;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Paused selection at a fixed source race time; archive work never runs in render(). */
final class RaceReplayScreen extends Screen {
    private static final ExecutorService FILES = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ZSG replay library"); thread.setDaemon(true); return thread;
    });
    private final ReplayHandler handler;
    private final ViewerControls.State state;
    private final int sourceTime;
    private final RaceReplayLibrary library;
    private RaceRecording source;
    private RaceRecording.Race race;
    private long elapsed;
    private List<RaceReplayLibrary.Entry> entries = Collections.emptyList();
    private final List<ButtonWidget> rows = new ArrayList<>();
    private String status = "Reading race metadata...";
    private boolean busy = true;
    private boolean started;
    private boolean closed;
    private int page;
    private int pageSize;
    private int generation;
    private int left;
    private int contentWidth;

    RaceReplayScreen(ReplayHandler handler, ViewerControls.State state) {
        super(new LiteralText("Race Players"));
        this.handler = handler;
        this.state = state;
        sourceTime = handler.getReplaySender().currentTimeStamp();
        handler.getReplaySender().setReplaySpeed(0);
        handler.getOverlay().setVisible(false);
        library = ReplayViewer.library();
    }

    @Override protected void init() {
        contentWidth = Math.min(360, width - 32);
        left = (width - contentWidth) / 2;
        pageSize = Math.max(1, (height - 130) / 24);
        page = Math.min(page, Math.max(0, (entries.size() - 1) / pageSize));
        rows.clear();
        for (int i = 0; i < pageSize; i++) {
            int index = page * pageSize + i;
            if (index >= entries.size()) break;
            RaceReplayLibrary.Entry entry = entries.get(index);
            int target = entry.recording.targetTime(entry.race, elapsed);
            boolean current = source != null && entry.recording.recordingId.equals(source.recordingId) && entry.race.id.equals(race.id);
            String suffix = current ? " [Current]" : target < 0 ? " [Unavailable]" : "";
            ButtonWidget button = addButton(new ButtonWidget(left, 64 + i * 24, contentWidth, 20,
                    new LiteralText(textRenderer.trimToWidth(entry.label(), contentWidth - textRenderer.getWidth(suffix) - 14) + suffix), b -> select(entry)));
            button.active = !busy && target >= 0 && !current;
            rows.add(button);
        }
        addButton(new ButtonWidget(left, height - 60, 24, 20, new LiteralText("<"), b -> { page--; refresh(); })).active = !busy && page > 0;
        addButton(new ButtonWidget(left + contentWidth - 24, height - 60, 24, 20, new LiteralText(">"), b -> { page++; refresh(); })).active = !busy && (page + 1) * pageSize < entries.size();
        int half = (contentWidth - 6) / 2;
        addButton(new ButtonWidget(left, height - 32, half, 20, new LiteralText("Import Replay..."), b -> importFile())).active = !busy && race != null;
        addButton(new ButtonWidget(left + half + 6, height - 32, half, 20, new LiteralText("Back"), b -> onClose())).active = !busy;
        if (!started) {
            started = true;
            load();
        }
    }

    private void refresh() { buttons.clear(); children.clear(); init(); }

    private void load() {
        File file = RecordingIndex.file(handler.getReplayFile());
        if (file == null) { busy = false; status = "Save this recording before grouping"; refresh(); return; }
        work(() -> {
            RaceRecording recording = RaceRecording.read(file.toPath());
            RaceRecording.Race selected = recording.raceAt(sourceTime);
            if (selected == null) throw new java.io.IOException("No race start at this time; legacy replays play individually");
            List<RaceReplayLibrary.Entry> found = library.scan(recording, selected);
            return () -> {
                source = recording; race = selected; elapsed = sourceTime * 1000000L - race.start;
                ReplayViewer.raceContext(recording);
                entries = found; status = found.size() + " recordings";
            };
        });
    }

    private void importFile() {
        String chosen = TinyFileDialogs.tinyfd_openFileDialog("Import race replay", "", (PointerBuffer) null, "Minecraft replay (*.mcpr)", false);
        if (chosen == null) return;
        importPath(Paths.get(chosen));
    }

    private void importPath(Path path) {
        busy = true; status = "Importing replay..."; refresh();
        work(() -> {
            library.importReplay(path, race.group());
            List<RaceReplayLibrary.Entry> found = library.scan(source, race);
            return () -> { entries = found; status = "Replay imported"; };
        });
    }

    private void select(RaceReplayLibrary.Entry entry) {
        busy = true; status = "Checking recording..."; refresh();
        work(() -> {
            RaceRecording checked = RaceRecording.read(entry.recording.path);
            if (!checked.recordingId.equals(entry.recording.recordingId)) throw new java.io.IOException("Recording changed on disk");
            RaceRecording.Race targetRace = checked.races.stream().filter(r -> r.id.equals(entry.race.id) && r.group().equals(race.group())).findFirst()
                    .orElseThrow(() -> new java.io.IOException("Race no longer matches"));
            int target = checked.targetTime(targetRace, elapsed);
            if (target < 0) throw new java.io.IOException("No player coverage at this race time");
            return () -> {
                closed = true;
                ReplayViewer.switchRecording(handler, source, sourceTime, checked, target, state);
            };
        });
    }

    private void work(FileWork action) {
        int token = ++generation;
        CompletableFuture.supplyAsync(() -> {
            try { return action.run(); }
            catch (Exception error) { throw new java.util.concurrent.CompletionException(error); }
        }, FILES).whenComplete((apply, error) -> MinecraftClient.getInstance().send(() -> {
            if (closed || generation != token || client.currentScreen != this || ReplayModReplay.instance.getReplayHandler() != handler) return;
            busy = false;
            if (error == null) {
                try { apply.run(); }
                catch (Exception failure) {
                    org.apache.logging.log4j.LogManager.getLogger("ZSG Replay Viewer").warn("Replay operation failed", failure);
                    status = "Replay operation failed";
                }
            } else {
                String message = error.getCause() == null ? null : error.getCause().getMessage();
                status = message == null ? "Replay operation failed" : message;
            }
            if (!closed) refresh();
        }));
    }

    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        drawCenteredString(matrices, textRenderer, title.getString(), width / 2, 12, 0xFFFFFF);
        String group = race == null ? status : (race.testGroup == null ? "Race" : "Solo test") + "  |  "
                + (elapsed < 0 ? "Before start" : ViewerLayout.time((int) (elapsed / 1000000L)));
        drawCenteredString(matrices, textRenderer, textRenderer.trimToWidth(group, contentWidth), width / 2, 30, 0xA8D8FF);
        if (race != null) drawCenteredString(matrices, textRenderer, textRenderer.trimToWidth(status, contentWidth), width / 2, 46, 0xAAAAAA);
        drawCenteredString(matrices, textRenderer, (page + 1) + " / " + Math.max(1, (entries.size() + pageSize - 1) / pageSize), width / 2, height - 54, 0xAAAAAA);
        super.render(matrices, mouseX, mouseY, delta);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).isHovered()) {
                RaceReplayLibrary.Entry entry = entries.get(page * pageSize + i);
                int target = entry.recording.targetTime(entry.race, elapsed);
                renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(entry.label() + "\n"
                        + (target < 0 ? "Loading gap, before race start, or recording ended." : "Replay time " + ViewerLayout.time(target))
                        + "\n" + entry.recording.path.getFileName()), Math.min(300, width - 32)), mouseX, mouseY);
            }
        }
    }

    @Override public void onClose() {
        if (busy) return;
        closed = true;
        if (ReplayModReplay.instance.getReplayHandler() == handler) {
            handler.getOverlay().setVisible(true);
            handler.getReplaySender().setReplaySpeed(state.speed);
        }
        client.openScreen(null);
    }

    @Override public boolean isPauseScreen() { return false; }
    private interface FileWork { Runnable run() throws Exception; }
}
