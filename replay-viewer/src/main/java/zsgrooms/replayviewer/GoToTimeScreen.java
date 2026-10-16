// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntConsumer;

final class GoToTimeScreen extends Screen {
    private final ReplayHandler handler;
    private final IntConsumer seek;
    private final double speed;
    private final boolean mouseVisible;
    private TextFieldWidget time;
    private ButtonWidget jump;
    private String draft;
    private int left, top, contentWidth;
    private boolean closed;

    GoToTimeScreen(ReplayHandler handler, IntConsumer seek) {
        super(new LiteralText("Go to Replay Time"));
        this.handler = handler;
        this.seek = seek;
        speed = handler.getReplaySender().paused() ? 0 : handler.getReplaySender().getReplaySpeed();
        mouseVisible = handler.getOverlay().isMouseVisible();
        draft = ReplayTimeInput.format(handler.getReplaySender().currentTimeStamp());
        handler.getReplaySender().setReplaySpeed(0);
        handler.getOverlay().setVisible(false);
    }

    @Override protected void init() {
        contentWidth = Math.min(280, width - 32);
        left = (width - contentWidth) / 2;
        top = Math.max(28, height / 2 - 48);
        time = addButton(new TextFieldWidget(textRenderer, left, top, contentWidth, 20, new LiteralText("Replay time")));
        time.setMaxLength(32);
        time.setText(draft);
        time.setChangedListener(value -> { draft = value; validate(); });
        jump = addButton(new ButtonWidget(left + (contentWidth + 6) / 2, top + 54, (contentWidth - 6) / 2, 20,
                new LiteralText("Go"), button -> submit()));
        addButton(new ButtonWidget(left, top + 54, (contentWidth - 6) / 2, 20,
                new LiteralText("Cancel"), button -> onClose()));
        setFocused(time);
        time.setSelected(true);
        validate();
    }

    private void validate() {
        if (jump != null) {
            int value = ReplayTimeInput.parse(draft);
            jump.active = value >= 0 && value <= handler.getReplayDuration();
        }
    }

    private void submit() {
        if (!jump.active || closed || ReplayModReplay.instance.getReplayHandler() != handler) return;
        try { seek.accept(ReplayTimeInput.parse(draft)); }
        finally { onClose(); }
    }

    @Override public void tick() { time.tick(); }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { submit(); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        fill(matrices, 0, 0, width, height, 0xB5101316);
        drawCenteredString(matrices, textRenderer, title.getString(), width / 2, top - 22, 0xFFFFFF);
        int value = ReplayTimeInput.parse(draft);
        String label = value < 0 ? "Seconds, m:ss or h:mm:ss" : value > handler.getReplayDuration()
                ? "After end of recording" : "Replay time " + ReplayTimeInput.format(value);
        drawCenteredString(matrices, textRenderer, textRenderer.trimToWidth(label, contentWidth), width / 2, top + 29,
                jump.active ? 0xA9D9CD : 0xF2B5A7);
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override public void onClose() {
        if (closed) return;
        closed = true;
        if (ReplayModReplay.instance.getReplayHandler() == handler) {
            handler.getOverlay().setVisible(true);
            handler.getOverlay().setMouseVisible(mouseVisible);
            handler.getReplaySender().setReplaySpeed(speed);
        }
        client.openScreen(null);
    }

    @Override public boolean isPauseScreen() { return false; }
}
