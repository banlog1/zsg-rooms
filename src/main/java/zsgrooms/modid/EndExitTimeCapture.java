package zsgrooms.modid;

import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/** Bridges the synchronized start gate and integrated-server completion into a local duration. */
public final class EndExitTimeCapture {
    private static final LocalRaceClock CLOCK = new LocalRaceClock();
    private static volatile boolean aaMode;
    private static volatile String aaRaceId = "";

    private EndExitTimeCapture() {
    }

    public static void arm(InGame game, MinecraftClient client) {
        if (game != null && client != null && client.player != null) {
            Room room = ZsgRooms.getActiveRoom();
            zsgrooms.modid.replay.ReplayPrototype.armRace(game.getRaceId(), room != null && room.getPlayerCount() == 1);
            CLOCK.arm(game.getRaceId(), client.getServer(), client.player.getUuid());
            aaMode = game.isAaThunderless();
            aaRaceId = aaMode ? game.getRaceId() : "";
        }
    }

    public static void onResumedTick(MinecraftServer server) {
        LocalRaceClock.Start start = CLOCK.onResumedTick(server, System.nanoTime());
        if (start != null) {
            zsgrooms.modid.replay.ReplayPrototype.raceStarted(start.raceId, start.player, start.nanos);
        }
    }

    public static void tickClient(InGame game, MinecraftClient client, boolean awaitingStart) {
        if (game == null || !game.getIsInGame()) {
            CLOCK.clear();
            aaMode = false;
            aaRaceId = "";
        } else if (!awaitingStart && client != null && client.player != null) {
            CLOCK.bindWorld(game.getRaceId(), client.getServer(), client.player.getUuid());
        }
    }

    public static void capture(ServerPlayerEntity player) {
        if (!player.server.isDedicated() && !aaMode) {
            CLOCK.capture(player.server, player.getUuid(), System.nanoTime());
        }
    }

    public static void onAdvancementGranted(ServerPlayerEntity player, net.minecraft.advancement.Advancement advancement) {
        String raceId = aaRaceId;
        if (raceId.isEmpty() || player.server.isDedicated() || !AaThunderless.isRequired(advancement.getId().toString())
                || !player.getAdvancementTracker().getProgress(advancement).isDone()) return;
        if (!AaThunderless.isComplete(player.server.getAdvancementLoader().getAdvancements(),
                value -> value.getId().toString(), value -> player.getAdvancementTracker().getProgress(value).isDone())) return;
        if (!CLOCK.capture(player.server, player.getUuid(), System.nanoTime())) return;
        aaRaceId = "";
        SpeedRunIgtBridge.completeAaRun();
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> ZsgRoomsClient.onAaCompleted(raceId));
    }

    public static long consume(MinecraftClient client, String raceId) {
        return client == null || client.player == null ? -1L
                : CLOCK.consume(raceId, client.getServer(), client.player.getUuid());
    }
}
