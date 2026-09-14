package zsgrooms.modid.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import zsgrooms.modid.net.HostSeedPrefetchManager;
import zsgrooms.modid.seedbank.SeedBankClient;

import java.io.IOException;

public final class SeedBankSettingsScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget endpoint;
    private String status = "";

    public SeedBankSettingsScreen(Screen parent) {
        super(new LiteralText("Seed Bank"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        String value = this.endpoint == null ? SeedBankClient.getEndpoint() : this.endpoint.getText();
        int width = Math.min(360, this.width - 32);
        int left = (this.width - width) / 2;
        int top = Math.max(32, this.height / 2 - 35);
        this.endpoint = new TextFieldWidget(this.textRenderer, left, top, width, 20, new LiteralText("Service URL"));
        this.endpoint.setMaxLength(200);
        this.endpoint.setText(value);
        this.addButton(this.endpoint);
        this.addButton(new ButtonWidget(left, top + 50, (width - 6) / 2, 20, new LiteralText("Save"), button -> {
            try {
                SeedBankClient.saveEndpoint(this.endpoint.getText());
                HostSeedPrefetchManager.getInstance().invalidate();
                this.client.openScreen(this.parent);
            } catch (IOException error) { this.status = "Invalid URL or settings could not be saved"; }
        }));
        this.addButton(new ButtonWidget(left + (width + 6) / 2, top + 50, (width - 6) / 2, 20,
                new LiteralText("Cancel"), button -> onClose()));
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        int top = Math.max(32, this.height / 2 - 35);
        drawCenteredString(matrices, this.textRenderer, "Seed Bank", this.width / 2, top - 28, 0xFFFFFF);
        this.textRenderer.drawWithShadow(matrices, "Service URL", this.endpoint.x, top - 12, 0xA8D8FF);
        String message = this.textRenderer.trimToWidth(this.status, Math.max(1, this.width - 32));
        drawCenteredString(matrices, this.textRenderer, message, this.width / 2, top + 30, 0xFFAAAA);
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public void tick() { this.endpoint.tick(); }

    @Override
    public void onClose() { this.client.openScreen(this.parent); }
}
