package zsgrooms.modid.ui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import zsgrooms.modid.settings.SettingsSnapshotStore;
import zsgrooms.modid.settings.SettingsSnapshotStore.Snapshot;
import java.nio.file.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

final class SavedSettingsScreen extends Screen {
    private enum Page { LIST, DETAIL, NAME }
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private final Screen parent;
    private final SettingsImportScreen importer;
    private final Path game = FabricLoader.getInstance().getGameDir();
    private final Map<AbstractButtonWidget, String> tips = new LinkedHashMap<>();
    private SettingsSnapshotStore store;
    private List<Snapshot> snapshots = Collections.emptyList();
    private Snapshot chosen;
    private Page page = Page.LIST;
    private TextFieldWidget nameField;
    private ButtonWidget saveButton;
    private boolean started, busy, renaming;
    private int left, contentWidth, offset, capacity, generation, unreadable;
    private String draft = "", status = "";

    SavedSettingsScreen(Screen parent, SettingsImportScreen importer) {
        super(new LiteralText("Saved Settings")); this.parent = parent; this.importer = importer;
    }

    @Override protected void init() {
        left = Math.max(10, (width - 520) / 2); contentWidth = width - 2 * left;
        capacity = Math.max(1, (height - 164) / 38); tips.clear(); nameField = null;
        int half = contentWidth / 2 - 2, right = left + contentWidth / 2 + 2;
        if (!started) {
            started = true;
            work(() -> { store = new SettingsSnapshotStore(game); return store.list(); }, this::listing);
            return;
        }
        if (busy) { button(left, height - 28, contentWidth, "Back", this::onClose).active = false; return; }
        switch (page) {
            case LIST:
                button(left, 32, half, "Instances", importer::showInstances);
                button(right, 32, half, "Saved Settings", () -> {}).active = false;
                tips.put(button(left, 58, half, "Save Current...", () -> editName(false)),
                        "Save compatible Minecraft settings and StandardSettings overrides. Accounts, worlds, packs and other mod settings are excluded.");
                button(right, 58, half, "Import File...", this::importFile);
                offset = Math.max(0, Math.min(offset, Math.max(0, snapshots.size() - capacity)));
                for (int i = offset; i < Math.min(snapshots.size(), offset + capacity); i++) {
                    Snapshot item = snapshots.get(i);
                    ButtonWidget entry = addButton(new SettingsEntryButton(textRenderer, left, 88 + (i - offset) * 38,
                            contentWidth, item.name, DATE.format(item.savedAt) + " | " + item.count() + " settings", b -> {
                                chosen = item; page = Page.DETAIL; status = ""; refresh();
                            }));
                    tips.put(entry, item.name + "\nMinecraft " + item.minecraftVersion + "\n" + DATE.format(item.savedAt) + "\n" + item.file);
                }
                if (snapshots.size() > capacity) {
                    button(left, height - 54, 24, "<", () -> { offset -= capacity; refresh(); }).active = offset > 0;
                    button(left + contentWidth - 24, height - 54, 24, ">", () -> { offset += capacity; refresh(); }).active = offset + capacity < snapshots.size();
                }
                button(left, height - 28, contentWidth, "Back", this::onClose);
                break;
            case DETAIL:
                button(left, height - 80, half, "Rename...", () -> editName(true));
                button(right, height - 80, half, "Delete...", this::confirmDelete);
                button(left, height - 54, contentWidth, "Export File...", this::exportFile);
                button(left, height - 28, half, "Back", () -> { page = Page.LIST; status = ""; refresh(); });
                tips.put(button(right, height - 28, half, "Review Restore", () -> importer.selectSnapshot(chosen, this)),
                        "Choose categories and review changes first. Restore applies after closing Minecraft completely and relaunching; an undo backup is kept.");
                break;
            case NAME:
                nameField = addButton(new TextFieldWidget(textRenderer, left + 4, 76, contentWidth - 8, 20, new LiteralText("Snapshot name")));
                nameField.setMaxLength(64); nameField.setText(draft);
                nameField.setChangedListener(value -> { draft = value; if (saveButton != null) saveButton.active = !draft.trim().isEmpty(); });
                setInitialFocus(nameField);
                button(left, height - 28, half, "Cancel", this::cancelName);
                saveButton = button(right, height - 28, half, renaming ? "Rename" : "Save", this::saveName);
                saveButton.active = !draft.trim().isEmpty();
                break;
        }
    }

