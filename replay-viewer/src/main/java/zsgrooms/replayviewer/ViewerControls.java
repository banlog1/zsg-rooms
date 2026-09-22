// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiCheckbox;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiElement;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiTooltip;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.advanced.GuiDropdownMenu;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.CustomLayout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.Layout;
import com.replaymod.lib.de.johni0702.minecraft.gui.layout.LayoutData;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.lwjgl.Color;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplaySender;
import com.replaymod.replay.FullReplaySender;
import com.replaymod.replay.camera.CameraEntity;
import com.replaymod.replay.camera.CameraController;
import com.replaymod.replay.camera.ClassicCameraController;
import com.replaymod.replay.camera.VanillaCameraController;
import com.replaymod.replay.gui.overlay.GuiReplayOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.glfw.GLFW;
import zsgrooms.replayviewer.mixin.OverlayScreenAccessor;

import java.util.LinkedHashMap;
import java.util.Map;

final class ViewerControls {
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final ReplayHandler handler;
    private final GuiReplayOverlay overlay;
    private final Layout originalLayout;
    private final Map<GuiElement, LayoutData> originalElements;
    private final GuiPanel bar = new GuiPanel().setBackgroundColor(new Color(12, 12, 12, 210));
    private final GuiPanel transport = new GuiPanel();
    private final GuiLabel timestamp = new GuiLabel();
    private final GuiButton back;
    private final GuiButton forward;
    private final GuiButton editor;
    private final GuiButton players;
    private final GuiButton analysisButton;
    private final ReplayAnalysis analysis = new ReplayAnalysis();
    private final FollowDetailOptions details = new FollowDetailOptions();
    private final DetailedFollowHud detailHud = new DetailedFollowHud();
    private final PlaybackMode playback;
    private final GuiCheckbox autoHide = new GuiCheckbox().setLabel("Auto-hide").setChecked(true);
    private final GuiCheckbox showChat = new GuiCheckbox().setLabel("Chat").setChecked(false);
    private final GuiDropdownMenu<String> camera = new GuiDropdownMenu<String>()
            .setValues("Direct Freecam", "Classic Freecam", "Follow Player", "Follow: Drone", "Follow: Details");
    private final CameraSpeeds speeds = new CameraSpeeds();
    private final FarFollowController.Distance followDistance = new FarFollowController.Distance();
    private final MilestoneIndex milestoneIndex;
    private final RecordingIndex recordingIndex;
    private boolean metadataApplied;
    private final MilestoneBar milestones;
    private CameraController managedController;
    private final ViewerVisibility visibility = new ViewerVisibility(System.nanoTime());
    private boolean editing;
    private boolean shown = true;
    private CameraEntity lastCamera;
    private int lastMode = -1;
    private int lastSecond = -1;
    private int lastWidth = -1;
    private boolean lastQuick;

