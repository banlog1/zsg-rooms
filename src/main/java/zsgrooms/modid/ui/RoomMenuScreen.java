package zsgrooms.modid.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.LiteralText;

public class RoomMenuScreen extends Screen {
    private final Screen parent;
    private ButtonWidget settingsButton;
    private ButtonWidget historyButton;

    public RoomMenuScreen(Screen parent) {
        super(new LiteralText("ZSG Rooms"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int panelWidth = panelWidth();
        int buttonWidth = panelWidth - 32;
        int startY = panelY() + 52;
        int panelX = (this.width - panelWidth) / 2;

        this.addButton(new ButtonWidget(centerX - buttonWidth / 2, startY, buttonWidth, 20, new LiteralText("Create Room"), button -> {
            this.client.openScreen(new RoomSetupScreen(this, true));
        }));

        this.addButton(new ButtonWidget(centerX - buttonWidth / 2, startY + 28, buttonWidth, 20, new LiteralText("Join Room"), button -> {
            this.client.openScreen(new RoomSetupScreen(this, false));
        }));

        this.historyButton = new ButtonWidget(panelX + 16, startY + 56, 24, 20, new LiteralText(""), button -> {
            this.client.openScreen(new RunHistoryScreen(this));
        });
        this.addButton(this.historyButton);

        this.addButton(new ButtonWidget(panelX + 48, startY + 56, panelWidth - 96, 20, new LiteralText("Back"), button -> {
            this.client.openScreen(this.parent);
        }));

        this.settingsButton = new ButtonWidget(panelX + panelWidth - 40, startY + 56, 24, 20, new LiteralText("\u2699"), button -> {
            this.client.openScreen(new RoomSettingsScreen(this));
        });
        this.addButton(this.settingsButton);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderRoomBackground(matrices);
        int panelWidth = panelWidth();
        int panelX = (this.width - panelWidth) / 2;
        int panelY = panelY();
        fill(matrices, panelX, panelY, panelX + panelWidth, panelY + 136, 0xCC090909);
        fill(matrices, panelX, panelY, panelX + panelWidth, panelY + 28, 0xAA1A120C);
        fill(matrices, panelX, panelY + 28, panelX + panelWidth, panelY + 29, 0xFF000000);
        fill(matrices, panelX, panelY + 135, panelX + panelWidth, panelY + 136, 0xFF000000);

        drawCenteredString(matrices, this.textRenderer, "ZSG Rooms", this.width / 2, panelY + 14, 0xFFFFFF);
        drawCenteredString(matrices, this.textRenderer, "Private filtered seed races", this.width / 2, panelY + 30, 0xA8D8FF);
        super.render(matrices, mouseX, mouseY, delta);
        if (this.historyButton != null) {
            this.itemRenderer.renderInGui(new ItemStack(Items.CLOCK), panelX + 20, panelY + 110);
        }
        if (this.historyButton != null && this.historyButton.isHovered()) {
            this.renderTooltip(matrices, new LiteralText("Recent Runs"), mouseX, mouseY);
        }
        if (this.settingsButton != null && this.settingsButton.isHovered()) {
            this.renderTooltip(matrices, new LiteralText("HUD Settings"), mouseX, mouseY);
        }
    }

    private void renderRoomBackground(MatrixStack matrices) {
        this.renderBackground(matrices);
        fill(matrices, 0, 0, this.width, this.height, 0x66000000);
        fill(matrices, 0, 0, this.width, 42, 0xAA120C08);
        fill(matrices, 0, 42, this.width, 43, 0xFF000000);
    }

    private int panelWidth() {
        return Math.min(248, this.width - 20);
    }

    private int panelY() {
        return Math.max(10, (this.height - 136) / 2);
    }
}
