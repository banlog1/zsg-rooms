package zsgrooms.modid.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import zsgrooms.modid.update.UpdateManager;
import zsgrooms.modid.update.UpdatePreferences;
import zsgrooms.modid.update.UpdateRelease;

public class UpdateScreen extends Screen {
    private final Screen parent;
    private final UpdateRelease release;
    private String status;
    private ButtonWidget downloadButton;

    public UpdateScreen(Screen parent, UpdateRelease release) {
        super(new LiteralText("ZSG Rooms Update"));
        this.parent = parent;
        this.release = release;
        this.status = "A newer version is available.";
    }

    @Override
    protected void init() {
        int panelX = (this.width - panelWidth()) / 2;
        int x = panelX + 16;
        int y = panelY() + 72;
        int innerWidth = panelWidth() - 32;
        this.downloadButton = new ButtonWidget(x, y, innerWidth, 20, new LiteralText("Download Update"), button -> {
            button.active = false;
            this.status = "Downloading and verifying update...";
            UpdateManager.download(this.release,
                    message -> this.client.execute(() -> this.status = message),
                    message -> this.client.execute(() -> {
                        this.status = message;
                        this.downloadButton.active = true;
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
        fill(matrices, panelX, panelY, panelX + panelWidth(), panelY + 142, 0xEE080808);
        fill(matrices, panelX, panelY, panelX + panelWidth(), panelY + 28, 0xCC1A120C);
        drawCenteredString(matrices, this.textRenderer, "ZSG Rooms " + this.release.version, this.width / 2, panelY + 10, 0xFFFFFF);
        drawCenteredString(matrices, this.textRenderer, this.status, this.width / 2, panelY + 42,
                this.status.toLowerCase().contains("failed") ? 0xFF7777 : 0xA8D8FF);
        drawCenteredString(matrices, this.textRenderer, "You can keep using this version.", this.width / 2, panelY + 56, 0xAAAAAA);
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
        return Math.max(10, (this.height - 142) / 2);
    }
}
