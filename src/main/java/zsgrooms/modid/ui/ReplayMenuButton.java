package zsgrooms.modid.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

/** Draw a single surface for wide replay actions instead of stretching the button atlas. */
final class ReplayMenuButton extends ButtonWidget {
    ReplayMenuButton(int x, int y, int width, int height, Text label, PressAction action) {
        super(x, y, width, height, label, action);
    }

    @Override public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        int border = isHovered() ? 0xFFE0E0E0 : 0xFF777777;
        fill(matrices, x, y, x + width, y + height, border);
        fill(matrices, x + 1, y + 1, x + width - 1, y + height - 1, active ? 0xFF555555 : 0xFF333333);
        MinecraftClient client = MinecraftClient.getInstance();
        drawCenteredString(matrices, client.textRenderer, client.textRenderer.trimToWidth(getMessage().getString(), width - 12),
                x + width / 2, y + (height - 8) / 2, active ? 0xFFFFFF : 0x999999);
    }
}
