// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrailDiskStoreTest {
    @TempDir Path directory;
    private final UUID entity = UUID.randomUUID();
    private final Set<UUID> selected = Collections.singleton(entity);

    @Test void stationaryPredecessorSurvivesLongGapWithoutLeakingFutureMovement() throws Exception {
        try (TrailDiskStore store = new TrailDiskStore(directory)) {
            store.append(entity, 100, 1, 2, 3, TrailDiskStore.BREAK);
            store.append(entity, 200, 2, 2, 3, 0);
            store.append(entity, 20_000, 99, 2, 3, 0);
            store.finish();
            TrailDiskStore.Window window = store.window(5000, 25_000, selected, () -> false);
            List<TrailPoint> points = window.snapshot(10_000, selected, 5000).get(entity);
            assertEquals(2, points.size());
            assertPoint(points.get(0), 5000, 2, true);
            assertPoint(points.get(1), 10_000, 2, false);
            assertTrue(points.stream().noneMatch(p -> p.time > 10_000 || p.x == 99));
        }
    }

    @Test void previousValidPathStillShowsWhenEntityStopsAndNoHeartbeatArrives() throws Exception {
        try (TrailDiskStore store = new TrailDiskStore(directory)) {
            store.append(entity, 100, 1, 0, 0, TrailDiskStore.BREAK);
            store.append(entity, 200, 2, 0, 0, 0);
            store.finish();
            List<TrailPoint> points = store.window(0, 6000, selected, () -> false).snapshot(5000, selected, 5000).get(entity);
            assertEquals(3, points.size());
            assertPoint(points.get(0), 100, 1, true);
            assertPoint(points.get(1), 200, 2, false);
            assertPoint(points.get(2), 5000, 2, false);
        }
    }

    @Test void sparseAnchorsSeekDeepHistoryAndHandleEqualTimestamps() throws Exception {
        try (TrailDiskStore store = new TrailDiskStore(directory)) {
            for (int i = 0; i < 4000; i++) store.append(entity, i / 2, i, 0, 0, i == 0 ? TrailDiskStore.BREAK : 0);
            store.finish();
            TrailDiskStore.Window window = store.window(100, 150, selected, () -> false);
            List<TrailPoint> points = window.snapshot(125, selected, 25).get(entity);
            assertEquals(199, points.get(0).x);
            assertEquals(251, points.get(points.size() - 1).x);
            assertTrue(points.stream().allMatch(p -> p.time >= 100 && p.time <= 125));
            assertNull(window.snapshot(99, selected, 0));
            assertNull(window.snapshot(151, selected, 0));
            assertNull(window.snapshot(125, selected, 26));
            assertNull(window.snapshot(125, Collections.singleton(UUID.randomUUID()), 0));
        }
    }

    @Test void destructionAndReappearanceHaveSeparateSegmentsAndKnownEmptyVersusUnknown() throws Exception {
        try (TrailDiskStore store = new TrailDiskStore(directory)) {
            store.append(entity, 100, 1, 0, 0, TrailDiskStore.BREAK);
            store.append(entity, 200, 2, 0, 0, 0);
            store.append(entity, 300, 2, 0, 0, TrailDiskStore.END);
            store.append(entity, 700, 7, 0, 0, TrailDiskStore.BREAK);
            store.append(entity, 800, 8, 0, 0, 0);
            store.finish();
            TrailDiskStore.Window window = store.window(0, 1000, selected, () -> false);
            List<TrailPoint> points = window.snapshot(900, selected, 900).get(entity);
            assertPoint(points.get(2), 300, 2, false);
            assertPoint(points.get(3), 700, 7, true);
            assertTrue(window.snapshot(600, selected, 100).containsKey(entity));
            assertTrue(window.snapshot(600, selected, 100).get(entity).isEmpty());
            assertFalse(window.snapshot(50, selected, 50).containsKey(entity));
        }
    }

    @Test void boundariesFilterOtherDimensionsAndResetsIncludingSameMillisecond() throws Exception {
        try (TrailDiskStore store = new TrailDiskStore(directory)) {
            store.boundary(0);
            store.append(entity, 10, 1, 0, 0, TrailDiskStore.BREAK);
            store.boundary(100);
            store.append(entity, 100, 2, 0, 0, TrailDiskStore.BREAK);
            store.boundary(100);
            store.append(entity, 100, 3, 0, 0, TrailDiskStore.BREAK);
            store.finish();
            TrailDiskStore.Window window = store.window(0, 1000, selected, () -> false);
            assertEquals(1, window.snapshot(99, selected, 99).get(entity).get(0).x);
            List<TrailPoint> points = window.snapshot(101, selected, 101).get(entity);
            assertTrue(points.stream().allMatch(p -> p.x == 3));
            assertTrue(points.get(0).breakBefore);
        }
    }

    @Test void previousWorldUuidIsUnknownUntilItAppearsAgain() throws Exception {
        try (TrailDiskStore store = new TrailDiskStore(directory)) {
            store.append(entity, 10, 1, 0, 0, TrailDiskStore.BREAK);
            store.boundary(20);
            store.finish();
            assertFalse(store.window(0, 100, selected, () -> false).snapshot(30, selected, 30).containsKey(entity));
        }
    }

    @Test void snapshotsAndSelectionAreImmutableAndPointsReused() throws Exception {
        try (TrailDiskStore store = new TrailDiskStore(directory)) {
            store.append(entity, 0, 0, 0, 0, TrailDiskStore.BREAK);
            store.append(entity, 100, 1, 0, 0, 0);
            store.finish();
            java.util.HashSet<UUID> ids = new java.util.HashSet<>(selected);
            TrailDiskStore.Window window = store.window(0, 1000, ids, () -> false);
            ids.clear();
            Map<UUID, List<TrailPoint>> a = window.snapshot(200, selected, 200);
            Map<UUID, List<TrailPoint>> b = window.snapshot(300, selected, 300);
            assertSame(a.get(entity).get(1), b.get(entity).get(1));
            assertThrows(UnsupportedOperationException.class, a::clear);
            assertThrows(UnsupportedOperationException.class, () -> a.get(entity).clear());
        }
    }

    @Test void scratchIsBoundedAndDeletedAfterFailureAndClose() throws Exception {
        Path path;
        try (TrailDiskStore store = new TrailDiskStore(directory, TrailDiskStore.RECORD_BYTES)) {
            path = store.path;
            store.append(entity, 0, 0, 0, 0, 0);
            assertThrows(IOException.class, () -> store.append(entity, 1, 0, 0, 0, 0));
            assertTrue(Files.exists(path));
        }
        assertFalse(Files.exists(path));
    }

    @Test void cancellationInterruptsDiskQueryWithoutPublishingPartialWindow() throws Exception {
        try (TrailDiskStore store = new TrailDiskStore(directory)) {
            store.append(entity, 0, 0, 0, 0, 0);
            store.finish();
            assertThrows(InterruptedIOException.class, () -> store.window(0, 100, selected, () -> true));
        }
    }

    private static void assertPoint(TrailPoint point, int time, double x, boolean breakBefore) {
        assertEquals(time, point.time); assertEquals(x, point.x); assertEquals(breakBefore, point.breakBefore);
    }
}
