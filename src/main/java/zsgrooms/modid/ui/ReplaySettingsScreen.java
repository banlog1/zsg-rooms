package zsgrooms.modid.ui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import zsgrooms.modid.replay.ReplayPreferences;
import zsgrooms.modid.replay.ReplayPrototype;

public final class ReplaySettingsScreen extends Screen {
    private final Screen parent;
    private boolean setup;
    private String draftDirectory;
    private TextFieldWidget directory;
    private CheckboxWidget recording;
    private CheckboxWidget coexistence;
    private ButtonWidget browse;
    private ButtonWidget check;
    private ButtonWidget automatic;
    private ButtonWidget stop;
    private ButtonWidget help;
    private int left;
    private int contentWidth;

    public ReplaySettingsScreen(Screen parent) {
        super(new LiteralText("ZSG Replays (Experimental)"));
        this.parent = parent;
        this.draftDirectory = ReplayPrototype.getPreferences().libraryDirectory;
    }

    @Override
    protected void init() {
        this.contentWidth = Math.min(400, this.width - 32);
        this.left = (this.width - contentWidth) / 2;
        int half = (contentWidth - 6) / 2;
        recording = null;
        coexistence = null;
        browse = null;
        check = null;
        automatic = null;
        stop = null;
        directory = null;
        this.addButton(new ButtonWidget(left, 48, half, 20, new LiteralText("Recording"), b -> select(false))).active = setup;
        this.addButton(new ButtonWidget(left + half + 6, 48, half, 20, new LiteralText("Setup"), b -> select(true))).active = !setup;
        help = this.addButton(new ButtonWidget(left + contentWidth - 20, 8, 20, 20, new LiteralText("?"), b -> {}));
        ReplayPreferences preferences = ReplayPrototype.getPreferences();
        if (setup) {
            automatic = this.addButton(new ButtonWidget(left, 78, contentWidth, 20,
                    new LiteralText("Use Minecraft Folder & Set Up"), b -> {
                ReplayPreferences current = ReplayPrototype.getPreferences();
                if (ReplayPrototype.configure(current.enabled, "", current.replayModRecordingDisabled)) {
                    draftDirectory = "";
                    directory.setText("");
                    ReplayPrototype.checkLibraries();
                }
            }));
            directory = this.addButton(new TextFieldWidget(this.textRenderer, left, 120, contentWidth, 20,
                    new LiteralText("Replay library folder")));
            directory.setMaxLength(4096);
            directory.setText(draftDirectory);
            directory.setChangedListener(value -> draftDirectory = value);
            browse = this.addButton(new ButtonWidget(left, 148, half, 20, new LiteralText("Browse..."), b -> {
                String chosen = TinyFileDialogs.tinyfd_selectFolderDialog("Custom replay libraries", ReplayPrototype.getLibraryDirectory());
                if (chosen != null) directory.setText(chosen);
            }));
            check = this.addButton(new ButtonWidget(left + half + 6, 148, half, 20,
                    new LiteralText("Save & Check"), b -> {
                ReplayPreferences current = ReplayPrototype.getPreferences();
                if (ReplayPrototype.configure(current.enabled, draftDirectory, current.replayModRecordingDisabled)) {
                    ReplayPrototype.checkLibraries();
                }
            }));
        } else {
            recording = this.addButton(new CheckboxWidget(left, 80, contentWidth, 20,
                    new LiteralText("Record Local Worlds"), preferences.enabled) {
                @Override
                public void onPress() {
                    ReplayPreferences current = ReplayPrototype.getPreferences();
                    ReplayPrototype.configure(!current.enabled, current.libraryDirectory, current.replayModRecordingDisabled);
                    refresh();
                }
            });
            boolean installed = FabricLoader.getInstance().isModLoaded("replaymod");
            coexistence = this.addButton(new CheckboxWidget(left, 104, contentWidth, 20,
                    new LiteralText(installed ? "ReplayMod recorder disabled" : "ReplayMod not installed"),
                    preferences.replayModRecordingDisabled) {
                @Override
                public void onPress() {
                    ReplayPreferences current = ReplayPrototype.getPreferences();
                    ReplayPrototype.configure(current.enabled, current.libraryDirectory, !current.replayModRecordingDisabled);
                    refresh();
                }
            });
            this.addButton(new ButtonWidget(left, 128, half, 20, new LiteralText("Open Recordings"),
                    b -> ReplayPrototype.openRecordings()));
            stop = this.addButton(new ButtonWidget(left + half + 6, 128, half, 20, new LiteralText("Stop Recording"),
                    b -> ReplayPrototype.stopRecording()));
            this.addButton(new ReplayMenuButton(left, 152, contentWidth, 20,
                    new LiteralText("Solo Replay Testing"), b -> this.client.openScreen(new ReplayTestSettingsScreen(this))));
            this.addButton(new CheckboxWidget(left, 178, contentWidth, 20,
                    new LiteralText("Show Recording Indicator"), preferences.showRecordingHud) {
                @Override public void onPress() {
                    ReplayPrototype.configureHud(!ReplayPrototype.getPreferences().showRecordingHud);
                    refresh();
                }
            });
        }
        this.addButton(new ButtonWidget(this.width / 2 - 60, this.height - 28, 120, 20,
                new LiteralText("Done"), b -> onClose()));
        updateControls();
    }

