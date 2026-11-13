package zsgrooms.modid.ui;

import org.junit.jupiter.api.Test;
import zsgrooms.modid.InGame;
import zsgrooms.modid.Room;
import zsgrooms.modid.RoomRuleSettings;
import zsgrooms.modid.ZsgRooms;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RoomEntryDefaultsTest {
    @Test void oneClickUsesRoomsCollectionAndAllStandardRulesWithoutStartingRace() {
        String code = RoomEntryDefaults.newCode();
        RoomEntryDefaults.create(code, "Host", "");
        Room room = ZsgRooms.getRoom(code);
        InGame game = ZsgRooms.getGame(code);
        assertEquals(10, room.maxPlayers);
        assertEquals(1, room.getPlayerCount());
        assertEquals("rooms-mix", game.targetStructure);
        assertEquals(RoomRulePreset.STANDARD_ZSG_ROOMS, RoomRulePreset.matching(RoomRuleSettings.capture(game)));
        assertFalse(game.areCheatsAllowed());
        assertFalse(game.getIsInGame());
        assertFalse(game.getTournamentSettings().enabled);
        assertEquals(1, game.getFinishGoal());
        assertEquals(1, game.getFinisherLimit());
    }

    @Test void generatedCodesAreShareableAndLegacyCodesStayValid() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String code = RoomEntryDefaults.newCode();
            assertTrue(code.matches("[A-HJ-NP-Z2-9]{8}"));
            assertTrue(codes.add(code));
        }
        for (String code : new String[]{"ABCD", "legacy_code-42", "aBcD1234"}) assertTrue(RoomEntryDefaults.validCode(code));
        for (String code : new String[]{null, "", "abc", "abcd efgh", "http://host", "ABCD\n"}) assertFalse(RoomEntryDefaults.validCode(code));
    }

    @Test void roomProblemsAreNotMisreportedAsRelayOutages() {
        for (String reason : new String[]{"Room is full", "Room host is offline", "That player is already connected", "HTTP/1.1 404 Not Found", "HTTP/1.1 409 Conflict"})
            assertFalse(RoomEntryError.from(reason, false).connectionProblem, reason);
        assertEquals("Code already in use", RoomEntryError.from("Room code is already owned", true).title);
        assertTrue(RoomEntryError.from("Connection timed out", true).connectionProblem);
        assertTrue(RoomEntryError.from("HTTP/1.1 503 Service Unavailable", false).connectionProblem);
        assertTrue(RoomEntryError.from("Relay rejected connection: HTTP/1.1 502 Bad Gateway", false).connectionProblem);
        assertTrue(RoomEntryError.from("Relay rejected connection: HTTP/1.1 429 Too Many Requests", true).connectionProblem);
    }
}
