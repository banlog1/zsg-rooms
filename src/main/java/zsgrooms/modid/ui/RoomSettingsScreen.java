package zsgrooms.modid.ui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import zsgrooms.modid.net.HostSeedPrefetchManager;
import zsgrooms.modid.replay.ReplayPreferences;
import zsgrooms.modid.replay.ReplayPrototype;
import zsgrooms.modid.seedbank.SeedBankClient;
import zsgrooms.modid.update.UpdatePreferences;

import java.io.IOException;
import java.util.*;
import java.util.function.*;

public class RoomSettingsScreen extends Screen {
    enum Page {
        GENERAL("General"), HUD("HUD"), LOADING("Loading"), RECORDING("Recording"), ADVANCED("Advanced"),
        SEED_SERVICE("Seed Bank"), REPLAY_SETUP("Replay Libraries"), REPLAY_TEST("Solo Replay Testing");
        final String label;
        Page(String label) { this.label = label; }
        Page category() { return ordinal() > ADVANCED.ordinal() ? ADVANCED : this; }
    }

    private final Screen parent;
    private final EnumMap<Page, Integer> offsets = new EnumMap<>(Page.class);
    private final List<Row> rows = new ArrayList<>();
    private final List<ButtonWidget> menu = new ArrayList<>();
    private final MatchHudPreferences hud = RoomUiPreferences.getMatchHud();
    private PersonalSettingsLayout layout;
    private Page page = Page.GENERAL;
    private int offset, menuFocus;
    private boolean draggingScroll;
    private String seedDraft, libraryDraft, testDraft;
    private String status = "";
    private ButtonWidget testingBadge;

    public RoomSettingsScreen(Screen parent) {
        super(new LiteralText("Settings"));
        this.parent = parent;
    }

    @Override protected void init() {
        menu.clear(); draggingScroll = false;
        layout = new PersonalSettingsLayout(width, height);
        rows.clear(); populate();
        offset = layout.clampOffset(offsets.getOrDefault(page, 0), rows.size());
        if (layout.sidebar) {
            for (Page category : categories()) {
                ButtonWidget button = addButton(new ButtonWidget(layout.left, 58 + category.ordinal() * 26,
                        108, 20, new LiteralText(category.label), b -> select(category)));
                button.active = page != category;
            }
        } else {
            addButton(new ButtonWidget(layout.left, 30, layout.width, 20,
                    new LiteralText(page.category().label + "  v"), b -> {
                List<String> names = new ArrayList<>();
                for (Page category : categories()) names.add(category.label);
                openMenu(b.x, b.y + 22, b.getWidth(), names, page.category().ordinal(), index -> select(categories()[index]));
            }));
        }
        for (int i = offset; i < Math.min(rows.size(), offset + layout.capacity); i++) {
            Row row = rows.get(i);
            row.y = layout.top + (i - offset) * layout.rowHeight;
            row.widget = addButton(row.factory.create(layout.controlX(), row.y + (layout.stacked ? 16 : 2), layout.controlWidth()));
        }
        testingBadge = addButton(new ButtonWidget(layout.left + layout.width - 108, 6, 108, 20,
                new LiteralText("Testing active").formatted(net.minecraft.util.Formatting.GOLD), b -> select(Page.ADVANCED)));
        addButton(new ButtonWidget(width / 2 - 60, height - 28, 120, 20,
                new LiteralText(page == page.category() ? "Done" : "Back"), b -> onClose()));
        updateControls();
    }

    private static Page[] categories() {
        return new Page[] {Page.GENERAL, Page.HUD, Page.LOADING, Page.RECORDING, Page.ADVANCED};
    }

