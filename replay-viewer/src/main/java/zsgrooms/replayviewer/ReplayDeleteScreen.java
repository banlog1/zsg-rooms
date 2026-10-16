// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.StringRenderable;
import java.util.List;
import java.util.function.Consumer;

/** Long paths cannot push the irreversible-action warning into the buttons. */
final class ReplayDeleteScreen extends ConfirmScreen {
    private final ReplayBrowserFiles.Entry entry;
    private final Consumer<Boolean> decision;

    ReplayDeleteScreen(ReplayBrowserFiles.Entry entry, Consumer<Boolean> decision) {
        super(confirmed -> decision.accept(confirmed), new LiteralText("Delete recording?"), LiteralText.EMPTY,
                new LiteralText("Delete"), new LiteralText("Cancel"));
        this.entry = entry;
        this.decision = decision;
    }

    @Override protected void init() {
        int buttonWidth = Math.min(150, (width - 40) / 2);
        int y = height / 2 + 44;
        addButton(new ButtonWidget(width / 2 - buttonWidth - 4, y, buttonWidth, 20,
                new LiteralText("Delete"), button -> decision.accept(true)));
        addButton(new ButtonWidget(width / 2 + 4, y, buttonWidth, 20,
                new LiteralText("Cancel"), button -> decision.accept(false)));
    }

    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        int top = height / 2 - 76;
        int lineWidth = Math.min(440, width - 32);
        drawCenteredString(matrices, textRenderer, title.getString(), width / 2, top, 0xFFFFFF);
        drawCenteredString(matrices, textRenderer, fit(entry.name, lineWidth), width / 2, top + 24, 0xFFFFFF);
        drawCenteredString(matrices, textRenderer, fit(entry.path.getFileName().toString(), lineWidth), width / 2, top + 38, 0xA9D9CD);
        drawCenteredString(matrices, textRenderer, fit(entry.path.getParent().toString(), lineWidth), width / 2, top + 52, 0xAAAAAA);
        drawCenteredString(matrices, textRenderer, "Permanently delete this replay?", width / 2, top + 80, 0xFFFFFF);
        drawCenteredString(matrices, textRenderer, "This cannot be undone.", width / 2, top + 93, 0xF2B5A7);
        // ConfirmScreen's default render uses a fixed message position, so render only our controls here.
        for (AbstractButtonWidget button : buttons) button.render(matrices, mouseX, mouseY, delta);
        if (mouseY >= top + 24 && mouseY < top + 62 && Math.abs(mouseX - width / 2) <= lineWidth / 2) {
            List<StringRenderable> lines = textRenderer.wrapLines(new LiteralText(entry.path.toString()), Math.min(300, width - 24));
            int tooltipWidth = 0;
            for (StringRenderable line : lines) tooltipWidth = Math.max(tooltipWidth, textRenderer.getWidth(line));
            if (mouseX + 12 + tooltipWidth > width) mouseX = Math.max(mouseX, tooltipWidth + 20);
            renderTooltip(matrices, lines, mouseX, mouseY);
        }
    }

    private String fit(String value, int width) {
        return textRenderer.getWidth(value) <= width ? value : textRenderer.trimToWidth(value, width - textRenderer.getWidth("...")) + "...";
    }

    @Override public void onClose() { decision.accept(false); }
}
