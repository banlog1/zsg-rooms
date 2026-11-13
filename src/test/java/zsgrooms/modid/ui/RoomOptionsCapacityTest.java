package zsgrooms.modid.ui;

import org.junit.jupiter.api.Test;
import zsgrooms.modid.InGame;
import zsgrooms.modid.Player;
import zsgrooms.modid.Room;
import zsgrooms.modid.RoomRuleSettings;
import zsgrooms.modid.Tournament;
import zsgrooms.modid.TournamentSettings;
import zsgrooms.modid.ZsgRooms;
import zsgrooms.modid.net.RoomSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RoomOptionsCapacityTest {
    private Room lobby() {
        Room room = new Room("options-capacity-" + UUID.randomUUID(), "123", new Player("Host", true, true), 8);
        room.addPlayer(new Player("Guest", true, false));
        InGame game = new InGame(room.seed, room.roomName, InGame.SeedType.FIXED, false);
        game.targetStructure = "rooms-mix";
        assertTrue(ZsgRooms.applyRoomSnapshot(RoomSnapshot.capture(room, game).toJson()));
        return ZsgRooms.getRoom(room.roomName);
    }

    @Test void invalidCapacityAndInvalidSeedSendNoActions() {
        Room room = lobby();
        String before = ZsgRooms.createRoomSnapshot(room.roomName);
        List<String> actions = new ArrayList<>();
        for (String value : new String[]{"", "1", "65", "broken"}) {
            String error = RoomOptionsScreen.applyOptions(room.roomName, "Host", value, "rooms-mix", 3, 2,
                    (action, payload) -> actions.add(action));
            assertEquals("Capacity must be 2-64", error);
            assertTrue(actions.isEmpty());
            assertEquals(before, ZsgRooms.createRoomSnapshot(room.roomName));
        }
        assertEquals("Enter Seed", RoomOptionsScreen.applyOptions(room.roomName, "Host", "4", "manual:", 3, 2,
                (action, payload) -> actions.add(action)));
        assertTrue(actions.isEmpty());
        assertEquals(before, ZsgRooms.createRoomSnapshot(room.roomName));
    }

    @Test void rosterGrowthWhileOptionsAreOpenIsAnErrorNotASilentClamp() {
        Room room = lobby();
        String requested = "2";
        assertEquals("", RoomOptionsScreen.capacityError(room, ZsgRooms.getGame(room.roomName), "Host", requested));
        room.addPlayer(new Player("Third", true, false));
        String before = ZsgRooms.createRoomSnapshot(room.roomName);
        List<String> actions = new ArrayList<>();
        assertEquals("Capacity below roster (3)", RoomOptionsScreen.applyOptions(room.roomName, "Host", requested,
                "rooms-mix", 3, 2, (action, payload) -> actions.add(action)));
        assertTrue(actions.isEmpty());
        assertEquals(before, ZsgRooms.createRoomSnapshot(room.roomName));
    }

    @Test void capacityRejectionDuringSendDoesNotApplyFilterOrRules() {
        Room room = lobby();
        InGame game = ZsgRooms.getGame(room.roomName);
        String rules = RoomRuleSettings.capture(game).toJson();
        List<String> actions = new ArrayList<>();
        String error = RoomOptionsScreen.applyOptions(room.roomName, "Host", "2", "rooms-village-v5", 3, 2,
                (action, payload) -> {
                    actions.add(action);
                    room.addPlayer(new Player("Third", true, false));
                    ZsgRooms.applyRoomAction(action, room.roomName, "Host", payload);
                });
        assertEquals("Capacity below roster (3)", error);
        assertEquals(Arrays.asList("capacity"), actions);
        assertEquals(8, room.maxPlayers);
        assertEquals("rooms-mix", game.targetStructure);
        assertEquals(rules, RoomRuleSettings.capture(game).toJson());
    }

    @Test void unconfirmedCapacityDoesNotApplyOtherSettings() {
        Room room = lobby();
        String before = ZsgRooms.createRoomSnapshot(room.roomName);
        List<String> actions = new ArrayList<>();
        assertEquals("Capacity not confirmed; retry", RoomOptionsScreen.applyOptions(room.roomName, "Host", "4",
                "rooms-mix", 3, 2, (action, payload) -> actions.add(action)));
        assertEquals(Arrays.asList("capacity"), actions);
        assertEquals(before, ZsgRooms.createRoomSnapshot(room.roomName));
    }

    @Test void validCapacityIsConfirmedBeforeApplyingOtherSettings() {
        Room room = lobby();
        List<String> actions = new ArrayList<>();
        assertEquals("", RoomOptionsScreen.applyOptions(room.roomName, "Host", "4", "rooms-mix", 3, 2,
                (action, payload) -> {
                    actions.add(action);
                    if ("capacity".equals(action)) {
                        assertTrue(ZsgRooms.changeRoomCapacity(room.roomName, "Host", payload));
                    } else {
                        assertEquals(4, room.maxPlayers);
                        if ("rules".equals(action)) {
                            RoomRuleSettings rules = RoomRuleSettings.fromJson(payload);
                            assertNotNull(rules);
                            assertEquals(3, rules.seedCount);
                            assertEquals(2, rules.finisherLimit);
                        }
                    }
                }));
        assertEquals(Arrays.asList("capacity", "filter", "rules"), actions);
        actions.clear();
        assertEquals("", RoomOptionsScreen.applyOptions(room.roomName, "Host", "4", "rooms-mix", 3, 2,
                (action, payload) -> actions.add(action)));
        assertEquals(Arrays.asList("filter", "rules"), actions);
    }

    @Test void guestRaceAndTournamentLockPreventAllApplyActions() {
        Room room = lobby();
        InGame game = ZsgRooms.getGame(room.roomName);
        List<String> actions = new ArrayList<>();
        assertFalse(RoomOptionsScreen.applyOptions(room.roomName, "Guest", "4", "rooms-mix", 3, 2,
                (action, payload) -> actions.add(action)).isEmpty());
        game.setInGame(true);
        assertFalse(RoomOptionsScreen.applyOptions(room.roomName, "Host", "4", "rooms-mix", 3, 2,
                (action, payload) -> actions.add(action)).isEmpty());
        game.setInGame(false);
        TournamentSettings settings = new TournamentSettings();
        settings.enabled = true;
        settings.order = room.getPlayerNames();
        game.setTournament(new Tournament(settings));
        assertFalse(RoomOptionsScreen.applyOptions(room.roomName, "Host", "4", "rooms-mix", 3, 2,
                (action, payload) -> actions.add(action)).isEmpty());
        assertTrue(actions.isEmpty());
        assertFalse(RoomOptionsScreen.capacityError(null, null, "Host", "4").isEmpty());
    }

    @Test void capacityRowErrorAndFooterFitCompactAndNormalViewports() {
        for (boolean details : new boolean[]{false, true}) for (int height : new int[]{180, 200, 239, 240, 360, 540}) {
            int panelHeight = RoomOptionsScreen.panelHeight(height, details);
            int top = Math.max(2, (height - panelHeight) / 2);
            assertTrue(top + panelHeight <= height);
            for (int row = 0; row < 4; row++) {
                assertTrue(RoomOptionsScreen.rowY(height, row) + 20 <= RoomOptionsScreen.rowY(height, row + 1));
            }
            int errorY = panelHeight - 40;
            int footerY = panelHeight - 28;
            assertTrue(RoomOptionsScreen.rowY(height, details ? 4 : 3) + 20 <= errorY);
            assertTrue(errorY + 9 <= footerY);
            assertTrue(footerY + 20 <= panelHeight);
        }
        for (int width : new int[]{320, 360, 480, 640}) {
            int panelWidth = Math.min(284, width - 20);
            int contentWidth = panelWidth - 32;
            assertTrue(contentWidth - 86 >= "Players (2-64)".length() * 6 + 8);
            assertTrue(contentWidth >= "Capacity needs an unlocked lobby host".length() * 6);
        }
    }

    @Test void onlyManualAndAaNeedTheExtraRow() {
        for (FilterCatalog.Entry entry : FilterCatalog.ENTRIES) {
            assertEquals("manual".equals(entry.id) || zsgrooms.modid.AaThunderless.FILTER.equals(entry.id),
                    RoomOptionsScreen.hasFilterDetails(entry.id), entry.id);
        }
    }
}
