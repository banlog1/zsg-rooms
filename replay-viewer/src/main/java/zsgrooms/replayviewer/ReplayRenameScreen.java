// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import org.lwjgl.glfw.GLFW;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Preparing a name is read-only; only explicit confirmation schedules filesystem work. */
final class ReplayRenameScreen extends Screen {
    private final ReplayBrowserFiles.Entry entry;
    private final BooleanSupplier allowed;
    private final Consumer<String> decision;
    private TextFieldWidget filename;
    private ButtonWidget rename;
    private String draft, error = "";
    private int left, top, contentWidth;
    private boolean closed;

    ReplayRenameScreen(ReplayBrowserFiles.Entry entry, BooleanSupplier allowed, Consumer<String> decision) {
        super(new LiteralText("Rename recording"));
        this.entry = entry;
        this.allowed = allowed;
        this.decision = decision;
        draft = entry.path.getFileName().toString();
    }

    @Override protected void init() {
        contentWidth = Math.min(440, width - 32);
        left = (width - contentWidth) / 2;
        top = Math.max(56, height / 2 - 12);
        filename = addButton(new TextFieldWidget(textRenderer, left, top, contentWidth, 20, new LiteralText("Recording filename")));
        filename.setMaxLength(Math.max(256, draft.length()));
        filename.setText(draft);
        filename.setChangedListener(value -> { draft = value; validate(); });
        int buttonWidth = (contentWidth - 8) / 2;
        rename = addButton(new ButtonWidget(left, top + 50, buttonWidth, 20,
                new LiteralText("Rename"), button -> submit()));
        addButton(new ButtonWidget(left + buttonWidth + 8, top + 50, buttonWidth, 20,
                new LiteralText("Cancel"), button -> onClose()));
        setFocused(filename);
        filename.setSelected(true);
        filename.setCursor(0);
        filename.setSelectionEnd(Math.max(0, draft.length() - 5));
        validate();
    }

    private void validate() {
        error = ReplayBrowserFiles.renameError(draft);
        if (!allowed.getAsBoolean()) error = "Return to the library before renaming this recording";
        rename.active = !closed && error.isEmpty() && !draft.equals(entry.path.getFileName().toString());
    }

    private void submit() {
        validate();
        if (rename.active) finish(draft);
    }

    private void finish(String value) {
        if (closed || client.currentScreen != this) return;
        closed = true;
        decision.accept(value);
    }

    @Override public void tick() { filename.tick(); validate(); }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { submit(); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        drawCenteredString(matrices, textRenderer, title.getString(), width / 2, top - 48, 0xFFFFFF);
        drawCenteredString(matrices, textRenderer, textRenderer.trimToWidth(entry.path.getFileName().toString(), contentWidth),
                width / 2, top - 30, 0xA9D9CD);
        drawCenteredString(matrices, textRenderer, textRenderer.trimToWidth(entry.path.getParent().toString(), contentWidth),
                width / 2, top - 18, 0xAAAAAA);
        drawCenteredString(matrices, textRenderer, textRenderer.trimToWidth(error, contentWidth), width / 2, top + 29, 0xF2B5A7);
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override public void onClose() { finish(null); }
    @Override public void removed() { closed = true; }
}