    private void editName(boolean rename) {
        renaming = rename; draft = rename ? chosen.name : "Settings " + DATE.format(java.time.Instant.now());
        page = Page.NAME; status = ""; refresh();
    }

    private void cancelName() { page = renaming ? Page.DETAIL : Page.LIST; status = ""; refresh(); }

    private void saveName() {
        final String name;
        try { name = SettingsSnapshotStore.validateName(draft); }
        catch (java.io.IOException e) { status = e.getMessage(); return; }
        if (renaming) {
            work(() -> { Snapshot result = store.rename(chosen, name); reload(); return result; }, result -> {
                chosen = result; page = Page.DETAIL; status = "Saved settings renamed.";
            });
        } else {
            client.options.write();
            Set<String> bindings = new HashSet<>();
            for (KeyBinding binding : client.options.keysAll) bindings.add("key_" + binding.getTranslationKey());
            String version = SharedConstants.getGameVersion().getName();
            boolean standard = FabricLoader.getInstance().isModLoaded("standardsettings");
            work(() -> { Snapshot result = store.save(name, version, bindings, standard); reload(); return result; }, result -> {
                chosen = result; page = Page.LIST; offset = 0; status = "Current settings saved.";
            });
        }
    }

    private void importFile() {
        String path = TinyFileDialogs.tinyfd_openFileDialog("Import saved settings JSON", game.toAbsolutePath().toString(), null, null, false);
        if (path == null) return;
        work(() -> { Snapshot result = store.importFile(Paths.get(path)); reload(); return result; }, result -> {
            chosen = result; page = Page.DETAIL; status = "Added to saved settings. No live settings changed.";
        });
    }

    private void exportFile() {
        String path = TinyFileDialogs.tinyfd_saveFileDialog("Export saved settings JSON", game.resolve("settings-snapshot.json").toAbsolutePath().toString(), null, null);
        if (path == null) return;
        final Path destination;
        try { destination = Paths.get(path.toLowerCase(Locale.ROOT).endsWith(".json") ? path : path + ".json"); }
        catch (InvalidPathException e) { status = "Invalid file path."; return; }
        if (Files.exists(destination)) client.openScreen(new ConfirmScreen(confirmed -> {
            client.openScreen(this); if (confirmed) export(destination, true);
        }, new LiteralText("Replace existing file?"), new LiteralText(destination.toString())));
        else export(destination, false);
    }

    private void export(Path destination, boolean overwrite) {
        work(() -> { store.exportFile(chosen, destination, overwrite); return null; }, ignored -> status = "Saved settings exported.");
    }

    private void confirmDelete() {
        client.openScreen(new ConfirmScreen(confirmed -> {
            client.openScreen(this);
            if (confirmed) work(() -> { store.delete(chosen); reload(); return null; }, ignored -> {
                chosen = null; page = Page.LIST; status = "Saved settings deleted. Current settings are unchanged.";
            });
        }, new LiteralText("Delete saved settings?"), new LiteralText(chosen.name + " will be deleted permanently. Current settings and the import undo backup are unchanged.")));
    }

    private void reload() throws java.io.IOException { listing(store.list()); }
    private void listing(SettingsSnapshotStore.Listing result) { snapshots = result.snapshots; unreadable = result.unreadable; }

