package zsgrooms.modid;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;
import zsgrooms.modid.net.RoomSnapshot;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RoomRuleSettingsTest {
    @Test void everyRuleRoundTripsIndependentlyThroughGameAndSnapshot() throws Exception {
        for (Field field : RoomRuleSettings.class.getFields()) {
            RoomRuleSettings rules = new RoomRuleSettings();
            field.setBoolean(rules, true);
            InGame game = new InGame("123|structure:rooms-temple-v5", "rules", InGame.SeedType.FIXED, false);
            RoomRuleSettings.fromJson(rules.toJson()).applyTo(game);
            assertEquals(rules.toJson(), RoomRuleSettings.capture(game).toJson(), field.getName());
            Room room = new Room("rule-test-" + UUID.randomUUID(), game.getSeed(), new Player("Host", true, true), 2);
            assertTrue(ZsgRooms.applyRoomSnapshot(RoomSnapshot.capture(room, game).toJson()));
            assertEquals(rules.toJson(), RoomRuleSettings.capture(ZsgRooms.getGame(room.roomName)).toJson());
        }
    }

    @Test void onlyLobbyHostCanApplyChangesAndOtherMatchStateIsUntouched() {
        Room room = new Room("rule-test-" + UUID.randomUUID(), "123|structure:rooms-temple-v5",
                new Player("Host", true, true), 2);
        room.addPlayer(new Player("Guest", true, false));
        InGame source = new InGame(room.seed, room.roomName, InGame.SeedType.FIXED, false);
        source.targetStructure = "rooms-mix";
        source.restoreRaceId("same-race");
        source.setPlayerProgress("Guest", 4, "Entered Nether");
        ZsgRooms.applyRoomSnapshot(RoomSnapshot.capture(room, source).toJson());
        InGame game = ZsgRooms.getGame(room.roomName);
        RoomRuleSettings rules = RoomRuleSettings.capture(game);
        rules.sharedNetherEntry = true;
        assertFalse(ZsgRooms.changeRoomRules(room.roomName, "Guest", rules.toJson()));
        assertFalse(ZsgRooms.changeRoomRules(room.roomName, "stranger", rules.toJson()));
        assertFalse(game.hasSharedNetherEntry());
        assertTrue(ZsgRooms.changeRoomRules(room.roomName, "Host", rules.toJson()));
        assertTrue(game.hasSharedNetherEntry());
        assertEquals(source.seed, game.seed);
        assertEquals(source.targetStructure, game.targetStructure);
        assertEquals("same-race", game.getRaceId());
        assertEquals(source.getPlayerProgress(), game.getPlayerProgress());
        assertFalse(ZsgRooms.changeRoomRules(room.roomName, "Host", rules.toJson()));
        rules.sharedNetherEntry = false;
        game.setInGame(true);
        assertFalse(ZsgRooms.changeRoomRules(room.roomName, "Host", rules.toJson()));
        assertTrue(game.hasSharedNetherEntry());
        game.endGame();
        assertTrue(ZsgRooms.changeRoomRules(room.roomName, "Host", rules.toJson()));
        assertFalse(ZsgRooms.changeRoomRules("missing", "Host", rules.toJson()));
    }

    @Test void malformedOrPartialRulePayloadsAreRejected() {
        for (String value : new String[]{null, "", "null", "[]", "{}", "{broken"}) {
            assertNull(RoomRuleSettings.fromJson(value));
        }
        JsonObject object = new JsonParser().parse(new RoomRuleSettings().toJson()).getAsJsonObject();
        object.addProperty("allowCheats", "true");
        assertNull(RoomRuleSettings.fromJson(object.toString()));
        object.remove("allowCheats");
        assertNull(RoomRuleSettings.fromJson(object.toString()));
    }

    @Test void legacyPacketSupportsCompleteRulePayload() {
        String json = new RoomRuleSettings().toJson();
        PacketByteBuf packet = ZsgRoomNetworking.packet("rules", "room", "Host", json);
        try {
            String action = packet.readString(64);
            assertEquals("rules", action);
            assertEquals("room", packet.readString(64));
            assertEquals("Host", packet.readString(64));
            assertEquals(json, packet.readString(ZsgRoomNetworking.valueLimit(action)));
            assertEquals(256, ZsgRoomNetworking.valueLimit("chat"));
        } finally { packet.release(); }
    }
}
