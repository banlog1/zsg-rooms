// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntConsumer;

final class TrailSettingsScreen extends Screen {
    private final Screen parent;
    private final TrailStyle style;
    private final Map<ButtonWidget, TrailStyle.Color> swatches = new LinkedHashMap<>();

    TrailSettingsScreen(Screen parent, String title, TrailStyle style) {
        super(new LiteralText(title));
        this.parent = parent;
        this.style = style;
    }

    @Override protected void init() {
        swatches.clear();
        int x = width / 2 - 130;
        int y = height / 2 - 70;
        int index = 0;
        for (TrailStyle.Color color : TrailStyle.Color.values()) {
            ButtonWidget swatch = addButton(new ButtonWidget(x + 6 + index++ * 32, y, 24, 20,
                    new LiteralText(color.label), button -> style.color = color) {
                @Override public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
                    fill(matrices, this.x, this.y, this.x + width, this.y + height,
                            style.color == color ? 0xFFFFFFFF : isHovered() || isFocused() ? 0xFFAAAAAA : 0xFF333333);
                    fill(matrices, this.x + 2, this.y + 2, this.x + width - 2, this.y + height - 2, 0xFF000000 | color.rgb);
                    if (style.color == color) {
                        fill(matrices, this.x + 7, this.y + 8, this.x + 17, this.y + 12, 0xFF111111);
                        fill(matrices, this.x + 8, this.y + 9, this.x + 16, this.y + 11, 0xFFFFFFFF);
                    }
                }
            });
            swatches.put(swatch, color);
        }
        slider(x, y + 28, "Length", 2, 30, style.seconds, " s", value -> style.seconds = value);
        slider(x, y + 52, "Thickness", 1, 4, style.width, " px", value -> style.width = value);
        slider(x, y + 76, "Opacity", 10, 100, style.opacity, "%", value -> style.opacity = value);
        addButton(new CheckboxWidget(x, y + 102, 260, 20, new LiteralText("Fade older segments"), style.fade) {
            @Override public void onPress() { super.onPress(); style.fade = isChecked(); }
        });
        addButton(new ButtonWidget(x, y + 132, 126, 20, new LiteralText("Reset"), button -> {
            style.reset();
            client.openScreen(new TrailSettingsScreen(parent, title.getString(), style));
        }));
        addButton(new ButtonWidget(x + 134, y + 132, 126, 20, new LiteralText("Done"), button -> onClose()));
    }

    private void slider(int x, int y, String label, int min, int max, int current, String suffix, IntConsumer setter) {
        addButton(new SliderWidget(x, y, 260, 20, new LiteralText(label + ": " + current + suffix),
                (current - min) / (double) (max - min)) {
            private int selected() { return min + (int) Math.round(value * (max - min)); }
            @Override protected void updateMessage() { setMessage(new LiteralText(label + ": " + selected() + suffix)); }
            @Override protected void applyValue() { setter.accept(selected()); }
        });
    }

    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        drawCenteredString(matrices, textRenderer, title.getString(), width / 2, height / 2 - 99, 0xFFFFFF);
        super.render(matrices, mouseX, mouseY, delta);
        for (Map.Entry<ButtonWidget, TrailStyle.Color> entry : swatches.entrySet()) {
            if (entry.getKey().isHovered()) renderTooltip(matrices, new LiteralText(entry.getValue().label), mouseX, mouseY);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { client.openScreen(parent); }
}
