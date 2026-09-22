// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplayAudioTest {
    @Test void seekingWaitsForTwoSettledFramesAndRepeatedSeeksRestartWait() {
        SeekAudioGate gate = new SeekAudioGate();
        assertFalse(gate.muted());
        gate.begin();
        for (int i = 0; i < 100; i++) assertFalse(gate.frame(true));
        assertTrue(gate.muted());
        assertFalse(gate.frame(false));
        gate.begin();
        assertFalse(gate.frame(false));
        assertTrue(gate.muted());
        assertTrue(gate.frame(false));
        assertFalse(gate.muted());
        assertFalse(gate.frame(false));
    }

    @Test void asynchronousCatchupRestartsSettlement() {
        SeekAudioGate gate = new SeekAudioGate();
        gate.begin();
        gate.frame(false);
        gate.frame(true);
        assertFalse(gate.frame(false));
        assertTrue(gate.muted());
        assertTrue(gate.frame(false));
    }

    @Test void footstepsNeedDistanceNotCameraMovementOrElapsedTime() {
        PlayerSoundCadence cadence = new PlayerSoundCadence();
        assertFalse(cadence.step(0, 64, 0, true));
        for (int i = 0; i < 100; i++) assertFalse(cadence.step(0, 64, 0, true));
        assertFalse(cadence.step(1, 64, 0, true));
        assertTrue(cadence.step(2, 64, 0, true));
        assertFalse(cadence.step(2, 64, 0, true));
    }

    @Test void teleportsAirborneAndResetsDoNotGenerateCatchupSteps() {
        PlayerSoundCadence cadence = new PlayerSoundCadence();
        cadence.step(0, 64, 0, true);
        assertFalse(cadence.step(200, 64, 0, true));
        assertFalse(cadence.step(202, 64, 0, false));
        assertFalse(cadence.step(202.1, 64, 0, true));
        cadence.reset();
        assertFalse(cadence.step(-200, 64, 0, true));
    }

    @Test void aSingleLongInteractionSwingIsNotMining() {
        MiningSoundTracker mining = new MiningSoundTracker();
        for (int phase = 0; phase < 12; phase++) assertFalse(mining.sample(phase * 50, 123, 1, phase));
    }

    @Test void repeatedSwingsAtTheSameBlockProduceBoundedMiningSounds() {
        MiningSoundTracker mining = new MiningSoundTracker();
        assertFalse(mining.sample(0, 123, 1, 0));
        assertFalse(mining.sample(50, 123, 1, 1));
        assertFalse(mining.sample(100, 123, 1, 2));
        assertTrue(mining.sample(150, 123, 1, 0));
        for (int i = 4; i < 7; i++) assertFalse(mining.sample(i * 50, 123, 1, i % 3));
        assertTrue(mining.sample(350, 123, 1, 1));
        assertFalse(mining.sample(350, 123, 1, 1));
    }

    @Test void changingTargetBlockPausingOrSeekingRequiresFreshEvidence() {
        for (int mode = 0; mode < 4; mode++) {
            MiningSoundTracker mining = new MiningSoundTracker();
            mining.sample(0, 123, 1, 0);
            mining.sample(50, 123, 1, 1);
            mining.sample(100, 123, 1, 2);
            assertFalse(mining.sample(mode == 2 ? 500 : mode == 3 ? 0 : 150,
                    mode == 0 ? 456 : 123, mode == 1 ? 2 : 1, 0));
        }
    }

    @Test void explicitResetClearsMiningEvidence() {
        MiningSoundTracker mining = new MiningSoundTracker();
        mining.sample(0, 1, 1, 1);
        mining.sample(50, 1, 1, 2);
        mining.reset();
        assertFalse(mining.sample(150, 1, 1, 0));
    }

    @Test void bucketsRequireMatchingHeldTypeAndSourceTransitions() {
        assertEquals(BucketSoundRules.Action.FILL_WATER, BucketSoundRules.action(1, 0, true, 1));
        assertEquals(BucketSoundRules.Action.FILL_LAVA, BucketSoundRules.action(2, 0, true, 1));
        assertEquals(BucketSoundRules.Action.EMPTY_WATER, BucketSoundRules.action(0, 1, false, 2));
        assertEquals(BucketSoundRules.Action.EMPTY_LAVA, BucketSoundRules.action(0, 2, false, 4));
        assertEquals(BucketSoundRules.Action.NONE, BucketSoundRules.action(0, 1, false, 4));
        assertEquals(BucketSoundRules.Action.NONE, BucketSoundRules.action(0, 2, false, 2));
        assertEquals(BucketSoundRules.Action.NONE, BucketSoundRules.action(1, 0, true, 0));
    }

    @Test void ordinaryFlowAndRepeatedUpdatesDoNotMakeBucketSounds() {
        assertEquals(BucketSoundRules.Action.NONE, BucketSoundRules.action(0, 0, false, 7));
        assertEquals(BucketSoundRules.Action.NONE, BucketSoundRules.action(1, 1, false, 7));
        assertEquals(BucketSoundRules.Action.NONE, BucketSoundRules.action(1, 0, false, 7));
    }
}
