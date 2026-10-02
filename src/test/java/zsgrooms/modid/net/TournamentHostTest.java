package zsgrooms.modid.net;

import org.junit.jupiter.api.Test;
import zsgrooms.modid.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TournamentHostTest {
    private InGame setup(boolean bracket) {
        String name = "tournament-" + UUID.randomUUID();
        Room room = new Room(name, "123|structure:zsg", new Player("Host", true, true), 6);
        for (String player : Arrays.asList("A", "B", "C")) {
            Player guest = new Player(player, true, false);
            guest.sequenceVersion = RaceSequence.VERSION; guest.tournamentVersion = 1;
            room.addPlayer(guest);
        }
        InGame game = new InGame(room.seed, name, InGame.SeedType.FIXED, false);
        TournamentSettings settings = new TournamentSettings();
        settings.enabled = true; settings.bracket = bracket;
        settings.order = Arrays.asList("A", "B", "Host", "C");
        game.setTournamentSettings(settings);
        assertTrue(ZsgRooms.applyRoomSnapshot(RoomSnapshot.capture(room, game).toJson()));
        return ZsgRooms.getGame(name);
    }
    @Test void onlyCurrentPairLoadsAndWaitingHostIsNotPartOfReadyGate() {
        InGame game = setup(true); Room room = ZsgRooms.getRoom(game.roomName);
        assertTrue(RoomSequenceHost.prepareTournament(room, game));
        game.startGame(); RoomSequenceHost.assignSequence(room, game, Arrays.asList(room.seed, "456"));
        assertNull(game.getSequence().runner("Host"));
        assertNull(game.getSequence().runner("C"));
        assertFalse(ZsgRooms.markPlayerWorldReady(room.roomName, "Host", room.seed));
        assertTrue(ZsgRooms.markPlayerWorldReady(room.roomName, "A", room.seed));
        assertFalse(ZsgRooms.areAllPlayersWorldReady(room.roomName));
        assertTrue(ZsgRooms.markPlayerWorldReady(room.roomName, "B", room.seed));
        assertTrue(ZsgRooms.areAllPlayersWorldReady(room.roomName));
        assertTrue(ZsgRooms.releaseSynchronizedStart(room.roomName, room.seed));
        assertTrue(game.hostRaceElapsed() >= 0);
        assertTrue(RoomSequenceHost.handle(room.roomName, "Host", "advancement", "minecraft:story/enter_the_nether", () -> {}));
        assertTrue(RoomSequenceHost.handle(room.roomName, "A", "seed_change", "", () -> {}));
        assertTrue(RoomSequenceHost.handle(room.roomName, "B", "forfeit",
                RaceSequence.report(game.getRaceId(), 0, -1, "dnf"), () -> {}));
        assertFalse(game.getIsInGame());
        assertTrue(game.getSequence().runner("A").wonByForfeit);
        assertEquals("A", game.getTournament().matches.get(0).winner);
        assertEquals(Arrays.asList("Host", "C"), game.getTournament().participants());
        assertTrue(RoomSnapshot.fromJson(ZsgRooms.createRoomSnapshot(room.roomName)).sequence.valid(game.getRaceId()));
    }

    @Test void lockedRulesRequireExplicitHostResetAndNeverResetDuringRace() {
        InGame game = setup(true); Room room = ZsgRooms.getRoom(game.roomName);
        assertTrue(RoomSequenceHost.prepareTournament(room, game));
        RoomRuleSettings edit = RoomRuleSettings.capture(game); edit.seedCount = 3;
        assertFalse(ZsgRooms.changeRoomRules(room.roomName, "Host", edit.toJson()));
        String filter = game.targetStructure;
        ZsgRooms.changeRoomFilter(room.roomName, "village"); assertEquals(filter, game.targetStructure);
        edit.resetTournament = true;
        assertFalse(ZsgRooms.changeRoomRules(room.roomName, "A", edit.toJson()));
        game.setInGame(true);
        assertFalse(ZsgRooms.changeRoomRules(room.roomName, "Host", edit.toJson()));
        game.setInGame(false);
        assertTrue(ZsgRooms.changeRoomRules(room.roomName, "Host", edit.toJson()));
        assertNull(game.getTournament());
    }

    @Test void snapshotCarriesTournamentAndOldCapabilitiesCannotStartIt() {
        InGame game = setup(false); Room room = ZsgRooms.getRoom(game.roomName);
        ZsgRooms.applyRoomAction("profile", room.roomName, "A", "uuid|sequence:1");
        assertFalse(RoomSequenceHost.compatible(room));
        ZsgRooms.applyRoomAction("profile", room.roomName, "A", "uuid|sequence:1|tournament:1");
        assertTrue(RoomSequenceHost.compatible(room));
        assertTrue(RoomSequenceHost.prepareTournament(room, game));
        game.startGame(); RoomSequenceHost.assignSequence(room, game, Arrays.asList(room.seed));
        String snapshot = ZsgRooms.createRoomSnapshot(room.roomName);
        assertTrue(ZsgRooms.applyRoomSnapshot(snapshot));
        assertEquals(game.getTournament().activeRaceId, ZsgRooms.getGame(room.roomName).getTournament().activeRaceId);
        assertEquals(4, ZsgRooms.getGame(room.roomName).getSequence().standings().size());
        assertTrue(TournamentRaceClient.isLaunchPayload(TournamentRaceClient.launchPayload(room.roomName)));
    }

    @Test void departureAfterResultIsWithdrawnAndDoesNotRescore() {
        InGame game = setup(false); Room room = ZsgRooms.getRoom(game.roomName);
        assertTrue(RoomSequenceHost.prepareTournament(room, game));
        game.startGame(); game.releaseSynchronizedStart();
        RoomSequenceHost.assignSequence(room, game, Arrays.asList(room.seed));
        RoomSequenceHost.handle(room.roomName, "A", "complete_run", RaceSequence.report(game.getRaceId(), 0, 100, "finish"), () -> {});
        assertEquals(1, game.getTournament().completedRounds);
        RoomSequenceHost.handle(room.roomName, "A", "leave_room", "", () -> {});
        RoomSequenceHost.tick(room.roomName, () -> {});
        assertEquals(1, game.getTournament().completedRounds);
        assertTrue(game.getTournament().withdrawn.contains("A"));
        assertEquals(Integer.valueOf(10), game.getTournament().scores.get("A"));
    }

    @Test void tournamentSnapshotsKeepUnlaunchedManualSeedPrivate() {
        InGame game = setup(true);
        game.targetStructure = "manual:987654321";
        assertTrue(RoomSequenceHost.prepareTournament(ZsgRooms.getRoom(game.roomName), game));
        String snapshot = ZsgRooms.createRoomSnapshot(game.roomName);
        assertFalse(snapshot.contains("987654321"));
        assertEquals("manual", RoomSnapshot.fromJson(snapshot).filter);
    }
}