    ViewerControls(ReplayHandler handler) {
        this.handler = handler;
        playback = new PlaybackMode(handler, () -> {
            analysis.clear();
            lastCamera = null;
            lastMode = -1;
            lastSecond = -1;
        });
        overlay = handler.getOverlay();
        milestoneIndex = new MilestoneIndex(handler.getReplayFile());
        recordingIndex = new RecordingIndex(handler.getReplayFile());
        milestones = new MilestoneBar(milestoneIndex, handler.getReplayDuration(), this::seekTo);
        originalLayout = overlay.getLayout();
        originalElements = new LinkedHashMap<>(overlay.getElements());
        back = button("<<", "Back 5 seconds", () -> seek(-5000));
        forward = button(">>", "Forward 5 seconds", () -> seek(5000));
        editor = button("Editor", "Show ReplayMod's camera-path editor", () -> setEditing(!editing));
        players = button("Players", "Import and switch race recordings at the same elapsed race time", () -> {
            if (playback.busy()) return;
            ReplaySender sender = handler.getReplaySender();
            if (!sender.isAsyncMode() || sender instanceof FullReplaySender && ((FullReplaySender) sender).isHurrying()) return;
            client.openScreen(new RaceReplayScreen(handler, snapshot()));
        });
        analysisButton = button("Analysis", "Playback mode, follow details, piglin counter and trails", () -> client.openScreen(new AnalysisScreen(analysis, details, playback)));
        camera.setTooltip(new GuiTooltip().setText("Drone: hold left mouse and move to orbit the player with the cursor captured. Scroll changes distance. Player turns do not rotate the drone."));
        showChat.setTooltip(new GuiTooltip().setText("Show recorded chat during playback. Does not change live chat or ReplayMod's capture/filter settings."));
        autoHide.setTooltip(new GuiTooltip().setText("Hide after 3 seconds idle. Press T to release the cursor and reveal controls."));
        transport.setLayout(new CustomLayout<GuiPanel>() {
            @Override
            protected void layout(GuiPanel container, int width, int height) {
                ViewerLayout layout = new ViewerLayout(width + 28);
                set(overlay.playPauseButton, 0, 0, 20, 20);
                set(back, 24, 0, 20, 20);
                set(forward, 48, 0, 20, 20);
                set(overlay.speedSlider, 72, 0, 90, 20);
                int cameraX = layout.narrow ? 0 : 170;
                set(camera, cameraX, layout.cameraY, 110, 20);
                set(editor, width - 52, layout.cameraY, 52, 20);
                set(autoHide, cameraX + 116, layout.cameraY + 2, 70, 16);
                set(showChat, cameraX + 192, layout.cameraY + 2, 44, 16);
            }
        });
        bar.setLayout(new CustomLayout<GuiPanel>() {
            @Override
            protected void layout(GuiPanel container, int width, int height) {
                ViewerLayout layout = new ViewerLayout(width + 16);
                // Menus open downwards: place the transport above the timeline.
                set(transport, 6, 6, width - 12, layout.narrow ? 44 : 20);
                int y = layout.narrow ? 54 : 30;
                set(timestamp, 6, y + 5, width - 152, 10);
                set(analysisButton, width - 138, y, 64, 20);
                set(players, width - 68, y, 62, 20);
                set(milestones, 6, y + 24, width - 12, 14);
                set(overlay.timeline, 6, y + 38, width - 12, 20);
            }
        });
        setEditing(false);
    }

    private static GuiButton button(String label, String tooltip, Runnable click) {
        return new GuiButton().setLabel(label).setTooltip(new GuiTooltip().setText(tooltip)).onClick(click);
    }

    private void seek(int delta) {
        seekTo(ViewerLayout.seekTarget(handler.getReplaySender().currentTimeStamp(), delta, handler.getReplayDuration()));
    }

    void seekFromShortcut(int delta) {
        if (editing || camera.isOpened() || !overlay.isAllowUserInput() || client.world == null
                || Screen.hasControlDown() || Screen.hasAltDown() || Screen.hasShiftDown()) return;
        Screen screen = client.currentScreen;
        if (screen != null && !(screen instanceof OverlayScreenAccessor
                && ((OverlayScreenAccessor) screen).zsgViewer$getOverlay() == overlay)) return;
        seek(delta);
    }

    private void seekTo(int time) {
        if (playback.busy()) return;
        visibility.touch(System.nanoTime());
        ReplaySender sender = handler.getReplaySender();
        if (!sender.isAsyncMode() || sender instanceof FullReplaySender && ((FullReplaySender) sender).isHurrying()) return;
        double speed = sender.paused() ? 0 : sender.getReplaySpeed();
        // Short forward jumps otherwise finish asynchronously and can pause after we restore speed.
        sender.setReplaySpeed(0);
        try {
            handler.doJump(time, true);
        } finally {
            sender.setReplaySpeed(speed);
        }
    }

