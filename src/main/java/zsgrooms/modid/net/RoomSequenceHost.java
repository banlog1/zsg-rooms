package zsgrooms.modid.net;

import zsgrooms.modid.*;

/** Shared authoritative action handling for relay and LAN hosts. */
final class RoomSequenceHost {
    static boolean handle(String roomName, String player, String type, String value, Runnable publish) {
        InGame game = ZsgRooms.getGame(roomName);
        RaceSequence race = game == null ? null : game.getSequence();
        if (game != null && game.getTournament() != null && "seed_change".equals(type)) {
            ZsgRooms.shareChat(roomName, "Tournament seeds are fixed for this race. Reset or forfeit instead.");
            publish.run();
            return true;
        }
        if (race == null) return false;
        if ("start".equals(type) || "filter".equals(type) || "seed_change".equals(type)) {
            if ("seed_change".equals(type) && race.goal == 1
                    && race.standings().stream().noneMatch(RaceSequence.Runner::done)) return false;
            if (game.getIsInGame()) {
                ZsgRooms.shareChat(roomName, "Race still running. Finish or leave the race before starting another.");
                publish.run();
                return true;
            }
            return false;
        }
        if ("complete_run".equals(type) || "forfeit".equals(type)) {
            if (game.getIsInGame() && race.submit(player, value, game.isSynchronizedStartReleased())) {
                RaceSequence.Runner runner = race.runner(player);
                ZsgRooms.resetPlayerRun(roomName, player);
                ZsgRooms.shareChat(roomName, player + ": " + (runner.dnf ? "DNF" : runner.finished
                        ? "finished in " + RaceSequence.time(runner.adjustedNanos())
                        : "seed " + (runner.stage + 1) + "/" + race.goal)
                        + (runner.skipped == 0 ? "" : " (+" + RaceSequence.time(runner.penaltyNanos()) + " penalty)"));
                settle(game, race);
            }
            publish.run();
            return true;
        }
        if ("leave_room".equals(type)) {
            race.withdraw(player);
            ZsgRooms.removeRoomPlayer(roomName, player);
            settle(game, race);
            publish.run();
            return true;
        }
        if (("advancement".equals(type) || "reset_run".equals(type) || "progress".equals(type))
                && !race.active(player)) return true;
        return false;
    }

    static void settle(InGame game, RaceSequence race) {
        Tournament tournament = game.getTournament();
        if (tournament != null && tournament.settings.bracket) race.awardForfeitWin();
        else race.awardSingleSeedForfeitWin();
        boolean closeContender = race.standings().stream().anyMatch(r -> !r.done() && r.stage == race.goal - 1
                && game.getPlayerProgress().getOrDefault(r.name, 0) >= 8);
        long elapsed = EndExitTimeCapture.elapsed(game.getRaceId());
        if (tournament != null && elapsed < 0) elapsed = game.hostRaceElapsed();
        if (race.closeAtLimit(elapsed, System.nanoTime(), closeContender)) {
            game.setInGame(false);
            if (tournament != null && tournament.record(race))
                ZsgRooms.shareChat(game.roomName, tournament.lastResult + ". " + tournament.status());
        }
    }

    static void tick(String roomName, Runnable publish) {
        InGame game = ZsgRooms.getGame(roomName);
        if (game == null || game.getSequence() == null) return;
        if (!game.getIsInGame()) {
            if (game.getTournament() != null && game.getTournament().record(game.getSequence())) publish.run();
            return;
        }
        settle(game, game.getSequence());
        if (!game.getIsInGame()) publish.run();
    }

    static boolean compatible(Room room) {
        InGame game = ZsgRooms.getGame(room.roomName);
        boolean needsSpawnRules = game != null && (game.preventsTempleHostileSpawns()
                || zsgrooms.modid.seedbank.SeedBankProfile.find(game.targetStructure) != null
                || ZsgRoomsSeedMode.SPECIFICATION.equals(game.targetStructure));
        for (Player player : room.players) {
            if (player != null && player != room.host && needsSpawnRules && player.spawnRulesVersion != 1) {
                ZsgRooms.shareChat(room.roomName, player.getName() + " needs the spawn-rules update before starting. All runners must update ZSG Rooms.");
                return false;
            }
            if (player != null && player != room.host && game != null && game.getTournamentSettings().enabled
                    && player.tournamentVersion != 1) {
                ZsgRooms.shareChat(room.roomName, player.getName() + " needs the tournament update before starting.");
                return false;
            }
            if (player != null && player != room.host && player.sequenceVersion != RaceSequence.VERSION) {
                ZsgRooms.shareChat(room.roomName, player.getName() + " needs the race-sequence update before starting.");
                return false;
            }
        }
        return true;
    }

    static boolean prepareTournament(Room room, InGame game) {
        if (!game.getTournamentSettings().enabled) return true;
        if (game.getTournament() == null) {
            String error = game.getTournamentSettings().startError(room.getPlayerNames());
            if (!error.isEmpty()) { ZsgRooms.shareChat(room.roomName, error); return false; }
            game.setTournament(new Tournament(game.getTournamentSettings()));
        }
        Tournament tournament = game.getTournament();
        if (tournament.complete || !tournament.activeRaceId.isEmpty()) return false;
        if (!room.getPlayerNames().containsAll(tournament.participants())) {
            ZsgRooms.shareChat(room.roomName, "Waiting for the next tournament players to join");
            return false;
        }
        return true;
    }

    static java.util.List<String> participants(Room room, InGame game) {
        return game.getTournament() == null ? room.getPlayerNames() : game.getTournament().participants();
    }

    static void assignSequence(Room room, InGame game, java.util.List<String> seeds) {
        Tournament tournament = game.getTournament();
        game.setSequence(new RaceSequence(game.getRaceId(), seeds, participants(room, game),
                tournament != null && tournament.settings.bracket ? 1 : game.getFinisherLimit()));
        if (tournament != null) tournament.beginRace(game.getRaceId());
    }
}