    private void select(boolean setup) {
        this.setup = setup;
        refresh();
    }

    private void refresh() {
        this.buttons.clear();
        this.children.clear();
        this.init();
    }

    private void updateControls() {
        boolean available = ReplayPrototype.canConfigure();
        if (directory != null) directory.setEditable(available);
        if (browse != null) browse.active = available;
        if (check != null) check.active = available;
        if (automatic != null) automatic.active = available;
        if (recording != null) recording.active = available;
        if (coexistence != null) coexistence.active = available && FabricLoader.getInstance().isModLoaded("replaymod");
        if (stop != null) stop.active = ReplayPrototype.isRecording();
    }

    @Override
    public void tick() {
        if (directory != null) directory.tick();
        updateControls();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        fill(matrices, left - 8, 4, left + contentWidth + 8, this.height - 4, 0xBB090909);
        String title = this.textRenderer.trimToWidth(this.title.getString(), contentWidth - 48);
        drawCenteredString(matrices, this.textRenderer, title, this.width / 2 - 10, 14, 0xFFFFFF);
        String status = setup ? ReplayPrototype.getLibraryStatus() : ReplayPrototype.getRecordingStatus();
        drawCenteredString(matrices, this.textRenderer, this.textRenderer.trimToWidth(status, contentWidth),
                this.width / 2, 32, ReplayPrototype.isLibraryReady() ? 0x99EE99 : 0xFFD080);
        if (setup) this.textRenderer.drawWithShadow(matrices, "Library Folder Override (Optional)", left, 106, 0xA8D8FF);
        super.render(matrices, mouseX, mouseY, delta);
        if (help.isHovered()) {
            String text = setup
                    ? "Default: this Minecraft instance's zsgrooms/replay-libraries. First setup downloads verified libraries from JitPack and Maven Central. Blank override uses automatic setup. Custom folders must contain the writer and all dependencies."
                    : "Record Local Worlds starts automatic library setup on first use (internet required). Wait until ready, then enter a local world. The REC badge confirms recording. Room results, returning to the room, and quitting to title save automatically to replay_recordings. Playback: ReplayMod Replay Viewer. Client HUD overlays are not recorded.";
            this.renderTooltip(matrices, this.textRenderer.wrapLines(new LiteralText(text), Math.min(280, this.width - 32)), mouseX, mouseY);
        } else if (coexistence != null && coexistence.isHovered()) {
            this.renderTooltip(matrices, this.textRenderer.wrapLines(new LiteralText(
                    "Confirmation only. ReplayMod settings must have Record Singleplayer, Record Server and Automatic Recording OFF."),
                    Math.min(280, this.width - 32)), mouseX, mouseY);
        } else if (mouseY >= 30 && mouseY <= 43 && mouseX >= left && mouseX <= left + contentWidth) {
            this.renderTooltip(matrices, this.textRenderer.wrapLines(new LiteralText(status), Math.min(280, this.width - 32)), mouseX, mouseY);
        }
    }

    @Override
    public void onClose() {
        this.client.openScreen(parent);
    }
}
