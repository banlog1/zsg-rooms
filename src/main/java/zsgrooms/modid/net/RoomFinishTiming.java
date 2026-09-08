package zsgrooms.modid.net;

import zsgrooms.modid.InGame;
import zsgrooms.modid.Room;
import zsgrooms.modid.ZsgRooms;

import java.util.function.BiConsumer;

final class RoomFinishTiming {
    private final RaceFinishArbiter arbiter = new RaceFinishArbiter();
    private String raceId = "";

    void tick(String roomName, BiConsumer<String, String> finish) {
        InGame game = currentRace(roomName);
        if (game == null) {
            return;
        }
        long now = RaceFinishArbiter.now();
        RaceFinishArbiter.Decision decision = arbiter.poll(now);
        if (decision != null) {
            finish.accept(decision.winner, decision.reason);
        }
    }

    void submit(String roomName, String player, String value) {
        submit(roomName, player, value, RaceFinishArbiter.now());
    }

    void submit(String roomName, String player, String value, long received) {
        InGame game = currentRace(roomName);
        Room room = ZsgRooms.getRoom(roomName);
        if (game != null && game.isSynchronizedStartReleased() && room != null && room.getPlayer(player) != null) {
            arbiter.submit(player, value, received, hasOtherDragonFinisher(game, room, player));
        }
    }

    static boolean hasOtherDragonFinisher(InGame game, Room room, String finisher) {
        java.util.Map<String, Integer> progress = game.getPlayerProgress();
        for (String player : room.getPlayerNames()) {
            Integer stage = progress.get(player);
            if (!player.equals(finisher) && stage != null && stage >= 8) {
                return true;
            }
        }
        return false;
    }

    boolean hasPendingFinish() {
        return arbiter.hasPendingFinish();
    }

    private InGame currentRace(String roomName) {
        InGame game = ZsgRooms.getGame(roomName);
        if (game == null || !game.getIsInGame()) {
            return null;
        }
        if (!raceId.equals(game.getRaceId())) {
            raceId = game.getRaceId();
            arbiter.beginRace(raceId);
        }
        return game;
    }
}
