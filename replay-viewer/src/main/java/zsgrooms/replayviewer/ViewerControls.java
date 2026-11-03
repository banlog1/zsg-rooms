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
    private final GuiPanel bar = new GuiPanel();
    private final GuiPanel transport = new GuiPanel();
    private final GuiLabel timestamp = new GuiLabel();
    private final GuiButton back;
    private final GuiButton forward;
    private final GuiButton editor;
    private final GuiButton players;
    private final GuiButton splits;
    private final GuiButton analysisButton;
    private final GuiButton goTo;
    private final GuiButton hide;
    private final GuiButton reveal;
    private final ReplayAnalysis analysis = new ReplayAnalysis();
    private final FollowDetailOptions details = new FollowDetailOptions();
    private final DetailedFollowHud detailHud = new DetailedFollowHud();
    private final FaithfulFollow faithful = new FaithfulFollow();
    private final QuickHeldItems quickHands = new QuickHeldItems();
    private final PlaybackMode playback;
    private final GuiCheckbox autoHide = new GuiCheckbox().setLabel("Auto-hide").setChecked(true);
    private final GuiCheckbox showChat = new GuiCheckbox().setLabel("Chat").setChecked(false);
    private final GuiDropdownMenu<String> camera = new ViewerCameraMenu()
            .setValues("Direct Freecam", "Classic Freecam", "Follow Player", "Follow: Drone", "Follow: Details", "Faithful Follow");
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
    private boolean revealShown;
    private CameraEntity lastCamera;
    private int lastMode = -1;
    private int lastSecond = -1;
    private int lastWidth = -1;
    private boolean lastQuick;
    private final float[] recordedRotation = new float[2];
    private boolean cameraSampled, cameraSampleAvailable;
    private CameraTrack sampledCameraTrack;
    private net.minecraft.entity.Entity sampledCameraEntity;
    private int cameraDiscontinuities;

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
        back = icon(ViewerIconButton.Icon.BACK, "<<", "Back 5 seconds", () -> seek(-5000));
        forward = icon(ViewerIconButton.Icon.FORWARD, ">>", "Forward 5 seconds", () -> seek(5000));
        editor = icon(ViewerIconButton.Icon.EDITOR, "Editor", "Toggle ReplayMod's camera-path editor", () -> setEditing(!editing));
        players = icon(ViewerIconButton.Icon.PLAYERS, "Players", "Import and switch race recordings\nat the same elapsed race time", () -> {
            if (playback.busy()) return;
            ReplaySender sender = handler.getReplaySender();
            if (!sender.isAsyncMode() || sender instanceof FullReplaySender && ((FullReplaySender) sender).isHurrying()) return;
            client.openScreen(new RaceReplayScreen(handler, snapshot()));
        });
        splits = icon(ViewerIconButton.Icon.SPLITS, "Splits", "Compare recorded splits\nClick a time to jump and follow that runner", this::openSplits);
        analysisButton = icon(ViewerIconButton.Icon.ANALYSIS, "Analysis", "Replay settings\nPlayback, analysis and display", this::openSettings);
        goTo = icon(ViewerIconButton.Icon.CLOCK, "Go to time", "Go to an absolute replay time\nSeconds, m:ss or h:mm:ss", this::openTimeJump);
        hide = icon(ViewerIconButton.Icon.HIDE, "Hide controls", "Hide replay controls, even while paused\nUse the eye button to reveal them", this::toggleControls);
        reveal = icon(ViewerIconButton.Icon.REVEAL, "Show controls", "Show replay controls\nRelease the cursor with your ReplayMod\nchat/cursor key to click", () -> {
            visibility.setManuallyHidden(false, System.nanoTime());
            setControlsShown(true, true);
        });
        camera.setTooltip(new GuiTooltip().setText(new String[] {"Camera view", "Drone: hold left mouse to orbit.", "Scroll changes distance.", "Player turns do not rotate the drone.", "Faithful Follow needs Detailed Capture.", "It follows recorded screens and F1/F5.", "Use the inventory binding to inspect."}));
        showChat.setTooltip(new GuiTooltip().setText(new String[] {"Show recorded chat during playback.", "Does not change live chat or recording."}));
        autoHide.setTooltip(new GuiTooltip().setText(new String[] {"Hide after 3 seconds idle.", "Releasing the cursor reveals idle-hidden", "controls. Explicitly hidden controls", "stay hidden until shown."}));
        transport.setLayout(new CustomLayout<GuiPanel>() {
            @Override
            protected void layout(GuiPanel container, int width, int height) {
                ViewerLayout layout = new ViewerLayout(width + 16);
                set(overlay.playPauseButton, 0, 0, 20, 20);
                set(back, 24, 0, 20, 20);
                set(forward, 48, 0, 20, 20);
                set(overlay.speedSlider, 72, 0, 60, 20);
                set(timestamp, 142, 6, layout.textWidth, 10);
                set(camera, layout.cameraX, layout.cameraY, layout.cameraWidth, 20);
                set(goTo, layout.utilityX, layout.utilityY, 20, 20);
                set(splits, layout.utilityX + 24, layout.utilityY, 20, 20);
                set(players, layout.utilityX + 48, layout.utilityY, 20, 20);
                set(analysisButton, layout.utilityX + 72, layout.utilityY, 20, 20);
                set(hide, layout.utilityX + 96, layout.utilityY, 20, 20);
            }
        });
        bar.setLayout(new CustomLayout<GuiPanel>() {
            @Override
            protected void layout(GuiPanel container, int width, int height) {
                ViewerLayout layout = new ViewerLayout(width + 16);
                set(transport, 0, 0, width, layout.narrow ? 44 : 20);
                set(milestones, 0, layout.milestoneY, width, 16);
                set(overlay.timeline, 0, layout.timelineY, width, 16);
            }
        });
        setEditing(false);
    }

    private static GuiButton icon(ViewerIconButton.Icon icon, String label, String tooltip, Runnable click) {
        return new ViewerIconButton(icon, label, tooltip, click);
    }

    private void openSettings() {
        client.openScreen(new AnalysisScreen(analysis, details, playback,
                autoHide::isChecked, showChat::isChecked, value -> autoHide.setChecked(value),
                value -> showChat.setChecked(value), () -> { client.openScreen(null); setEditing(true); },
                parent -> client.openScreen(new BarterScreen(parent, analysis, recordingIndex, handler, this::seekTo))));
    }

    int chromeWidget(Object widget) {
        if (editing) return 0;
        return widget == overlay.playPauseButton ? 1 : widget == overlay.speedSlider ? 2 : widget == overlay.timeline ? 3 : 0;
    }

    double sliderSpeed() { return overlay.getSpeedSliderValue(); }
    boolean paused() { return handler.getReplaySender().paused(); }

    private boolean shortcutContext() {
        if (editing || playback.busy() || camera.isOpened() || !overlay.isAllowUserInput() || client.world == null
                || Screen.hasControlDown() || Screen.hasAltDown() || Screen.hasShiftDown()) return false;
        Screen screen = client.currentScreen;
        return screen == null || screen instanceof OverlayScreenAccessor
                && ((OverlayScreenAccessor) screen).zsgViewer$getOverlay() == overlay;
    }

    void openTimeJump() {
        if (!shortcutContext()) return;
        ReplaySender sender = handler.getReplaySender();
        if (!sender.isAsyncMode() || sender instanceof FullReplaySender && ((FullReplaySender) sender).isHurrying()) return;
        visibility.touch(System.nanoTime());
        client.openScreen(new GoToTimeScreen(handler, this::seekTo));
    }

    void openSplits() {
        if (!shortcutContext()) return;
        ReplaySender sender = handler.getReplaySender();
        if (!sender.isAsyncMode() || sender instanceof FullReplaySender && ((FullReplaySender) sender).isHurrying()) return;
        visibility.touch(System.nanoTime());
        client.openScreen(new ReplaySplitsScreen(handler, snapshot(), milestoneIndex, time -> {
            seekTo(time);
            followRecordedPlayer();
        }));
    }

    void followRecordedPlayer() {
        camera.setSelected(ReplaySplits.followMode(camera.getSelected()));
        details.closeInventory();
        lastMode = -1;
        updateCamera();
    }

    void toggleControls() {
        if (!shortcutContext()) return;
        visibility.toggle(System.nanoTime());
        setControlsShown(!visibility.manuallyHidden(), true);
    }

    boolean controlsManuallyHidden() { return visibility.manuallyHidden(); }

    private void setControlsShown(boolean visible, boolean recoverable) {
        if (visible != shown) {
            shown = visible;
            if (visible) overlay.addElements(null, bar);
            else overlay.removeElement(bar);
        }
        boolean showReveal = !visible && recoverable;
        if (showReveal != revealShown) {
            revealShown = showReveal;
            if (showReveal) overlay.addElements(null, reveal);
            else overlay.removeElement(reveal);
        }
    }

    private void seek(int delta) {
        seekTo(ViewerLayout.seekTarget(handler.getReplaySender().currentTimeStamp(), delta, handler.getReplayDuration()));
    }

    void seekFromShortcut(int delta) {
        if (!shortcutContext()) return;
        seek(delta);
    }

    private void seekTo(int time) {
        if (playback.busy()) { PresentationPlayback.clear(); LocalSoundPlayback.reset(); return; }
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
            overlay.removeElement(reveal);
            revealShown = false;
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
            transport.addElements(null, overlay.playPauseButton, back, forward, overlay.speedSlider, timestamp, camera, goTo, splits, players, analysisButton, hide);
            bar.addElements(null, transport, milestones, overlay.timeline);
            shown = !visibility.manuallyHidden();
            revealShown = !shown;
            if (shown) overlay.addElements(null, bar);
            else overlay.addElements(null, reveal);
            overlay.setLayout(new CustomLayout<GuiReplayOverlay>() {
                @Override
                protected void layout(GuiReplayOverlay container, int width, int height) {
                    ViewerLayout layout = new ViewerLayout(width);
                    if (shown) set(bar, 8, layout.barY(height, hudInset()), layout.width, layout.height);
                    else if (revealShown) set(reveal, width - 28, Math.max(0, height - 28 - hudInset()), 20, 20);
                }
            });
            editor.setLabel("Editor");
        }
    }

    void update() {
        cameraSampled = false;
        IndicatorTooltips.beginFrame(overlay.isMouseVisible() && statusVisible());
        playback.update();
        if (playback.busy()) { PresentationPlayback.clear(); LocalSoundPlayback.reset(); return; }
        analysis.update(handler);
        if (!metadataApplied && recordingIndex.recording != null) {
            metadataApplied = true;
            ReplayViewer.raceContext(recordingIndex.recording);
        }
        updateQuickHands();
        faithful.update(recordingIndex, handler.getReplaySender().currentTimeStamp(), !editing && camera.getSelected() == 5);
        if (editing) { PresentationPlayback.clear(); LocalSoundPlayback.reset(); return; }
        int time = handler.getReplaySender().currentTimeStamp();
        int width = client.getWindow().getScaledWidth();
        if (time / 1000 != lastSecond || width != lastWidth || lastQuick != handler.isQuickMode()) {
            lastQuick = handler.isQuickMode();
            lastSecond = time / 1000;
            lastWidth = width;
            timestamp.setText(client.textRenderer.trimToWidth((handler.isQuickMode() ? "Q | " : "") + ViewerLayout.time(time) + " / " + ViewerLayout.time(handler.getReplayDuration()),
                    new ViewerLayout(width).textWidth));
            timestamp.setTooltip(new GuiTooltip().setText(new String[] {ReplayViewer.timeLabel(time, handler.getReplayDuration())}));
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
        boolean inventory = camera.getSelected() == 4 && details.inventoryVisible();
        boolean modal = client.currentScreen != null && !(client.currentScreen instanceof OverlayScreenAccessor);
        if (inventory || modal) visible = false;
        setControlsShown(visible, !inventory && !modal && !client.options.hudHidden);
        if (client.currentScreen == null || client.currentScreen instanceof OverlayScreenAccessor) updateCamera();
        updateSupplemental();
    }

    private void updateSupplemental() {
        ReplaySender sender = handler.getReplaySender();
        RaceRecording recording = recordingIndex.recording;
        PlayerEntity target = FarFollowController.recordedPlayer(client);
        boolean exporting = client.gameRenderer instanceof com.replaymod.render.hooks.EntityRendererHandler.IEntityRenderer
                && ((com.replaymod.render.hooks.EntityRendererHandler.IEntityRenderer) client.gameRenderer).replayModRender_getHandler() != null;
        boolean blocked = cameraDiscontinuities != 0 || !sender.isAsyncMode() || exporting
                || sender instanceof FullReplaySender && ((FullReplaySender) sender).isHurrying();
        int timestamp = sender.currentTimeStamp();
        RaceRecording.Interval interval = recording == null ? null : recording.intervalAt(timestamp);
        blocked |= interval == null || target == null || client.world == null || target.world != client.world
                || recording != null && recording.loading.at(timestamp);
        LocalSoundPlayback.frame(recordingIndex.localSounds, timestamp, interval == null ? -1 : interval.world,
                target == null ? -1 : target.getEntityId(), sender.paused(), blocked);
        if (blocked || camera.getSelected() != 5 || handler.isCameraView() || client.options.perspective != 0
                || client.getCameraEntity() != target || target.hasVehicle() || target.isSleeping()) {
            PresentationPlayback.clear();
            return;
        }
        long quickUpdate = handler.isQuickMode() && sender instanceof zsgrooms.replayviewer.mixin.QuickCameraClockAccessor
                ? ((zsgrooms.replayviewer.mixin.QuickCameraClockAccessor) sender).zsgViewer$cameraUpdateTime() : 0;
        double time = CameraPlaybackClock.time(timestamp, handler.isQuickMode(), sender.paused(),
                sender.getReplaySpeed(), System.currentTimeMillis(), quickUpdate);
        PresentationPlayback.update(recordingIndex.presentation, time, interval.world, target.getEntityId(), interval.start,
                interval.end, true);
    }

    private void updateCamera() {
        CameraEntity entity = handler.getCameraEntity();
        if (entity == null) return;
        int mode = camera.getSelected();
        speeds.remember(managedController);
        if (mode == 2 || mode == 4 || mode == 5) {
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

    void cameraDiscontinuity(boolean begin) {
        cameraSampled = false;
        PresentationPlayback.clear();
        LocalSoundPlayback.reset();
        if (begin) cameraDiscontinuities++;
        else cameraDiscontinuities = Math.max(0, cameraDiscontinuities - 1);
    }

    boolean recordedCameraRotation(net.minecraft.entity.Entity focused, float[] rotation) {
        int mode = camera.getSelected();
        if (editing || playback.busy() || cameraDiscontinuities != 0 || client.options.perspective != 0
                || mode != 2 && mode != 4 && mode != 5 || client.world == null || handler.isCameraView()
                || focused == null || focused.world != client.world || focused != client.getCameraEntity()
                || focused != FarFollowController.recordedPlayer(client) || focused.hasVehicle()
                || ((PlayerEntity) focused).isSleeping()) return false;
        if (client.gameRenderer instanceof com.replaymod.render.hooks.EntityRendererHandler.IEntityRenderer
                && ((com.replaymod.render.hooks.EntityRendererHandler.IEntityRenderer) client.gameRenderer)
                .replayModRender_getHandler() != null) return false;
        ReplaySender sender = handler.getReplaySender();
        if (!sender.isAsyncMode() || sender instanceof FullReplaySender && ((FullReplaySender) sender).isHurrying()) return false;
        CameraTrack track = recordingIndex.camera;
        RaceRecording recording = recordingIndex.recording;
        if (track == null || recording == null) return false;
        if (!cameraSampled || sampledCameraTrack != track || sampledCameraEntity != focused) {
            cameraSampled = true;
            sampledCameraTrack = track;
            sampledCameraEntity = focused;
            cameraSampleAvailable = false;
            long quickUpdate = handler.isQuickMode() && sender instanceof zsgrooms.replayviewer.mixin.QuickCameraClockAccessor
                    ? ((zsgrooms.replayviewer.mixin.QuickCameraClockAccessor) sender).zsgViewer$cameraUpdateTime() : 0;
            double time = CameraPlaybackClock.time(sender.currentTimeStamp(), handler.isQuickMode(), sender.paused(),
                    sender.getReplaySpeed(), System.currentTimeMillis(), quickUpdate);
            RaceRecording.Interval interval = !Double.isFinite(time) || time < 0 || time >= recording.duration
                    ? null : recording.intervalAt((int) time);
            if (interval != null && !recording.loading.at((int) time)) {
                cameraSampleAvailable = track.sample(time, interval.world, focused.getEntityId(), interval.start,
                        interval.end, recordedRotation);
            }
        }
        if (!cameraSampleAvailable) return false;
        rotation[0] = recordedRotation[0];
        rotation[1] = recordedRotation[1];
        return true;
    }

    boolean hideChat() { return faithful.hidden() || !showChat.isChecked(); }
    boolean faithfulActive() { return faithful.active(); }
    boolean faithfulHudHidden() { return faithful.hidden(); }
    void releaseFaithfulOptions() { PresentationPlayback.clear(); faithful.releaseOptions(); }
    boolean inspectChest() {
        if (editing || playback.busy() || camera.getSelected() > 1 || !handler.isCameraView()
                || client.world == null || client.currentScreen != null || !client.mouse.isCursorLocked()
                || handler.getCameraEntity() == null || !overlay.isAllowUserInput()) return false;
        ReplaySender sender = handler.getReplaySender();
        if (!sender.isAsyncMode() || sender instanceof FullReplaySender && ((FullReplaySender) sender).isHurrying()) return false;
        net.minecraft.util.math.Vec3d start = handler.getCameraEntity().getCameraPosVec(1.0F);
        net.minecraft.util.math.Vec3d end = start.add(handler.getCameraEntity().getRotationVec(1.0F).multiply(6.0));
        net.minecraft.util.hit.HitResult hit = client.world.rayTrace(new net.minecraft.world.RayTraceContext(start, end,
                net.minecraft.world.RayTraceContext.ShapeType.OUTLINE, net.minecraft.world.RayTraceContext.FluidHandling.NONE,
                handler.getCameraEntity()));
        if (!(hit instanceof net.minecraft.util.hit.BlockHitResult) || hit.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK) return false;
        net.minecraft.util.math.BlockPos pos = ((net.minecraft.util.hit.BlockHitResult) hit).getBlockPos();
        net.minecraft.block.BlockState block = client.world.getBlockState(pos);
        if (!(block.getBlock() instanceof net.minecraft.block.ChestBlock) || !client.options.keyUse.wasPressed()) return false;
        while (client.options.keyUse.wasPressed()) { }
        int time = sender.currentTimeStamp();
        RaceRecording recording = recordingIndex.recording;
        RaceRecording.Interval interval = recording == null ? null : recording.intervalAt(time);
        ChestHistory<net.minecraft.item.ItemStack> history = milestoneIndex.chests;
        ChestHistory.Frame<net.minecraft.item.ItemStack> frame = history == null || interval == null ? null
                : history.at(interval.world, client.world.getRegistryKey().getValue().toString(), pos.asLong(), time);
        boolean recorded = frame != null;
        if (frame != null) {
            ChestOpening opening = frame.opening;
            net.minecraft.util.math.BlockPos first = net.minecraft.util.math.BlockPos.fromLong(opening.first);
            net.minecraft.util.math.BlockPos second = net.minecraft.util.math.BlockPos.fromLong(opening.second);
            if (!client.world.isChunkLoaded(first) || !client.world.isChunkLoaded(second)
                    || net.minecraft.block.Block.getRawIdFromState(client.world.getBlockState(first)) != opening.stateFirst
                    || net.minecraft.block.Block.getRawIdFromState(client.world.getBlockState(second)) != opening.stateSecond) frame = null;
        }
        int rows = block.get(net.minecraft.block.ChestBlock.CHEST_TYPE) == net.minecraft.block.enums.ChestType.SINGLE ? 3 : 6;
        java.util.List<net.minecraft.item.ItemStack> predicted = !recorded && interval != null && milestoneIndex.chestStatus.isEmpty()
                ? ChestLootPrediction.predict(recording, milestoneIndex.chestLoot, client.world, pos, interval.world, time) : null;
        client.openScreen(new ChestInspectionScreen(handler, frame, milestoneIndex.chestStatus, rows, predicted));
        return true;
    }
    private int hudInset() {
        if (client.options.hudHidden) return 0;
        return camera.getSelected() == 5 ? faithful.reservedHeight() : camera.getSelected() == 4 ? detailHud.reservedHeight() : 0;
    }
    boolean detailedActive() { return (camera.getSelected() == 4 || camera.getSelected() == 5) && statusVisible(); }
    boolean inventoryKey(int key, int scanCode, int action) {
        if (!detailedActive() || camera.isOpened() || !overlay.isAllowUserInput()
                || Screen.hasControlDown() || Screen.hasAltDown() || Screen.hasShiftDown()) return false;
        if (camera.getSelected() == 5) {
            if (!client.options.keyInventory.matchesKey(key, scanCode)) return false;
            if (action == GLFW.GLFW_PRESS) faithful.inspect();
            return true;
        }
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
    int highlightColor(net.minecraft.entity.Entity entity) {
        if (!analysis.highlights.active() || !statusVisible() || entity == null || entity.world != client.world
                || !(entity instanceof net.minecraft.entity.mob.MobEntity) || !entity.isAlive()) return -1;
        return analysis.highlights.selected(net.minecraft.util.registry.Registry.ENTITY_TYPE.getId(entity.getType()))
                ? analysis.highlights.color.rgb : -1;
    }
    void renderTrails(net.minecraft.client.util.math.MatrixStack matrices, net.minecraft.client.render.Camera view) {
        if (statusVisible()) analysis.render(matrices, view);
    }
    void refreshTimestamp() { lastSecond = -1; }
    void close() { PresentationPlayback.clear(); LocalSoundPlayback.reset(); faithful.releaseOptions(); IndicatorTooltips.beginFrame(false); quickHands.clear(); playback.close(); analysis.close(); milestoneIndex.close(); recordingIndex.close(); }

    private void updateQuickHands() {
        if (!handler.isQuickMode() || client.world == null || recordingIndex.hud == null) {
            quickHands.clear();
            return;
        }
        int time = handler.getReplaySender().currentTimeStamp();
        RaceRecording recording = recordingIndex.recording;
        RaceRecording.Interval interval = recording == null ? null : recording.intervalAt(time);
        PlayerEntity target = FarFollowController.recordedPlayer(client);
        PlayerHudTrack.Frame frame = target == null || interval == null || recording.loading.at(time) ? null
                : recordingIndex.hud.at(time, interval.world, target.getEntityId(), interval.start);
        quickHands.update(target, frame, time);
    }

    net.minecraft.item.ItemStack quickHeldItem(PlayerEntity player, net.minecraft.entity.EquipmentSlot slot) {
        if (playback.busy() || client.world == null || player.world != client.world) return null;
        if (camera.getSelected() == 5) return faithful.heldItem(player, slot, handler.getReplaySender().currentTimeStamp());
        if (!handler.isQuickMode()) return null;
        return quickHands.get(player, slot, handler.getReplaySender().currentTimeStamp());
    }

    private boolean statusVisible() {
        return !editing && !client.options.hudHidden && client.world != null
                && (client.currentScreen == null || client.currentScreen instanceof OverlayScreenAccessor);
    }

    private ReplayTimings.Value recordedTiming() {
        RaceRecording recording = recordingIndex.recording;
        return recording == null ? ReplayTimings.Value.UNKNOWN
                : recording.timing.at(handler.getReplaySender().currentTimeStamp());
    }

    private int recordedRenderDistance() {
        RaceRecording recording = recordingIndex.recording;
        int time = handler.getReplaySender().currentTimeStamp();
        RaceRecording.Interval interval = recording == null ? null : recording.intervalAt(time);
        PlayerEntity target = FarFollowController.recordedPlayer(client);
        DetailedTrack.Frame frame = interval == null || target == null || recordingIndex.detail == null ? null
                : recordingIndex.detail.at(time, interval.world, target.getEntityId(), interval.start);
        return frame == null ? -1 : frame.renderDistance;
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
        if (statusVisible() && !visibility.manuallyHidden() && player == FarFollowController.recordedPlayer(client)) {
            boolean loading = recordedLoading();
            boolean paused = recordedTiming().paused;
            int screen = recordedScreen();
            if (loading || paused || screen != ReplayScreens.NONE) PauseIndicator.renderAbove(player, matrices, consumers,
                    loading, paused, handler.getReplaySender().currentTimeStamp(), screen);
        }
    }

    void renderPiglinActivity(net.minecraft.entity.mob.PiglinEntity piglin,
                             net.minecraft.client.util.math.MatrixStack matrices,
                             net.minecraft.client.render.VertexConsumerProvider consumers, int light) {
        if (analysis.barteringLabels && statusVisible() && !visibility.manuallyHidden()
                && !faithfulHudHidden() && piglin.world == client.world) {
            PiglinVisuals.renderAbove(piglin, matrices, consumers, light);
        }
    }

    void renderStatus(net.minecraft.client.util.math.MatrixStack matrices) {
        if (!statusVisible()) return;
        boolean faithfulMode = camera.getSelected() == 5;
        boolean counterVisible = analysis.piglinCounter && !visibility.manuallyHidden();
        int effectsTop = counterVisible ? 52 : 36;
        if (faithfulMode) {
            ViewerLayout layout = new ViewerLayout(client.getWindow().getScaledWidth());
            faithful.render(matrices, layout.recordedScreenInset(client.getWindow().getScaledHeight(), hudInset(), shown), effectsTop);
        } else if (detailedActive()) {
            RaceRecording recording = recordingIndex.recording;
            int time = handler.getReplaySender().currentTimeStamp();
            RaceRecording.Interval interval = recording == null ? null : recording.intervalAt(time);
            PlayerEntity target = FarFollowController.recordedPlayer(client);
            PlayerHudTrack.Frame frame = interval == null || target == null || recordingIndex.hud == null ? null
                    : recordingIndex.hud.at(time, interval.world, target.getEntityId(), interval.start);
            detailHud.render(matrices, frame, details, target, effectsTop);
        }
        if (visibility.manuallyHidden()) return;
        int distance = analysis.renderDistance ? recordedRenderDistance() : -1;
        String distanceText = analysis.renderDistance ? "RD " + (distance < 0 ? "--" : distance + " chunks") : "";
        if (analysis.renderDistance) {
            for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) {
                int left = 12 + column * 4, top = 10 + row * 4;
                net.minecraft.client.gui.DrawableHelper.fill(matrices, left, top, left + 3, top + 3,
                        row == 1 && column == 1 ? 0xFF77DCC8 : 0xFFB8C1C0);
            }
            client.textRenderer.drawWithShadow(matrices, distanceText, 30, 10, 0xE8EEED);
        }
        ReplayTimings.Value value = recordedTiming();
        String rta = "RTA " + ReplayTimings.clock(value.rta);
        String igt = "IGT " + ReplayTimings.clock(value.igt);
        int width = Math.max(client.textRenderer.getWidth(rta), client.textRenderer.getWidth(igt));
        int x = client.getWindow().getScaledWidth() - (analysis.runTimers ? width : 0) - 12;
        if (analysis.runTimers) {
            client.textRenderer.drawWithShadow(matrices, rta, x, 10, 0xFFFFFF);
            client.textRenderer.drawWithShadow(matrices, igt, x, 21, 0xA8D8FF);
        }
        if (analysis.piglinCounter) {
            PiglinVisuals.renderCounter(matrices, client.getWindow().getScaledWidth() - 12, 36,
                    analysis.piglins, analysis.counterAvailable);
        }
        if (faithfulMode) {
            if (analysis.piglinCounter) IndicatorTooltips.piglinCounter(matrices, analysis.piglins, analysis.counterAvailable);
            if (analysis.renderDistance) IndicatorTooltips.renderDistance(matrices, distance, 30 + client.textRenderer.getWidth(distanceText));
            return;
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
        if (analysis.piglinCounter) IndicatorTooltips.piglinCounter(matrices, analysis.piglins, analysis.counterAvailable);
        if (analysis.renderDistance) IndicatorTooltips.renderDistance(matrices, distance, 30 + client.textRenderer.getWidth(distanceText));
    }

    State snapshot() {
        speeds.remember(managedController);
        State state = new State();
        state.speeds.copyFrom(speeds);
        state.mode = camera.getSelected();
        state.orbit.copyFrom(followDistance);
        state.details.copyFrom(details);
        state.piglins = analysis.piglinCounter;
        state.runTimers = analysis.runTimers;
        state.renderDistance = analysis.renderDistance;
        state.barteringLabels = analysis.barteringLabels;
        state.trail = analysis.dragonTrail;
        state.piglinTrail = analysis.piglinTrail;
        state.playerTrail = analysis.playerTrail;
        state.dragonStyle.copyFrom(analysis.dragonStyle);
        state.piglinStyle.copyFrom(analysis.piglinStyle);
        state.playerStyle.copyFrom(analysis.playerStyle);
        state.mobStyle.copyFrom(analysis.mobStyle);
        state.mobTrails.copyFrom(analysis.mobTrails);
        state.highlights.copyFrom(analysis.highlights);
        state.chat = showChat.isChecked();
        state.autoHide = autoHide.isChecked();
        state.controlsHidden = visibility.manuallyHidden();
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
        analysis.runTimers = state.runTimers;
        analysis.renderDistance = state.renderDistance;
        analysis.barteringLabels = state.barteringLabels;
        analysis.dragonTrail = state.trail;
        analysis.piglinTrail = state.piglinTrail;
        analysis.playerTrail = state.playerTrail;
        analysis.dragonStyle.copyFrom(state.dragonStyle);
        analysis.piglinStyle.copyFrom(state.piglinStyle);
        analysis.playerStyle.copyFrom(state.playerStyle);
        analysis.mobStyle.copyFrom(state.mobStyle);
        analysis.mobTrails.copyFrom(state.mobTrails);
        analysis.highlights.copyFrom(state.highlights);
        showChat.setChecked(state.chat);
        autoHide.setChecked(state.autoHide);
        visibility.setManuallyHidden(state.controlsHidden, System.nanoTime());
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
        boolean runTimers = true;
        boolean renderDistance;
        boolean barteringLabels;
        boolean trail;
        boolean piglinTrail;
        boolean playerTrail;
        final TrailStyle dragonStyle = new TrailStyle(TrailStyle.Color.CYAN);
        final TrailStyle piglinStyle = new TrailStyle(TrailStyle.Color.GOLD);
        final TrailStyle playerStyle = new TrailStyle(TrailStyle.Color.GREEN);
        final TrailStyle mobStyle = new TrailStyle(TrailStyle.Color.VIOLET);
        final MobSelection mobTrails = new MobSelection();
        final MobHighlights highlights = new MobHighlights();
        boolean chat;
        boolean autoHide;
        boolean controlsHidden;
        double speed;
        boolean mouseVisible;
    }
}
