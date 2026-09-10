package zsgrooms.modid.replay;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ReplayBufferTest {
    @Test
    void resetReleasesCancelledClientTasksWithoutDroppingCommittedPackets() throws Exception {
        ReplayBuffer buffer = new ReplayBuffer(200, 2);
        ReplayBuffer.Record oldTask = buffer.reserve(0, 1, 36);
        ReplayBuffer.Record queued = buffer.reserve(0, 2, 36);
        buffer.commit(queued, 10);
        buffer.cancelPending();
        assertTrue(buffer.isOpen());
        assertEquals(100, buffer.retainedBytes());
        ReplayBuffer.Record nextWorld = buffer.reserve(0, 3, 36);
        assertNotNull(nextWorld);
        assertFalse(buffer.commit(oldTask, 20));
        buffer.cancel(oldTask);
        assertEquals(200, buffer.retainedBytes());
        buffer.commit(nextWorld, 30);
        assertSame(queued, buffer.take());
        buffer.complete(queued);
        assertSame(nextWorld, buffer.take());
        buffer.complete(nextWorld);
        assertEquals(0, buffer.retainedBytes());
    }

    @Test
    void manyResetsDoNotAccumulateReservationsFromDiscardedTasks() {
        ReplayBuffer buffer = new ReplayBuffer(100, 1);
        for (int i = 0; i < 100; i++) {
            ReplayBuffer.Record cancelled = buffer.reserve(0, 1, 36);
            assertNotNull(cancelled);
            buffer.cancelPending();
            assertFalse(buffer.commit(cancelled, i));
            assertEquals(0, buffer.retainedBytes());
        }
    }

    @Test
    void budgetIncludesPendingQueuedAndWritingPackets() throws Exception {
        ReplayBuffer buffer = new ReplayBuffer(200, 10);
        ReplayBuffer.Record first = buffer.reserve(0, 1, 36);
        ReplayBuffer.Record second = buffer.reserve(0, 2, 36);
        assertNotNull(first);
        assertNotNull(second);
        assertEquals(200, buffer.retainedBytes());
        assertNull(buffer.reserve(0, 3, 0));
        buffer.commit(first, 0);
        assertSame(first, buffer.take());
        assertNull(buffer.reserve(0, 3, 0));
        buffer.complete(first);
        assertEquals(100, buffer.retainedBytes());
        assertNotNull(buffer.reserve(0, 3, 36));
    }

    @Test
    void recordLimitAlsoBoundsEmptyPacketsAndRejectsOversizedLengths() {
        ReplayBuffer buffer = new ReplayBuffer(1024, 1);
        assertNull(buffer.reserve(0, 1, -1));
        assertNull(buffer.reserve(0, 1, Integer.MAX_VALUE));
        ReplayBuffer.Record record = buffer.reserve(0, 1, 0);
        assertNotNull(record);
        assertNull(buffer.reserve(0, 2, 0));
        buffer.cancel(record);
        buffer.cancel(record);
        assertEquals(0, buffer.retainedBytes());
        assertNotNull(buffer.reserve(0, 2, 0));
    }

    @Test
    void equalTimestampsKeepClientSubmissionOrder() throws Exception {
        ReplayBuffer buffer = new ReplayBuffer(1024, 4);
        ReplayBuffer.Record earlierReservation = buffer.reserve(0, 1, 1);
        ReplayBuffer.Record firstSubmission = buffer.reserve(0, 2, 1);
        firstSubmission.payload[0] = 42;
        buffer.commit(firstSubmission, 100);
        buffer.commit(earlierReservation, 100);
        assertSame(firstSubmission, buffer.take());
        assertEquals(42, firstSubmission.payload[0]);
        assertEquals(100, firstSubmission.timestamp);
        assertSame(earlierReservation, buffer.take());
        buffer.complete(firstSubmission);
        buffer.complete(earlierReservation);
        assertEquals(0, buffer.retainedBytes());
    }

    @Test
    void closingDrainsCommittedPacketsAndReleasesLateSubmissions() throws Exception {
        ReplayBuffer buffer = new ReplayBuffer(1024, 4);
        ReplayBuffer.Record pending = buffer.reserve(0, 1, 5);
        ReplayBuffer.Record queued = buffer.reserve(0, 2, 5);
        buffer.commit(queued, 0);
        buffer.close();
        assertFalse(buffer.isOpen());
        assertNull(buffer.reserve(0, 3, 0));
        assertFalse(buffer.commit(pending, 1));
        assertSame(queued, buffer.take());
        buffer.complete(queued);
        assertNull(buffer.take());
        assertEquals(0, buffer.retainedBytes());
    }

    @Test
    void failureDiscardsQueueWithoutDoubleReleasingActiveWrites() throws Exception {
        ReplayBuffer buffer = new ReplayBuffer(1024, 4);
        ReplayBuffer.Record active = buffer.reserve(0, 1, 10);
        ReplayBuffer.Record queued = buffer.reserve(0, 2, 10);
        ReplayBuffer.Record pending = buffer.reserve(0, 3, 10);
        buffer.commit(active, 0);
        buffer.commit(queued, 0);
        assertSame(active, buffer.take());
        buffer.discardQueued();
        assertEquals(148, buffer.retainedBytes());
        buffer.complete(active);
        assertFalse(buffer.commit(pending, 0));
        assertNull(buffer.take());
        assertEquals(0, buffer.retainedBytes());
    }

    @Test
    void recordsCannotBeSubmittedTwiceOrUsedByAnotherRecording() throws Exception {
        ReplayBuffer buffer = new ReplayBuffer(1024, 4);
        ReplayBuffer other = new ReplayBuffer(1024, 4);
        ReplayBuffer.Record record = buffer.reserve(0, 1, 10);
        assertThrows(IllegalArgumentException.class, () -> other.commit(record, 0));
        assertThrows(IllegalStateException.class, () -> buffer.complete(record));
        buffer.commit(record, 0);
        assertThrows(IllegalStateException.class, () -> buffer.commit(record, 0));
        buffer.cancel(record);
        assertSame(record, buffer.take());
        buffer.complete(record);
        assertThrows(IllegalStateException.class, () -> buffer.complete(record));
        assertEquals(0, buffer.retainedBytes());
    }

    @Test
    void repeatedEndSignalsDrainOnceAndDoNotAffectTheNextRecording() throws Exception {
        ReplayBuffer first = new ReplayBuffer(1024, 4);
        ReplayBuffer next = new ReplayBuffer(1024, 4);
        ReplayBuffer.Record packet = first.reserve(0, 1, 1);
        first.commit(packet, 10);
        first.close(); // Match result.
        first.close(); // Return to room.
        first.close(); // Network disconnect.
        assertSame(packet, first.take());
        first.complete(packet);
        assertNull(first.take());
        assertEquals(0, first.retainedBytes());
        assertTrue(next.isOpen());
        assertNotNull(next.reserve(0, 1, 1));
    }

    @Test
    void writerWakesForPacketsAndForClose() throws Exception {
        ReplayBuffer buffer = new ReplayBuffer(1024, 4);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<ReplayBuffer.Record> first = worker.submit(buffer::take);
            ReplayBuffer.Record record = buffer.reserve(0, 1, 1);
            buffer.commit(record, 0);
            assertSame(record, first.get(5, TimeUnit.SECONDS));
            buffer.complete(record);
            Future<ReplayBuffer.Record> end = worker.submit(buffer::take);
            buffer.close();
            assertNull(end.get(5, TimeUnit.SECONDS));
        } finally {
            buffer.close();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