    private <T> void work(Callable<T> operation, Consumer<T> done) {
        busy = true; int token = ++generation; refresh();
        SettingsImportScreen.WORKER.submit(() -> {
            try {
                T result = operation.call();
                client.execute(() -> { if (token == generation) { busy = false; done.accept(result); if (client.currentScreen == this) refresh(); } });
            } catch (Exception e) {
                client.execute(() -> {
                    if (token == generation) {
                        busy = false; status = e.getMessage() == null ? "Saved settings could not be read or written." : e.getMessage();
                        if (client.currentScreen == this) refresh();
                    }
                });
            }
        });
    }

    private ButtonWidget button(int x, int y, int w, String text, Runnable action) {
        int size = Math.min(320, w);
        return addButton(new ButtonWidget(x + (w - size) / 2, y, size, 20, new LiteralText(text), b -> action.run()));
    }
    private void refresh() { if (client != null) { buttons.clear(); children.clear(); init(); } }

    @Override public void tick() { if (nameField != null) nameField.tick(); }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (!busy && page == Page.NAME && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            if (saveButton.active) saveName(); return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (!busy && page == Page.LIST && y >= 88 && y < height - 58) { offset += amount > 0 ? -3 : 3; refresh(); return true; }
        return super.mouseScrolled(x, y, amount);
    }
    @Override public void onClose() {
        if (busy) return;
        if (page == Page.NAME) cancelName();
        else if (page == Page.DETAIL) { page = Page.LIST; status = ""; refresh(); }
        else client.openScreen(parent);
    }

    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        drawCenteredText(matrices, textRenderer, title, width / 2, 12, 0xFFFFFF);
        if (busy) drawCenteredText(matrices, textRenderer, new LiteralText("Working..."), width / 2, 92, 0xBBBBBB);
        else if (page == Page.NAME) {
            drawCenteredText(matrices, textRenderer, new LiteralText(renaming ? "Rename saved settings" : "Save current settings"), width / 2, 38, 0xBBBBBB);
            textRenderer.drawWithShadow(matrices, "Name", left + 4, 62, 0xCCCCCC);
        } else if (page == Page.DETAIL) {
            drawCenteredText(matrices, textRenderer, new LiteralText(fit(chosen.name, contentWidth)), width / 2, 38, 0x99DDDD);
            textRenderer.drawWithShadow(matrices, "Saved " + DATE.format(chosen.savedAt), left + 4, 64, 0xDDDDDD);
            textRenderer.drawWithShadow(matrices, fit("Minecraft " + chosen.minecraftVersion, contentWidth - 8), left + 4, 80, 0xDDDDDD);
            textRenderer.drawWithShadow(matrices, chosen.count() + " settings", left + 4, 96, 0xDDDDDD);
        } else if (snapshots.isEmpty()) textRenderer.drawWithShadow(matrices, "No saved settings yet.", left + 4, 92, 0xBBBBBB);
        int statusY = height - (page == Page.LIST ? 68 : 106);
        String message = status.isEmpty() && unreadable > 0 ? unreadable + " unreadable saved file(s) skipped." : status;
        if (!message.isEmpty()) drawCenteredText(matrices, textRenderer, new LiteralText(fit(message, contentWidth - 8)), width / 2, statusY, 0xFFCC77);
        super.render(matrices, mouseX, mouseY, delta);
        if (!message.isEmpty() && mouseY >= statusY - 2 && mouseY < statusY + 12)
            renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(message), Math.min(contentWidth, 360)), mouseX, mouseY);
        if (!busy && page == Page.DETAIL && mouseY >= 36 && mouseY < 50)
            renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(chosen.name), Math.min(contentWidth, 360)), mouseX, mouseY);
        for (Map.Entry<AbstractButtonWidget, String> tip : tips.entrySet()) if (tip.getKey().isHovered()) {
            renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(tip.getValue()), Math.min(contentWidth, 360)), mouseX, mouseY); break;
        }
    }

    private String fit(String text, int limit) { return textRenderer.getWidth(text) <= limit ? text : textRenderer.trimToWidth(text, Math.max(0, limit - 12)) + "..."; }
}
