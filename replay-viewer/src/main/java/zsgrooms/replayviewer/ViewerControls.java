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
    private final GuiCheckbox autoHide = new GuiCheckbox().setLabel("Auto-hide").setChecked(true);
    private final GuiCheckbox showChat = new GuiCheckbox().setLabel("Chat").setChecked(false);
    private final GuiDropdownMenu<String> camera = new GuiDropdownMenu<String>()
            .setValues("Direct Freecam", "Classic Freecam", "Follow Player", "Follow: Far");
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

    ViewerControls(ReplayHandler handler) {
        this.handler = handler;
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
            ReplaySender sender = handler.getReplaySender();
            if (!sender.isAsyncMode() || sender instanceof FullReplaySender && ((FullReplaySender) sender).isHurrying()) return;
            client.openScreen(new RaceReplayScreen(handler, snapshot()));
        });
        camera.setTooltip(new GuiTooltip().setText("Freecam, first-person follow, or follow from behind and above. Scroll changes distance in Far mode."));
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
                set(timestamp, 6, y + 5, width - 82, 10);
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
        visibility.touch(System.nanoTime());
        ReplaySender sender = handler.getReplaySender();
        if (!sender.isAsyncMode() || sender instanceof FullReplaySender && ((FullReplaySender) sender).isHurrying()) return;
        double speed = sender.getReplaySpeed();
        // Short forward jumps otherwise finish asynchronously and can pause after we restore speed.
        sender.setReplaySpeed(0);
        try {
            handler.doJump(time, true);
        } finally {
            sender.setReplaySpeed(speed);
        }
    }

    private void setEditing(boolean value) {
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
            bar.addElements(null, transport, timestamp, players, milestones, overlay.timeline);
            overlay.addElements(null, bar);
            overlay.setLayout(new CustomLayout<GuiReplayOverlay>() {
                @Override
                protected void layout(GuiReplayOverlay container, int width, int height) {
                    ViewerLayout layout = new ViewerLayout(width);
                    if (shown) set(bar, 8, Math.max(0, height - layout.height - 8), layout.width, layout.height);
                }
            });
            editor.setLabel("Editor");
            shown = true;
        }
    }

    void update() {
        if (!metadataApplied && recordingIndex.recording != null) {
            metadataApplied = true;
            ReplayViewer.raceContext(recordingIndex.recording);
        }
        if (editing) return;
        int time = handler.getReplaySender().currentTimeStamp();
        int width = client.getWindow().getScaledWidth();
        if (time / 1000 != lastSecond || width != lastWidth) {
            lastSecond = time / 1000;
            lastWidth = width;
            timestamp.setText(client.textRenderer.trimToWidth(ReplayViewer.timeLabel(time, handler.getReplayDuration()),
                    Math.max(30, width - 114)));
        }
        int height = client.getWindow().getScaledHeight();
        double x = client.mouse.getX() * width / client.getWindow().getWidth();
        double y = client.mouse.getY() * height / client.getWindow().getHeight();
        boolean mouse = overlay.isMouseVisible();
        boolean hover = mouse && x >= 8 && x < width - 8 && y >= height - new ViewerLayout(width).height - 8;
        boolean held = mouse && GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean visible = visibility.update(System.nanoTime(), autoHide.isChecked(), mouse, x, y,
                hover || held || camera.isOpened() || !overlay.isAllowUserInput(), handler.getReplaySender().paused());
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
        if (mode == 2) {
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
    void refreshTimestamp() { lastSecond = -1; }
    void close() { milestoneIndex.close(); recordingIndex.close(); }

    private boolean statusVisible() {
        return !editing && !client.options.hudHidden && client.world != null
                && (client.currentScreen == null || client.currentScreen instanceof OverlayScreenAccessor);
    }

    private ReplayTimings.Value recordedTiming() {
        RaceRecording recording = recordingIndex.recording;
        return recording == null ? ReplayTimings.Value.UNKNOWN
                : recording.timing.at(handler.getReplaySender().currentTimeStamp());
    }

    void renderPlayerPause(net.minecraft.entity.player.PlayerEntity player,
                           net.minecraft.client.util.math.MatrixStack matrices,
                           net.minecraft.client.render.VertexConsumerProvider consumers) {
        if (statusVisible() && player == FarFollowController.recordedPlayer(client) && recordedTiming().paused) {
            PauseIndicator.renderAbove(player, matrices, consumers);
        }
    }

    void renderStatus(net.minecraft.client.util.math.MatrixStack matrices) {
        if (!statusVisible()) return;
        ReplayTimings.Value value = recordedTiming();
        String rta = "RTA " + ReplayTimings.clock(value.rta);
        String igt = "IGT " + ReplayTimings.clock(value.igt);
        int width = Math.max(client.textRenderer.getWidth(rta), client.textRenderer.getWidth(igt));
        int x = client.getWindow().getScaledWidth() - width - 12;
        net.minecraft.client.gui.DrawableHelper.fill(matrices, x - 4, 6, x + width + 4, 31, 0xBB101114);
        client.textRenderer.drawWithShadow(matrices, rta, x, 10, 0xFFFFFF);
        client.textRenderer.drawWithShadow(matrices, igt, x, 21, 0xA8D8FF);
        if (value.paused && client.options.perspective == 0
                && client.getCameraEntity() == FarFollowController.recordedPlayer(client)) {
            PauseIndicator.renderHud(matrices, x - 16, 18);
        }
    }

    State snapshot() {
        speeds.remember(managedController);
        State state = new State();
        state.speeds.copyFrom(speeds);
        state.mode = camera.getSelected();
        state.distance = followDistance.blocks;
        state.chat = showChat.isChecked();
        state.autoHide = autoHide.isChecked();
        state.speed = handler.getReplaySender().getReplaySpeed();
        state.mouseVisible = overlay.isMouseVisible();
        return state;
    }

    void restore(State state) {
        speeds.copyFrom(state.speeds);
        camera.setSelected(state.mode);
        followDistance.blocks = state.distance;
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
        double distance;
        boolean chat;
        boolean autoHide;
        double speed;
        boolean mouseVisible;
    }
}
