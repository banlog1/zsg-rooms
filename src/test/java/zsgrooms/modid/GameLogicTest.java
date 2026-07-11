package zsgrooms.modid;

import org.junit.jupiter.api.Test;
import zsgrooms.modid.net.RoomProtocol;
import zsgrooms.modid.net.RoomSnapshot;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GameLogicTest {
    @Test
    public void chestIronInjectionAddsEnoughIron() {
        InGame game = new InGame("seed", "room", InGame.SeedType.FIXED, true);
        List<String> chestItems = Arrays.asList("empty", "empty", "empty", "empty");

        List<String> updated = game.ensureChestIron(chestItems, 4);

        int ironCount = 0;
        for (String item : updated) {
            if (item != null && item.toLowerCase().contains("iron")) {
                ironCount += 1;
            }
        }

        assertTrue(ironCount >= 4, "Expected at least four iron items after injection");
    }

    @Test
    public void seedModificationAddsStructureAndIronHint() {
        InGame game = new InGame("seed", "room", InGame.SeedType.FIXED, false);
        game.seedModification("bastion", 4);

        assertTrue(game.getSeed().contains("bastion"));
        assertTrue(game.getSeed().contains("iron:4"));
    }

    @Test
    public void roomTracksPlayersAndMessages() {
        Player host = new Player("Host", true, true);
        Room room = new Room("room", "seed", host, 4);
        Player guest = new Player("Guest", false, false);

        room.addPlayer(guest);
        assertEquals(2, room.getRoomMessages().size());
        assertEquals(2, room.getPlayerCount());
        assertTrue(room.getPlayerNames().contains("Host"));
        assertTrue(room.getPlayerNames().contains("Guest"));

        room.removePlayer(guest);
        assertTrue(room.getRoomMessages().size() >= 2);
    }

    @Test
    public void roomSnapshotRoundTripsAuthoritativeState() {
        Player host = new Player("Host", true, true);
        host.setUuid("8667ba71-b85a-4004-af54-457a9734eed7");
        Player guest = new Player("Guest", true, false);
        guest.setRequestingSeedChange(true);
        Room room = new Room("room-code", "12345|structure:zsg|iron:4", host, 10);
        room.addPlayer(guest);
        room.addRoomMessage("<Guest> hello, \"host\"");

        InGame game = new InGame(room.getSeed(), room.roomName, InGame.SeedType.FIXED, false);
        game.targetStructure = "zsg";
        game.setFinishGoal(3);
        game.setPlayerProgress("Host", 1);
        game.setPlayerProgress("Guest", 2);
        game.setPlayerProgress("Host", 6, "Found Stronghold");

        RoomSnapshot original = RoomSnapshot.capture(room, game);
        String wireMessage = RoomProtocol.encode("snapshot", room.roomName, host.getName(), original.toJson());
        Map<String, String> decodedWire = RoomProtocol.decode(wireMessage);
        RoomSnapshot decoded = RoomSnapshot.fromJson(decodedWire.get("value"));

        assertEquals("snapshot", decodedWire.get("type"));
        assertEquals("room-code", decoded.roomName);
        assertEquals("12345|structure:zsg|iron:4", decoded.seed);
        assertEquals("Host", decoded.hostName);
        assertEquals("zsg", decoded.filter);
        assertEquals(10, decoded.maxPlayers);
        assertEquals(3, decoded.finishGoal);
        assertEquals(2, decoded.players.size());
        assertEquals("8667ba71-b85a-4004-af54-457a9734eed7", decoded.players.get(0).uuid);
        assertTrue(decoded.players.get(1).requestingSeedChange);
        assertEquals(2, decoded.progress.get("Guest"));
        assertEquals("Found Stronghold", decoded.progressLabels.get("Host"));
        assertTrue(decoded.messages.contains("<Guest> hello, \"host\""));

        assertTrue(ZsgRooms.applyRoomSnapshot(decodedWire.get("value")));
        Room appliedRoom = ZsgRooms.getRoom("room-code");
        InGame appliedGame = ZsgRooms.getGame("room-code");
        assertEquals(Arrays.asList("Host", "Guest"), appliedRoom.getPlayerNames());
        assertEquals("8667ba71-b85a-4004-af54-457a9734eed7", appliedRoom.getPlayer("Host").getUuid());
        assertTrue(appliedRoom.getPlayer("Guest").getIsRequestingSeedChange());
        assertEquals(3, appliedGame.getFinishGoal());
        assertEquals(2, appliedGame.getPlayerProgress().get("Guest"));
    }

    @Test
    public void exactManualSeedRequestKeepsOneSeedValue() {
        String seed = ZsgSeedBridge.requestExactSeedForRoom("room", "manual:987654321").join();

        assertEquals("987654321", ZsgSeedBridge.extractMinecraftSeed(seed));
        assertEquals("manual", ZsgSeedBridge.resolveStructure(seed));
    }

    @Test
    public void repeatedSeedChangeVoteDoesNotRestartRequest() {
        ZsgRooms.createRoom("vote-test-room", 4, 1, "zsg", "Host");
        ZsgRooms.applyRoomAction("join_room", "vote-test-room", "Guest", "");

        assertFalse(ZsgRooms.registerSeedChangeRequest("Host"));
        assertFalse(ZsgRooms.registerSeedChangeRequest("Host"));
        assertTrue(ZsgRooms.registerSeedChangeRequest("Guest"));
        assertFalse(ZsgRooms.registerSeedChangeRequest("Guest"));
    }

    @Test
    public void roomResetPermissionCanOnlyBeConsumedOnce() {
        RoomResetAuthorization.clear();
        assertFalse(RoomResetAuthorization.consumeResetPermission());

        RoomResetAuthorization.allowNextReset();
        assertTrue(RoomResetAuthorization.consumeResetPermission());
        assertFalse(RoomResetAuthorization.consumeResetPermission());
    }

    @Test
    public void raceHudTracksBastionStrongholdAndEndMilestones() {
        ZsgRooms.createRoom("milestone-test-room", 2, 1, "zsg", "Runner");

        ZsgRooms.trackAdvancement("milestone-test-room", "Runner",
                "minecraft:nether/find_bastion\tThose Were the Days");
        assertEquals(5, ZsgRooms.getGame("milestone-test-room").getPlayerProgress().get("Runner"));
        assertEquals("Entered Bastion", ZsgRooms.getGame("milestone-test-room").getPlayerProgressLabels().get("Runner"));

        ZsgRooms.trackAdvancement("milestone-test-room", "Runner",
                "minecraft:story/follow_ender_eye\tEye Spy");
        assertEquals(6, ZsgRooms.getGame("milestone-test-room").getPlayerProgress().get("Runner"));
        assertEquals("Found Stronghold", ZsgRooms.getGame("milestone-test-room").getPlayerProgressLabels().get("Runner"));

        ZsgRooms.trackAdvancement("milestone-test-room", "Runner",
                "minecraft:end/root\tThe End?");
        assertEquals(7, ZsgRooms.getGame("milestone-test-room").getPlayerProgress().get("Runner"));
        assertEquals("Entered End", ZsgRooms.getGame("milestone-test-room").getPlayerProgressLabels().get("Runner"));
    }
}
