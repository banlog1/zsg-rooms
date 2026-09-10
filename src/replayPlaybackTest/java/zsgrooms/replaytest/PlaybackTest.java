package zsgrooms.replaytest;

import net.fabricmc.api.ClientModInitializer;
import com.replaymod.core.events.PreRenderCallback;
import com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.util.ScreenshotUtils;
import zsgrooms.modid.ZsgRooms;

import java.io.File;
import java.lang.reflect.Method;

/** Drives the separately installed viewer in a development instance, never shipped with ZSG. */
public final class PlaybackTest implements ClientModInitializer {
    private int menuTicks;
    private long started;
    private Object viewer;
    private Object handler;
    private Object sender;
    private Method time;
    private String lastState = "";
    private boolean screenshot;
    private boolean done;
    private boolean driving;
    private boolean jumped;
    private long jumpTime;
    private final ViewerSmokeChecks viewerChecks = new ViewerSmokeChecks();
    private final RaceSwitchSmoke raceChecks = new RaceSwitchSmoke();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> { if (viewer == null) tick(client); });
        // ReplayMod can stop client ticks while paused or seeking; the driver must still progress.
        new EventRegistrations().on(PreRenderCallback.EVENT, () -> {
            if (viewer != null) tick(MinecraftClient.getInstance());
        }).register();
    }

    private void tick(MinecraftClient client) {
        // ReplayMod seeking can run client ticks before doJump returns.
        if (done || driving) return;
        driving = true;
        try {
            if (viewer == null) {
                if (client.getOverlay() != null || ++menuTicks < 40) return;
                client.options.pauseOnLostFocus = false;
                client.options.maxFps = 60;
                if (Boolean.getBoolean("zsgrooms.replayViewerSmoke")) ShortcutSmokeChecks.outsideReplay(client);
                Class<?> api = Class.forName("com.replaymod.replay.ReplayModReplay");
                viewer = api.getField("instance").get(null);
                File replay = new File(System.getProperty("zsgrooms.replayPlaybackFile"));
                if (Boolean.getBoolean("zsgrooms.replayRaceSmoke")) replay = raceChecks.prepare(client, replay);
                api.getMethod("startReplay", File.class).invoke(viewer, replay);
                handler = api.getMethod("getReplayHandler").invoke(viewer);
                sender = handler.getClass().getMethod("getReplaySender").invoke(handler);
                time = sender.getClass().getMethod("currentTimeStamp");
                started = System.nanoTime();
                return;
            }
            if (Boolean.getBoolean("zsgrooms.replayRaceSmoke")) {
                if (System.nanoTime() - started > 180000000000L) throw new IllegalStateException("Race switching test timed out");
                if (raceChecks.tick(client)) {
                    done = true;
                    com.replaymod.replay.ReplayModReplay.instance.getReplayHandler().endReplay();
                    client.scheduleStop();
                }
                return;
            }
            int timestamp = (Integer) time.invoke(sender);
            if (Boolean.getBoolean("zsgrooms.replayViewerSmoke") && timestamp >= 2500) viewerChecks.tick(handler, client);
            String state = (client.currentScreen == null ? "none" : client.currentScreen.getClass().getSimpleName())
                    + "; world=" + (client.world != null) + "; player=" + (client.player != null);
            if (!state.equals(lastState)) {
                lastState = state;
                ZsgRooms.LOGGER.info("[ReplayPlaybackTest] {}ms: {}", timestamp, state);
            }
            if (timestamp >= 2500 && !screenshot) {
                screenshot = true;
                ScreenshotUtils.saveScreenshot(client.runDirectory, "initial-playback.png", client.getWindow().getFramebufferWidth(),
                        client.getWindow().getFramebufferHeight(), client.getFramebuffer(), message -> {});
                ZsgRooms.LOGGER.info("[ReplayPlaybackTest] Initial terrain screen stuck={}", client.currentScreen instanceof DownloadingTerrainScreen);
            }
            boolean reachedEnd = Boolean.getBoolean("zsgrooms.replayViewerSmoke") ? viewerChecks.complete()
                    : timestamp >= Integer.getInteger("zsgrooms.replayPlaybackEnd", 118000);
            if (!jumped && reachedEnd) {
                jumped = true;
                ZsgRooms.LOGGER.info("[ReplayPlaybackTest] Seeking back to 1500ms");
                handler.getClass().getMethod("doJump", int.class, boolean.class).invoke(handler, 1500, false);
                jumpTime = System.nanoTime();
                ZsgRooms.LOGGER.info("[ReplayPlaybackTest] Seek returned; terrain screen={}",
                        client.currentScreen instanceof DownloadingTerrainScreen);
                // Seeking pauses ReplayMod's timer, which also stops this tick callback.
                sender.getClass().getMethod("setReplaySpeed", double.class).invoke(sender, 1.0D);
            } else if (jumped && System.nanoTime() - jumpTime > 5000000000L
                    || System.nanoTime() - started > 240000000000L) {
                done = true;
                ZsgRooms.LOGGER.info("[ReplayPlaybackTest] Finished at {}ms; terrain screen={}", timestamp,
                        client.currentScreen instanceof DownloadingTerrainScreen);
                handler.getClass().getMethod("endReplay").invoke(handler);
                if (Boolean.getBoolean("zsgrooms.replayViewerSmoke")) ShortcutSmokeChecks.outsideReplay(client);
                if (Boolean.getBoolean("zsgrooms.replayViewerSmoke") && (Boolean) Class.forName("zsgrooms.replayviewer.ReplayViewer")
                        .getMethod("shouldHideChat").invoke(null)) {
                    throw new IllegalStateException("Chat remained hidden after closing replay");
                }
                client.scheduleStop();
            }
        } catch (Exception e) {
            done = true;
            ZsgRooms.LOGGER.error("[ReplayPlaybackTest] Failed", e);
            client.scheduleStop();
        } finally {
            driving = false;
        }
    }
}