    private void populate() {
        switch (page) {
            case GENERAL:
                toggle("Update checks", "Checks for Rooms and optional viewer updates. Local preference; does not change room rules.",
                        UpdatePreferences::areChecksEnabled, UpdatePreferences::setChecksEnabled);
                break;
            case HUD:
                toggle("Show match HUD", "Shows the race overlay on your screen only.", () -> hud.visible, v -> hud.visible = v);
                toggle("Show header", "Shows the match header. Local display only.", () -> hud.header, v -> hud.header = v);
                toggle("Player heads", "Shows player heads beside their race rows.", () -> hud.heads, v -> hud.heads = v);
                toggle("Progress numbers", "Shows numerical progress beside each player.", () -> hud.progressNumbers, v -> hud.progressNumbers = v);
                slider("Background opacity", "Changes HUD background opacity, not text.", 0, 100, 5, () -> hud.opacity, "%", v -> hud.opacity = v);
                slider("HUD size", "Scales your race HUD. The existing fit-to-screen behavior still applies.", 75, 150, 5, () -> hud.scale, "%", v -> hud.scale = v);
                toggle("Show seed type", "Shows the current seed type in the race header.", () -> hud.seedType, v -> hud.seedType = v);
                slider("Seed header size", "Scales the seed-type header independently of player rows.", 50, 150, 5, () -> hud.seedTypeScale, "%", v -> hud.seedTypeScale = v);
                slider("Visible players", "Number of visible race rows. Keeping your own row visible requires at least two rows.", hud.pinSelf ? 2 : 1, 4, 1, () -> hud.rows, "", v -> hud.rows = v);
                slider("Rotate every", "Time between rotations of players who do not fit on screen.", 2, 10, 1, () -> hud.rotationSeconds, "s", v -> hud.rotationSeconds = v);
                toggle("Keep my row visible", "Keeps your own row visible while other players rotate.", () -> hud.pinSelf,
                        v -> { hud.pinSelf = v; if (v) hud.rows = Math.max(2, hud.rows); });
                choice("HUD position", "Moves the race overlay on your screen only.", RoomUiPreferences.HudPosition.values(),
                        RoomUiPreferences::getHudPosition, RoomUiPreferences.HudPosition::getLabel, RoomUiPreferences::setHudPosition);
                command("HUD preview", "Opens the existing live HUD editor and preview. Returning keeps this category and scroll position.",
                        "Preview...", () -> client.openScreen(new MatchHudSettingsScreen(this)));
                break;
            case LOADING:
                choice("Progress style", "Uses the original Minecraft loading square or the ZSG logo on room loading screens.",
                        LoadingIndicatorStyle.values(), RoomUiPreferences::getLoadingIndicatorStyle, s -> s.label, RoomUiPreferences::setLoadingIndicatorStyle);
                toggle("Loading images", "Uses seed-type artwork from the optional resource pack. Missing images fall back to the regular background.",
                        RoomUiPreferences::areLoadingImagesEnabled, RoomUiPreferences::setLoadingImagesEnabled);
                choice("Progress position", "Positions the loading indicator and percentage. Used on subsequent renders.",
                        LoadingProgressPosition.values(), RoomUiPreferences::getLoadingProgressPosition, p -> p.label, RoomUiPreferences::setLoadingProgressPosition);
                toggle("Nether entry warmup", "Allows Nether preloading on this computer when enabled by the room rule. This local switch cannot enable a rule the host disabled.",
                        RoomUiPreferences::isNetherEntryWarmupEnabled, RoomUiPreferences::setNetherEntryWarmupEnabled);
                break;
            case RECORDING:
                toggle("Record local worlds", "Enables local replay recording. First use may download verified libraries. Returning to the room and quitting to title save normally.",
                        () -> ReplayPrototype.getPreferences().enabled, v -> {
                            ReplayPreferences p = ReplayPrototype.getPreferences();
                            ReplayPrototype.configure(v, p.libraryDirectory, p.replayModRecordingDisabled);
                        }).available = RoomSettingsScreen::recordingConfigReason;
                toggle("Performance mode", "Skips unchanged player metadata. Keeps full-rate movement, terrain, entities, sounds and timers. Choose before recording.",
                        () -> ReplayPrototype.getPreferences().performanceMode, ReplayPrototype::configurePerformance).available = RoomSettingsScreen::recordingConfigReason;
                toggle("Recording indicator", "Shows the REC/status badge during live play. Does not affect what is recorded.",
                        () -> ReplayPrototype.getPreferences().showRecordingHud, ReplayPrototype::configureHud);
                toggle("Save on seed change", "OFF discards unfinished recordings when a different seed loads. ON saves them separately. Returning to the room still saves; same-seed resets stay in one replay; completed recordings are kept.",
                        () -> ReplayPrototype.getPreferences().keepSeedChanges, ReplayPrototype::configureSeedRetention);
                command("Recorded files", "Opens the recordings folder. Playback settings are in the replay viewer.", "Open folder", ReplayPrototype::openRecordings);
                command("Current recording", "Stops and saves the current recording.", "Stop recording", ReplayPrototype::stopRecording)
                        .available = () -> ReplayPrototype.isRecording() ? "" : "No recording is currently running.";
                break;
            case ADVANCED:
                command("Seed-bank service", "Custom service URL for this installation. Normal play uses the hosted bank; no change is needed.", "Configure...", () -> select(Page.SEED_SERVICE));
                command("Replay libraries", "Library setup and ReplayMod recording confirmation. Does not configure playback.", "Configure...", () -> select(Page.REPLAY_SETUP));
                command("Solo replay testing", "Groups solo recordings for replay comparison. Changes replay metadata only, never live race IDs or results.", "Configure...", () -> select(Page.REPLAY_TEST));
                toggle("RP repair on all seeds", "Testing only: repairs newly generated ruined portals on any seed, including singleplayer. RP room seeds are always repaired with this off. Existing chunks are not repaired retroactively.",
                        RoomUiPreferences::isRuinedPortalRepairTestingEnabled, RoomUiPreferences::setRuinedPortalRepairTestingEnabled);
                toggle("Seed debug logging", "Writes additional seed/structure diagnostics to the local log. Does not change generation or filtering.",
                        RoomUiPreferences::isSeedDebugLoggingEnabled, RoomUiPreferences::setSeedDebugLoggingEnabled);
                break;
            case SEED_SERVICE:
                if (seedDraft == null) seedDraft = SeedBankClient.getEndpoint();
                field("Service URL", "HTTPS service URL. Local HTTP is allowed for testing. Blank uses the default hosted service. Changes apply only after Save.", seedDraft, 200, v -> seedDraft = v);
                command("Service configuration", "Saves the URL and invalidates prepared seed requests. Does not change a race already in progress.", "Save", () -> {
                    try { SeedBankClient.saveEndpoint(seedDraft); HostSeedPrefetchManager.getInstance().invalidate(); status = "Service URL saved"; }
                    catch (IOException error) { status = "Invalid URL or settings could not be saved"; }
                });
                break;
            case REPLAY_SETUP:
                if (libraryDraft == null) libraryDraft = ReplayPrototype.getPreferences().libraryDirectory;
                command("Automatic setup", "Uses this Minecraft instance's zsgrooms/replay-libraries folder and downloads verified dependencies if needed.", "Set up", () -> {
                    ReplayPreferences p = ReplayPrototype.getPreferences();
                    if (ReplayPrototype.configure(p.enabled, "", p.replayModRecordingDisabled)) {
                        libraryDraft = ""; ReplayPrototype.checkLibraries(); refresh();
                    }
                }).available = RoomSettingsScreen::recordingConfigReason;
                field("Library folder override", "Optional custom folder containing the writer and its dependencies. Blank uses automatic setup. Changes apply only after Save & Check.",
                        libraryDraft, 4096, v -> libraryDraft = v).available = RoomSettingsScreen::recordingConfigReason;
                command("Custom library folder", "Selects a folder without saving it yet.", "Browse...", () -> {
                    String chosen = TinyFileDialogs.tinyfd_selectFolderDialog("Custom replay libraries", ReplayPrototype.getLibraryDirectory());
                    if (chosen != null) { libraryDraft = chosen; refresh(); }
                }).available = RoomSettingsScreen::recordingConfigReason;
                command("Library configuration", "Saves the override and checks that replay libraries are ready.", "Save & Check", () -> {
                    ReplayPreferences p = ReplayPrototype.getPreferences();
                    if (ReplayPrototype.configure(p.enabled, libraryDraft, p.replayModRecordingDisabled)) ReplayPrototype.checkLibraries();
                }).available = RoomSettingsScreen::recordingConfigReason;
                toggle("ReplayMod recorder off", "Confirmation only: turn off Record Singleplayer, Record Server and Automatic Recording in ReplayMod itself. This checkbox does not switch off ReplayMod's recorder.",
                        () -> ReplayPrototype.getPreferences().replayModRecordingDisabled, v -> {
                            ReplayPreferences p = ReplayPrototype.getPreferences(); ReplayPrototype.configure(p.enabled, p.libraryDirectory, v);
                        }).available = () -> FabricLoader.getInstance().isModLoaded("replaymod") ? recordingConfigReason() : "ReplayMod is not installed.";
                break;
            case REPLAY_TEST:
                if (testDraft == null) testDraft = ReplayPrototype.getPreferences().soloTestGroup;
                field("Test group ID", "Use the same group UUID for separate solo takes on the same manual seed. Multiplayer rooms ignore this setting. Apply to save.", testDraft, 36, v -> testDraft = v);
                command("New test group", "Creates a draft ID; Apply saves it.", "New group", () -> { testDraft = UUID.randomUUID().toString(); refresh(); });
                command("Group clipboard", "Copies the draft group ID.", "Copy ID", () -> client.keyboard.setClipboard(testDraft));
                command("Test grouping", "Saves the group for subsequent recordings. Does not change live room results.", "Apply", () -> {
                    status = ReplayPrototype.configureTestGroup(testDraft) ? "Test group saved" : "Could not save test group";
                }).available = () -> {
                    try { ReplayPreferences.normalizeTestGroup(testDraft); } catch (IllegalArgumentException error) { return "Enter a valid group UUID."; }
                    return recordingConfigReason();
                };
                command("Solo testing", "Clears the saved test group.", "Disable testing", () -> {
                    if (ReplayPrototype.configureTestGroup("")) { testDraft = ""; status = "Testing off"; refresh(); }
                    else status = "Could not save test group";
                }).available = RoomSettingsScreen::recordingConfigReason;
                break;
        }
    }

