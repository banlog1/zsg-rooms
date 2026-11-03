package zsgrooms.modid.replay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ReplayPresentationCaptureTest {
    @Test void disabledHooksDoNotResolveTheClientOrReadAnyLiveState() {
        assertDoesNotThrow(() -> {
            ReplayPresentationCapture.stop(null);
            ReplayPresentationCapture.invalidate();
            ReplayPresentationCapture.beginFrame(Float.NaN);
            ReplayPresentationCapture.fov(true, Double.NaN, Float.NaN, Float.NaN, Float.NaN);
            ReplayPresentationCapture.hands(null, null, Float.NaN);
            ReplayPresentationCapture.endFrame();
        });
    }
}
