package zsgrooms.modid;

import org.junit.jupiter.api.Test;
import zsgrooms.modid.net.RoomSnapshot;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RoomCapacityTest {
    private Room lobby() {
        Room room = new Room("capacity-" + UUID.randomUUID(), "123", new Player("Host", true, true), 8);
        room.addPlayer(new Player("Guest", true, false));
        InGame game = new InGame(room.seed, room.roomName, InGame.SeedType.FIXED, false);
        game.targetStructure = "rooms-mix";
        game.restoreRaceId("unchanged-race");
        game.setPlayerProgress("Guest", 4, "Entered Nether");
        assertTrue(ZsgRooms.applyRoomSnapshot(RoomSnapshot.capture(room, game).toJson()));
        return ZsgRooms.getRoom(room.roomName);
    }

    @Test void sparseRosterShrinksAndGrowsWithoutReplacingPlayersHostOrMessages() {
        Room room = lobby();
        Player departed = room.getPlayer("Guest");
        Player tail = new Player("Tail", "tail-uuid", true, false);
        tail.setRequestingSeedChange(true);
        tail.sequenceVersion = 1;
        tail.tournamentVersion = 1;
        tail.spawnRulesVersion = 1;
        room.addPlayer(tail);
        room.removePlayer(departed);
        room.addRoomMessage("Keep this message");
        Player host = room.host;
        List<String> messages = room.getRoomMessages();
        InGame game = ZsgRooms.getGame(room.roomName);
        String gameState = RoomRuleSettings.capture(game).toJson();

        assertTrue(ZsgRooms.changeRoomCapacity(room.roomName, "Host", "2"));
        assertArrayEquals(new Player[]{host, tail}, room.players);
        assertSame(host, room.host);
        assertTrue(room.isFull());
        assertThrows(IllegalStateException.class, () -> room.addPlayer(new Player("Full", true, false)));
        assertTrue(ZsgRooms.changeRoomCapacity(room.roomName, "Host", "64"));
        assertEquals(64, room.players.length);
        assertEquals(64, room.maxPlayers);
        assertSame(host, room.players[0]);
        assertSame(tail, room.players[1]);
        assertSame(host, room.host);
        assertFalse(room.isFull());
        assertEquals(Arrays.asList("Host", "Tail"), room.getPlayerNames());
        assertEquals(messages, room.getRoomMessages());
        assertEquals("tail-uuid", tail.getUuid());
        assertTrue(tail.getIsRequestingSeedChange());
        assertTrue(tail.getIsInRoom());
        assertFalse(tail.getIsHost());
        assertEquals(1, tail.sequenceVersion);
        assertEquals(1, tail.tournamentVersion);
        assertEquals(1, tail.spawnRulesVersion);
        assertSame(game, ZsgRooms.getGame(room.roomName));
        assertEquals(gameState, RoomRuleSettings.capture(game).toJson());
        assertEquals("123", game.getSeed());
        assertEquals("rooms-mix", game.targetStructure);
        assertEquals("unchanged-race", game.getRaceId());
        assertEquals(Integer.valueOf(4), game.getPlayerProgress().get("Guest"));
    }

    @Test void malformedOutOfRangeAndUnchangedCapacityHaveNoSideEffects() {
        Room room = lobby();
        Player[] roster = room.players;
        String before = ZsgRooms.createRoomSnapshot(room.roomName);
        for (String value : new String[]{null, "", " ", " 4", "4 ", "+4", "-4", "4.0", "1e1",
                "4\n", "2147483648", "null", "{}", "\u0664", "0", "1", "65", "999", "8"}) {
            assertFalse(ZsgRooms.changeRoomCapacity(room.roomName, "Host", value), String.valueOf(value));
            ZsgRooms.applyRoomAction("capacity", room.roomName, "Host", value);
            assertSame(roster, room.players);
            assertEquals(before, ZsgRooms.createRoomSnapshot(room.roomName), String.valueOf(value));
        }
        for (int capacity = 2; capacity <= 64; capacity++) {
            assertEquals(capacity, Room.parseCapacity(Integer.toString(capacity)));
        }
    }

    @Test void onlyExactHostInUnlockedInactiveLobbyCanChangeCapacity() {
        Room room = lobby();
        String before = ZsgRooms.createRoomSnapshot(room.roomName);
        for (String player : new String[]{null, "", "Guest", "Stranger", "host", " Host "}) {
            assertFalse(ZsgRooms.changeRoomCapacity(room.roomName, player, "4"));
            ZsgRooms.applyRoomAction("capacity", room.roomName, player, "4");
            assertEquals(before, ZsgRooms.createRoomSnapshot(room.roomName));
            assertEquals(2, room.getPlayerCount());
        }
        InGame game = ZsgRooms.getGame(room.roomName);
        game.setInGame(true);
        before = ZsgRooms.createRoomSnapshot(room.roomName);
        assertFalse(ZsgRooms.changeRoomCapacity(room.roomName, "Host", "4"));
        ZsgRooms.applyRoomAction("capacity", room.roomName, "Host", "4");
        assertEquals(before, ZsgRooms.createRoomSnapshot(room.roomName));
        game.setInGame(false);
        TournamentSettings settings = new TournamentSettings();
        settings.enabled = true;
        settings.order = room.getPlayerNames();
        game.setTournament(new Tournament(settings));
        assertTrue(game.tournamentLocked());
        before = ZsgRooms.createRoomSnapshot(room.roomName);
        assertFalse(ZsgRooms.changeRoomCapacity(room.roomName, "Host", "4"));
        ZsgRooms.applyRoomAction("capacity", room.roomName, "Host", "4");
        assertEquals(before, ZsgRooms.createRoomSnapshot(room.roomName));
        game.setTournament(null);
        assertTrue(ZsgRooms.changeRoomCapacity(room.roomName, "Host", "4"));
    }

    @Test void capacityCannotDropBelowLatestRosterAndModelAlsoEnforcesBounds() {
        Room room = lobby();
        room.addPlayer(new Player("Third", true, false));
        Player[] roster = room.players;
        String before = ZsgRooms.createRoomSnapshot(room.roomName);
        for (int capacity : new int[]{-1, 0, 1, 2, 65, Integer.MAX_VALUE}) {
            assertFalse(room.setCapacity(capacity));
            assertSame(roster, room.players);
            assertEquals(before, ZsgRooms.createRoomSnapshot(room.roomName));
        }
        assertFalse(ZsgRooms.changeRoomCapacity(room.roomName, "Host", "2"));
        ZsgRooms.applyRoomAction("capacity", room.roomName, "Host", "2");
        assertEquals(before, ZsgRooms.createRoomSnapshot(room.roomName));
        assertTrue(ZsgRooms.changeRoomCapacity(room.roomName, "Host", "3"));
        assertTrue(room.isFull());
    }

    @Test void capacityActionsNeverCreateRoomsOrPlayersOrChangeActiveRoom() {
        Room room = lobby();
        String active = ZsgRooms.getActiveRoomName();
        String missing = "missing-capacity-" + UUID.randomUUID();
        assertFalse(ZsgRooms.changeRoomCapacity(null, "Host", "4"));
        assertFalse(ZsgRooms.changeRoomCapacity(missing, "Host", "4"));
        for (String value : new String[]{"4", "broken"}) {
            ZsgRooms.applyRoomAction("capacity", missing, "Stranger", value);
            assertNull(ZsgRooms.getRoom(missing));
            assertNull(ZsgRooms.getGame(missing));
            assertEquals(active, ZsgRooms.getActiveRoomName());
        }
        Room other = lobby();
        ZsgRooms.applyRoomAction("capacity", room.roomName, "Host", "4");
        assertEquals(4, room.maxPlayers);
        assertEquals(other.roomName, ZsgRooms.getActiveRoomName());
    }

    @Test void existingSnapshotMaxPlayersSynchronizesBothShrinkAndGrow() {
        Room room = lobby();
        for (int capacity : new int[]{2, 64, 4}) {
            assertTrue(ZsgRooms.changeRoomCapacity(room.roomName, "Host", Integer.toString(capacity)));
            RoomSnapshot snapshot = RoomSnapshot.fromJson(ZsgRooms.createRoomSnapshot(room.roomName));
            assertNotNull(snapshot);
            assertEquals(capacity, snapshot.maxPlayers);
            assertTrue(ZsgRooms.applyRoomSnapshot(snapshot.toJson()));
            room = ZsgRooms.getRoom(room.roomName);
            assertEquals(capacity, room.maxPlayers);
            assertEquals(capacity, room.players.length);
            assertEquals(Arrays.asList("Host", "Guest"), room.getPlayerNames());
            assertSame(room.getPlayer("Host"), room.host);
            assertEquals(snapshot.messages, room.getRoomMessages());
        }
    }
}
