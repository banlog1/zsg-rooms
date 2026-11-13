package zsgrooms.modid.ui;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.TranslatableText;

/** Vanilla-sized title-screen tool with a hand-drawn pixel import glyph. */
public final class ImportSettingsButton extends ButtonWidget {
    private static final String[] GLYPH = {
            "000011000000", "000011000000", "000011000000", "000011000000",
            "011011011000", "001111110000", "000111100000", "000011000000",
            "100000000100", "100000000100", "111111111100", "000000000000"
    };
    public ImportSettingsButton(int x, int y, PressAction action) {
        super(x, y, 20, 20, new LiteralText(""), action);
    }

    @Override public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.renderButton(matrices, mouseX, mouseY, delta);
        int color = ((int) (255 * alpha) << 24) | (active ? 0xE7F2F2 : 0x8A8A8A);
        for (int row = 0; row < GLYPH.length; row++) for (int column = 0; column < GLYPH[row].length(); column++) {
            if (GLYPH[row].charAt(column) == '1') fill(matrices, x + 5 + column, y + 4 + row, x + 6 + column, y + 5 + row, color);
        }
    }

    @Override protected MutableText getNarrationMessage() {
        return new TranslatableText("gui.narrate.button", new LiteralText("Import or save settings"));
    }
}
