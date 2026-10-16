// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.replay.ReplayModReplay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** The library chooser is separate from the grouped race-player switcher. */
public final class ReplayBrowserScreen extends Screen {
    private static final ExecutorService FILES = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ZSG recording browser"); thread.setDaemon(true); return thread;
    });
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private final Screen parent;
    private final Path game;
    private List<ReplayBrowserFiles.Entry> entries = Collections.emptyList();
    private final List<ReplayBrowserFiles.Entry> filtered = new ArrayList<>();
    private final List<ButtonWidget> rowButtons = new ArrayList<>();
    private final List<ReplayBrowserButton> tools = new ArrayList<>();
    private Path selected, browse;
    private Future<?> scan;
    private TextFieldWidget search;
    private ReplayBrowserButton renameButton, deleteButton;
    private String query = "", status = "Reading recordings...";
    private int left, contentWidth, page, pageSize, generation;
    private boolean started, busy = true, closed;

    public ReplayBrowserScreen(Screen parent) {
        super(new LiteralText("Replay Library"));
        this.parent = parent;
        game = MinecraftClient.getInstance().runDirectory.toPath();
    }

    @Override protected void init() {
        int cursor = search == null ? query.length() : search.getCursor();
        contentWidth = Math.min(660, width - 24);
        left = (width - contentWidth) / 2;
        pageSize = Math.max(1, (height - 133) / 36);
        page = Math.max(0, Math.min(page, (filtered.size() - 1) / pageSize));
        tools.clear(); rowButtons.clear();
        search = addButton(new TextFieldWidget(textRenderer, left, 34, Math.max(40, contentWidth - 136), 22, new LiteralText("Search recordings")));
        search.setMaxLength(256);
        search.setText(query);
        search.setCursor(cursor);
        search.setChangedListener(value -> {
            if (value.equals(query)) return;
            query = value; filter(); page = 0; refresh();
        });
        tools.add(addButton(new ReplayBrowserButton(left + contentWidth - 128, 34, "Refresh recordings", ReplayBrowserButton.REFRESH, b -> load())));
        tools.add(addButton(new ReplayBrowserButton(left + contentWidth - 102, 34, "Browse another folder", ReplayBrowserButton.FOLDER, b -> chooseFolder())));
        tools.add(addButton(new ReplayBrowserButton(left + contentWidth - 76, 34, "Open selected folder", ReplayBrowserButton.OPEN, b -> openFolder())));
        renameButton = addButton(new ReplayBrowserButton(left + contentWidth - 50, 34,
                "Rename selected recording", ReplayBrowserButton.RENAME, b -> confirmRename()));
        tools.add(renameButton);
        deleteButton = addButton(new ReplayBrowserButton(left + contentWidth - 24, 34,
                "Delete selected recording", ReplayBrowserButton.DELETE, b -> confirmDelete()));
        tools.add(deleteButton);
        for (ReplayBrowserButton tool : tools) tool.active = !busy;
        renameButton.active = deleteButton.active = canDelete();
        for (int i = 0; i < pageSize; i++) {
            int index = page * pageSize + i;
            if (index >= filtered.size()) break;
            ReplayBrowserFiles.Entry entry = filtered.get(index);
            ButtonWidget row = addButton(new ButtonWidget(left, 78 + i * 36, contentWidth, 34, new LiteralText(entry.name), b -> { selected = entry.path; refresh(); }) {
                @Override public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
                    boolean chosen = entry.path.equals(selected);
                    fill(matrices, x, y, x + width, y + height, chosen ? 0xDB293D40 : isHovered() ? 0xCF303536 : 0xAC202324);
                    if (chosen) fill(matrices, x, y, x + 2, y + height, 0xFF79D4D0);
                    String time = entry.duration < 0 ? "--:--" : ViewerLayout.time(entry.duration);
                    int titleWidth = Math.max(20, width - textRenderer.getWidth(time) - 26);
                    textRenderer.drawWithShadow(matrices, textRenderer.trimToWidth(entry.name, titleWidth), x + 8, y + 5, entry.playable() ? 0xF2F2F2 : 0xD69B83);
                    textRenderer.drawWithShadow(matrices, time, x + width - textRenderer.getWidth(time) - 8, y + 5, 0xB6D7D5);
                    String description = date(entry.date) + "  |  " + entry.path.getFileName();
                    textRenderer.drawWithShadow(matrices, textRenderer.trimToWidth(description, width - 16), x + 8, y + 20, 0xA8ADAE);
                }
            });
            row.active = !busy;
            rowButtons.add(row);
        }
        int footer = height - 28;
        addButton(new ButtonWidget(left, footer, 24, 20, new LiteralText("<"), b -> { page--; refresh(); })).active = page > 0;
        addButton(new ButtonWidget(left + 28, footer, 24, 20, new LiteralText(">"), b -> { page++; refresh(); })).active = (page + 1) * pageSize < filtered.size();
        int actionWidth = Math.min(100, Math.max(56, (contentWidth - 64) / 2));
        ButtonWidget play = addButton(new ButtonWidget(left + contentWidth - actionWidth * 2 - 6, footer, actionWidth, 20, new LiteralText("Play"), b -> play()));
        ReplayBrowserFiles.Entry selectedEntry = selectedEntry();
        play.active = !busy && selectedEntry != null && selectedEntry.playable() && client.world == null;
        addButton(new ButtonWidget(left + contentWidth - actionWidth, footer, actionWidth, 20, new LiteralText("Back"), b -> onClose()));
        if (!started) { started = true; load(); }
    }

    private void refresh() {
        boolean focused = search != null && search.isFocused();
        buttons.clear(); children.clear(); init();
        if (focused) { search.setSelected(true); setFocused(search); }
    }

    private void filter() {
        filtered.clear();
        for (ReplayBrowserFiles.Entry entry : entries) if (entry.matches(query)) filtered.add(entry);
        if (selectedEntry() == null) selected = null;
    }

    private void load() {
        if (scan != null) scan.cancel(true);
        int token = ++generation;
        busy = true; status = "Reading recordings...";
        refresh();
        final Path requested = browse;
        scan = FILES.submit(() -> {
            try {
                Path directory = requested;
                ReplayBrowserFiles.Result result = ReplayBrowserFiles.scan(game, directory);
                final Path remembered = directory;
                MinecraftClient.getInstance().send(() -> {
                    if (closed || generation != token || client.currentScreen != this) return;
                    browse = remembered; entries = result.entries; busy = false; filter();
                    status = !result.warning.isEmpty() ? result.warning : result.entries.size() + " recordings" + (result.limited ? " (scan limit reached)" : "");
                    refresh();
                });
            } catch (Exception error) {
                MinecraftClient.getInstance().send(() -> {
                    if (closed || generation != token || client.currentScreen != this) return;
                    busy = false; status = "Cannot read recordings: " + error.getMessage(); refresh();
                });
            }
        });
    }

    private void chooseFolder() {
        Path initial = browse == null ? game.resolve("replay_recordings") : browse;
        String chosen = TinyFileDialogs.tinyfd_selectFolderDialog("Browse recordings (does not change recording destination)", initial.toAbsolutePath().toString());
        if (chosen == null) return;
        browse = Paths.get(chosen);
        // The browsing folder is session-local; the recorder's output setting stays independent.
        load();
    }

    private void openFolder() {
        Path path = selected == null ? (browse == null ? game.resolve("replay_recordings") : browse) : selected.getParent();
        if (!Files.isDirectory(path)) { status = "Folder does not exist yet"; return; }
        try { net.minecraft.util.Util.getOperatingSystem().open(path.toFile()); }
        catch (Exception error) { status = "Cannot open folder: " + error.getMessage(); }
    }

    private ReplayBrowserFiles.Entry selectedEntry() {
        if (selected == null) return null;
        for (ReplayBrowserFiles.Entry entry : filtered) if (entry.path.equals(selected)) return entry;
        return null;
    }

    private boolean canDelete() {
        return !busy && selectedEntry() != null && client.world == null
                && ReplayModReplay.instance.getReplayHandler() == null;
    }

    private void confirmDelete() {
        if (!canDelete()) return;
        ReplayBrowserFiles.Entry entry = selectedEntry();
        client.openScreen(new ReplayDeleteScreen(entry, confirmed -> {
            client.openScreen(this);
            if (confirmed && canDelete()) delete(entry);
        }));
    }

    private void confirmRename() {
        if (!canDelete() || closed) return;
        ReplayBrowserFiles.Entry entry = selectedEntry();
        int token = generation;
        client.openScreen(new ReplayRenameScreen(entry,
                () -> !closed && token == generation && selectedEntry() == entry && canDelete(), name -> {
            if (closed || token != generation || selectedEntry() != entry) return;
            client.openScreen(this);
            if (name != null && canDelete()) rename(entry, name);
        }));
    }

    private void rename(ReplayBrowserFiles.Entry entry, String name) {
        int token = ++generation;
        busy = true; status = "Renaming recording..."; refresh();
        scan = FILES.submit(() -> {
            ReplayBrowserFiles.Entry renamed = null;
            String error = null;
            try { renamed = ReplayBrowserFiles.read(ReplayBrowserFiles.rename(entry, name)); }
            catch (java.nio.file.FileAlreadyExistsException collision) { error = "A file or folder with that name already exists"; }
            catch (Exception failure) { error = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage(); }
            final ReplayBrowserFiles.Entry result = renamed;
            final String failure = error;
            MinecraftClient.getInstance().send(() -> {
                if (closed || token != generation || client.currentScreen != this) return;
                busy = false;
                if (failure == null) {
                    List<ReplayBrowserFiles.Entry> updated = new ArrayList<>(entries);
                    int index = updated.indexOf(entry);
                    if (index >= 0) updated.set(index, result);
                    entries = Collections.unmodifiableList(updated);
                    selected = result.path;
                    if (!result.matches(query)) query = "";
                    filter();
                    page = Math.max(0, filtered.indexOf(result) / pageSize);
                    status = "Renamed to " + result.path.getFileName();
                } else status = "Cannot rename recording: " + failure;
                refresh();
            });
        });
    }

    private void delete(ReplayBrowserFiles.Entry entry) {
        int token = ++generation;
        busy = true; status = "Deleting recording..."; refresh();
        scan = FILES.submit(() -> {
            String error = null;
            try { ReplayBrowserFiles.delete(entry); }
            catch (Exception failure) { error = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage(); }
            final String failure = error;
            MinecraftClient.getInstance().send(() -> {
                if (closed || token != generation || client.currentScreen != this) return;
                busy = false;
                if (failure == null) {
                    List<ReplayBrowserFiles.Entry> remaining = new ArrayList<>(entries);
                    remaining.removeIf(value -> value.path.equals(entry.path));
                    entries = Collections.unmodifiableList(remaining);
                    filter();
                    status = "Deleted " + entry.path.getFileName();
                } else status = "Cannot delete recording: " + failure;
                refresh();
            });
        });
    }

    private void play() {
        ReplayBrowserFiles.Entry entry = selectedEntry();
        if (busy || entry == null || !entry.playable() || client.world != null) return;
        busy = true; status = "Opening recording..."; refresh();
        com.replaymod.replaystudio.replay.ReplayFile archive = null;
        try {
            archive = com.replaymod.core.ReplayMod.instance.files.open(entry.path);
            com.replaymod.replay.ReplayHandler handler = ReplayModReplay.instance.startReplay(archive, true, true);
            closed = handler != null;
            busy = false;
            if (handler == null) { status = "Confirm replay compatibility to continue"; refresh(); }
        } catch (Exception error) {
            com.replaymod.replay.ReplayHandler failed = ReplayModReplay.instance.getReplayHandler();
            if (failed != null) {
                try { failed.endReplay(); } catch (Exception cleanup) { error.addSuppressed(cleanup); }
                finally { ReplayModReplay.instance.forcefullyStopReplay(); }
            }
            if (archive != null) {
                try { archive.close(); } catch (java.io.IOException closeError) { error.addSuppressed(closeError); }
            }
            org.apache.logging.log4j.LogManager.getLogger("ZSG Replay Viewer").warn("Could not open recording", error);
            busy = false; status = "Cannot open recording: " + error.getMessage();
            client.openScreen(this);
            refresh();
        }
    }

    @Override public void tick() {
        search.tick();
        renameButton.active = deleteButton.active = canDelete();
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int pages = Math.max(1, (filtered.size() + pageSize - 1) / pageSize);
        page = Math.max(0, Math.min(pages - 1, page + (amount < 0 ? 1 : -1)));
        refresh(); return true;
    }

    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        textRenderer.drawWithShadow(matrices, title.getString(), left, 14, 0xFFFFFF);
        String count = (page + 1) + " / " + Math.max(1, (filtered.size() + pageSize - 1) / pageSize);
        textRenderer.drawWithShadow(matrices, count, left + contentWidth - textRenderer.getWidth(count), 14, 0xA9B6B7);
        textRenderer.drawWithShadow(matrices, textRenderer.trimToWidth(status, contentWidth), left, 62, 0xA9B6B7);
        if (!busy && filtered.isEmpty()) drawCenteredString(matrices, textRenderer, query.isEmpty() ? "No recordings found" : "No matching recordings", width / 2, 100, 0xBBBBBB);
        super.render(matrices, mouseX, mouseY, delta);
        if (query.isEmpty() && !search.isFocused()) textRenderer.drawWithShadow(matrices, "Search recordings", left + 4, 41, 0x808989);
        for (ReplayBrowserButton tool : tools) if (tool.isHovered()) {
            libraryTooltip(matrices, tool.getMessage().getString(), mouseX, mouseY); return;
        }
        for (int i = 0; i < rowButtons.size(); i++) if (rowButtons.get(i).isHovered()) {
            ReplayBrowserFiles.Entry entry = filtered.get(page * pageSize + i);
            String tip = entry.name + "\n" + entry.path + "\n" + date(entry.date) + " | " + String.format(java.util.Locale.ROOT, "%.1f MiB", entry.size / 1048576.0);
            if (!entry.playable()) tip += "\n" + entry.error;
            if (client.world != null) tip += "\nReturn to the title screen before opening a recording.";
            libraryTooltip(matrices, tip, mouseX, mouseY); return;
        }
        if (mouseY >= 59 && mouseY <= 73) libraryTooltip(matrices, status, mouseX, mouseY);
    }

    private void libraryTooltip(MatrixStack matrices, String text, int mouseX, int mouseY) {
        List<net.minecraft.text.StringRenderable> lines = textRenderer.wrapLines(new LiteralText(text), Math.max(40, Math.min(360, width - 24)));
        int tooltipWidth = 0;
        for (net.minecraft.text.StringRenderable line : lines) tooltipWidth = Math.max(tooltipWidth, textRenderer.getWidth(line));
        // Vanilla flips overflowing tooltips to the left, but does not clamp that edge.
        if (mouseX + 12 + tooltipWidth > width) mouseX = Math.max(mouseX, tooltipWidth + 20);
        renderTooltip(matrices, lines, mouseX, mouseY);
    }

    private static String date(long time) {
        try { return DATE.format(Instant.ofEpochMilli(time)); }
        catch (java.time.DateTimeException error) { return "Unknown date"; }
    }

    @Override public void onClose() {
        closed = true; generation++;
        if (scan != null) scan.cancel(true);
        client.openScreen(parent);
    }
}
