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
    private CheckboxWidget fastSeeking;
    private ButtonWidget highlights;

    AnalysisScreen(ReplayAnalysis analysis, FollowDetailOptions details, PlaybackMode playback) {
        super(new LiteralText("Replay Analysis"));
        this.analysis = analysis;
        this.details = details;
        this.playback = playback;
    }

    @Override protected void init() {
        int x = width / 2 - 140;
        int y = Math.max(24, (height - 218) / 2);
        pigs = addButton(new CheckboxWidget(x, y, 280, 20, new LiteralText("Piglin cluster counter"), analysis.piglinCounter) {
            @Override public void onPress() { super.onPress(); analysis.piglinCounter = isChecked(); analysis.clear(); }
        });
        dragon = addButton(new CheckboxWidget(x, y + 22, 184, 20, new LiteralText("Dragon trail"), analysis.dragonTrail) {
            @Override public void onPress() { super.onPress(); analysis.dragonTrail = isChecked(); analysis.clear(); }
        });
        piglinTrail = addButton(new CheckboxWidget(x, y + 44, 184, 20, new LiteralText("Piglin trails"), analysis.piglinTrail) {
            @Override public void onPress() { super.onPress(); analysis.piglinTrail = isChecked(); analysis.clear(); }
        });
        addButton(new ButtonWidget(x + 190, y + 22, 90, 20, new LiteralText("Customize"),
                button -> client.openScreen(new TrailSettingsScreen(this, "Dragon Trail", analysis.dragonStyle))));
        addButton(new ButtonWidget(x + 190, y + 44, 90, 20, new LiteralText("Customize"),
                button -> client.openScreen(new TrailSettingsScreen(this, "Piglin Trails", analysis.piglinStyle))));
        inventory = addButton(new CheckboxWidget(x, y + 66, 280, 20, new LiteralText("Always show inventory"), details.alwaysInventory) {
            @Override public void onPress() { super.onPress(); details.alwaysInventory = isChecked(); details.inventoryOpen = false; details.inventoryDismissed = false; }
        });
        quick = addButton(new CheckboxWidget(x, y + 88, 280, 20,
                new LiteralText("Quick Mode (experimental)"), playback.quick()) {
            @Override public void onPress() { playback.setQuick(!playback.quick()); }
            @Override public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
                if (isChecked() != playback.quick()) super.onPress();
                active = playback.available();
                super.renderButton(matrices, mouseX, mouseY, delta);
            }
        });
        fastSeeking = addButton(new CheckboxWidget(x, y + 110, 280, 20,
                new LiteralText("Seek batching (experimental)"), playback.fastSeeking()) {
            @Override public void onPress() { playback.setFastSeeking(!playback.fastSeeking()); }
            @Override public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
                if (isChecked() != playback.fastSeeking()) super.onPress();
                active = !playback.busy() && !playback.quick();
                super.renderButton(matrices, mouseX, mouseY, delta);
            }
        });
        sounds = addButton(new CheckboxWidget(x, y + 132, 280, 20,
                new LiteralText("Player sounds (experimental)"), ReplayAudio.enabled()) {
            @Override public void onPress() { super.onPress(); ReplayAudio.setEnabled(isChecked()); }
        });
        highlights = addButton(new ButtonWidget(x, y + 154, 280, 20, new LiteralText("Mob Highlights"),
                button -> client.openScreen(new MobHighlightScreen(this, analysis.highlights))));
        addButton(new ButtonWidget(x, y + 190, 280, 20, new LiteralText("Done"), button -> onClose()));
    }

    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        int y = Math.max(24, (height - 218) / 2);
        drawCenteredString(matrices, textRenderer, title.getString(), width / 2, y - 20, 0xFFFFFF);
        drawCenteredString(matrices, textRenderer, playback.status(), width / 2, y + 178, 0xCCCCCC);
        super.render(matrices, mouseX, mouseY, delta);
        int tooltipWidth = Math.min(240, Math.max(mouseX - 24, width - mouseX - 24));
        if (pigs.isHovered()) renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(
                "Largest one-block-radius cluster within 64 blocks of the recorded player. Counts recorded piglins, not hole walls or bastion tags."), tooltipWidth), mouseX, mouseY);
        else if (dragon.isHovered()) renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(
                "Recent recorded dragon positions. Clears after seeks and world changes; hidden behind terrain."), tooltipWidth), mouseX, mouseY);
        else if (piglinTrail.isHovered()) renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(
                "Paths of up to 128 recorded piglins within 64 blocks of the player. Clears after seeks and world changes; hidden behind terrain."), tooltipWidth), mouseX, mouseY);
        else if (inventory.isHovered()) renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(
                "Follow: Details. When off, your inventory key toggles the full inventory. Hotbar and status remain visible."), tooltipWidth), mouseX, mouseY);
        else if (quick.isHovered()) renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(
                "Faster scrolling through the replay, but less detail. Viewer only; recording is unchanged. Off for each newly opened replay."), tooltipWidth), mouseX, mouseY);
        else if (sounds.isHovered()) renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(
                "Reconstructs footsteps, mining, placements, buckets, eating/drinking, damage and fall impacts. Approximate; Quick Mode may omit actions. Seeks are always silent."), tooltipWidth), mouseX, mouseY);
        else if (fastSeeking.isHovered()) renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(
                "Experimental lighting batching for normal-mode seeks. Speed gains are not established; lighting may differ. Off for each newly opened replay. Recording is unchanged. Disable and rewind to rebuild normally."), tooltipWidth), mouseX, mouseY);
        else if (highlights.isHovered()) renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(
                "Choose recorded mob types and an outline color. Visible through walls during playback only; no extra chunks or recording changes."), tooltipWidth), mouseX, mouseY);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { if (!playback.busy()) client.openScreen(null); }
}
