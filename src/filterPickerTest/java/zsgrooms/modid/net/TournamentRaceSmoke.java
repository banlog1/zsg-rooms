package zsgrooms.modid.net;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import zsgrooms.modid.*;
import zsgrooms.modid.ui.RoomLobbyScreen;
import java.util.*;

/** Real host transport, seed preparation, Atum launches and completion, with simulated opponents. */
public final class TournamentRaceSmoke {
    private static final String ROOM = "tournament-race-smoke";
    private int stage, ticks;
    private boolean done;
    private String local, previousRace;
    private long nextWorldReadyAt;
    private final long started = System.nanoTime();
    public void initialize() { ClientTickEvents.END_CLIENT_TICK.register(this::tick); }

    private void tick(MinecraftClient client) {
        if (done) return;
        try {
            require(System.nanoTime() - started < 180_000_000_000L, "Timeout at stage " + stage + ": " + HostSeedPrefetchManager.getInstance().getStatus());
            if (client.getOverlay() != null || ++ticks < 20) return;
            ticks = 0;
            InGame game = ZsgRooms.getGame(ROOM);
            switch (stage) {
                case 0:
                    client.options.pauseOnLostFocus = false; client.options.maxFps = 60; client.options.viewDistance = 2;
                    local = ZsgRoomsClient.localPlayerName(client);
                    Room room = new Room(ROOM, "12345|structure:manual", new Player(local, true, true), 4);
                    for (String name : Arrays.asList("A", "B", "C")) {
                        Player player = new Player(name, true, false);
                        player.sequenceVersion = RaceSequence.VERSION; player.tournamentVersion = 1;
                        room.addPlayer(player);
                    }
                    game = new InGame(room.seed, ROOM, InGame.SeedType.FIXED, false);
                    game.targetStructure = "manual:12345";
                    TournamentSettings settings = new TournamentSettings();
                    settings.enabled = true; settings.bracket = true;
                    settings.order = Arrays.asList("A", "B", local, "C");
                    game.setTournamentSettings(settings);
                    require(ZsgRooms.applyRoomSnapshot(RoomSnapshot.capture(room, game).toJson()), "Initial snapshot");
                    // Public snapshots intentionally omit the host's unlaunched manual seed.
                    ZsgRooms.getGame(ROOM).targetStructure = "manual:12345";
                    require(RoomSocketTransport.host(ROOM, local, 0), "Local transport failed");
                    client.openScreen(new RoomLobbyScreen(new TitleScreen(), ROOM));
                    ZsgRoomsClient.sendRoomAction("start", ROOM, "");
                    ZsgRooms.LOGGER.info("[TournamentRaceSmoke] Start requested: {}", ZsgRooms.getGame(ROOM).targetStructure);
                    stage++;
                    break;
                case 1:
                    require(game != null, "Room disappeared while preparing first pair");
                    if (!game.getIsInGame() || game.getSequence() == null) return;
                    require(client.world == null && client.currentScreen instanceof RoomLobbyScreen, "Waiting host loaded a world");
                    require(game.getSequence().runner(local) == null, "Host included in first pair");
                    require(ZsgRooms.markPlayerWorldReady(ROOM, "A", game.seed), "A not ready");
                    require(ZsgRooms.markPlayerWorldReady(ROOM, "B", game.seed), "B not ready");
                    require(ZsgRooms.releaseSynchronizedStart(ROOM, game.seed), "Waiting-host start release");
                    finishRemote(game, "A");
                    require(!game.getIsInGame(), "First race did not close");
                    require(game.getTournament().participants().contains(local), "Next pair missing host");
                    previousRace = game.getRaceId();
                    ZsgRoomsClient.sendRoomAction("start", ROOM, "");
                    stage++;
                    break;
                case 2:
                    if (game.getRaceId().equals(previousRace)) return;
                    ZsgRooms.applyRoomAction("world_ready", ROOM, "C", game.seed);
                    if (!playable(client, game)) return;
                    require(game.getSequence().runner("A") == null, "Previous winner loaded next pair");
                    client.getServer().execute(() -> {
                        EndExitTimeCapture.capture(client.getServer().getPlayerManager().getPlayer(client.player.getUuid()));
                        client.execute(ZsgRoomsClient::onEndExitPortalEntered);
                    });
                    stage++;
                    break;
                case 3:
                    if (!game.getSequence().runner(local).finished || !resultShown()) return;
                    require(client.world != null, "Missed the automatic-return countdown");
                    require(!game.getIsInGame(), "Host race still live after completion");
                    require(game.getTournament().participants().containsAll(Arrays.asList("A", local)), "Final pair wrong");
                    previousRace = game.getRaceId();
                    ZsgRooms.LOGGER.info("[TournamentRaceSmoke] Starting final while previous result return is pending");
                    ZsgRoomsClient.sendRoomAction("start", ROOM, "");
                    stage++;
                    break;
                case 4:
                    if (game.getRaceId().equals(previousRace)) return;
                    ZsgRooms.applyRoomAction("world_ready", ROOM, "A", game.seed);
                    if (!playable(client, game)) return;
                    if (nextWorldReadyAt == 0) nextWorldReadyAt = System.nanoTime();
                    if (System.nanoTime() - nextWorldReadyAt < 6_000_000_000L) return;
                    require(game.getSequence().active(local), "Old result returned/withdrew runner from final");
                    finishRemote(game, "A");
                    stage++;
                    break;
                case 5:
                    if (!(client.currentScreen instanceof RoomLobbyScreen)) return;
                    require(game.getTournament().complete && "A".equals(game.getTournament().champion), "Final result wrong");
                    require(game.getSequence().runner(local).stopped, "Losing runner not stopped");
                    require(game.getTournament().valid(), "Final snapshot invalid");
                    done = true; RoomSocketTransport.stop();
                    ZsgRooms.LOGGER.info("[FilterPickerSmoke] PASS: real tournament host transport and seed preparation, waiting host, two Atum race worlds, synchronized starts, immediate next match before old result return, six seconds uninterrupted play, final return and champion");
                    client.scheduleStop();
                    break;
            }
        } catch (Throwable error) {
            done = true; ZsgRooms.LOGGER.error("[TournamentRaceSmoke] FAILED", error);
            RoomSocketTransport.stop(); client.scheduleStop();
        }
    }
    private static void finishRemote(InGame game, String name) {
        RoomSequenceHost.handle(ROOM, name, "complete_run", RaceSequence.report(game.getRaceId(), 0, 1_000_000_000L, "finish"), () -> {});
    }
    private static boolean resultShown() throws Exception {
        java.lang.reflect.Field field = RaceSequenceClient.class.getDeclaredField("resultShown");
        field.setAccessible(true);
        return field.getBoolean(null);
    }
    private static boolean playable(MinecraftClient client, InGame game) {
        return client.player != null && client.world != null && client.currentScreen == null && game.isSynchronizedStartReleased()
                && EndExitTimeCapture.elapsed(game.getRaceId()) >= 0 && RaceSequenceClient.canPlay(game);
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
