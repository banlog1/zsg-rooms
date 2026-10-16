// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AbstractButtonWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class AnalysisScreen extends Screen {
    private enum Tab { PLAYBACK, ANALYSIS, DISPLAY }

    private final ReplayAnalysis analysis;
    private final FollowDetailOptions details;
    private final PlaybackMode playback;
    private final BooleanSupplier autoHide;
    private final BooleanSupplier showChat;
    private final Consumer<Boolean> setAutoHide;
    private final Consumer<Boolean> setShowChat;
    private final Runnable editor;
    private final java.util.function.Consumer<Screen> barters;
    private final Map<Tab, List<AbstractButtonWidget>> groups = new EnumMap<>(Tab.class);
    private final Map<AbstractButtonWidget, String> tooltips = new LinkedHashMap<>();
    private Tab tab = Tab.PLAYBACK;
    private ReplaySettingsLayout layout;
    private CheckboxWidget pigs;
    private CheckboxWidget dragon;
    private CheckboxWidget piglinTrail;
    private CheckboxWidget inventory;
    private CheckboxWidget quick;
    private CheckboxWidget sounds;
    private CheckboxWidget fastSeeking;
    private ButtonWidget highlights;
    private ButtonWidget pathEditor;
    private ButtonWidget done;

    AnalysisScreen(ReplayAnalysis analysis, FollowDetailOptions details, PlaybackMode playback) {
        this(analysis, details, playback, null, null, null, null, null);
    }

    // Optional Display controls require both a getter and setter; the editor callback may be null.
    AnalysisScreen(ReplayAnalysis analysis, FollowDetailOptions details, PlaybackMode playback,
                   BooleanSupplier autoHide, BooleanSupplier showChat,
                   Consumer<Boolean> setAutoHide, Consumer<Boolean> setShowChat, Runnable editor) {
        this(analysis, details, playback, autoHide, showChat, setAutoHide, setShowChat, editor, null);
    }

    AnalysisScreen(ReplayAnalysis analysis, FollowDetailOptions details, PlaybackMode playback,
                   BooleanSupplier autoHide, BooleanSupplier showChat,
                   Consumer<Boolean> setAutoHide, Consumer<Boolean> setShowChat, Runnable editor, Consumer<Screen> barters) {
        super(new LiteralText("Replay Settings"));
        this.analysis = analysis;
        this.details = details;
        this.playback = playback;
        this.autoHide = autoHide;
        this.showChat = showChat;
        this.setAutoHide = setAutoHide;
        this.setShowChat = setShowChat;
        this.editor = editor;
        this.barters = barters;
    }

    @Override protected void init() {
        groups.clear();
        tooltips.clear();
        layout = new ReplaySettingsLayout(width, height);
        for (Tab value : Tab.values()) groups.put(value, new ArrayList<>());
        tabButton(Tab.PLAYBACK, "Playback");
        tabButton(Tab.ANALYSIS, "Analysis");
        tabButton(Tab.DISPLAY, "Display");

        // Keep the original field types and lookup labels for client smoke checks.
        pigs = checkbox(Tab.ANALYSIS, 0, barters == null ? layout.width : layout.trailWidth(), "Piglin cluster counter", () -> analysis.piglinCounter,
                value -> { analysis.piglinCounter = value; analysis.clear(); },
                "Largest one-block-radius cluster within 64 blocks of the recorded player, including Faithful Follow. Counts recorded piglins, not hole walls or bastion tags. Hidden when replay UI is explicitly hidden.");
        if (barters != null) action(Tab.ANALYSIS, layout.customizeX(), 0, ReplaySettingsLayout.CUSTOMIZE_WIDTH, "Barters",
                () -> barters.accept(this), "Completed trades, received items and bartering-state labels. Item history requires a new Rooms recording; old recordings are not guessed.");
        dragon = checkbox(Tab.ANALYSIS, 1, layout.trailWidth(), "Dragon trail", () -> analysis.dragonTrail,
                value -> { analysis.dragonTrail = value; analysis.clear(); },
                "Recent recorded dragon positions, including history before a seek. History is prepared in the background; hidden behind terrain.");
        piglinTrail = checkbox(Tab.ANALYSIS, 2, layout.trailWidth(), "Piglin trails", () -> analysis.piglinTrail,
                value -> { analysis.piglinTrail = value; analysis.clear(); },
                "Paths of up to 128 recorded piglins within 64 blocks of the player, including the previous configured seconds after a seek. Hidden behind terrain.");
        checkbox(Tab.ANALYSIS, 3, layout.trailWidth(), "Player trails", () -> analysis.playerTrail,
                value -> { analysis.playerTrail = value; analysis.clear(); },
                "Paths of recorded players, including history before enabling or seeking. Never joins worlds or teleports. Hidden behind terrain; recording is unchanged.");
        action(Tab.ANALYSIS, layout.customizeX(), 1, ReplaySettingsLayout.CUSTOMIZE_WIDTH, "Customize",
                () -> client.openScreen(new TrailSettingsScreen(this, "Dragon Trail", analysis.dragonStyle)),
                "Dragon trail color, length, thickness, opacity and fading.");
        action(Tab.ANALYSIS, layout.customizeX(), 2, ReplaySettingsLayout.CUSTOMIZE_WIDTH, "Customize",
                () -> client.openScreen(new TrailSettingsScreen(this, "Piglin Trails", analysis.piglinStyle)),
                "Piglin trail color, length, thickness, opacity and fading.");
        action(Tab.ANALYSIS, layout.customizeX(), 3, ReplaySettingsLayout.CUSTOMIZE_WIDTH, "Customize",
                () -> client.openScreen(new TrailSettingsScreen(this, "Player Trails", analysis.playerStyle)),
                "Player trail color, length, thickness, opacity and fading.");
        inventory = checkbox(Tab.DISPLAY, 3, layout.width, "Always show inventory", () -> details.alwaysInventory,
                value -> { details.alwaysInventory = value; details.inventoryOpen = false; details.inventoryDismissed = false; },
                "Follow: Details. When off, your inventory key toggles the full inventory. Hotbar and status remain visible.");
        quick = checkbox(Tab.PLAYBACK, 0, layout.width, "Quick Mode (experimental)", playback::quick, playback::setQuick,
                "Experimental. Faster scrolling through the replay, but less detail. Viewer only; recording is unchanged. Off for each newly opened replay.");
        fastSeeking = checkbox(Tab.PLAYBACK, 1, layout.width, "Seek batching (experimental)", playback::fastSeeking, playback::setFastSeeking,
                "Experimental lighting batching for normal-mode seeks. Speed gains are not established; lighting may differ. Off for each newly opened replay. Disable and rewind to rebuild normally.");
        sounds = checkbox(Tab.PLAYBACK, 3, layout.width, "Player sounds (experimental)", ReplayAudio::enabled, ReplayAudio::setEnabled,
                "Experimental. Approximate footsteps, mining, placements, buckets, eating, damage and impacts. Quick Mode may omit actions. Seeks are always silent.");
        highlights = action(Tab.ANALYSIS, layout.left, 4, (layout.width - 4) / 2, "Mob Highlights",
                () -> client.openScreen(new MobHighlightScreen(this, analysis.highlights)),
                "Choose recorded mob types and an outline color. Visible through walls during playback only; no extra chunks or recording changes.");
        action(Tab.ANALYSIS, layout.left + (layout.width + 4) / 2, 4, (layout.width - 4) / 2, "Mob Trails",
                () -> client.openScreen(new MobHighlightScreen(this, analysis.mobTrails, analysis.mobStyle)),
                "Choose mob types and path styling. Tracks the nearest 128 loaded matching mobs to the camera. Dragon and piglin shortcuts keep their own style when enabled. History is prepared once in temporary storage; no recorder changes.");
        if (autoHide != null && setAutoHide != null) {
            checkbox(Tab.DISPLAY, 0, layout.width, "Auto-hide controls", autoHide, setAutoHide,
                    "Hide replay controls while idle. Move the pointer to reveal them again.");
        }
        if (showChat != null && setShowChat != null) {
            checkbox(Tab.DISPLAY, 1, layout.width, "Show chat", showChat, setShowChat,
                    "Show recorded chat during playback.");
        }
        int displayHalf = (layout.width - ReplaySettingsLayout.GAP) / 2;
        checkbox(Tab.DISPLAY, 2, displayHalf, "Run timers", () -> analysis.runTimers,
                value -> analysis.runTimers = value,
                "Show recorded RTA and IGT in every camera mode, including Faithful Follow and recorded containers. Independent of recorded F1; explicitly hiding replay UI hides these timers too.");
        register(Tab.DISPLAY, new ReplaySettingsCheckbox(layout.left + displayHalf + ReplaySettingsLayout.GAP,
                layout.rowY(2), displayHalf, "Render distance", () -> analysis.renderDistance,
                value -> analysis.renderDistance = value),
                "Show the recorded player's render distance in chunks at the top left. Updates during playback and seeking; requires Detailed Capture. This is not the viewer's render distance. Hidden when replay UI is explicitly hidden.");
        pathEditor = null;
        if (editor != null) {
            pathEditor = action(Tab.DISPLAY, layout.left, 4, layout.width, "Path editor", editor,
                    "Open ReplayMod's camera path editor.");
        }
        done = addButton(new ButtonWidget(layout.left + (layout.width - 100) / 2, layout.doneY(), 100, 20,
                new LiteralText("Done"), button -> onClose()));
        updateControls();
    }

    private void tabButton(Tab value, String label) {
        addButton(new ButtonWidget(layout.tabLeft(value.ordinal()), layout.tabsY(), layout.tabWidth(value.ordinal()), 20,
                new LiteralText(label), button -> selectTab(value)) {
            @Override public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
                boolean selected = tab == value;
                if (isHovered() || isFocused()) fill(matrices, x, y, x + width, y + height, 0x30000000);
                drawCenteredString(matrices, textRenderer, getMessage().getString(), x + width / 2, y + 6,
                        selected ? 0xFFFFFF : 0xBBBBBB);
                fill(matrices, x, y + height - 1, x + width, y + height, selected ? 0xFFA9D9CD : 0x60777777);
            }
        });
    }

    private void selectTab(Tab value) {
        if (tab == value) return;
        // Remove focus before hiding a group so keyboard input cannot stay on its controls.
        AbstractButtonWidget focused = getFocused() instanceof AbstractButtonWidget ? (AbstractButtonWidget) getFocused() : null;
        if (focused != null && focused.isFocused()) focused.changeFocus(false);
        setFocused(null);
        tab = value;
        updateControls();
    }

    void openDisplayTab() {
        if (layout == null) tab = Tab.DISPLAY;
        else selectTab(Tab.DISPLAY);
    }

    private CheckboxWidget checkbox(Tab group, int row, int controlWidth, String label, BooleanSupplier getter,
                                    Consumer<Boolean> setter, String tooltip) {
        return register(group, new ReplaySettingsCheckbox(layout.left, layout.rowY(row), controlWidth, label, getter, setter), tooltip);
    }

    private ButtonWidget action(Tab group, int x, int row, int controlWidth, String label, Runnable action, String tooltip) {
        return register(group, new ButtonWidget(x, layout.rowY(row), controlWidth, 20, new LiteralText(label),
                button -> { if (!playback.busy()) action.run(); }), tooltip);
    }

    private <T extends AbstractButtonWidget> T register(Tab group, T widget, String tooltip) {
        groups.get(group).add(widget);
        tooltips.put(widget, tooltip);
        return addButton(widget);
    }

    private void updateControls() {
        for (Map.Entry<Tab, List<AbstractButtonWidget>> group : groups.entrySet()) {
            for (AbstractButtonWidget widget : group.getValue()) {
                widget.visible = group.getKey() == tab;
                widget.active = widget.visible && (!(widget instanceof ButtonWidget) || !playback.busy());
            }
        }
        quick.active = quick.visible && playback.available();
        fastSeeking.active = fastSeeking.visible && !playback.busy() && !playback.quick();
        done.active = !playback.busy();
        for (AbstractButtonWidget widget : tooltips.keySet()) {
            if (widget instanceof ReplaySettingsCheckbox) ((ReplaySettingsCheckbox) widget).refresh();
        }
    }

    @Override public void tick() { updateControls(); }

    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        fill(matrices, 0, 0, width, height, 0xA0000000);
        updateControls();
        drawCenteredString(matrices, textRenderer, title.getString(), width / 2, layout.top, 0xFFFFFF);
        if (tab == Tab.PLAYBACK) {
            heading(matrices, "Mode", 0);
            heading(matrices, "Audio", 3);
        } else if (tab == Tab.ANALYSIS) {
            heading(matrices, "Tracking", 0);
        } else {
            heading(matrices, "Interface", 0);
        }
        String status = tab == Tab.ANALYSIS && !playback.busy() ? analysis.trailStatus() : playback.status();
        drawCenteredString(matrices, textRenderer, textRenderer.trimToWidth(status, layout.width), width / 2, layout.statusY(),
                playback.busy() ? 0xA9D9CD : 0xF2B5A7);
        super.render(matrices, mouseX, mouseY, delta);
        int tooltipWidth = Math.min(240, Math.max(mouseX - 24, width - mouseX - 24));
        for (Map.Entry<AbstractButtonWidget, String> entry : tooltips.entrySet()) {
            AbstractButtonWidget widget = entry.getKey();
            if (widget.visible && widget.isHovered()) {
                String tooltip = entry.getValue();
                if (widget == quick && !widget.active) tooltip += playback.busy() ? " Preparing the mode switch." : " Unavailable in minimal mode or after closing the replay.";
                if (widget == fastSeeking && !widget.active) tooltip += playback.busy() ? " Unavailable during a mode switch." : " Unavailable in Quick Mode.";
                renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(tooltip), tooltipWidth), mouseX, mouseY);
                return;
            }
        }
        if (!status.isEmpty() && mouseX >= layout.left && mouseX < layout.left + layout.width
                && mouseY >= layout.statusY() && mouseY < layout.statusY() + 10) {
            renderTooltip(matrices, textRenderer.wrapLines(new LiteralText(status), tooltipWidth), mouseX, mouseY);
        }
    }

    private void heading(MatrixStack matrices, String label, int row) {
        drawStringWithShadow(matrices, textRenderer, label, layout.left, layout.headingY(row), 0xA9D9CD);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { if (!playback.busy()) client.openScreen(null); }
}