    private void setEditing(boolean value) {
        if (playback.busy()) return;
        visibility.touch(System.nanoTime());
        editing = value;
        if (value) {
            overlay.removeElement(bar);
            transport.removeElement(overlay.playPauseButton).removeElement(overlay.speedSlider);
            bar.removeElement(overlay.timeline);
            overlay.topPanel.addElements(null, overlay.playPauseButton, overlay.speedSlider, overlay.timeline);
            for (Map.Entry<GuiElement, LayoutData> element : originalElements.entrySet()) {
                overlay.addElements(element.getValue(), element.getKey());
            }
            overlay.addElements(null, editor);
            overlay.setLayout(new CustomLayout<GuiReplayOverlay>(originalLayout) {
                @Override
                protected void layout(GuiReplayOverlay container, int width, int height) {
                    set(editor, 8, height - 28, 64, 20);
                }
            });
            editor.setLabel("Compact");
        } else {
            overlay.removeElement(editor);
            for (GuiElement element : originalElements.keySet()) overlay.removeElement(element);
            overlay.topPanel.removeElement(overlay.playPauseButton).removeElement(overlay.speedSlider).removeElement(overlay.timeline);
            transport.addElements(null, overlay.playPauseButton, back, forward, overlay.speedSlider, camera, autoHide, showChat, editor);
            bar.addElements(null, transport, timestamp, analysisButton, players, milestones, overlay.timeline);
            overlay.addElements(null, bar);
            overlay.setLayout(new CustomLayout<GuiReplayOverlay>() {
                @Override
                protected void layout(GuiReplayOverlay container, int width, int height) {
                    ViewerLayout layout = new ViewerLayout(width);
                    if (shown) set(bar, 8, layout.barY(height, hudInset()), layout.width, layout.height);
                }
            });
            editor.setLabel("Editor");
            shown = true;
        }
    }

    void update() {
        IndicatorTooltips.beginFrame(overlay.isMouseVisible() && statusVisible());
        playback.update();
        if (playback.busy()) return;
        analysis.update(handler);
        if (!metadataApplied && recordingIndex.recording != null) {
            metadataApplied = true;
            ReplayViewer.raceContext(recordingIndex.recording);
        }
        if (editing) return;
        int time = handler.getReplaySender().currentTimeStamp();
        int width = client.getWindow().getScaledWidth();
        if (time / 1000 != lastSecond || width != lastWidth || lastQuick != handler.isQuickMode()) {
            lastQuick = handler.isQuickMode();
            lastSecond = time / 1000;
            lastWidth = width;
            timestamp.setText(client.textRenderer.trimToWidth((handler.isQuickMode() ? "Quick | " : "") + ReplayViewer.timeLabel(time, handler.getReplayDuration()),
                    Math.max(30, width - 184)));
        }
        int height = client.getWindow().getScaledHeight();
        double x = client.mouse.getX() * width / client.getWindow().getWidth();
        double y = client.mouse.getY() * height / client.getWindow().getHeight();
        boolean mouse = overlay.isMouseVisible();
        ViewerLayout layout = new ViewerLayout(width);
        int barY = layout.barY(height, hudInset());
        boolean hover = mouse && x >= 8 && x < width - 8 && y >= barY && y < barY + layout.height;
        boolean held = mouse && GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean visible = visibility.update(System.nanoTime(), autoHide.isChecked(), mouse, x, y,
                hover || held || camera.isOpened() || !overlay.isAllowUserInput(), handler.getReplaySender().paused());
        if (camera.getSelected() == 4 && details.inventoryVisible()) visible = false;
        if (client.currentScreen != null && !(client.currentScreen instanceof OverlayScreenAccessor)) visible = false;
        if (visible != shown) {
            shown = visible;
            if (visible) overlay.addElements(null, bar);
            else overlay.removeElement(bar);
        }
        if (client.currentScreen == null || client.currentScreen instanceof OverlayScreenAccessor) updateCamera();
    }

    private void updateCamera() {
        CameraEntity entity = handler.getCameraEntity();
        if (entity == null) return;
        int mode = camera.getSelected();
        speeds.remember(managedController);
        if (mode == 2 || mode == 4) {
            if (client.world != null && handler.isCameraView()) {
                PlayerEntity target = FarFollowController.recordedPlayer(client);
                if (target != null) handler.spectateEntity(target);
            }
        } else if (entity != lastCamera || mode != lastMode) {
            handler.spectateCamera();
            managedController = mode == 0 ? new VanillaCameraController(client, entity)
                    : mode == 1 ? new ClassicCameraController(entity) : new FarFollowController(client, entity, followDistance);
            speeds.restore(managedController);
            entity.setCameraController(managedController);
        }
        lastCamera = entity;
        lastMode = mode;
    }

