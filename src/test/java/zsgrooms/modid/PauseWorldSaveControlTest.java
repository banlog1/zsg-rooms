package zsgrooms.modid;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PauseWorldSaveControlTest {
    @AfterEach
    public void resetControl() {
        PauseWorldSaveControl.configure(false);
    }

    @Test
    public void optionDefaultsToDisabled() {
        assertFalse(PauseWorldSaveControl.isEnabled());
    }

    @Test
    public void configureEnablesAndDisablesOption() {
        PauseWorldSaveControl.configure(true);
        assertTrue(PauseWorldSaveControl.isEnabled());

        PauseWorldSaveControl.configure(false);
        assertFalse(PauseWorldSaveControl.isEnabled());
    }

    @Test
    public void enabledWithoutManagedRoomDoesNotSkip() {
        PauseWorldSaveControl.configure(true);
        assertFalse(shouldSkip(false, false, false, false));
    }

    @Test
    public void enabledWithRoomButNoGameDoesNotSkip() {
        PauseWorldSaveControl.configure(true);
        assertFalse(shouldSkip(true, true, false, false));
    }

    @Test
    public void enabledWithInactiveGameDoesNotSkip() {
        PauseWorldSaveControl.configure(true);
        assertFalse(shouldSkip(true, true, true, false));
    }

    @Test
    public void enabledWithActiveManagedRaceSkips() {
        PauseWorldSaveControl.configure(true);
        assertTrue(shouldSkip(true, true, true, true));
    }

    @Test
    public void disabledWithActiveManagedRaceDoesNotSkip() {
        PauseWorldSaveControl.configure(false);
        assertFalse(shouldSkip(true, true, true, true));
    }

    @Test
    public void malformedStateFailsSafely() {
        PauseWorldSaveControl.configure(true);

        assertFalse(PauseWorldSaveControl.shouldSkipPauseWorldSave(
                new PauseWorldSaveControl.RaceStateLookup() {
                    @Override
                    public PauseWorldSaveControl.RaceState read() {
                        throw new IllegalStateException("malformed state");
                    }
                }));
    }

    private boolean shouldSkip(
            boolean managedRoom,
            boolean activeRoom,
            boolean activeGame,
            boolean raceActive
    ) {
        final PauseWorldSaveControl.RaceState state =
                new PauseWorldSaveControl.RaceState(
                        managedRoom, activeRoom, activeGame, raceActive);
        return PauseWorldSaveControl.shouldSkipPauseWorldSave(
                new PauseWorldSaveControl.RaceStateLookup() {
                    @Override
                    public PauseWorldSaveControl.RaceState read() {
                        return state;
                    }
                });
    }
}
