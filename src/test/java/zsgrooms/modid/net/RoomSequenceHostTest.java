package zsgrooms.modid.net;

import org.junit.jupiter.api.Test;
import zsgrooms.modid.*;
import java.util.Arrays;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RoomSequenceHostTest {
    private InGame room(boolean started) {
        String name = "sequence-" + UUID.randomUUID();
        Room room = new Room(name, "11|structure:zsg", new Player("Host", true, true), 4);
        room.addPlayer(new Player("Guest", true, false));
        InGame game = new InGame(room.seed, name, InGame.SeedType.FIXED, false);
        game.setFinishGoal(2);
        game.setFinisherLimit(2);
        game.startGame();
        if (started) game.releaseSynchronizedStart();
        assertTrue(ZsgRooms.applyRoomSnapshot(RoomSnapshot.capture(room, game).toJson()));
        game = ZsgRooms.getGame(name);
        game.setSequence(new RaceSequence(game.getRaceId(), Arrays.asList(room.seed, "22|structure:zsg"), room.getPlayerNames(), game.getFinisherLimit()));
        return game;
    }
    private void submit(InGame game, String player, int stage, long elapsed, String action) {
        assertTrue(RoomSequenceHost.handle(game.roomName, player, "finish".equals(action) ? "complete_run" : "forfeit",
                RaceSequence.report(game.getRaceId(), stage, elapsed, action), () -> {}));
    }

    @Test void winnerDoesNotStopRoomAndSnapshotsPreserveLocalLoadedSeed() {
        InGame game = room(true);
        submit(game, "Guest", 0, 1000, "finish");
        assertEquals(1, game.getSequence().runner("Guest").stage);
        assertTrue(game.getIsInGame());
        assertEquals("11|structure:zsg", game.getSeed());
        submit(game, "Guest", 1, 2000, "finish");
        assertTrue(game.getIsInGame());
        String snapshot = ZsgRooms.createRoomSnapshot(game.roomName);
        game.setSeed("22|structure:zsg");
        assertTrue(ZsgRooms.applyRoomSnapshot(snapshot));
        InGame restored = ZsgRooms.getGame(game.roomName);
        assertEquals("22|structure:zsg", restored.getSeed());
        assertEquals("11|structure:zsg", ZsgRooms.getRoom(game.roomName).getSeed());
        assertEquals(game.getRaceId(), restored.getRaceId());
        submit(restored, "Host", 0, 3000, "skip");
        submit(restored, "Host", 1, 4000, "finish");
        assertFalse(restored.getIsInGame());
        assertEquals(2, restored.getSequence().place("Host"));
    }

    @Test void leavingDuringInitialLoadCannotBlockStartAndCannotFinishEarly() {
        InGame game = room(false);
        submit(game, "Guest", 0, 1, "finish");
        assertEquals(0, game.getSequence().runner("Guest").stage);
        submit(game, "Guest", 0, -1, "dnf");
        game.markPlayerReady("Host");
        assertTrue(ZsgRooms.areAllPlayersWorldReady(game.roomName));
        assertTrue(game.getSequence().runner("Guest").dnf);
        assertTrue(game.getIsInGame());
    }

    @Test void disconnectRecordsDnfAndCannotChangeAnotherRunnersSeed() {
        InGame game = room(true);
        assertTrue(RoomSequenceHost.handle(game.roomName, "Guest", "leave_room", "", () -> {}));
        assertTrue(game.getSequence().runner("Guest").dnf);
        assertTrue(game.getIsInGame());
        assertTrue(RoomSequenceHost.handle(game.roomName, "Host", "seed_change", "", () -> {}));
        assertEquals("11|structure:zsg", game.getSeed());
        submit(game, "Host", 0, 1000, "finish");
        submit(game, "Host", 1, 2000, "finish");
        assertFalse(game.getIsInGame());
    }

    @Test void profileCapabilityGatesOldClientsWithoutAlteringTheirUuid() {
        InGame game = room(false);
        Room room = ZsgRooms.getRoom(game.roomName);
        assertFalse(RoomSequenceHost.compatible(room));
        ZsgRooms.applyRoomAction("profile", game.roomName, "Guest", "abc|sequence:1");
        assertTrue(RoomSequenceHost.compatible(room));
        assertEquals("abc", room.getPlayer("Guest").getUuid());
        RoomSnapshot snapshot = RoomSnapshot.fromJson(ZsgRooms.createRoomSnapshot(game.roomName));
        assertEquals(1, snapshot.players.get(1).toPlayer().sequenceVersion);
    }

    @Test void oneFinisherLimitEndsRoomAndStopsOtherRunner() {
        InGame game = room(true);
        game.getSequence().finisherLimit = 1;
        submit(game, "Guest", 0, 1000, "finish");
        assertTrue(game.getIsInGame());
        submit(game, "Guest", 1, 2000, "finish");
        assertFalse(game.getIsInGame());
        assertTrue(game.getSequence().runner("Host").stopped);
        assertTrue(RoomSnapshot.fromJson(ZsgRooms.createRoomSnapshot(game.roomName)).sequence.runner("Host").stopped);
    }

    @Test void bankAndMixedRoomsRequireTargetAwareGuestsAndPreserveCapability() {
        InGame game = room(false);
        Room room = ZsgRooms.getRoom(game.roomName);
        for (String filter : new String[]{"rooms-village-v5", "rooms-shipwreck-v5", "rooms-mix"}) {
            game.targetStructure = filter;
            ZsgRooms.applyRoomAction("profile", game.roomName, "Guest", "abc|sequence:1|tournament:1");
            assertFalse(RoomSequenceHost.compatible(room));
            ZsgRooms.applyRoomAction("profile", game.roomName, "Guest", "abc|sequence:1|tournament:1|spawnrules:1");
            assertTrue(RoomSequenceHost.compatible(room));
        }
        RoomSnapshot snapshot = RoomSnapshot.fromJson(ZsgRooms.createRoomSnapshot(game.roomName));
        assertEquals(1, snapshot.players.get(1).toPlayer().spawnRulesVersion);
        assertEquals("abc", room.getPlayer("Guest").getUuid());
        game.targetStructure = "zsg";
        game.setPreventTempleHostileSpawns(true);
        ZsgRooms.applyRoomAction("profile", game.roomName, "Guest", "abc|sequence:1");
        assertFalse(RoomSequenceHost.compatible(room));
        game.setPreventTempleHostileSpawns(false);
        assertTrue(RoomSequenceHost.compatible(room));
    }

    @Test void singleSeedForfeitEndsNormalMatchAndNeverAddsPenalty() {
        InGame game = room(true);
        game.setFinishGoal(1);
        game.setFinisherLimit(1);
        game.setSequence(new RaceSequence(game.getRaceId(), Arrays.asList("11"), Arrays.asList("Host", "Guest"), 1));
        submit(game, "Guest", 0, -1, "dnf");
        assertFalse(game.getIsInGame());
        assertTrue(game.getSequence().runner("Guest").dnf);
        assertEquals(0, game.getSequence().runner("Guest").skipped);
        assertTrue(game.getSequence().runner("Host").wonByForfeit);
    }

    @Test void disconnectOfLastActiveRunnerSettlesFinisherLimitInSnapshot() {
        InGame game = room(true);
        Room room = ZsgRooms.getRoom(game.roomName);
        room.addPlayer(new Player("Third", true, false));
        RaceSequence race = new RaceSequence(game.getRaceId(), Arrays.asList("11"), room.getPlayerNames(), 1);
        game.setFinishGoal(1);
        game.setFinisherLimit(1);
        game.setSequence(race);
        assertTrue(race.submit("Host", RaceSequence.report(race.raceId, 0, 100, "finish")));
        assertTrue(race.submit("Guest", RaceSequence.report(race.raceId, 0, 101, "finish")));
        ZsgRooms.removeRoomPlayer(game.roomName, "Third");
        assertFalse(game.getIsInGame());
        assertEquals(1, race.place("Host"));
        assertEquals(0, race.place("Guest"));
        RoomSnapshot snapshot = RoomSnapshot.fromJson(ZsgRooms.createRoomSnapshot(game.roomName));
        assertTrue(snapshot.sequence.runner("Guest").stopped);
        assertTrue(ZsgRooms.applyRoomSnapshot(snapshot.toJson()));
        assertEquals(0, ZsgRooms.getGame(game.roomName).getSequence().place("Guest"));
    }
}
