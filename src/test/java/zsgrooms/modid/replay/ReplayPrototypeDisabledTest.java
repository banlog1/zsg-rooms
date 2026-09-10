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
            ReplayPrototype.raceStarted("race", java.util.UUID.randomUUID(), 1L);
            ReplayPrototype.raceFinished("race", 1L, 1L);
            ReplayPrototype.stopRecording();
        });
        assertFalse(ReplayPrototype.hasSession());
    }
}