    boolean hideChat() { return !showChat.isChecked(); }
    private int hudInset() { return camera.getSelected() == 4 && !client.options.hudHidden ? detailHud.reservedHeight() : 0; }
    boolean detailedActive() { return camera.getSelected() == 4 && statusVisible(); }
    boolean inventoryKey(int key, int scanCode, int action) {
        if (!detailedActive() || camera.isOpened() || !overlay.isAllowUserInput()
                || Screen.hasControlDown() || Screen.hasAltDown() || Screen.hasShiftDown()) return false;
        boolean close = key == GLFW.GLFW_KEY_ESCAPE && details.inventoryVisible();
        if (!close && !client.options.keyInventory.matchesKey(key, scanCode)) return false;
        if (action == GLFW.GLFW_PRESS) {
            if (close) details.closeInventory(); else details.toggleInventory();
            visibility.touch(System.nanoTime());
        }
        return true;
    }
    boolean droneActive() { return !editing && camera.getSelected() == 3 && handler.isCameraView(); }
    void orbit(double x, double y) { followDistance.drag(x, y); }
    void clearTrails() { analysis.clear(); }
    void renderTrails(net.minecraft.client.util.math.MatrixStack matrices, net.minecraft.client.render.Camera view) {
        if (statusVisible()) analysis.render(matrices, view);
    }
    void refreshTimestamp() { lastSecond = -1; }
    void close() { IndicatorTooltips.beginFrame(false); playback.close(); analysis.clear(); milestoneIndex.close(); recordingIndex.close(); }

    private boolean statusVisible() {
        return !editing && !client.options.hudHidden && client.world != null
                && (client.currentScreen == null || client.currentScreen instanceof OverlayScreenAccessor);
    }

    private ReplayTimings.Value recordedTiming() {
        RaceRecording recording = recordingIndex.recording;
        return recording == null ? ReplayTimings.Value.UNKNOWN
                : recording.timing.at(handler.getReplaySender().currentTimeStamp());
    }

    private boolean recordedLoading() {
        RaceRecording recording = recordingIndex.recording;
        return recording != null && recording.loading.at(handler.getReplaySender().currentTimeStamp());
    }

    private int recordedScreen() {
        RaceRecording recording = recordingIndex.recording;
        int time = handler.getReplaySender().currentTimeStamp();
        return recording == null || recording.loading.at(time) || recording.intervalAt(time) == null
                ? ReplayScreens.NONE : recording.screens.at(time);
    }

    void renderPlayerPause(net.minecraft.entity.player.PlayerEntity player,
                           net.minecraft.client.util.math.MatrixStack matrices,
                           net.minecraft.client.render.VertexConsumerProvider consumers) {
        if (statusVisible() && player == FarFollowController.recordedPlayer(client)) {
            boolean loading = recordedLoading();
            boolean paused = recordedTiming().paused;
            int screen = recordedScreen();
            if (loading || paused || screen != ReplayScreens.NONE) PauseIndicator.renderAbove(player, matrices, consumers,
                    loading, paused, handler.getReplaySender().currentTimeStamp(), screen);
        }
    }

