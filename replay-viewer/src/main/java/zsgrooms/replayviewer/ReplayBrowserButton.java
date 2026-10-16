// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

/** Small pixel glyphs match Minecraft's raster UI without generated artwork. */
final class ReplayBrowserButton extends ButtonWidget {
    static final String[] REFRESH = {"0011100", "0100010", "1000101", "1001111", "1000000", "0100010", "0011100"};
    static final String[] FOLDER = {"1110000", "1001111", "1111111", "1000001", "1000001", "1000001", "1111111"};
    static final String[] OPEN = {"0001111", "0000011", "0010101", "0101000", "1010000", "1000000", "1111100"};
    static final String[] DELETE = {"0011100", "1111111", "0100010", "0101010", "0101010", "0100010", "0011100"};
    static final String[] RENAME = {"0000110", "0001101", "0011110", "0111100", "1111000", "1110000", "1100000"};
    private final String[] glyph;

    ReplayBrowserButton(int x, int y, String label, String[] glyph, PressAction action) {
        super(x, y, 22, 22, new LiteralText(label), action);
        this.glyph = glyph;
    }

    @Override public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        fill(matrices, x, y, x + width, y + height, isHovered() ? 0xFF414A4C : 0xFF282C2D);
        int color = active ? 0xFFDDE7E7 : 0xFF626666;
        int startX = x + 7, startY = y + 7;
        for (int row = 0; row < glyph.length; row++) {
            for (int column = 0; column < glyph[row].length(); column++) {
                if (glyph[row].charAt(column) == '1') fill(matrices, startX + column, startY + row, startX + column + 1, startY + row + 1, color);
            }
        }
    }
}
