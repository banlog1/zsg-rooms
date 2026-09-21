package zsgrooms.modid.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

public final class LoadingSettingsScreen extends Screen {
    private final Screen parent;

    public LoadingSettingsScreen(Screen parent) {
        super(new LiteralText("Loading Screen"));
        this.parent = parent;
    }

    @Override protected void init() {
        int left = (width - panelWidth()) / 2 + 16;
        int y = panelY() + 32;
        int row = Math.min(24, (panelHeight() - 40) / 7);
        int buttonHeight = Math.min(20, row - 2);
        int buttonWidth = panelWidth() - 32;
        addButton(new CheckboxWidget(left, y, buttonWidth, buttonHeight,
                new LiteralText("Loading Images"), RoomUiPreferences.areLoadingImagesEnabled()) {
            @Override public void onPress() {
                super.onPress();
                RoomUiPreferences.setLoadingImagesEnabled(isChecked());
            }
        });
        for (LoadingProgressPosition position : LoadingProgressPosition.values()) {
            y += row;
            ButtonWidget button = addButton(new ButtonWidget(left, y, buttonWidth, buttonHeight,
                    new LiteralText("Progress: " + position.label), pressed -> {
                RoomUiPreferences.setLoadingProgressPosition(position);
                client.openScreen(this);
            }));
            button.active = position != RoomUiPreferences.getLoadingProgressPosition();
        }
        addButton(new ButtonWidget(left, y + row, buttonWidth, buttonHeight,
                new LiteralText("Back"), button -> onClose()));
    }

    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        int x = (width - panelWidth()) / 2;
        fill(matrices, x, panelY(), x + panelWidth(), panelY() + panelHeight(), 0xCC090909);
        fill(matrices, x, panelY(), x + panelWidth(), panelY() + 28, 0xAA1A120C);
        drawCenteredString(matrices, textRenderer, title.getString(), width / 2, panelY() + 10, 0xFFFFFF);
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override public void onClose() { client.openScreen(parent); }

    private int panelWidth() { return Math.min(272, width - 20); }
    private int panelHeight() { return Math.min(214, height - 12); }
    private int panelY() { return Math.max(6, (height - panelHeight()) / 2); }
}
