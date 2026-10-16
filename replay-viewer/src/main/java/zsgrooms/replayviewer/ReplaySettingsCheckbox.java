// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Read back the actual setting after a setter, including deferred playback changes. */
final class ReplaySettingsCheckbox extends CheckboxWidget {
    private final BooleanSupplier value;
    private final Consumer<Boolean> setter;
    private final String displayLabel;

    ReplaySettingsCheckbox(int x, int y, int width, String label, BooleanSupplier value, Consumer<Boolean> setter) {
        super(x, y, width, ReplaySettingsLayout.CONTROL_HEIGHT, new LiteralText(label), value.getAsBoolean(), false);
        this.value = value;
        this.setter = setter;
        displayLabel = label.replace(" (experimental)", "");
    }

    @Override public void onPress() {
        setter.accept(!value.getAsBoolean());
        refresh();
    }

    void refresh() {
        if (isChecked() != value.getAsBoolean()) super.onPress();
        setAlpha(active ? 1.0F : 0.5F);
    }

    @Override public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.renderButton(matrices, mouseX, mouseY, delta);
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        drawStringWithShadow(matrices, renderer, renderer.trimToWidth(displayLabel, width - 24), x + 24, y + 6,
                active ? 0xE0E0E0 : 0x909090);
    }
}
