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
        int y = panelY() + 52;
        int gap = 8;
        int optionWidth = (panelWidth() - 32 - gap) / 2;
        int left = panelX + 16;
        int right = left + optionWidth + gap;

        addPositionButton(left, y, optionWidth, RoomUiPreferences.HudPosition.TOP_LEFT);
        addPositionButton(right, y, optionWidth, RoomUiPreferences.HudPosition.TOP_RIGHT);
        addPositionButton(left, y + 28, optionWidth, RoomUiPreferences.HudPosition.BOTTOM_LEFT);
        addPositionButton(right, y + 28, optionWidth, RoomUiPreferences.HudPosition.BOTTOM_RIGHT);
        this.addButton(new ButtonWidget(left, y + 62, panelWidth() - 32, 20, rpRepairText(), button -> {
            RoomUiPreferences.setRuinedPortalChestRepairEnabled(
                    !RoomUiPreferences.isRuinedPortalChestRepairEnabled());
            button.setMessage(rpRepairText());
        }));
        this.addButton(new ButtonWidget(left, y + 90, panelWidth() - 32, 20, updateChecksText(), button -> {
            UpdatePreferences.setChecksEnabled(!UpdatePreferences.areChecksEnabled());
            button.setMessage(updateChecksText());
        }));
        this.addButton(new ButtonWidget(left, y + 118, panelWidth() - 32, 20, new LiteralText("Back"), button -> {
            this.client.openScreen(this.parent);
        }));
    }

    private void addPositionButton(int x, int y, int width, RoomUiPreferences.HudPosition position) {
        boolean selected = RoomUiPreferences.getHudPosition() == position;
        String label = selected ? "[" + position.getLabel() + "]" : position.getLabel();
        this.addButton(new ButtonWidget(x, y, width, 20, new LiteralText(label), button -> {
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
        fill(matrices, panelX, panelY, panelX + panelWidth(), panelY + 202, 0xCC090909);
        fill(matrices, panelX, panelY, panelX + panelWidth(), panelY + 28, 0xAA1A120C);
        drawCenteredString(matrices, this.textRenderer, "Room Settings", this.width / 2, panelY + 10, 0xFFFFFF);
        drawCenteredString(matrices, this.textRenderer, "HUD and race preferences", this.width / 2, panelY + 32, 0xA8D8FF);
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
        return Math.max(10, (this.height - 202) / 2);
    }

    private LiteralText updateChecksText() {
        return new LiteralText("Update Checks: " + (UpdatePreferences.areChecksEnabled() ? "On" : "Off"));
    }

    private LiteralText rpRepairText() {
        return new LiteralText("Repair RP Chests: "
                + (RoomUiPreferences.isRuinedPortalChestRepairEnabled() ? "On" : "Off"));
    }
}
