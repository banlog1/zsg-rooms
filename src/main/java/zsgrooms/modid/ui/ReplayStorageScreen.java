package zsgrooms.modid.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import zsgrooms.modid.replay.ReplayStoragePreferences;

public final class ReplayStorageScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget directory;
    private String draft = "";
    private String status = "";
    private int left, contentWidth;

    public ReplayStorageScreen(Screen parent) {
        super(new LiteralText("Recording Folder"));
        this.parent = parent;
        try { draft = ReplayStoragePreferences.load(net.minecraft.client.MinecraftClient.getInstance().runDirectory.toPath()); }
        catch (java.io.IOException error) { status = "Could not read storage setting: " + error.getMessage(); }
    }

    @Override protected void init() {
        contentWidth = Math.min(420, width - 32);
        left = (width - contentWidth) / 2;
        int top = Math.max(48, height / 2 - 56);
        directory = addButton(new TextFieldWidget(textRenderer, left, top, contentWidth, 20, new LiteralText("Recording output folder")));
        directory.setMaxLength(4096);
        directory.setText(draft);
        directory.setChangedListener(value -> draft = value);
        int half = (contentWidth - 6) / 2;
        addButton(new ButtonWidget(left, top + 28, half, 20, new LiteralText("Choose Folder..."), b -> {
            String chosen = TinyFileDialogs.tinyfd_selectFolderDialog("Recording output folder", currentDirectory());
            if (chosen != null) directory.setText(chosen);
        }));
        addButton(new ButtonWidget(left + half + 6, top + 28, half, 20, new LiteralText("Use Default"), b -> directory.setText("")));
        addButton(new ButtonWidget(left, top + 56, half, 20, new LiteralText("Save"), b -> save()));
        addButton(new ButtonWidget(left + half + 6, top + 56, half, 20, new LiteralText("Open Current Folder"), b -> {
            try {
                net.minecraft.util.Util.getOperatingSystem().open(ReplayStoragePreferences.prepareDirectory(client.runDirectory.toPath()).toFile());
            } catch (Exception error) { status = "Cannot open folder: " + error.getMessage(); }
        }));
        addButton(new ButtonWidget(width / 2 - 60, height - 28, 120, 20, new LiteralText("Done"), b -> onClose()));
    }

    private String currentDirectory() {
        try { return ReplayStoragePreferences.resolveDirectory(client.runDirectory.toPath()).toAbsolutePath().toString(); }
        catch (java.io.IOException error) { return client.runDirectory.getAbsolutePath(); }
    }

    private void save() {
        try {
            ReplayStoragePreferences.save(client.runDirectory.toPath(), draft);
            status = "Saved for the next recording";
        } catch (java.io.IOException error) { status = "Cannot save folder: " + error.getMessage(); }
    }

    @Override public void tick() { directory.tick(); }

    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        drawCenteredString(matrices, textRenderer, title.getString(), width / 2, 16, 0xFFFFFF);
        int top = Math.max(48, height / 2 - 56);
        textRenderer.drawWithShadow(matrices, "Output Folder", left, top - 14, 0xB0D8DB);
        drawCenteredString(matrices, textRenderer, textRenderer.trimToWidth(status, contentWidth), width / 2, top + 86, 0xDDDDDD);
        super.render(matrices, mouseX, mouseY, delta);
        if (directory.isHovered()) renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(
                "Blank uses this instance's replay_recordings folder. A custom folder must be an absolute path. Applies to new recordings only; existing files are not moved and an active recording stays in its original folder. This does not change ReplayMod's own recorder destination."), Math.min(300, width - 32)), mouseX, mouseY);
        else if (mouseY >= top + 82 && mouseY <= top + 100 && !status.isEmpty()) renderTooltip(matrices, new LiteralText(status), mouseX, mouseY);
    }

    @Override public void onClose() { client.openScreen(parent); }
}
