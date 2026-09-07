package zsgrooms.modid.net;

import zsgrooms.modid.InGame;
import zsgrooms.modid.Room;
import zsgrooms.modid.ZsgRooms;

import java.util.function.BiConsumer;

final class RoomFinishTiming {
    private final RaceFinishArbiter arbiter = new RaceFinishArbiter();
    private String raceId = "";
    private volatile long nextProbe;
    private int probesSent;

    void tick(String roomName, BiConsumer<String, String> send, BiConsumer<String, String> finish) {
        InGame game = currentRace(roomName);
        if (game == null) {
            return;
        }
        long now = RaceFinishArbiter.now();
        if (now >= nextProbe) {
            nextProbe = now + (++probesSent < 3 ? 1000L : 10000L);
            send.accept("finish_clock_probe", arbiter.probe(now));
        }
        RaceFinishArbiter.Decision decision = arbiter.poll(now);
        if (decision != null) {
            finish.accept(decision.winner, decision.reason);
        }
    }

    void submit(String roomName, String player, String value, boolean host) {
        submit(roomName, player, value, host, RaceFinishArbiter.now());
    }

    void submit(String roomName, String player, String value, boolean host, long received) {
        InGame game = currentRace(roomName);
        Room room = ZsgRooms.getRoom(roomName);
        if (game != null && game.isSynchronizedStartReleased() && room != null && room.getPlayer(player) != null) {
            arbiter.submit(player, value, host, received, hasOtherDragonFinisher(game, room, player));
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

    void receiveReply(String player, String value, long received) {
        arbiter.receiveReply(player, value, received);
    }

    boolean hasPendingFinish() {
        return arbiter.hasPendingFinish();
    }

    void reconnect() {
        arbiter.resetClocks();
        nextProbe = RaceFinishArbiter.now();
    }

    void forgetPlayer(String player) {
        arbiter.forgetPlayer(player);
        nextProbe = RaceFinishArbiter.now();
    }

    private InGame currentRace(String roomName) {
        InGame game = ZsgRooms.getGame(roomName);
        if (game == null || !game.getIsInGame()) {
            return null;
        }
        if (!raceId.equals(game.getRaceId())) {
            raceId = game.getRaceId();
            long now = RaceFinishArbiter.now();
            arbiter.beginRace(raceId, now);
            nextProbe = now;
            probesSent = 0;
        }
        return game;
    }
}
