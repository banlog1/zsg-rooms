package zsgrooms.modid.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

/** Small native-pixel controls shared by the entry and connection settings screens. */
final class RoomEntryButton extends ButtonWidget {
    enum Icon { NONE, BACK, PASTE, EYE, SETTINGS }
    final Icon icon;
    private final boolean primary;

    RoomEntryButton(int x, int y, int w, int h, String label, Icon icon, boolean primary, Runnable action) {
        super(x, y, w, h, new LiteralText(label), b -> action.run());
        this.icon = icon; this.primary = primary;
    }

    @Override public void renderButton(MatrixStack m, int mx, int my, float delta) {
        boolean hovered = active && (isHovered() || isFocused());
        int bg = primary ? hovered ? 0xFF656565 : 0xFF505050 : hovered ? 0xFF3A3A3A : 0xD0262626;
        fill(m, x, y, x + getWidth(), y + getHeight(), bg);
        int color = active ? 0xFFF0F0F0 : 0xFF777777;
        int cx = x + getWidth() / 2, cy = y + getHeight() / 2;
        if (icon == Icon.NONE) {
            net.minecraft.client.font.TextRenderer font = MinecraftClient.getInstance().textRenderer;
            font.drawWithShadow(m, getMessage(), cx - font.getWidth(getMessage()) / 2F, cy - 4, color);
        } else if (icon == Icon.BACK) {
            fill(m, cx - 4, cy, cx + 5, cy + 1, color);
            for (int i = 0; i < 5; i++) fill(m, cx - 5 + i, cy - i, cx - 4 + i, cy + i + 1, color);
        } else if (icon == Icon.PASTE) {
            frame(m, cx - 5, cy - 5, 10, 12, color);
            fill(m, cx - 2, cy - 7, cx + 2, cy - 3, color);
            fill(m, cx - 2, cy, cx + 3, cy + 1, color);
            fill(m, cx - 2, cy + 3, cx + 3, cy + 4, color);
        } else if (icon == Icon.EYE) {
            fill(m, cx - 3, cy - 4, cx + 4, cy - 3, color);
            fill(m, cx - 3, cy + 3, cx + 4, cy + 4, color);
            for (int i = 0; i < 3; i++) {
                fill(m, cx - 6 + i, cy - i, cx - 5 + i, cy + i + 1, color);
                fill(m, cx + 6 - i, cy - i, cx + 7 - i, cy + i + 1, color);
            }
            fill(m, cx - 1, cy - 1, cx + 2, cy + 2, color);
        } else {
            frame(m, cx - 4, cy - 4, 9, 9, color);
            fill(m, cx - 1, cy - 6, cx + 2, cy - 3, color);
            fill(m, cx - 1, cy + 4, cx + 2, cy + 7, color);
            fill(m, cx - 6, cy - 1, cx - 3, cy + 2, color);
            fill(m, cx + 4, cy - 1, cx + 7, cy + 2, color);
            fill(m, cx, cy, cx + 1, cy + 1, color);
        }
    }

    static void frame(MatrixStack m, int x, int y, int w, int h, int color) {
        fill(m, x, y, x + w, y + 1, color); fill(m, x, y + h - 1, x + w, y + h, color);
        fill(m, x, y, x + 1, y + h, color); fill(m, x + w - 1, y, x + w, y + h, color);
    }
}
