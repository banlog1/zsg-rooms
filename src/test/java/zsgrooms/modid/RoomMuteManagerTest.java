package zsgrooms.modid;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RoomMuteManagerTest {
    @AfterEach
    public void clearMutedPlayers() {
        RoomMuteManager.clearAll();
    }

    @Test
    public void togglesPlayersIndependentlyPerRoom() {
        assertTrue(RoomMuteManager.toggle("room-a", "Runner"));
        assertTrue(RoomMuteManager.isMuted("room-a", "runner"));
        assertFalse(RoomMuteManager.isMuted("room-b", "Runner"));

        assertFalse(RoomMuteManager.toggle("room-a", "RUNNER"));
        assertFalse(RoomMuteManager.isMuted("room-a", "Runner"));
    }

    @Test
    public void hidesChatAndAdvancementsButKeepsRaceEvents() {
        RoomMuteManager.toggle("room", "Runner");

        assertTrue(RoomMuteManager.shouldHideRoomMessage("room", "<Runner> hello"));
        assertTrue(RoomMuteManager.shouldHideRoomMessage(
                "room", "Runner made the advancement [Those Were the Days]"));
        assertFalse(RoomMuteManager.shouldHideRoomMessage("room", "Runner requested a seed change"));
        assertFalse(RoomMuteManager.shouldHideRoomMessage("room", "Match winner: Runner"));
    }
}
