package zsgrooms.modid.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import zsgrooms.modid.update.UpdatePreferences;

public class RoomSettingsScreen extends Screen {
    private final Screen parent;

    public RoomSettingsScreen(Screen parent) {
        super(new LiteralText("Room Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int panelX = (this.width - panelWidth()) / 2;
        boolean tiny = this.height < 180;
        boolean compact = this.height < 270;
        int buttonHeight = tiny ? 14 : 20;
        int y = panelY() + (tiny ? 32 : compact ? 40 : 52);
        int row = tiny ? 15 : compact ? 20 : 28;
        int gap = 8;
        int optionWidth = (panelWidth() - 32 - gap) / 2;
        int left = panelX + 16;
        int right = left + optionWidth + gap;

        addPositionButton(left, y, optionWidth, buttonHeight, RoomUiPreferences.HudPosition.TOP_LEFT);
        addPositionButton(right, y, optionWidth, buttonHeight, RoomUiPreferences.HudPosition.TOP_RIGHT);
        addPositionButton(left, y + row, optionWidth, buttonHeight, RoomUiPreferences.HudPosition.BOTTOM_LEFT);
        addPositionButton(right, y + row, optionWidth, buttonHeight, RoomUiPreferences.HudPosition.BOTTOM_RIGHT);
        int preferenceY = y + row * 2 + (compact ? 0 : 6);
        this.addButton(new ButtonWidget(left, preferenceY, panelWidth() - 32, buttonHeight, rpRepairText(), button -> {
            RoomUiPreferences.setRuinedPortalChestRepairEnabled(
                    !RoomUiPreferences.isRuinedPortalChestRepairEnabled());
            button.setMessage(rpRepairText());
        }));
        this.addButton(new ButtonWidget(left, preferenceY + row, panelWidth() - 32, buttonHeight, updateChecksText(), button -> {
            UpdatePreferences.setChecksEnabled(!UpdatePreferences.areChecksEnabled());
            button.setMessage(updateChecksText());
        }));
        this.addButton(new ButtonWidget(left, preferenceY + row * 2, panelWidth() - 32, buttonHeight,
                netherEntryWarmupText(), button -> {
            RoomUiPreferences.setNetherEntryWarmupEnabled(!RoomUiPreferences.isNetherEntryWarmupEnabled());
            button.setMessage(netherEntryWarmupText());
        }));
        this.addButton(new ButtonWidget(left, preferenceY + row * 3, panelWidth() - 32, buttonHeight,
                seedDebugLoggingText(), button -> {
            RoomUiPreferences.setSeedDebugLoggingEnabled(!RoomUiPreferences.isSeedDebugLoggingEnabled());
            button.setMessage(seedDebugLoggingText());
        }));
        this.addButton(new ButtonWidget(left, preferenceY + row * 4, panelWidth() - 32, buttonHeight,
                new LiteralText("Back"), button -> {
            this.client.openScreen(this.parent);
        }));
    }

    private void addPositionButton(int x, int y, int width, int height, RoomUiPreferences.HudPosition position) {
        boolean selected = RoomUiPreferences.getHudPosition() == position;
        String label = selected ? "[" + position.getLabel() + "]" : position.getLabel();
        this.addButton(new ButtonWidget(x, y, width, height, new LiteralText(label), button -> {
            RoomUiPreferences.setHudPosition(position);
            this.init(this.client, this.width, this.height);
        }));
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        fill(matrices, 0, 0, this.width, this.height, 0x66000000);
        int panelX = (this.width - panelWidth()) / 2;
        int panelY = panelY();
        fill(matrices, panelX, panelY, panelX + panelWidth(), panelY + panelHeight(), 0xCC090909);
        fill(matrices, panelX, panelY, panelX + panelWidth(), panelY + 28, 0xAA1A120C);
        drawCenteredString(matrices, this.textRenderer, "Room Settings", this.width / 2, panelY + 10, 0xFFFFFF);
        if (this.height >= 180) {
            drawCenteredString(matrices, this.textRenderer, "HUD and race preferences",
                    this.width / 2, panelY + 32, 0xA8D8FF);
        }
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        this.client.openScreen(this.parent);
    }

    private int panelWidth() {
        return Math.min(272, this.width - 20);
    }

    private int panelY() {
        return Math.max(6, (this.height - panelHeight()) / 2);
    }

    private int panelHeight() {
        return Math.min(258, this.height - 12);
    }

    private LiteralText updateChecksText() {
        return new LiteralText("Update Checks: " + (UpdatePreferences.areChecksEnabled() ? "On" : "Off"));
    }

    private LiteralText rpRepairText() {
        return new LiteralText("Repair RP Corruption: "
                + (RoomUiPreferences.isRuinedPortalChestRepairEnabled() ? "On" : "Off"));
    }

    private LiteralText seedDebugLoggingText() {
        return new LiteralText("Seed Debug Logging: "
                + (RoomUiPreferences.isSeedDebugLoggingEnabled() ? "On" : "Off"));
    }

    private LiteralText netherEntryWarmupText() {
        return new LiteralText("Nether Entry Warmup: "
                + (RoomUiPreferences.isNetherEntryWarmupEnabled() ? "On" : "Off"));
    }
}
