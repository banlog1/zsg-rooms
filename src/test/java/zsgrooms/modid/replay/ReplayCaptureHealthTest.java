package zsgrooms.modid.replay;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplayCaptureHealthTest {
    @Test void measuresActualSamplingWithoutCallingMissedFramesDrops() {
        ReplayCaptureHealth health = new ReplayCaptureHealth();
        ReplayBuffer.Snapshot packets = new ReplayBuffer().snapshot();
        assertTrue(health.due(0));
        assertTrue(health.update(0, "Super Detailed", 240, "recording", new long[]{5, 0, 0, 4096}, packets, "recording")
                .contains("--/240 Hz"));
        assertFalse(health.due(999_999_999L));
        assertTrue(health.due(1_000_000_000L));
        String sampled = health.update(2_000_000_000L, "Super Detailed", 240, "recording",
                new long[]{125, 0, 2048, 4096}, packets, "recording");
        assertTrue(sampled.contains("60/240 Hz"));
        assertTrue(sampled.contains("Dropped: 0"));
        assertTrue(sampled.contains("Queue: 50%"));
        assertEquals(sampled, health.text());
    }

    @Test void handlesStandardUnavailableAndOptionalTrackFailures() {
        ReplayCaptureHealth health = new ReplayCaptureHealth();
        ReplayBuffer.Snapshot packets = new ReplayBuffer().snapshot();
        assertEquals("Standard | Camera: Standard | Queue: 0%",
                health.update(0, "Standard", 0, "off", null, packets, "off"));
        assertTrue(health.update(1_000_000_000L, "Detailed", 60, "unsupported", null, packets, "failed")
                .endsWith("Camera: unsupported | Queue: 0% | Detail: failed"));
        assertTrue(health.update(2_000_000_000L, "Detailed", 60, "capped", new long[]{0, 3, 0, 4096}, packets, "recording")
                .contains("Dropped: 3 (capped)"));
    }
}
