package zsgrooms.modid.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import zsgrooms.modid.update.UpdateManager;
import zsgrooms.modid.update.UpdatePreferences;
import zsgrooms.modid.update.UpdateRelease;
import zsgrooms.modid.update.UpdateArtifact;

import java.util.List;

public class UpdateScreen extends Screen {
    private final Screen parent;
    private final UpdateRelease release;
    private final List<UpdateArtifact> updates;
    private String status;
    private ButtonWidget downloadButton;
    private boolean downloading;
    private boolean updateReady;

    public UpdateScreen(Screen parent, UpdateRelease release) {
        super(new LiteralText("ZSG Rooms Update"));
        this.parent = parent;
        this.release = release;
        this.updates = UpdateManager.updates(release);
        this.status = "A newer version is available.";
        this.downloading = false;
        this.updateReady = false;
    }

    @Override
    protected void init() {
        int panelX = (this.width - panelWidth()) / 2;
        int x = panelX + 16;
        int y = panelY() + 76 + this.updates.size() * 14;
        int innerWidth = panelWidth() - 32;

        if (this.updateReady) {
            this.addButton(new ButtonWidget(x, y, innerWidth, 20, new LiteralText("Close This Instance"), button -> {
                this.client.scheduleStop();
            }));
            return;
        }

        if (this.downloading) {
            ButtonWidget progressButton = new ButtonWidget(x, y, innerWidth, 20, new LiteralText("Downloading Update..."), button -> {
            });
            progressButton.active = false;
            this.addButton(progressButton);
            return;
        }

        this.downloadButton = new ButtonWidget(x, y, innerWidth, 20, new LiteralText("Download Update"), button -> {
            this.downloading = true;
            this.status = "Downloading and verifying update...";
            rebuildButtons();
            UpdateManager.download(this.release,
                    message -> this.client.execute(() -> {
                        this.status = message;
                        this.downloading = false;
                        this.updateReady = true;
                        rebuildButtons();
                    }),
                    message -> this.client.execute(() -> {
                        this.status = message;
                        this.downloading = false;
                        rebuildButtons();
                    }));
        });
        this.addButton(this.downloadButton);

        int halfWidth = (innerWidth - 8) / 2;
        this.addButton(new ButtonWidget(x, y + 28, halfWidth, 20, new LiteralText("Later"), button -> {
            this.client.openScreen(this.parent);
        }));
        this.addButton(new ButtonWidget(x + halfWidth + 8, y + 28, halfWidth, 20, new LiteralText("Skip This Version"), button -> {
            UpdatePreferences.skipVersion(this.release.version);
            this.client.openScreen(this.parent);
        }));
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        fill(matrices, 0, 0, this.width, this.height, 0x66000000);
        int panelX = (this.width - panelWidth()) / 2;
        int panelY = panelY();
        fill(matrices, panelX, panelY, panelX + panelWidth(), panelY + panelHeight(), 0xEE080808);
        fill(matrices, panelX, panelY, panelX + panelWidth(), panelY + 28, 0xCC1A120C);
        drawCenteredString(matrices, this.textRenderer, "Mod Updates", this.width / 2, panelY + 10, 0xFFFFFF);
        for (int index = 0; index < this.updates.size(); index++) {
            UpdateArtifact artifact = this.updates.get(index);
            drawCenteredString(matrices, this.textRenderer, artifact.label + " " + artifact.version,
                    this.width / 2, panelY + 36 + index * 14, 0xFFFFFF);
        }
        int statusY = panelY + 42 + this.updates.size() * 14;
        drawFitted(matrices, this.status, statusY,
                this.status.toLowerCase().contains("failed") ? 0xFF7777 : 0xA8D8FF);
        String detail = this.updateReady
                ? "Close Minecraft to finish installing."
                : (this.downloading ? "Verifying downloads..." : "You can keep using your installed versions.");
        drawFitted(matrices, detail, statusY + 14, 0xAAAAAA);
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        this.client.openScreen(this.parent);
    }

    private int panelWidth() {
        return Math.min(320, this.width - 20);
    }

    private int panelY() {
        return Math.max(10, (this.height - panelHeight()) / 2);
    }

    private int panelHeight() {
        return 142 + this.updates.size() * 14;
    }

    private void drawFitted(MatrixStack matrices, String text, int y, int color) {
        float scale = Math.min(1.0F, (panelWidth() - 24.0F) / Math.max(1, this.textRenderer.getWidth(text)));
        matrices.push();
        matrices.translate(this.width / 2.0F, y, 0);
        matrices.scale(scale, scale, 1.0F);
        drawCenteredString(matrices, this.textRenderer, text, 0, 0, color);
        matrices.pop();
    }

    private void rebuildButtons() {
        this.init(this.client, this.width, this.height);
    }
}
