package zsgrooms.modid.net;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotUtils;
import org.lwjgl.glfw.GLFW;
import zsgrooms.modid.*;
import zsgrooms.modid.replay.ReplayPrototype;
import zsgrooms.modid.ui.RaceStandingsScreen;
import zsgrooms.modid.ui.RoomLobbyScreen;
import zsgrooms.modid.ui.ZsgInGameActions;
import java.util.Arrays;

/** Isolated actual Atum transitions plus a simulated second runner, never packaged. */
public final class RaceSequenceSmoke {
    private static final String ROOM = "sequence-smoke";
    private int stage, ticks;
    private final long started = System.nanoTime();
    private boolean stopped;
    private String local;
    private Object recording, world;
    private long before;
    private long lostReadyAt;
    private java.nio.file.Path file;

    public void initialize() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient client) {
        if (stopped) return;
        try {
            require(System.nanoTime() - started < 300_000_000_000L, "Timed out at stage " + stage);
            if (client.getOverlay() != null || ++ticks < 20) return;
            ticks = 0;
            InGame game = ZsgRooms.getGame(ROOM);
            switch (stage) {
                case 0:
                    client.options.pauseOnLostFocus = false;
                    client.options.maxFps = 60;
                    client.options.viewDistance = 2;
                    client.options.guiScale = 2;
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1280, 720);
                    require(ReplayPrototype.configure(true, "", false), "Recorder setup failed");
                    stage++;
                    break;
                case 1:
                    if (!ReplayPrototype.isLibraryReady()) return;
                    local = client.getSession().getUsername();
                    String seed = "12345|structure:manual";
                    Room room = new Room(ROOM, seed, new Player(local, true, true), 4);
                    room.addPlayer(new Player("OtherRunner", true, false));
                    game = new InGame(seed, ROOM, InGame.SeedType.FIXED, false);
                    game.targetStructure = "manual:12345";
                    game.setFinishGoal(3);
                    game.setFinisherLimit(2);
                    game.startGame();
                    ZsgRooms.applyRoomSnapshot(RoomSnapshot.capture(room, game).toJson());
                    game = ZsgRooms.getGame(ROOM);
                    game.setSequence(new RaceSequence(game.getRaceId(), Arrays.asList(seed,
                            "67890|structure:manual", "98765|structure:manual"), room.getPlayerNames(), game.getFinisherLimit()));
                    require(RoomSocketTransport.host(ROOM, local, 0), "Local transport failed");
                    ZsgRoomsClient.beginSynchronizedStart(ROOM, seed);
                    require(ZsgSeedBridge.launchSeedWithAtum(seed), "Initial Atum launch failed");
                    stage++;
                    break;
                case 2:
                    if (lostReadyAt == 0) {
                        if (!game.getReadyPlayers().contains(local)) return;
                        game.removeReadyPlayer(local);
                        lostReadyAt = System.nanoTime();
                        ZsgRooms.LOGGER.info("[RaceSequenceSmoke] Simulating lost initial readiness acknowledgement");
                        return;
                    }
                    if (!game.getReadyPlayers().contains(local)) {
                        require(System.nanoTime() - lostReadyAt < 8_000_000_000L, "Lost world_ready was never retried");
                        return;
                    }
                    if (!game.isSynchronizedStartReleased()) ZsgRooms.applyRoomAction("world_ready", ROOM, "OtherRunner", game.seed);
                    if (!playable(client, game, 12345) || EndExitTimeCapture.elapsed(game.getRaceId()) < 0
                            || !ReplayPrototype.isRecording()) return;
                    recording = recordingValue("recordingIdentity");
                    file = (java.nio.file.Path) recordingValue("recordingFile");
                    before = EndExitTimeCapture.elapsed(game.getRaceId());
                    finish(client);
                    stage++;
                    break;
                case 3:
                    if (!playable(client, game, 67890)) return;
                    require(game.getSequence().runner(local).stage == 1, "Did not advance independently");
                    require(game.getSequence().runner("OtherRunner").stage == 0, "Moved the other runner");
                    require(recording == recordingValue("recordingIdentity"), "Advancing split/discarded recording");
                    require(EndExitTimeCapture.elapsed(game.getRaceId()) > before, "Loading reset the clock");
                    before = EndExitTimeCapture.elapsed(game.getRaceId());
                    world = client.world;
                    ZsgInGameActions.resetCurrentRun(client);
                    stage++;
                    break;
                case 4:
                    if (client.world == world || !playable(client, game, 67890)) return;
                    require(game.getSequence().runner(local).stage == 1, "Reset advanced stage");
                    require(recording == recordingValue("recordingIdentity"), "Reset split recording");
                    require(EndExitTimeCapture.elapsed(game.getRaceId()) > before, "Reset reset the clock");
                    RaceSequenceClient.report(game, "skip", EndExitTimeCapture.elapsed(game.getRaceId()));
                    stage++;
                    break;
                case 5:
                    if (!playable(client, game, 98765)) return;
                    require(game.getSequence().runner(local).skipped == 1, "Skip penalty missing");
                    require(recording == recordingValue("recordingIdentity"), "Skip split recording");
                    finish(client);
                    stage++;
                    break;
                case 6:
                    if (!(client.currentScreen instanceof RoomLobbyScreen)) return;
                    require(game.getIsInGame(), "First finish stopped the other runner");
                    require(game.getSequence().runner(local).finished, "Local result missing");
                    require(game.getSequence().active("OtherRunner"), "Other runner ended");
                    require(!ReplayPrototype.isRecording(), "Recording did not stop on finish");
                    if (!java.nio.file.Files.exists(file)) return;
                    try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file.toFile())) {
                        require(zip.getEntry("recording.tmcpr") != null, "Missing saved packet stream");
                        try (java.io.Reader reader = new java.io.InputStreamReader(
                                zip.getInputStream(zip.getEntry("zsg-rooms/races.json")), java.nio.charset.StandardCharsets.UTF_8)) {
                            com.google.gson.JsonObject metadata = new com.google.gson.JsonParser().parse(reader).getAsJsonObject();
                            require(metadata.get("complete").getAsBoolean() && !metadata.get("truncated").getAsBoolean(), "Replay incomplete");
                            require(metadata.getAsJsonArray("races").size() == 1, "Sequence split into multiple race identities");
                            require(metadata.getAsJsonArray("intervals").size() == 4, "Replay missing a seed or reset world");
                            require(game.getRaceId().equals(metadata.getAsJsonArray("races").get(0).getAsJsonObject()
                                    .get("raceId").getAsString()), "Replay race identity changed");
                        }
                    }
                    for (int i = 0; i < 3; i++) RoomSequenceHost.handle(ROOM, "OtherRunner", "complete_run",
                            RaceSequence.report(game.getRaceId(), i, (i + 1) * 60_000_000_000L, "finish"), () -> {});
                    require(!game.getIsInGame() && game.getSequence().place(local) == 2, "Final adjusted standings wrong");
                    client.openScreen(new RaceStandingsScreen(client.currentScreen, ROOM));
                    stage++;
                    break;
                case 7:
                    screenshot(client, "sequence-standings-desktop.png");
                    GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
                    stage++;
                    break;
                case 8:
                    screenshot(client, "sequence-standings-small.png");
                    ZsgRooms.LOGGER.info("[FilterPickerSmoke] PASS: lost-readiness recovery, shared initial start, independent advance, reset, skip, cumulative clock, complete four-world replay with one race ID, continued opponent, final standings. Replay: {}", file);
                    stopped = true;
                    RoomSocketTransport.stop();
                    client.scheduleStop();
                    break;
                default: throw new IllegalStateException("Unexpected stage");
            }
        } catch (Throwable error) {
            stopped = true;
            ZsgRooms.LOGGER.error("[RaceSequenceSmoke] FAILED", error);
            RoomSocketTransport.stop();
            client.scheduleStop();
        }
    }

    private boolean playable(MinecraftClient client, InGame game, long seed) {
        return client.player != null && client.world != null && client.currentScreen == null && client.getServer() != null
                && client.getServer().getOverworld().getSeed() == seed && RaceSequenceClient.canPlay(game);
    }
    private void finish(MinecraftClient client) {
        client.getServer().execute(() -> {
            EndExitTimeCapture.capture(client.getServer().getPlayerManager().getPlayer(client.player.getUuid()));
            client.execute(ZsgRoomsClient::onEndExitPortalEntered);
        });
    }
    private static Object recordingValue(String name) throws Exception {
        java.lang.reflect.Method method = ReplayPrototype.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(null);
    }
    private static void screenshot(MinecraftClient client, String name) {
        ScreenshotUtils.saveScreenshot(client.runDirectory, name, client.getWindow().getFramebufferWidth(),
                client.getWindow().getFramebufferHeight(), client.getFramebuffer(), text -> ZsgRooms.LOGGER.info(text.getString()));
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
