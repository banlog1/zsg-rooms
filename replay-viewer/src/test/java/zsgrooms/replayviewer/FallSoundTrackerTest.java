// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FallSoundTrackerTest {
    @Test void vanillaFallThresholdsAndLandingModifiers() {
        assertEquals(0, FallSoundTracker.damage(3, 0, false, false));
        assertEquals(1, FallSoundTracker.damage(3.1F, 0, false, false));
        assertEquals(4, FallSoundTracker.damage(7, 0, false, false));
        assertEquals(5, FallSoundTracker.damage(8, 0, false, false));
        assertEquals(0, FallSoundTracker.damage(4, 1, false, false));
        assertEquals(3, FallSoundTracker.damage(12, 0, true, false));
        assertEquals(2, FallSoundTracker.damage(12, 0, false, true));
    }

    private FallSoundTracker descent() {
        FallSoundTracker falls = new FallSoundTracker();
        falls.move(0, 0, 70, 0, true, true);
        falls.move(50, 0, 68, 0, false, true);
        falls.move(100, 0, 66, 0, false, true);
        return falls;
    }

    @Test void damageBeforeLandingEmitsOnce() {
        FallSoundTracker falls = descent();
        assertEquals(0, falls.hurt(100));
        assertEquals(4, falls.move(150, 0, 65, 0, true, true));
        assertEquals(0, falls.move(200, 0, 65, 0, true, true));
        assertEquals(0, falls.hurt(200));
    }

    @Test void standingStillDoesNotRequireMovementPackets() {
        FallSoundTracker falls = new FallSoundTracker();
        falls.move(0, 0, 70, 0, true, true);
        falls.move(3000, 0, 68, 0, false, true);
        falls.move(3050, 0, 66, 0, false, true);
        falls.hurt(3050);
        assertEquals(4, falls.move(3100, 0, 65, 0, true, true));
    }

    @Test void damageAfterLandingEmitsOnce() {
        FallSoundTracker falls = descent();
        assertEquals(0, falls.move(150, 0, 65, 0, true, true));
        assertEquals(4, falls.hurt(200));
        assertEquals(0, falls.hurt(250));
    }

    @Test void harmlessFallsAndGroundDamageAreSilent() {
        FallSoundTracker falls = descent();
        assertEquals(0, falls.move(150, 0, 65, 0, true, true));
        assertEquals(0, falls.hurt(450));
        falls.reset();
        falls.move(0, 0, 65, 0, true, true);
        assertEquals(0, falls.hurt(50));
        falls.move(50, 0, 64, 0, false, true);
        assertEquals(0, falls.move(100, 0, 63, 0, true, true));
    }

    @Test void waterFlightAndOtherIneligibleMovementClearEvidence() {
        FallSoundTracker falls = descent();
        falls.hurt(100);
        assertEquals(0, falls.move(150, 0, 65, 0, true, false));
        assertEquals(0, falls.move(200, 0, 65, 0, true, true));
        assertEquals(0, falls.hurt(200));
    }

    @Test void resetsDiscontinuitiesAndBackwardsTimeCannotCreateImpacts() {
        for (int mode = 0; mode < 4; mode++) {
            FallSoundTracker falls = descent();
            falls.hurt(100);
            if (mode == 0) falls.reset();
            int time = mode == 1 ? 1000 : mode == 2 ? 0 : 150;
            assertEquals(0, falls.move(time, mode == 3 ? 100 : 0, 65, 0, true, true));
            assertEquals(0, falls.hurt(time));
        }
    }

    @Test void enablingInMidairDoesNotInventACompleteFall() {
        FallSoundTracker falls = new FallSoundTracker();
        falls.move(0, 0, 70, 0, false, true);
        falls.move(50, 0, 64, 0, false, true);
        falls.hurt(50);
        assertEquals(0, falls.move(100, 0, 63, 0, true, true));
    }

    @Test void lateOrOldDamageCannotMatchALanding() {
        FallSoundTracker falls = descent();
        falls.hurt(100);
        assertEquals(0, falls.move(400, 0, 65, 0, true, true));
        assertEquals(0, falls.hurt(701));
    }
}
