// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MilestonesTest {
    @Test void selectsOnlyTheFiveMainAdvancements() {
        for (Milestones.Kind kind : Milestones.Kind.values()) assertEquals(kind, Milestones.Kind.fromId(kind.id));
        assertNull(Milestones.Kind.fromId("minecraft:nether/root"));
        assertNull(Milestones.Kind.fromId("minecraft:adventure/shoot_arrow"));
    }

    @Test void initialSnapshotIsNotACompletionAtReplayStart() {
        Milestones tracker = new Milestones();
        tracker.newWorld();
        tracker.observe(Milestones.Kind.NETHER, true, true, 100);
        tracker.observe(Milestones.Kind.NETHER, true, false, 200);
        assertTrue(tracker.snapshot().isEmpty());
    }

    @Test void incompleteInitialSnapshotDoesNotSuppressALaterCompletion() {
        Milestones tracker = new Milestones();
        tracker.newWorld();
        tracker.observe(Milestones.Kind.NETHER, false, true, 100);
        tracker.observe(Milestones.Kind.NETHER, true, false, 2000);
        tracker.observe(Milestones.Kind.NETHER, true, false, 3000);
        assertEquals(1, tracker.snapshot().size());
        assertEquals(2000, tracker.snapshot().get(0).time);
    }

    @Test void resetAllowsNewMilestonesButRevokingDoesNotDuplicateAnAttempt() {
        Milestones tracker = new Milestones();
        tracker.newWorld();
        tracker.observe(Milestones.Kind.NETHER, true, false, 2000);
        tracker.observe(Milestones.Kind.NETHER, false, false, 2500);
        tracker.observe(Milestones.Kind.NETHER, true, false, 3000);
        tracker.newWorld();
        tracker.observe(Milestones.Kind.NETHER, true, false, 4000);
        assertEquals(2, tracker.snapshot().size());
        assertEquals(2, tracker.snapshot().get(1).world);
    }

    @Test void simultaneousEventsHaveStableOrderAndSnapshotsDoNotMutate() {
        Milestones tracker = new Milestones();
        tracker.observe(Milestones.Kind.END, true, false, 1000);
        tracker.observe(Milestones.Kind.BASTION, true, false, 1000);
        assertEquals(Milestones.Kind.BASTION, tracker.snapshot().get(0).kind);
        assertThrows(UnsupportedOperationException.class, () -> tracker.snapshot().clear());
    }
}
