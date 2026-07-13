package zsgrooms.modid;

import org.junit.jupiter.api.Test;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import zsgrooms.modid.net.RoomProtocol;
import zsgrooms.modid.net.RoomSnapshot;

import java.util.Arrays;
import java.util.concurrent.CompletionException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        game.setCheatsAllowed(true);
        game.setRngStandardized(true);
        game.setBoostedBarters(true);
        game.setMinimumBastionIron(true);
        game.setPlayerProgress("Host", 1);
        game.setPlayerProgress("Guest", 2);
        game.setPlayerProgress("Host", 6, "Found Stronghold");
        game.startGame();
        game.markPlayerReady("Host");
        game.releaseSynchronizedStart();

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
        assertTrue(decoded.cheatsAllowed);
        assertTrue(decoded.rngStandardized);
        assertTrue(decoded.boostedBarters);
        assertTrue(decoded.minimumBastionIron);
        assertTrue(decoded.synchronizedStartReleased);
        assertEquals(Arrays.asList("Host"), decoded.readyPlayers);
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
        assertTrue(appliedGame.areCheatsAllowed());
        assertTrue(appliedGame.isRngStandardized());
        assertTrue(appliedGame.areBartersBoosted());
        assertTrue(appliedGame.hasMinimumBastionIron());
        assertTrue(appliedGame.isSynchronizedStartReleased());
        assertEquals(1, appliedGame.getReadyPlayerCount());
        assertEquals(2, appliedGame.getPlayerProgress().get("Guest"));
    }

    @Test
    public void bastionIronTopUpAddsOnlyTheExactMissingUnits() {
        assertEquals(27, BastionIronGuarantee.missingIronUnits(0));
        assertEquals(9, BastionIronGuarantee.missingIronUnits(18));
        assertEquals(1, BastionIronGuarantee.missingIronUnits(26));
        assertEquals(0, BastionIronGuarantee.missingIronUnits(27));
        assertEquals(0, BastionIronGuarantee.missingIronUnits(40));
    }

    @Test
    public void bastionIronTopUpCountsExistingIngotsAndNuggets() {
        Identifier bastionLoot = new Identifier("minecraft", "chests/bastion_other");

        SimpleInventory ingotChest = new SimpleInventory(27);
        ingotChest.setStack(0, new ItemStack(Items.IRON_INGOT, 5));
        BastionIronGuarantee.configure(true);
        BastionIronGuarantee.topUpFirstChest(ingotChest, bastionLoot);
        assertEquals(5, ingotChest.getStack(0).getCount());
        assertEquals(45, BastionIronGuarantee.countIronUnits(ingotChest));

        SimpleInventory nuggetChest = new SimpleInventory(27);
        nuggetChest.setStack(0, new ItemStack(Items.IRON_NUGGET, 5));
        BastionIronGuarantee.configure(true);
        BastionIronGuarantee.topUpFirstChest(nuggetChest, bastionLoot, 12345L);
        assertEquals(5, nuggetChest.getStack(0).getCount());
        assertEquals(27, BastionIronGuarantee.countIronUnits(nuggetChest));
        assertEquals(4, occupiedSlots(nuggetChest));

        SimpleInventory emptyChest = new SimpleInventory(27);
        BastionIronGuarantee.configure(true);
        BastionIronGuarantee.topUpFirstChest(emptyChest, bastionLoot, 12345L);
        assertEquals(27, BastionIronGuarantee.countIronUnits(emptyChest));
        assertEquals(1, occupiedSlots(emptyChest));
    }

    @Test
    public void synchronizedStartWaitsForEveryPlayerOnTheExactSeed() {
        ZsgRooms.createRoom("start-barrier-room", 4, 1, "manual:12345", "Host");
        ZsgRooms.applyRoomAction("join_room", "start-barrier-room", "Guest", "");
        InGame game = ZsgRooms.getGame("start-barrier-room");
        game.startGame();
        String seed = game.getSeed();

        assertFalse(ZsgRooms.markPlayerWorldReady("start-barrier-room", "Host", "wrong-seed"));
        assertTrue(ZsgRooms.markPlayerWorldReady("start-barrier-room", "Host", seed));
        assertFalse(ZsgRooms.areAllPlayersWorldReady("start-barrier-room"));
        assertTrue(ZsgRooms.markPlayerWorldReady("start-barrier-room", "Guest", seed));
        assertTrue(ZsgRooms.areAllPlayersWorldReady("start-barrier-room"));
        assertTrue(ZsgRooms.releaseSynchronizedStart("start-barrier-room", seed));
        assertTrue(game.isSynchronizedStartReleased());
        assertFalse(ZsgRooms.releaseSynchronizedStart("start-barrier-room", seed));
    }

    @Test
    public void synchronizedStartRechecksPlayersWhoLeaveWhileLoading() {
        ZsgRooms.createRoom("start-leave-room", 4, 1, "manual:67890", "Host");
        ZsgRooms.applyRoomAction("join_room", "start-leave-room", "Guest", "");
        InGame game = ZsgRooms.getGame("start-leave-room");
        game.startGame();

        assertTrue(ZsgRooms.markPlayerWorldReady("start-leave-room", "Host", game.getSeed()));
        assertFalse(ZsgRooms.areAllPlayersWorldReady("start-leave-room"));
        ZsgRooms.removeRoomPlayer("start-leave-room", "Guest");
        assertTrue(ZsgRooms.areAllPlayersWorldReady("start-leave-room"));
    }

    @Test
    public void standardizedRngSeedsMatchForTheSameRoomEvent() {
        long first = RngStandardization.eventSeed(12345L, "mob_drop", "minecraft:blaze", 0L);
        long repeated = RngStandardization.eventSeed(12345L, "mob_drop", "minecraft:blaze", 0L);
        long nextDrop = RngStandardization.eventSeed(12345L, "mob_drop", "minecraft:blaze", 1L);
        long differentMob = RngStandardization.eventSeed(12345L, "mob_drop", "minecraft:enderman", 0L);

        assertEquals(first, repeated);
        assertNotEquals(first, nextDrop);
        assertNotEquals(first, differentMob);
    }

    @Test
    public void rngAndBoostedBarterSettingsAreIndependent() {
        RngStandardization.configure(true, false);
        assertTrue(RngStandardization.isEnabled());
        assertFalse(RngStandardization.areBartersBoosted());

        RngStandardization.configure(false, true);
        assertFalse(RngStandardization.isEnabled());
        assertTrue(RngStandardization.areBartersBoosted());

        RngStandardization.configure(false, false);
    }

    @Test
    public void exactManualSeedRequestKeepsOneSeedValue() {
        String seed = ZsgSeedBridge.requestExactSeedForRoom("room", "manual:987654321").join();

        assertEquals("987654321", ZsgSeedBridge.extractMinecraftSeed(seed));
        assertEquals("manual", ZsgSeedBridge.resolveStructure(seed));
    }

    @Test
    public void manualSeedSpecificationSurvivesRoomSnapshotsAndNewRequests() {
        ZsgRooms.createRoom("manual-room", 2, 1, "manual:24680", "Host");
        InGame game = ZsgRooms.getGame("manual-room");

        assertEquals("manual:24680", game.targetStructure);
        assertEquals("24680", ZsgSeedBridge.extractMinecraftSeed(game.getSeed()));

        RoomSnapshot snapshot = RoomSnapshot.capture(ZsgRooms.getRoom("manual-room"), game);
        assertEquals("manual:24680", snapshot.filter);
        assertEquals("24680", ZsgSeedBridge.extractMinecraftSeed(
                ZsgSeedBridge.requestExactSeedForRoom("manual-room", snapshot.filter).join()));
    }

    @Test
    public void emptyManualSeedIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ZsgSeedBridge.fetchSeedForRoom("manual-room", "manual:"));
    }

    @Test
    public void launchedManualSeedReconstructsItsFullSpecification() {
        String seed = ZsgSeedBridge.buildSeedForStructure("13579", "manual", 4);

        assertEquals("manual:13579", ZsgSeedBridge.seedSpecificationFromSeed(seed));
    }

    @Test
    public void invalidAsyncSeedRequestDoesNotThrowOnTheCallingThread() {
        assertThrows(CompletionException.class,
                () -> ZsgSeedBridge.requestExactSeedForRoom("manual-room", "manual").join());
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
        ZsgRooms.getGame("milestone-test-room").startGame();

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

        assertFalse(ZsgRooms.trackAdvancement("milestone-test-room", "Runner",
                "minecraft:end/kill_dragon\tFree the End"));
        assertEquals(8, ZsgRooms.getGame("milestone-test-room").getPlayerProgress().get("Runner"));
        assertEquals("Dragon Defeated", ZsgRooms.getGame("milestone-test-room").getPlayerProgressLabels().get("Runner"));

        ZsgRooms.getGame("milestone-test-room").endGame();
        assertFalse(ZsgRooms.trackAdvancement("milestone-test-room", "Runner",
                "minecraft:end/kill_dragon\tFree the End"));
    }

    @Test
    public void speedRunIgtTimesUseTimerFormatting() {
        assertEquals("12:34.567", SpeedRunIgtBridge.formatMilliseconds(754567L));
        assertEquals("1:02:03.004", SpeedRunIgtBridge.formatMilliseconds(3723004L));
    }

    @Test
    public void matchCompletionCanOnlyChooseOneWinner() {
        ZsgRooms.createRoom("finish-once-room", 2, 1, "manual:123", "Runner");
        ZsgRooms.getGame("finish-once-room").startGame();

        assertTrue(ZsgRooms.finishMatch("finish-once-room", "Runner", "Beat the seed in 01:23.456 IGT"));
        assertFalse(ZsgRooms.finishMatch("finish-once-room", "Other", "Beat the seed"));
    }

    @Test
    public void resettingOneRunKeepsTheSharedSeedAndResetsOnlyThatPlayer() {
        ZsgRooms.createRoom("reset-test-room", 4, 1, "manual:12345", "Host");
        ZsgRooms.applyRoomAction("join_room", "reset-test-room", "Guest", "");
        ZsgRooms.getGame("reset-test-room").setPlayerProgress("Host", 6, "Found Stronghold");
        ZsgRooms.getGame("reset-test-room").setPlayerProgress("Guest", 5, "Entered Bastion");
        String seed = ZsgRooms.getGame("reset-test-room").getSeed();

        ZsgRooms.applyRoomAction("reset_run", "reset-test-room", "Guest", "");

        assertEquals(seed, ZsgRooms.getGame("reset-test-room").getSeed());
        assertEquals(6, ZsgRooms.getGame("reset-test-room").getPlayerProgress().get("Host"));
        assertEquals(0, ZsgRooms.getGame("reset-test-room").getPlayerProgress().get("Guest"));
        assertEquals("Restarting", ZsgRooms.getGame("reset-test-room").getPlayerProgressLabels().get("Guest"));
    }

    private static int occupiedSlots(SimpleInventory inventory) {
        int occupied = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (!inventory.getStack(slot).isEmpty()) {
                occupied++;
            }
        }
        return occupied;
    }
}
