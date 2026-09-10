package zsgrooms.modid;

import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/** Bridges the synchronized start gate and integrated-server completion into a local duration. */
public final class EndExitTimeCapture {
    private static final LocalRaceClock CLOCK = new LocalRaceClock();

    private EndExitTimeCapture() {
    }

    public static void arm(InGame game, MinecraftClient client) {
        if (game != null && client != null && client.player != null) {
            Room room = ZsgRooms.getActiveRoom();
            zsgrooms.modid.replay.ReplayPrototype.armRace(game.getRaceId(), room != null && room.getPlayerCount() == 1);
            CLOCK.arm(game.getRaceId(), client.getServer(), client.player.getUuid());
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
        } else if (!awaitingStart && client != null && client.player != null) {
            CLOCK.bindWorld(game.getRaceId(), client.getServer(), client.player.getUuid());
        }
    }

    public static void capture(ServerPlayerEntity player) {
        if (!player.server.isDedicated()) {
            CLOCK.capture(player.server, player.getUuid(), System.nanoTime());
        }
    }

    public static long consume(MinecraftClient client, String raceId) {
        return client == null || client.player == null ? -1L
                : CLOCK.consume(raceId, client.getServer(), client.player.getUuid());
    }
}