    void renderStatus(net.minecraft.client.util.math.MatrixStack matrices) {
        if (!statusVisible()) return;
        if (detailedActive()) {
            RaceRecording recording = recordingIndex.recording;
            int time = handler.getReplaySender().currentTimeStamp();
            RaceRecording.Interval interval = recording == null ? null : recording.intervalAt(time);
            PlayerEntity target = FarFollowController.recordedPlayer(client);
            PlayerHudTrack.Frame frame = interval == null || target == null || recordingIndex.hud == null ? null
                    : recordingIndex.hud.at(time, interval.world, target.getEntityId(), interval.start);
            detailHud.render(matrices, frame, details, target);
        }
        ReplayTimings.Value value = recordedTiming();
        String rta = "RTA " + ReplayTimings.clock(value.rta);
        String igt = "IGT " + ReplayTimings.clock(value.igt);
        int width = Math.max(client.textRenderer.getWidth(rta), client.textRenderer.getWidth(igt));
        int x = client.getWindow().getScaledWidth() - width - 12;
        net.minecraft.client.gui.DrawableHelper.fill(matrices, x - 4, 6, x + width + 4, 31, 0xBB101114);
        client.textRenderer.drawWithShadow(matrices, rta, x, 10, 0xFFFFFF);
        client.textRenderer.drawWithShadow(matrices, igt, x, 21, 0xA8D8FF);
        if (analysis.piglinCounter) {
            String label = "Piglin cluster: " + (analysis.counterAvailable ? analysis.piglins : "--");
            int countX = client.getWindow().getScaledWidth() - client.textRenderer.getWidth(label) - 12;
            net.minecraft.client.gui.DrawableHelper.fill(matrices, countX - 4, 35, client.getWindow().getScaledWidth() - 8, 50, 0xBB101114);
            client.textRenderer.drawWithShadow(matrices, label, countX, 38, 0xFFE3AD);
        }
        boolean loading = recordedLoading();
        boolean paused = value.paused && client.options.perspective == 0
                && client.getCameraEntity() == FarFollowController.recordedPlayer(client);
        if (loading) {
            PauseIndicator.renderLoadingHud(matrices, x - 16, 18, handler.getReplaySender().currentTimeStamp());
        } else if (paused) {
            PauseIndicator.renderHud(matrices, x - 16, 18);
        }
        int screen = recordedScreen();
        if (screen != ReplayScreens.NONE) PauseIndicator.renderScreenHud(matrices,
                x - (loading || paused ? 38 : 16), 18, screen);
        IndicatorTooltips.render(matrices, x, loading, paused, screen);
    }

    State snapshot() {
        speeds.remember(managedController);
        State state = new State();
        state.speeds.copyFrom(speeds);
        state.mode = camera.getSelected();
        state.orbit.copyFrom(followDistance);
        state.details.copyFrom(details);
        state.piglins = analysis.piglinCounter;
        state.trail = analysis.dragonTrail;
        state.piglinTrail = analysis.piglinTrail;
        state.dragonStyle.copyFrom(analysis.dragonStyle);
        state.piglinStyle.copyFrom(analysis.piglinStyle);
        state.chat = showChat.isChecked();
        state.autoHide = autoHide.isChecked();
        state.speed = handler.getReplaySender().paused() ? 0 : handler.getReplaySender().getReplaySpeed();
        state.mouseVisible = overlay.isMouseVisible();
        return state;
    }

    void restore(State state) {
        speeds.copyFrom(state.speeds);
        camera.setSelected(state.mode);
        followDistance.copyFrom(state.orbit);
        details.copyFrom(state.details);
        analysis.piglinCounter = state.piglins;
        analysis.dragonTrail = state.trail;
        analysis.piglinTrail = state.piglinTrail;
        analysis.dragonStyle.copyFrom(state.dragonStyle);
        analysis.piglinStyle.copyFrom(state.piglinStyle);
        showChat.setChecked(state.chat);
        autoHide.setChecked(state.autoHide);
        lastMode = -1;
        updateCamera();
        if (managedController instanceof FarFollowController) managedController.update(0);
        overlay.setMouseVisible(state.mouseVisible);
    }

    static final class State {
        final CameraSpeeds speeds = new CameraSpeeds();
        int mode;
        final FarFollowController.Distance orbit = new FarFollowController.Distance();
        final FollowDetailOptions details = new FollowDetailOptions();
        boolean piglins;
        boolean trail;
        boolean piglinTrail;
        final TrailStyle dragonStyle = new TrailStyle(TrailStyle.Color.CYAN);
        final TrailStyle piglinStyle = new TrailStyle(TrailStyle.Color.GOLD);
        boolean chat;
        boolean autoHide;
        double speed;
        boolean mouseVisible;
    }
}
