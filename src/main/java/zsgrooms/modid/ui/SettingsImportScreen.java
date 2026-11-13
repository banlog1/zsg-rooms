package zsgrooms.modid.ui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import zsgrooms.modid.settings.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static zsgrooms.modid.settings.ImportSettingsSchema.Category;

public final class SettingsImportScreen extends Screen {
    private enum Page { SOURCES, CATEGORIES, REVIEW, PENDING, UNDO }
    static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ZSG settings import"); thread.setDaemon(true); return thread;
    });
    private final Screen parent;
    private final Path game = FabricLoader.getInstance().getGameDir();
    private final Set<Category> selected = EnumSet.allOf(Category.class);
    private final Map<AbstractButtonWidget, String> tips = new LinkedHashMap<>();
    private final List<String> lines = new ArrayList<>();
    private List<SettingsImportInstance> sources = Collections.emptyList();
    private SettingsImportPlan plan;
    private SettingsImportStore store;
    private Screen sourceScreen;
    private Page page = Page.SOURCES;
    private int offset, left, contentWidth, capacity, generation;
    private boolean busy, started, pending, undoAvailable;
    private String status = "";

    public SettingsImportScreen(Screen parent) { super(new LiteralText("Import Settings")); this.parent = parent; }

    @Override protected void init() {
        left = Math.max(10, (width - 520) / 2); contentWidth = width - 2 * left;
        capacity = Math.max(1, textPage() ? (height - (page == Page.PENDING ? 148 : 124)) / 12 : page == Page.SOURCES ? (height - 164) / 38 : (height - 138) / 22);
        tips.clear(); lines.clear();
        if (!started) {
            started = true;
            work(() -> {
                store = new SettingsImportStore(game);
                sources = SettingsImportInstance.discover(game);
                pending = store.pending(); undoAvailable = store.canUndo(); status = store.status();
                return pending ? Page.PENDING : Page.SOURCES;
            }, result -> page = result);
            return;
        }
        if (busy) {
            lines.add("Reading settings...");
            button(left, height - 28, contentWidth, "Back", this::onClose).active = false;
            return;
        }
        switch (page) {
            case SOURCES:
                button(left, 32, contentWidth / 2 - 2, "Instances", () -> {}).active = false;
                button(left + contentWidth / 2 + 2, 32, contentWidth / 2 - 2, "Saved Settings", () -> client.openScreen(new SavedSettingsScreen(parent, this)));
                button(left, 58, contentWidth, "Choose Instance Folder...", () -> {
                    String chosen = TinyFileDialogs.tinyfd_selectFolderDialog("Choose an instance or Minecraft folder", game.toAbsolutePath().toString());
                    if (chosen != null) {
                        try { selectSource(Paths.get(chosen)); }
                        catch (InvalidPathException e) { status = "Invalid folder path."; refresh(); }
                    }
                });
                offset = clamp(offset, sources.size());
                for (int i = offset; i < Math.min(sources.size(), offset + capacity); i++) {
                    SettingsImportInstance source = sources.get(i);
                    ButtonWidget entry = addButton(new SettingsEntryButton(textRenderer, left, 88 + (i - offset) * 38,
                            contentWidth, source.name, source.details(), b -> selectSource(source.directory)));
                    tips.put(entry, source.name + "\n" + source.details() + "\n" + source.directory);
                }
                if (sources.isEmpty()) lines.add("No nearby instances found.");
                pager(sources.size());
                button(left, height - 28, contentWidth / 2 - 2, "Back", this::onClose);
                button(left + contentWidth / 2 + 2, height - 28, contentWidth / 2 - 2, "Undo Last Import", () -> navigate(Page.UNDO)).active = undoAvailable;
                break;
            case CATEGORIES:
                offset = clamp(offset, Category.values().length);
                for (int i = offset; i < Math.min(Category.values().length, offset + capacity); i++) {
                    Category category = Category.values()[i];
                    long count = plan.changes.stream().filter(c -> c.category == category).count();
                    CheckboxWidget check = addButton(new CheckboxWidget(left, 62 + (i - offset) * 22, contentWidth, 20,
                            new LiteralText(category.label + " (" + count + ")"), count > 0 && selected.contains(category)) {
                        @Override public void onPress() {
                            super.onPress(); if (isChecked()) selected.add(category); else selected.remove(category);
                            refresh();
                        }
                    });
                    check.active = count > 0;
                    tips.put(check, "Minecraft settings and matching StandardSettings overrides. World-join values and enabled states are preserved.");
                }
                pager(Category.values().length);
                button(left, height - 28, contentWidth / 2 - 2, "Back", () -> {
                    if (sourceScreen != null) client.openScreen(sourceScreen); else navigate(Page.SOURCES);
                });
                button(left + contentWidth / 2 + 2, height - 28, contentWidth / 2 - 2, "Review (" + plan.count(selected) + ")", () -> navigate(Page.REVIEW));
                break;
            case REVIEW:
                if (plan.count(selected) == 0) paragraph("No changes selected. Matching settings are already the same; unsupported settings are listed below.");
                paragraph("Close this Minecraft instance completely and relaunch after importing. Returning to the title screen is not enough.");
                paragraph("A backup is made before applying changes. Accounts, worlds, mods, resource packs, launcher settings and replay paths are not copied.");
                for (String warning : plan.warnings) paragraph(warning);
                for (SettingsImportPlan.Change change : plan.changes) if (selected.contains(change.category)) paragraph(change.description());
                paragraph("Skipped: " + plan.skipped.size());
                for (String skipped : plan.skipped) paragraph(skipped);
                pager(lines.size());
                button(left, height - 28, contentWidth / 2 - 2, "Back", () -> navigate(Page.CATEGORIES));
                button(left + contentWidth / 2 + 2, height - 28, contentWidth / 2 - 2, "Import on Next Launch", this::confirmImport).active = plan.count(selected) > 0;
                break;
            case PENDING:
                paragraph("Restart required");
                paragraph("Close this Minecraft instance completely, then relaunch it from your launcher. Changes apply on the next launch.");
                paragraph("Returning to the title screen or rejoining a world will NOT apply these changes.");
                pager(lines.size());
                button(left, height - 54, contentWidth, "Cancel Pending Changes", () -> work(() -> {
                    store.cancel(); pending = false; undoAvailable = store.canUndo(); return null;
                }, ignored -> { status = "Pending changes cancelled."; navigate(Page.SOURCES); }));
                button(left, height - 28, contentWidth / 2 - 2, "Later", this::onClose);
                button(left + contentWidth / 2 + 2, height - 28, contentWidth / 2 - 2, "Close Minecraft", () -> client.scheduleStop());
                break;
            case UNDO:
                paragraph("Restore settings from before the last import?");
                paragraph("Undo restores the backed-up files, including settings you changed after importing. A previous shared StandardSettings link is restored as well.");
                paragraph("Close this instance completely and relaunch to apply the undo. Returning to the title screen is not enough.");
                pager(lines.size());
                button(left, height - 28, contentWidth / 2 - 2, "Back", () -> navigate(Page.SOURCES));
                button(left + contentWidth / 2 + 2, height - 28, contentWidth / 2 - 2, "Undo on Next Launch", () -> work(() -> {
                    store.stageUndo(); pending = true; return null;
                }, ignored -> navigate(Page.PENDING)));
                break;
        }
    }

    private void selectSource(Path path) {
        sourceScreen = null;
        Set<String> bindings = new HashSet<>();
        for (KeyBinding binding : client.options.keysAll) bindings.add("key_" + binding.getTranslationKey());
        boolean standard = FabricLoader.getInstance().isModLoaded("standardsettings");
        work(() -> SettingsImportPlan.preview(path, game, bindings, standard), result -> {
            plan = result; status = ""; navigate(Page.CATEGORIES);
        });
    }

    void showInstances() { sourceScreen = null; navigate(Page.SOURCES); client.openScreen(this); }

    void selectSnapshot(SettingsSnapshotStore.Snapshot snapshot, Screen returnTo) {
        sourceScreen = returnTo;
        client.options.write();
        Set<String> bindings = new HashSet<>();
        for (KeyBinding binding : client.options.keysAll) bindings.add("key_" + binding.getTranslationKey());
        boolean standard = FabricLoader.getInstance().isModLoaded("standardsettings");
        client.openScreen(this);
        work(() -> SettingsImportPlan.previewSnapshot(snapshot, game, bindings, standard), result -> {
            plan = result; navigate(Page.CATEGORIES);
        });
    }

    private void confirmImport() {
        if (plan.detachesShared(selected)) {
            client.openScreen(new ConfirmScreen(confirmed -> {
                client.openScreen(this);
                if (confirmed) stage(true);
            }, new LiteralText("Switch to local StandardSettings?"), new LiteralText(
                    "Only this instance will stop using the shared file. Other instances and the shared file are unchanged. Undo can restore the link.")));
        } else stage(false);
    }

    private void stage(boolean local) {
        Set<Category> categories = EnumSet.copyOf(selected);
        work(() -> { store.stage(plan, categories, local); pending = true; return null; }, ignored -> navigate(Page.PENDING));
    }

    private <T> void work(Callable<T> operation, Consumer<T> done) {
        busy = true; int token = ++generation; refresh();
        WORKER.submit(() -> {
            try {
                T result = operation.call();
                client.execute(() -> { if (token == generation) { busy = false; done.accept(result); if (client.currentScreen == this) refresh(); } });
            } catch (Exception e) {
                client.execute(() -> {
                    if (token != generation) return;
                    busy = false; status = e.getMessage() == null ? "Settings could not be read or saved." : e.getMessage();
                    if (client.currentScreen == this) refresh();
                });
            }
        });
    }

    private void paragraph(String text) {
        if (!lines.isEmpty()) lines.add("");
        for (net.minecraft.text.StringRenderable line : textRenderer.getTextHandler().wrapLines(text, contentWidth - 8, net.minecraft.text.Style.EMPTY))
            lines.add(line.getString());
    }

    private ButtonWidget button(int x, int y, int w, String text, Runnable action) {
        int buttonWidth = Math.min(w, 320);
        return addButton(new ButtonWidget(x + (w - buttonWidth) / 2, y, buttonWidth, 20, new LiteralText(text), b -> action.run()));
    }
    private void navigate(Page next) { page = next; offset = 0; status = ""; refresh(); }
    private void refresh() { if (client != null) { buttons.clear(); children.clear(); init(); } }
    private int clamp(int value, int count) { return Math.max(0, Math.min(value, Math.max(0, count - capacity))); }
    private boolean textPage() { return page == Page.REVIEW || page == Page.PENDING || page == Page.UNDO; }
    private void pager(int count) {
        offset = clamp(offset, count);
        if (count <= capacity) return;
        int y = height - (page == Page.PENDING ? 80 : 54);
        button(left, y, 24, "<", () -> { offset = clamp(offset - capacity, count); refresh(); }).active = offset > 0;
        button(left + contentWidth - 24, y, 24, ">", () -> { offset = clamp(offset + capacity, count); refresh(); }).active = offset + capacity < count;
    }

    @Override public boolean mouseScrolled(double x, double y, double amount) {
        int count = textPage() ? lines.size() : page == Page.CATEGORIES ? Category.values().length : sources.size();
        if (!busy && y >= 60 && y < height - 58) { offset = clamp(offset + (amount > 0 ? -3 : 3), count); refresh(); return true; }
        return super.mouseScrolled(x, y, amount);
    }

    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        drawCenteredText(matrices, textRenderer, title, width / 2, 12, 0xFFFFFF);
        String heading = page == Page.CATEGORIES ? "Choose settings" : page == Page.REVIEW ? "Review changes" : "";
        if (!heading.isEmpty()) drawCenteredText(matrices, textRenderer, new LiteralText(heading), width / 2, 38, 0xBBBBBB);
        if (plan != null && (page == Page.CATEGORIES || page == Page.REVIEW)) {
            drawCenteredText(matrices, textRenderer, new LiteralText(fit(plan.sourceName + " | " + plan.sourceDetails, contentWidth)), width / 2, 50, 0x99BBBB);
        }
        int spacing = textPage() ? 12 : 22;
        for (int i = offset; i < Math.min(lines.size(), offset + capacity); i++)
            textRenderer.drawWithShadow(matrices, lines.get(i), left + 4, (page == Page.SOURCES && !busy ? 90 : 64) + (i - offset) * spacing, 0xDDDDDD);
        if (!status.isEmpty()) {
            String shortened = fit(status, contentWidth - 60);
            drawCenteredText(matrices, textRenderer, new LiteralText(shortened), width / 2, height - 68, 0xFFCC77);
            if (mouseY >= height - 72 && mouseY <= height - 56) renderTooltip(matrices,
                    textRenderer.wrapLines(new LiteralText(status), Math.min(contentWidth, 360)), mouseX, mouseY);
        }
        super.render(matrices, mouseX, mouseY, delta);
        if (plan != null && (page == Page.CATEGORIES || page == Page.REVIEW) && mouseX >= left && mouseX < left + contentWidth && mouseY >= 48 && mouseY < 60)
            renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(plan.sourceName + "\n" + plan.sourceDetails + "\n" + plan.source), Math.min(contentWidth, 360)), mouseX, mouseY);
        for (Map.Entry<AbstractButtonWidget, String> entry : tips.entrySet()) if (entry.getKey().isHovered()) {
            renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(entry.getValue()), Math.min(contentWidth, 360)), mouseX, mouseY); break;
        }
    }

    private String fit(String text, int limit) { return textRenderer.getWidth(text) <= limit ? text : textRenderer.trimToWidth(text, Math.max(0, limit - 12)) + "..."; }
    @Override public void onClose() { if (!busy) client.openScreen(parent); }
}
