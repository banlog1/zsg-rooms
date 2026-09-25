package zsgrooms.modid.replay;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ReplayBufferTest {
    @Test
    void snapshotsSeparateClientBacklogWriterQueueAndActiveWrites() throws Exception {
        ReplayBuffer buffer = new ReplayBuffer(1000, 10);
        ReplayBuffer.Record pending = buffer.reserve(1, 1, 36);
        ReplayBuffer.Record queued = buffer.reserve(1, 2, 136);
        ReplayBuffer.Record active = buffer.reserve(1, 3, 236);
        buffer.commit(active, 10);
        buffer.commit(queued, 20);
        assertSame(active, buffer.take());
        ReplayBuffer.Snapshot snapshot = buffer.snapshot();
        assertEquals(600, snapshot.bytes);
        assertEquals(3, snapshot.records);
        assertEquals(100, snapshot.pendingBytes);
        assertEquals(1, snapshot.pendingRecords);
        assertEquals(200, snapshot.queuedBytes);
        assertEquals(1, snapshot.queuedRecords);
        assertEquals(300, snapshot.writingBytes);
        assertEquals(1, snapshot.writingRecords);
        assertEquals(600, snapshot.peakBytes);
        assertEquals(600, snapshot.peakPendingBytes);
        assertEquals(3, snapshot.peakPendingRecords);
        assertEquals(500, snapshot.peakQueuedBytes);
        assertEquals(2, snapshot.peakQueuedRecords);
        assertEquals(236, snapshot.largestPayload);
        buffer.cancelPending();
        buffer.discardQueued();
        assertEquals(300, buffer.snapshot().bytes);
        assertEquals(0, buffer.snapshot().queuedBytes);
        assertEquals(0, buffer.snapshot().pendingBytes);
        assertFalse(buffer.commit(pending, 30));
        buffer.complete(active);
        assertEquals(0, buffer.snapshot().bytes);
        assertEquals(0, buffer.snapshot().records);
        assertEquals(0, buffer.snapshot().writingBytes);
        assertEquals(600, buffer.snapshot().peakBytes);
        assertEquals(600, snapshot.bytes); // An earlier snapshot is immutable.
    }

    @Test
    void capacityDiagnosticRetainsExactFailureEvenAfterQueueDrains() throws Exception {
        ReplayBuffer buffer = new ReplayBuffer(200, 10);
        ReplayBuffer.Record first = buffer.reserve(1, 1, 36);
        ReplayBuffer.Record second = buffer.reserve(1, 2, 36);
        buffer.commit(first, 10);
        assertNull(buffer.reserve(1, 99, 1));
        String failure = buffer.capacityFailure();
        assertTrue(failure.contains("limit=bytes requestedPayloadBytes=1 phase=1 packetId=99"));
        assertTrue(failure.contains("accountedBytes=200/200 packets=2/10"));
        assertTrue(failure.contains("pendingClientPackets=1 queuedWriterBytes=100 queuedWriterPackets=1"));
        assertSame(first, buffer.take());
        buffer.complete(first);
        buffer.cancel(second);
        assertEquals(failure, buffer.capacityFailure());
        assertEquals(0, buffer.snapshot().bytes);
    }

    @Test
    void diagnosesPacketLimitOversizedPacketAndBothLimitsWithoutAllocatingPayload() {
        ReplayBuffer count = new ReplayBuffer(1000, 1);
        assertNotNull(count.reserve(1, 1, 0));
        assertNull(count.reserve(1, 2, 0));
        assertTrue(count.capacityFailure().startsWith("limit=packets "));
        ReplayBuffer bytes = new ReplayBuffer(100, 1);
        assertNull(bytes.reserve(1, 1, Integer.MAX_VALUE));
        assertTrue(bytes.capacityFailure().startsWith("limit=bytes "));
        assertEquals(0, bytes.snapshot().bytes);
        ReplayBuffer both = new ReplayBuffer(100, 1);
        assertNotNull(both.reserve(1, 1, 36));
        assertNull(both.reserve(1, 2, 0));
        assertTrue(both.capacityFailure().startsWith("limit=bytes_and_packets "));
        ReplayBuffer closed = new ReplayBuffer();
        closed.close();
        assertNull(closed.reserve(1, 2, 0));
        assertNull(closed.capacityFailure());
    }

    @Test
    void largerBudgetAbsorbsSmallPacketAndChunkSizedBurstsWithoutChangingOrder() throws Exception {
        exerciseBurst(20000, 128, false);
        exerciseBurst(768, 64 * 1024, true);
    }

    private static void exerciseBurst(int count, int payloadSize, boolean commitImmediately) throws Exception {
        ReplayBuffer old = new ReplayBuffer(32 * 1024 * 1024, 8192);
        int oldAccepted = 0;
        while (old.reserve(1, 1, payloadSize) != null) oldAccepted++;
        assertTrue(oldAccepted < count);
        old.cancelPending();
        ReplayBuffer buffer = new ReplayBuffer();
        assertEquals(0, buffer.snapshot().bytes);
        for (int entry = 0; entry < 2; entry++) {
            ReplayBuffer.Record[] packets = new ReplayBuffer.Record[count];
            for (int i = 0; i < count; i++) {
                packets[i] = buffer.reserve(1, 1, payloadSize);
                assertNotNull(packets[i], "Burst should fit");
                packets[i].payload[0] = (byte) i;
                if (commitImmediately) assertTrue(buffer.commit(packets[i], entry * count + i));
            }
            if (!commitImmediately) {
                for (int i = 0; i < count; i++) assertTrue(buffer.commit(packets[i], entry * count + i));
            }
            for (int i = 0; i < count; i++) {
                ReplayBuffer.Record written = buffer.take();
                assertSame(packets[i], written);
                assertEquals((byte) i, written.payload[0]);
                assertEquals(entry * count + i, written.timestamp);
                buffer.complete(written);
                packets[i] = null;
            }
            assertTrue(buffer.isOpen());
            assertEquals(0, buffer.snapshot().bytes);
            assertEquals(0, buffer.snapshot().records);
        }
        assertNull(buffer.capacityFailure());
        System.out.println("Replay synthetic burst: packets=" + count + " payloadBytes=" + payloadSize
                + " oldAccepted=" + oldAccepted + " new " + buffer.snapshot());
    }

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
