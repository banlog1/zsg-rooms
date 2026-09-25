package zsgrooms.modid;

import org.junit.jupiter.api.Test;
import zsgrooms.modid.net.RoomProtocol;
import zsgrooms.modid.net.RoomSnapshot;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ZsgRoomsSeedModeTest {
    @Test
    void allOneHundredRollsHaveTheExactAgreedWeights() {
        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (int roll = 0; roll < 100; roll++) counts.merge(ZsgRoomsSeedMode.filterForRoll(roll), 1, Integer::sum);
        assertEquals(6, counts.size());
        for (String filter : new String[] {"rooms-temple-v5", "rooms-village-v5", "rooms-shipwreck-v5", "rooms-buried-treasure-v5"}) {
            assertEquals(20, counts.get(filter));
        }
        assertEquals(15, counts.get("rooms-ruined-portal-v5"));
        assertEquals(5, counts.get("rpseedbank"));
        assertFalse(counts.containsKey("zsg"));
        assertFalse(counts.containsKey("zsgop"));
        assertThrows(IllegalArgumentException.class, () -> ZsgRoomsSeedMode.filterForRoll(-1));
        assertThrows(IllegalArgumentException.class, () -> ZsgRoomsSeedMode.filterForRoll(100));
    }

    @Test
    void modeIsNotVanillaRandomAndCannotFallThroughToAnUnfilteredSeed() {
        assertEquals("rooms-mix", ZsgSeedBridge.normalizeSeedSpecification(" ZSG Rooms Mode "));
        assertEquals("ZSG Rooms Mode", ZsgSeedBridge.seedTypeLabel("rooms-mix"));
        assertEquals("random", ZsgSeedBridge.normalizeSeedType("rsg"));
        assertTrue(ZsgSeedBridge.extractMinecraftSeed(ZsgSeedBridge.fetchSeedForRoom("room", "rooms-mix")).startsWith("pending-"));
        assertTrue(ZsgSeedBridge.requestExactSeedForRoom("room", "rooms-mix").isCompletedExceptionally());
    }

    @Test
    void launchAndSnapshotKeepTheModeSeparateFromTheActualFilter() {
        String seed = "12345|structure:rpseedbank|iron:4|selection:rooms-mix";
        String decodedSeed = RoomProtocol.decode(RoomProtocol.encode("launch", "mode-test", "Host", seed)).get("value");
        assertEquals("rooms-mix", ZsgSeedBridge.seedSpecificationFromSeed(decodedSeed));
        assertEquals("rpseedbank", ZsgSeedBridge.resolveStructure(decodedSeed));
        assertEquals("12345", ZsgSeedBridge.extractMinecraftSeed(decodedSeed));

        InGame host = new InGame(seed, "mode-test", InGame.SeedType.FIXED, false);
        host.targetStructure = "rooms-mix";
        Room room = new Room("mode-test", seed, new Player("Host", true, true), 2);
        RoomSnapshot snapshot = RoomSnapshot.fromJson(RoomSnapshot.capture(room, host).toJson());
        assertEquals("rooms-mix", snapshot.filter);
        assertEquals(seed, snapshot.seed);
        InGame guest = new InGame(snapshot.seed, "mode-test", InGame.SeedType.FIXED, false);
        guest.targetStructure = snapshot.filter;
        assertEquals("rpseedbank", host.getActiveFilter());
        assertEquals(host.getActiveFilter(), guest.getActiveFilter());
        guest.setSeed(seed);
        assertEquals("rooms-mix", guest.targetStructure);
        assertEquals("rpseedbank", guest.getActiveFilter());
        assertEquals("manual:123", ZsgSeedBridge.seedSpecificationFromSeed("123|structure:manual|iron:4"));
    }

    @Test
    void everyMixedResultUsesItsConcreteGameplayFilter() {
        for (int roll = 0; roll < 100; roll++) {
            String filter = ZsgRoomsSeedMode.filterForRoll(roll);
            InGame game = new InGame("123|structure:" + filter + "|iron:4|selection:rooms-mix", "room", InGame.SeedType.FIXED, false);
            game.targetStructure = "rooms-mix";
            assertEquals(filter, game.getActiveFilter());
        }
    }
}
