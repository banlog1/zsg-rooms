// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.core.events.PreRenderCallback;
import com.replaymod.core.KeyBindingRegistry;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import net.fabricmc.api.ClientModInitializer;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.glfw.GLFW;

/** All ReplayMod coupling lives in this optional, separately built mod. */
public final class ReplayViewer implements ClientModInitializer {
    private static ReplayViewer instance;
    private ReplayHandler current;
    private ViewerControls controls;
    private RaceReplayLibrary library;
    private static boolean switching;
    private static java.util.UUID recorder;
    private static RaceRecording recording;

    @Override
    public void onInitializeClient() {
        instance = this;
        new EventRegistrations().on(PreRenderCallback.EVENT, () -> {
            if (switching || ReplayModReplay.instance == null) return;
            ReplayHandler handler = ReplayModReplay.instance.getReplayHandler();
            if (handler != current) {
                recorder = null;
                recording = null;
                library = new RaceReplayLibrary(net.minecraft.client.MinecraftClient.getInstance().runDirectory.toPath());
                attach(handler);
            }
            if (controls != null) controls.update();
        }).register();
    }

    private void attach(ReplayHandler handler) {
        if (controls != null) controls.close();
        current = handler;
        controls = handler == null ? null : new ViewerControls(handler);
        if (handler != null) LogManager.getLogger("ZSG Replay Viewer").info("Compact playback controls attached");
    }

    static java.util.UUID recordedUuid() { return recorder; }
    static RaceReplayLibrary library() { return instance.library; }

    static void raceContext(RaceRecording value) {
        recording = value;
        recorder = value.recorder;
        if (instance != null && instance.controls != null) instance.controls.refreshTimestamp();
    }

    static String timeLabel(int time, int duration) {
        RaceRecording.Race race = recording == null ? null : recording.raceAt(time);
        String prefix = race == null ? "" : "Race " + ViewerLayout.time((int) Math.max(0, time - race.start / 1000000L)) + " | ";
        return prefix + ViewerLayout.time(time) + " / " + ViewerLayout.time(duration);
    }

    static void switchRecording(ReplayHandler original, RaceRecording source, int originalTime,
                                RaceRecording target, int time, ViewerControls.State state) {
        if (switching || instance == null || ReplayModReplay.instance.getReplayHandler() != original) return;
        switching = true;
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        try {
            instance.attach(null);
            original.endReplay();
            open(target, time, state);
        } catch (Exception error) {
            LogManager.getLogger("ZSG Replay Viewer").warn("Replay switch failed", error);
            boolean restored = false;
            try {
                stopFailedReplay();
                open(source, originalTime, state);
                restored = true;
            } catch (Exception recovery) {
                LogManager.getLogger("ZSG Replay Viewer").warn("Could not restore previous replay", recovery);
                stopFailedReplay();
                instance.attach(null);
            }
            if (restored) {
                client.inGameHud.setOverlayMessage(new net.minecraft.text.LiteralText("Switch failed; previous recording restored"), false);
            } else {
                client.openScreen(new net.minecraft.client.gui.screen.DisconnectedScreen(
                        new net.minecraft.client.gui.screen.TitleScreen(), "Replay switch failed",
                        new net.minecraft.text.LiteralText("Could not reopen the recording. Your replay files have not been deleted.")));
            }
        } finally { switching = false; }
    }

    private static void stopFailedReplay() {
        ReplayHandler handler = ReplayModReplay.instance.getReplayHandler();
        if (handler == null) return;
        try { handler.endReplay(); }
        catch (Exception error) {
            LogManager.getLogger("ZSG Replay Viewer").warn("Replay cleanup failed", error);
            try { handler.getReplayFile().close(); }
            catch (java.io.IOException closeError) { error.addSuppressed(closeError); }
        } finally { ReplayModReplay.instance.forcefullyStopReplay(); }
    }

    private static void open(RaceRecording recording, int time, ViewerControls.State state) throws java.io.IOException {
        com.replaymod.replaystudio.replay.ReplayFile archive = com.replaymod.core.ReplayMod.instance.files.open(recording.path);
        ReplayHandler handler;
        try {
            // Start synchronously so the initial async sender cannot race the destination seek.
            handler = ReplayModReplay.instance.startReplay(archive, true, false);
            if (handler == null) throw new java.io.IOException("Open this replay individually to check mod compatibility first");
        } catch (java.io.IOException | RuntimeException error) {
            try { archive.close(); } catch (java.io.IOException closeError) { error.addSuppressed(closeError); }
            throw error;
        }
        handler.getReplaySender().setReplaySpeed(0);
        handler.getReplaySender().sendPacketsTill(time);
        // ReplayMod leaves this initial async-only rewind flag set after a first sync seek.
        ((zsgrooms.replayviewer.mixin.FullReplaySenderAccessor) handler.getReplaySender()).zsgViewer$setStartFromBeginning(false);
        raceContext(recording);
        instance.attach(handler);
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        client.openScreen(null);
        instance.controls.restore(state);
        net.minecraft.entity.player.PlayerEntity player = client.world == null ? null : client.world.getPlayerByUuid(recorder);
        if (player == null || handler.getCameraEntity() == null) throw new java.io.IOException("Recorded player unavailable after seeking");
        if (state.mode <= 1) {
            handler.spectateCamera();
            handler.getCameraEntity().setCameraPosition(player.getX(), player.getY() + 2, player.getZ());
            handler.getCameraEntity().setCameraRotation(player.yaw, player.pitch, 0);
        }
        handler.getReplaySender().setReplaySpeed(state.speed);
        handler.getReplaySender().setAsyncMode(true);
    }

    public static void registerShortcuts(KeyBindingRegistry keys) {
        // Invoked during ReplayMod initialization, before Minecraft loads key preferences,
        // regardless of which mod's Fabric entrypoint is called first.
        keys.registerKeyBinding("replaymod.input.zsg_skip_back", GLFW.GLFW_KEY_3, () -> {
            if (instance != null) instance.seek(-5000);
        }, true);
        keys.registerKeyBinding("replaymod.input.zsg_skip_forward", GLFW.GLFW_KEY_4, () -> {
            if (instance != null) instance.seek(5000);
        }, true);
    }

    private void seek(int delta) {
        if (controls != null && current != null && ReplayModReplay.instance.getReplayHandler() == current) {
            controls.seekFromShortcut(delta);
        }
    }

    public static boolean shouldHideChat() {
        return instance != null && instance.controls != null
                && ReplayModReplay.instance.getReplayHandler() == instance.current && instance.controls.hideChat();
    }

    public static void renderStatus(net.minecraft.client.util.math.MatrixStack matrices) {
        if (!switching && instance != null && instance.controls != null
                && ReplayModReplay.instance.getReplayHandler() == instance.current) instance.controls.renderStatus(matrices);
    }

    public static void renderPlayerPause(net.minecraft.entity.player.PlayerEntity player,
                                         net.minecraft.client.util.math.MatrixStack matrices,
                                         net.minecraft.client.render.VertexConsumerProvider consumers) {
        if (!switching && instance != null && instance.controls != null
                && ReplayModReplay.instance.getReplayHandler() == instance.current) {
            instance.controls.renderPlayerPause(player, matrices, consumers);
        }
    }
}
