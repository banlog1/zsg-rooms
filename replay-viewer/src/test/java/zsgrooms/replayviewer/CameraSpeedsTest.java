// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.replay.camera.CameraController;
import org.junit.jupiter.api.Test;
import zsgrooms.replayviewer.mixin.ClassicSpeedAccessor;
import zsgrooms.replayviewer.mixin.DirectSpeedAccessor;
import static org.junit.jupiter.api.Assertions.*;

class CameraSpeedsTest {
    private abstract static class Controller implements CameraController {
        public void update(float delta) {}
        public void increaseSpeed() {}
        public void decreaseSpeed() {}
    }
    private static final class Direct extends Controller implements DirectSpeedAccessor {
        int speed;
        Direct(int speed) { this.speed = speed; }
        public int zsgViewer$getSpeed() { return speed; }
        public void zsgViewer$setSpeed(int speed) { this.speed = speed; }
    }
    private static final class Classic extends Controller implements ClassicSpeedAccessor {
        double speed;
        Classic(double speed) { this.speed = speed; }
        public double zsgViewer$getSpeed() { return speed; }
        public void zsgViewer$setSpeed(double speed) { this.speed = speed; }
    }

    @Test void modesKeepIndependentSpeedsAcrossControllerReplacement() {
        CameraSpeeds state = new CameraSpeeds();
        state.remember(new Direct(13));
        state.remember(new Classic(7.25));
        Direct direct = new Direct(1);
        Classic classic = new Classic(1);
        state.restore(direct);
        state.restore(classic);
        assertEquals(13, direct.speed);
        assertEquals(7.25, classic.speed);
    }

    @Test void unselectedModesRetainVanillaDefaults() {
        CameraSpeeds state = new CameraSpeeds();
        state.remember(null);
        Direct direct = new Direct(4);
        state.restore(direct);
        assertEquals(4, direct.speed);
    }

    @Test void followDistanceIsBoundedAndSharedAcrossReplacements() {
        FarFollowController.Distance distance = new FarFollowController.Distance();
        assertEquals(8.0, distance.blocks);
        distance.change(100);
        assertEquals(32.0, distance.blocks);
        distance.change(-100);
        assertEquals(2.0, distance.blocks);
    }
}