    private static String recordingConfigReason() {
        return ReplayPrototype.canConfigure() ? "" : "Unavailable while recording, saving or setting up libraries.";
    }

    private Row toggle(String label, String tip, BooleanSupplier value, Consumer<Boolean> save) {
        return row(label, tip, (x, y, w) -> new CheckboxWidget(x + w - 20, y, 20, 20, new LiteralText(label), value.getAsBoolean(), false) {
            @Override public void onPress() { save.accept(!value.getAsBoolean()); refresh(); }
        });
    }

    private void slider(String label, String tip, int min, int max, int step, IntSupplier value, String suffix, IntConsumer save) {
        row(label, tip, (x, y, w) -> new SliderWidget(x, y, w, 20, new LiteralText(value.getAsInt() + suffix), (value.getAsInt() - min) / (double) (max - min)) {
            private int selected() { return min + (int) Math.round(this.value * (max - min) / step) * step; }
            @Override protected void updateMessage() { setMessage(new LiteralText(selected() + suffix)); }
            @Override protected void applyValue() { save.accept(selected()); }
        });
    }

    private <T> void choice(String label, String tip, T[] values, Supplier<T> current, Function<T, String> text, Consumer<T> save) {
        row(label, tip, (x, y, w) -> new ButtonWidget(x, y, w, 20, new LiteralText(text.apply(current.get()) + "  v"), b -> {
            List<String> names = new ArrayList<>();
            for (T value : values) names.add(text.apply(value));
            openMenu(x, y + 22, w, names, Arrays.asList(values).indexOf(current.get()), index -> { save.accept(values[index]); refresh(); });
        }));
    }

