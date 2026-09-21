// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class ReplayAnalysisTest {
    @Test void clusterCountsLargestOneBlockNeighborhoodNotAllPiglins() {
        assertEquals(0, PiglinClusters.largest(Collections.emptyList()));
        assertEquals(3, PiglinClusters.largest(Arrays.asList(new Vec3d(0, 0, 0),
                new Vec3d(0.4, 0, 0), new Vec3d(-0.4, 0, 0), new Vec3d(10, 0, 0))));
        assertEquals(1, PiglinClusters.largest(Arrays.asList(new Vec3d(0, 0, 0), new Vec3d(0, 1.01, 0))));
        assertEquals(2, PiglinClusters.largest(Arrays.asList(new Vec3d(0, 0, 0), new Vec3d(0, 1, 0))));
    }

    @Test void neighborhoodDoesNotFloodFillConnectedPiglins() {
        assertEquals(3, PiglinClusters.largest(Arrays.asList(new Vec3d(0, 0, 0), new Vec3d(1, 0, 0),
                new Vec3d(2, 0, 0), new Vec3d(3, 0, 0), new Vec3d(4, 0, 0))));
    }

    @Test void trailIsBoundedInReplayTimeAndSampleCount() {
        TrailHistory trail = new TrailHistory();
        for (int time = 0; time < 60000; time += 10) trail.add(time, time / 1000.0, 60, 0);
        assertEquals(101, trail.points.size());
        assertEquals(10000, trail.points.peekLast().time - trail.points.peekFirst().time);
    }

    @Test void pausedPlaybackDoesNotGrowTrail() {
        TrailHistory trail = new TrailHistory();
        for (int i = 0; i < 10000; i++) trail.add(50, 1, 2, 3);
        assertEquals(1, trail.points.size());
    }

    @Test void jumpsRewindsAndTeleportsBreakTrail() {
        TrailHistory trail = new TrailHistory();
        trail.add(500, 1, 2, 3);
        trail.add(600, 2, 2, 3);
        trail.add(100, 3, 2, 3);
        assertEquals(1, trail.points.size());
        trail.add(10000, 4, 2, 3);
        assertEquals(1, trail.points.size());
        trail.add(10100, 200, 2, 3);
        assertEquals(1, trail.points.size());
    }

    @Test void trailDurationClampsAndShrinksTheRetainedHistory() {
        TrailHistory trail = new TrailHistory();
        for (int time = 0; time <= 60000; time += 100) trail.add(time, 0, 0, 0, Integer.MAX_VALUE);
        assertEquals(301, trail.points.size());
        trail.add(60100, 0, 0, 0, 2000);
        assertEquals(21, trail.points.size());
        assertEquals(2000, trail.points.peekLast().time - trail.points.peekFirst().time);
    }

    @Test void stylesCopyIndependentlyAndResetToTheirOwnDefaults() {
        TrailStyle dragon = new TrailStyle(TrailStyle.Color.CYAN);
        TrailStyle piglin = new TrailStyle(TrailStyle.Color.GOLD);
        dragon.color = TrailStyle.Color.RED;
        dragon.seconds = 30;
        dragon.width = 4;
        dragon.opacity = 40;
        dragon.fade = true;
        piglin.copyFrom(dragon);
        dragon.reset();
        assertEquals(TrailStyle.Color.RED, piglin.color);
        assertEquals(30000, piglin.duration());
        assertEquals(4, piglin.lineWidth());
        assertEquals(40, piglin.opacity);
        assertTrue(piglin.fade);
        assertEquals(TrailStyle.Color.CYAN, dragon.color);
        piglin.reset();
        assertEquals(TrailStyle.Color.GOLD, piglin.color);
        assertEquals(10000, piglin.duration());
        assertEquals(1, piglin.lineWidth());
        assertFalse(piglin.fade);
    }

    @Test void fadeAndOpacityUseReplayAgeAndClampAtBothEnds() {
        TrailStyle style = new TrailStyle(TrailStyle.Color.CYAN);
        style.opacity = 100;
        assertEquals(255, style.alpha(10000, 0));
        style.fade = true;
        assertEquals(255, style.alpha(10000, 10000));
        assertEquals(128, style.alpha(10000, 5000));
        assertEquals(0, style.alpha(10000, 0));
        assertEquals(0, style.alpha(Integer.MAX_VALUE, 0));
        assertEquals(255, style.alpha(0, 100));
        style.opacity = 50;
        assertEquals(128, style.alpha(100, 100));
    }

    @Test void droneAnglesClampAndSurviveControllerReplacement() {
        FarFollowController.Distance orbit = new FarFollowController.Distance();
        orbit.initialized = true;
        orbit.drag(100, 1000);
        assertEquals(15, orbit.yaw);
        assertEquals(80, orbit.pitch);
        orbit.drag(0, -10000);
        assertEquals(-80, orbit.pitch);
        FarFollowController.Distance copy = new FarFollowController.Distance();
        copy.copyFrom(orbit);
        assertEquals(orbit.yaw, copy.yaw);
        assertEquals(orbit.pitch, copy.pitch);
        assertEquals(orbit.blocks, copy.blocks);
        assertTrue(copy.initialized);
    }
}
