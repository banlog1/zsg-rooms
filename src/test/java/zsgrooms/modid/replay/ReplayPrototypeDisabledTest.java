package zsgrooms.modid.replay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReplayPrototypeDisabledTest {
    @Test
    void defaultOffHooksDoNotNeedAClientConnectionOrReplayLibrary() {
        assertFalse(Boolean.getBoolean("zsgrooms.replayPrototype"), "Run this test without the opt-in JVM flag");
        assertDoesNotThrow(() -> {
            ReplayPrototype.connected(null);
            ReplayPrototype.received(null, null);
            ReplayPrototype.worldApplied(null);
            ReplayPrototype.disconnected(null);
            ReplayPrototype.beginReset();
            ReplayPrototype.clientDisconnect();
            ReplayPrototype.cancelReset();
            ReplayPrototype.stopRecording();
            ReplayPrototype.captureCamera();
            ReplayPresentationCapture.beginFrame(0);
            ReplayPresentationCapture.fov(true, 70, 1, 0, 0);
            ReplayPresentationCapture.hands(null, null, 0);
            ReplayPresentationCapture.endFrame();
            ReplayPresentationCapture.invalidate();
            ReplayLocalSoundCapture.player(null, null, null, 0, 0);
        ReplayLocalSoundCapture.mining(null, null, 1, 1, null);
            assertNull(ReplayPrototype.getCaptureHealthStatus());
            ReplayPrototype.raceStarted("race", java.util.UUID.randomUUID(), 1L);
            ReplayPrototype.raceFinished("race", 1L, 1L);
            ReplayPrototype.stopRecording();
        });
        assertFalse(ReplayPrototype.hasSession());
    }
}
