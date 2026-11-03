package zsgrooms.modid.replay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Field;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ReplayRecordingSettingsTest {
    @TempDir Path temp;

    @Test void independentChangesNeverDowngradeSuperDetailedOrCameraRate() throws Exception {
        Field preferences = ReplayPrototype.class.getDeclaredField("preferences");
        Field file = ReplayPrototype.class.getDeclaredField("preferencesFile");
        preferences.setAccessible(true);
        file.setAccessible(true);
        Object oldPreferences = preferences.get(null), oldFile = file.get(null);
        try {
            file.set(null, temp.resolve("replay.properties"));
            preferences.set(null, new ReplayPreferences(false, "", false, "", true, false, false,
                    ReplayPreferences.RecordingDetail.SUPER_DETAILED, ReplayPreferences.CameraRate.HZ_120, true));
            assertTrue(ReplayPrototype.configureHud(false));
            assertTrue(ReplayPrototype.configureSeedRetention(true));
            assertTrue(ReplayPrototype.configurePerformance(true));
            assertTrue(ReplayPrototype.configureTestGroup(java.util.UUID.randomUUID().toString()));
            ReplayPreferences changed = ReplayPrototype.getPreferences();
            assertEquals(ReplayPreferences.RecordingDetail.SUPER_DETAILED, changed.recordingDetail);
            assertEquals(ReplayPreferences.CameraRate.HZ_120, changed.cameraRate);
            assertTrue(changed.showCaptureHealth);
            assertTrue(changed.detailedCapture);
            assertTrue(changed.performanceMode);
            assertTrue(changed.keepSeedChanges);
            assertFalse(changed.showRecordingHud);
            assertTrue(ReplayPrototype.configureCameraRate(ReplayPreferences.CameraRate.HZ_240));
            assertTrue(ReplayPrototype.configureCaptureHealth(false));
            assertEquals(ReplayPreferences.RecordingDetail.SUPER_DETAILED, ReplayPrototype.getPreferences().recordingDetail);
            assertTrue(ReplayPrototype.configureRecordingDetail(ReplayPreferences.RecordingDetail.DETAILED));
            assertEquals(ReplayPreferences.CameraRate.HZ_240, ReplayPrototype.getPreferences().cameraRate);
            assertTrue(ReplayPrototype.getPreferences().performanceMode);
            assertEquals(ReplayPreferences.RecordingDetail.DETAILED,
                    ReplayPreferences.load(temp.resolve("replay.properties")).recordingDetail);
        } finally {
            preferences.set(null, oldPreferences);
            file.set(null, oldFile);
        }
    }
}
