package zsgrooms.modid.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.*;

final class SettingsEntryButton extends ButtonWidget {
    private final TextRenderer textRenderer;
    private final String name, details;

    SettingsEntryButton(TextRenderer renderer, int x, int y, int width, String name, String details, PressAction action) {
        super(x, y, width, 34, new LiteralText(fit(renderer, name, width - 20)), action);
        this.textRenderer = renderer; this.name = name; this.details = details;
    }

    @Override public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        boolean highlighted = isHovered() || isFocused();
        fill(matrices, x, y, x + getWidth(), y + getHeight(), highlighted ? 0xAA333333 : 0x66000000);
        fill(matrices, x, y + getHeight() - 1, x + getWidth(), y + getHeight(), 0x665E7070);
        if (highlighted) fill(matrices, x, y, x + 2, y + getHeight(), 0xFF99DDDD);
        textRenderer.drawWithShadow(matrices, getMessage(), x + 8, y + 5, 0xFFFFFF);
        textRenderer.drawWithShadow(matrices, fit(textRenderer, details, getWidth() - 20), x + 8, y + 20, 0xAABBBB);
    }

    @Override protected MutableText getNarrationMessage() {
        return new TranslatableText("gui.narrate.button", new LiteralText(name + ". " + details));
    }

    private static String fit(TextRenderer renderer, String text, int limit) {
        return renderer.getWidth(text) <= limit ? text : renderer.trimToWidth(text, Math.max(0, limit - 12)) + "...";
    }
}