    private Row field(String label, String tip, String value, int maxLength, Consumer<String> changed) {
        return row(label, tip, (x, y, w) -> {
            TextFieldWidget input = new TextFieldWidget(textRenderer, x, y, w, 20, new LiteralText(label));
            input.setMaxLength(maxLength); input.setText(value); input.setChangedListener(changed);
            return input;
        });
    }

    private Row command(String label, String tip, String action, Runnable run) {
        return row(label, tip, (x, y, w) -> new ButtonWidget(x, y, w, 20, new LiteralText(action), b -> run.run()));
    }

    private Row row(String label, String tip, Factory factory) {
        Row row = new Row(label, tip, factory); rows.add(row); return row;
    }

    private void select(Page next) {
        offsets.put(page, offset); RoomUiPreferences.saveMatchHud();
        page = next; status = ""; refresh();
    }

    private void refresh() { init(client, width, height); }

    private void updateControls() {
        testingBadge.visible = RoomUiPreferences.isRuinedPortalRepairTestingEnabled()
                || RoomUiPreferences.isSeedDebugLoggingEnabled() || !ReplayPrototype.getPreferences().soloTestGroup.isEmpty();
        for (Row row : rows) if (row.widget != null) {
            row.widget.active = row.available.get().isEmpty();
            if (row.widget instanceof TextFieldWidget) ((TextFieldWidget) row.widget).setEditable(row.widget.active);
        }
    }

