package zsgrooms.modid.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RaceReturnTimerTest {
    @Test void returnsOnceAfterNinetyTicksInTheSameRace() {
        RaceReturnTimer timer = new RaceReturnTimer();
        timer.bind("room", "race");
        timer.start();
        for (int tick = 0; tick < 89; tick++) assertFalse(timer.tick("room", "race"));
        assertTrue(timer.tick("room", "race"));
        assertFalse(timer.tick("room", "race"));
    }

    @Test void newRaceCannotInheritOrReviveOldReturnCountdown() {
        RaceReturnTimer timer = new RaceReturnTimer();
        timer.bind("room", "old");
        timer.start();
        for (int tick = 0; tick < 89; tick++) assertFalse(timer.tick("room", "old"));
        assertFalse(timer.tick("room", "new"));
        assertFalse(timer.tick("room", "old"));
        timer.bind("room", "new");
        timer.start();
        for (int tick = 0; tick < 89; tick++) assertFalse(timer.tick("room", "new"));
        assertTrue(timer.tick("room", "new"));
    }

    @Test void leavingOrChangingRoomsCancelsReturnEvenIfRaceIdMatches() {
        RaceReturnTimer timer = new RaceReturnTimer();
        timer.bind("room", "race");
        timer.start();
        assertFalse(timer.tick("other", "race"));
        assertFalse(timer.retain("room", "race"));
        timer.bind("room", "race");
        timer.start();
        assertFalse(timer.tick(null, null));
        assertFalse(timer.retain("room", "race"));
    }

    @Test void deferredResultHasNoCountdownAndExpiresOnRaceChange() {
        RaceReturnTimer timer = new RaceReturnTimer();
        timer.bind("room", "race");
        for (int tick = 0; tick < 100; tick++) assertFalse(timer.tick("room", "race"));
        assertTrue(timer.retain("room", "race"));
        assertFalse(timer.retain("room", "new"));
        assertFalse(timer.retain("room", "race"));
    }

    @Test void manualReturnOrLaunchCanCancelBeforeTheNextTick() {
        RaceReturnTimer timer = new RaceReturnTimer();
        timer.bind("room", "race");
        timer.start();
        timer.clear();
        for (int tick = 0; tick < 100; tick++) assertFalse(timer.tick("room", "race"));
        assertFalse(timer.retain("room", "race"));
    }
}
