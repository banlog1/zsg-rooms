// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

final class AnalysisScreen extends Screen {
    private final ReplayAnalysis analysis;
    private final FollowDetailOptions details;
    private final PlaybackMode playback;
    private CheckboxWidget pigs;
    private CheckboxWidget dragon;
    private CheckboxWidget piglinTrail;
    private CheckboxWidget inventory;
    private CheckboxWidget quick;
    private CheckboxWidget sounds;

    AnalysisScreen(ReplayAnalysis analysis, FollowDetailOptions details, PlaybackMode playback) {
        super(new LiteralText("Replay Analysis"));
        this.analysis = analysis;
        this.details = details;
        this.playback = playback;
    }

    @Override protected void init() {
        int x = width / 2 - 140;
        int y = height / 2 - 84;
        pigs = addButton(new CheckboxWidget(x, y, 280, 20, new LiteralText("Piglin cluster counter"), analysis.piglinCounter) {
            @Override public void onPress() { super.onPress(); analysis.piglinCounter = isChecked(); analysis.clear(); }
        });
        dragon = addButton(new CheckboxWidget(x, y + 24, 184, 20, new LiteralText("Dragon trail"), analysis.dragonTrail) {
            @Override public void onPress() { super.onPress(); analysis.dragonTrail = isChecked(); analysis.clear(); }
        });
        piglinTrail = addButton(new CheckboxWidget(x, y + 48, 184, 20, new LiteralText("Piglin trails"), analysis.piglinTrail) {
            @Override public void onPress() { super.onPress(); analysis.piglinTrail = isChecked(); analysis.clear(); }
        });
        addButton(new ButtonWidget(x + 190, y + 24, 90, 20, new LiteralText("Customize"),
                button -> client.openScreen(new TrailSettingsScreen(this, "Dragon Trail", analysis.dragonStyle))));
        addButton(new ButtonWidget(x + 190, y + 48, 90, 20, new LiteralText("Customize"),
                button -> client.openScreen(new TrailSettingsScreen(this, "Piglin Trails", analysis.piglinStyle))));
        inventory = addButton(new CheckboxWidget(x, y + 72, 280, 20, new LiteralText("Always show inventory"), details.alwaysInventory) {
            @Override public void onPress() { super.onPress(); details.alwaysInventory = isChecked(); details.inventoryOpen = false; details.inventoryDismissed = false; }
        });
        quick = addButton(new CheckboxWidget(x, y + 96, 280, 20,
                new LiteralText("Quick Mode (experimental)"), playback.quick()) {
            @Override public void onPress() { playback.setQuick(!playback.quick()); }
            @Override public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
                if (isChecked() != playback.quick()) super.onPress();
                active = playback.available();
                super.renderButton(matrices, mouseX, mouseY, delta);
            }
        });
        sounds = addButton(new CheckboxWidget(x, y + 120, 280, 20,
                new LiteralText("Player sounds (experimental)"), ReplayAudio.enabled()) {
            @Override public void onPress() { super.onPress(); ReplayAudio.setEnabled(isChecked()); }
        });
        addButton(new ButtonWidget(x, y + 164, 280, 20, new LiteralText("Done"), button -> onClose()));
    }

    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        drawCenteredString(matrices, textRenderer, title.getString(), width / 2, height / 2 - 108, 0xFFFFFF);
        drawCenteredString(matrices, textRenderer, playback.status(), width / 2, height / 2 + 62, 0xCCCCCC);
        super.render(matrices, mouseX, mouseY, delta);
        if (pigs.isHovered()) renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(
                "Largest one-block-radius cluster within 64 blocks of the recorded player. Counts recorded piglins, not hole walls or bastion tags."), 240), mouseX, mouseY);
        else if (dragon.isHovered()) renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(
                "Recent recorded dragon positions. Clears after seeks and world changes; hidden behind terrain."), 240), mouseX, mouseY);
        else if (piglinTrail.isHovered()) renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(
                "Paths of up to 128 recorded piglins within 64 blocks of the player. Clears after seeks and world changes; hidden behind terrain."), 240), mouseX, mouseY);
        else if (inventory.isHovered()) renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(
                "Follow: Details. When off, your inventory key toggles the full inventory. Hotbar and status remain visible."), 240), mouseX, mouseY);
        else if (quick.isHovered()) renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(
                "Faster scrolling through the replay, but less detail. Viewer only; recording is unchanged. Off for each newly opened replay."), 240), mouseX, mouseY);
        else if (sounds.isHovered()) renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(
                "Reconstructs footsteps, mining, placements, buckets, eating/drinking, damage and fall impacts. Approximate; Quick Mode may omit actions. Off for each newly opened replay. Seeks are always silent."), 240), mouseX, mouseY);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { if (!playback.busy()) client.openScreen(null); }
}