    @Override public void tick() {
        updateControls();
        for (Row row : rows) if (row.widget instanceof TextFieldWidget) ((TextFieldWidget) row.widget).tick();
    }

    private void openMenu(int x, int y, int w, List<String> names, int selected, IntConsumer choose) {
        menu.clear(); menuFocus = Math.max(0, selected);
        int top = Math.max(28, Math.min(y, height - 8 - names.size() * 22));
        for (int i = 0; i < names.size(); i++) {
            final int index = i;
            menu.add(new ButtonWidget(x, top + i * 22, w, 20, new LiteralText(names.get(i)), b -> {
                menu.clear(); choose.accept(index);
            }));
        }
    }

    private void scroll(int direction) {
        int next = layout.clampOffset(offset + direction, rows.size());
        if (next != offset) { offsets.put(page, next); refresh(); }
    }

    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (!menu.isEmpty()) return true;
        if (amount != 0 && x >= layout.contentX && y >= layout.top && y < layout.bottom) {
            scroll(amount > 0 ? -1 : 1); return true;
        }
        return super.mouseScrolled(x, y, amount);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (!menu.isEmpty()) {
            for (ButtonWidget option : new ArrayList<>(menu)) if (option.mouseClicked(x, y, button)) return true;
            menu.clear(); return true;
        }
        if (button == 0 && layout.maxOffset(rows.size()) > 0 && x >= layout.contentX + layout.contentWidth + 4
                && x <= layout.left + layout.width && y >= layout.top && y < layout.bottom) {
            moveScroll(y); draggingScroll = true; return true;
        }
        if (button == 0) for (Row row : rows) {
            if (row.widget instanceof CheckboxWidget && row.widget.active && x >= layout.contentX
                    && x < layout.contentX + layout.contentWidth && y >= row.y && y < row.y + layout.rowHeight - 2) {
                ((CheckboxWidget) row.widget).onPress(); return true;
            }
        }
        boolean handled = super.mouseClicked(x, y, button);
        // A callback may have rebuilt the visible controls during the click.
        if (getFocused() != null && !children.contains(getFocused())) setFocused(null);
        return handled;
    }

    private void moveScroll(double y) {
        int max = layout.maxOffset(rows.size());
        int thumb = Math.max(16, (layout.bottom - layout.top) * layout.capacity / rows.size());
        offsets.put(page, layout.clampOffset((int) Math.round((y - layout.top - thumb / 2.0)
                * max / Math.max(1, layout.bottom - layout.top - thumb)), rows.size()));
        refresh();
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (!menu.isEmpty()) return true;
        if (draggingScroll) { moveScroll(y); draggingScroll = true; return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (!menu.isEmpty()) return true;
        draggingScroll = false;
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (!menu.isEmpty()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) menu.clear();
            else if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_TAB) menuFocus = (menuFocus + 1) % menu.size();
            else if (key == GLFW.GLFW_KEY_UP) menuFocus = Math.floorMod(menuFocus - 1, menu.size());
            else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE) menu.get(menuFocus).onPress();
            return true;
        }
        if (!(getFocused() instanceof TextFieldWidget)) {
            if (key == GLFW.GLFW_KEY_PAGE_DOWN) { scroll(layout.capacity); return true; }
            if (key == GLFW.GLFW_KEY_PAGE_UP) { scroll(-layout.capacity); return true; }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public boolean charTyped(char chr, int modifiers) {
        return !menu.isEmpty() || super.charTyped(chr, modifiers);
    }

    @Override public void render(MatrixStack matrices, int mx, int my, float delta) {
        renderBackground(matrices);
        fill(matrices, 0, 0, width, 28, 0xC0101010);
        fill(matrices, 0, height - 32, width, height, 0xC0101010);
        textRenderer.drawWithShadow(matrices, "Settings", layout.left, 12, 0xFFFFFF);
        if (layout.sidebar) fill(matrices, layout.contentX - 8, 32, layout.contentX - 7, height - 36, 0xFF555555);
        String heading = status.isEmpty() ? page.label : status;
        if (status.isEmpty() && page == Page.RECORDING) heading = ReplayPrototype.getRecordingStatus();
        if (status.isEmpty() && page == Page.REPLAY_SETUP) heading = ReplayPrototype.getLibraryStatus();
        textRenderer.drawWithShadow(matrices, textRenderer.trimToWidth(heading, layout.contentWidth), layout.contentX, layout.top - 16, 0xA8D8FF);
        String tip = null;
        for (Row row : rows) if (row.widget != null) {
            fill(matrices, layout.contentX, row.y + layout.rowHeight - 2, layout.contentX + layout.contentWidth,
                    row.y + layout.rowHeight - 1, 0x30444444);
            textRenderer.drawWithShadow(matrices, textRenderer.trimToWidth(row.label, layout.labelWidth()), layout.contentX,
                    row.y + (layout.stacked ? 1 : 8), row.widget.active ? 0xEEEEEE : 0x999999);
            if (mx >= layout.contentX && mx < layout.contentX + layout.contentWidth && my >= row.y && my < row.y + layout.rowHeight) {
                String reason = row.available.get();
                tip = row.label + ": " + row.tip + (reason.isEmpty() ? "" : " " + reason);
            }
        }
        if (layout.maxOffset(rows.size()) > 0) {
            int x = layout.contentX + layout.contentWidth + 6;
            int track = layout.bottom - layout.top, thumb = Math.max(16, track * layout.capacity / rows.size());
            int y = layout.top + (track - thumb) * offset / layout.maxOffset(rows.size());
            fill(matrices, x, layout.top, x + 4, layout.bottom, 0xFF333333);
            fill(matrices, x, y, x + 4, y + thumb, 0xFFAAAAAA);
        }
        super.render(matrices, menu.isEmpty() ? mx : -1, menu.isEmpty() ? my : -1, delta);
        if (!menu.isEmpty()) {
            matrices.push(); matrices.translate(0, 0, 300);
            ButtonWidget first = menu.get(0), last = menu.get(menu.size() - 1);
            fill(matrices, first.x - 2, first.y - 2, first.x + first.getWidth() + 2, last.y + 22, 0xFF101010);
            for (int i = 0; i < menu.size(); i++) {
                ButtonWidget option = menu.get(i);
                option.render(matrices, mx, my, delta);
                if (i == menuFocus) fill(matrices, option.x - 2, option.y, option.x, option.y + 20, 0xFF99DDDD);
            }
            matrices.pop();
        } else {
            if (testingBadge.visible && testingBadge.isMouseOver(mx, my)) tip = "Testing or diagnostic overrides are active. Review them in Advanced.";
            if (tip != null) renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(tip), Math.min(300, width - 32)), mx, my);
        }
    }

    @Override public void removed() { RoomUiPreferences.saveMatchHud(); }

    @Override public void onClose() {
        if (!menu.isEmpty()) { menu.clear(); return; }
        if (page != page.category()) { select(page.category()); return; }
        client.openScreen(parent);
    }

    private interface Factory { AbstractButtonWidget create(int x, int y, int width); }
    private static final class Row {
        final String label, tip;
        final Factory factory;
        Supplier<String> available = () -> "";
        AbstractButtonWidget widget;
        int y;
        Row(String label, String tip, Factory factory) { this.label = label; this.tip = tip; this.factory = factory; }
    }
}
