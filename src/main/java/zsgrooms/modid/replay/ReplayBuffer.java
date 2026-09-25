package zsgrooms.modid.replay;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/** Accounts for packets awaiting the client thread, queued packets, and the active write. */
public final class ReplayBuffer {
    static final int DEFAULT_BYTE_LIMIT = 128 * 1024 * 1024;
    static final int DEFAULT_RECORD_LIMIT = 65536;
    // Accounting allowance, not an exact measurement of JVM object/collection overhead.
    private static final int RECORD_OVERHEAD = 64;
    private final int byteLimit;
    private final int recordLimit;
    private final Deque<Record> ready = new ArrayDeque<>();
    private final Set<Record> pending = new HashSet<>();
    private int retainedBytes;
    private int retainedRecords;
    private int pendingBytes;
    private int readyBytes;
    private int peakBytes;
    private int peakRecords;
    private int peakPendingBytes;
    private int peakPendingRecords;
    private int peakReadyBytes;
    private int peakReadyRecords;
    private int largestPayload;
    private String capacityFailure;
    private boolean closed;

    public ReplayBuffer() {
        this(DEFAULT_BYTE_LIMIT, DEFAULT_RECORD_LIMIT);
    }

    public ReplayBuffer(int byteLimit, int recordLimit) {
        if (byteLimit <= RECORD_OVERHEAD || recordLimit <= 0) throw new IllegalArgumentException();
        this.byteLimit = byteLimit;
        this.recordLimit = recordLimit;
    }

    public synchronized Record reserve(int phase, int packetId, int length) {
        if (closed) return null;
        boolean recordsFull = retainedRecords >= recordLimit;
        boolean bytesFull = length > byteLimit - RECORD_OVERHEAD
                || length >= 0 && retainedBytes > byteLimit - RECORD_OVERHEAD - length;
        if (length < 0 || recordsFull || bytesFull) {
            // Capture at rejection, before another thread drains the queue and hides the cause.
            if (capacityFailure == null) {
                String limit = length < 0 ? "invalid_length" : recordsFull && bytesFull ? "bytes_and_packets"
                        : recordsFull ? "packets" : "bytes";
                capacityFailure = "limit=" + limit + " requestedPayloadBytes=" + length
                        + " phase=" + phase + " packetId=" + packetId + " " + new Snapshot(this);
            }
            return null;
        }
        Record record = new Record(this, phase, packetId, length);
        pending.add(record);
        retainedBytes += length + RECORD_OVERHEAD;
        retainedRecords++;
        pendingBytes += length + RECORD_OVERHEAD;
        peakBytes = Math.max(peakBytes, retainedBytes);
        peakRecords = Math.max(peakRecords, retainedRecords);
        peakPendingBytes = Math.max(peakPendingBytes, pendingBytes);
        peakPendingRecords = Math.max(peakPendingRecords, pending.size());
        largestPayload = Math.max(largestPayload, length);
        return record;
    }

    public synchronized boolean commit(Record record, long timestamp) {
        requireOwner(record);
        if (record.state == 4) return false;
        if (record.state != 0) throw new IllegalStateException("Record already submitted");
        if (closed) {
            release(record);
            return false;
        }
        record.timestamp = timestamp;
        pending.remove(record);
        pendingBytes -= record.payload.length + RECORD_OVERHEAD;
        record.state = 1;
        ready.addLast(record);
        readyBytes += record.payload.length + RECORD_OVERHEAD;
        peakReadyBytes = Math.max(peakReadyBytes, readyBytes);
        peakReadyRecords = Math.max(peakReadyRecords, ready.size());
        notifyAll();
        return true;
    }

    public synchronized void cancel(Record record) {
        requireOwner(record);
        if (record.state == 0) {
            release(record);
            record.state = 4;
        }
    }

    /** Minecraft discards queued client tasks on reset, so release their reservations now. */
    public synchronized void cancelPending() {
        for (Record record : pending) {
            record.state = 4;
            retainedBytes -= record.payload.length + RECORD_OVERHEAD;
            retainedRecords--;
        }
        pending.clear();
        pendingBytes = 0;
    }

    public synchronized Record take() throws InterruptedException {
        while (ready.isEmpty() && !closed) wait();
        Record record = ready.pollFirst();
        if (record != null) {
            readyBytes -= record.payload.length + RECORD_OVERHEAD;
            record.state = 2;
        }
        return record;
    }

    public synchronized void complete(Record record) {
        requireOwner(record);
        if (record.state != 2) throw new IllegalStateException("Record is not being written");
        release(record);
    }

    public synchronized void close() {
        closed = true;
        notifyAll();
    }

    public synchronized void discardQueued() {
        close();
        Record record;
        while ((record = ready.pollFirst()) != null) release(record);
    }

    public synchronized boolean isOpen() {
        return !closed;
    }

    public synchronized int retainedBytes() {
        return retainedBytes;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(this);
    }

    public synchronized String capacityFailure() {
        return capacityFailure;
    }

    private void requireOwner(Record record) {
        if (record.owner != this) throw new IllegalArgumentException("Wrong recording");
    }

    private void release(Record record) {
        if (record.state == 0) {
            pending.remove(record);
            pendingBytes -= record.payload.length + RECORD_OVERHEAD;
        } else if (record.state == 1) {
            readyBytes -= record.payload.length + RECORD_OVERHEAD;
        }
        record.state = 3;
        retainedBytes -= record.payload.length + RECORD_OVERHEAD;
        retainedRecords--;
    }

    /** Created only for transition/final logs or a capacity failure, never per packet. */
    public static final class Snapshot {
        public final int byteLimit, recordLimit, bytes, records, pendingBytes, pendingRecords;
        public final int queuedBytes, queuedRecords, writingBytes, writingRecords;
        public final int peakBytes, peakRecords, peakPendingBytes, peakPendingRecords;
        public final int peakQueuedBytes, peakQueuedRecords, largestPayload;

        private Snapshot(ReplayBuffer buffer) {
            byteLimit = buffer.byteLimit;
            recordLimit = buffer.recordLimit;
            bytes = buffer.retainedBytes;
            records = buffer.retainedRecords;
            pendingBytes = buffer.pendingBytes;
            pendingRecords = buffer.pending.size();
            queuedBytes = buffer.readyBytes;
            queuedRecords = buffer.ready.size();
            writingBytes = bytes - pendingBytes - queuedBytes;
            writingRecords = records - pendingRecords - queuedRecords;
            peakBytes = buffer.peakBytes;
            peakRecords = buffer.peakRecords;
            peakPendingBytes = buffer.peakPendingBytes;
            peakPendingRecords = buffer.peakPendingRecords;
            peakQueuedBytes = buffer.peakReadyBytes;
            peakQueuedRecords = buffer.peakReadyRecords;
            largestPayload = buffer.largestPayload;
        }

        @Override public String toString() {
            return "accountedBytes=" + bytes + "/" + byteLimit + " packets=" + records + "/" + recordLimit
                    + " pendingClientBytes=" + pendingBytes + " pendingClientPackets=" + pendingRecords
                    + " queuedWriterBytes=" + queuedBytes + " queuedWriterPackets=" + queuedRecords
                    + " writingBytes=" + writingBytes + " writingPackets=" + writingRecords
                    + " peakAccountedBytes=" + peakBytes + " peakPackets=" + peakRecords
                    + " peakPendingClientBytes=" + peakPendingBytes + " peakPendingClientPackets=" + peakPendingRecords
                    + " peakQueuedWriterBytes=" + peakQueuedBytes + " peakQueuedWriterPackets=" + peakQueuedRecords
                    + " largestPayloadBytes=" + largestPayload;
        }
    }

    public static final class Record {
        private final ReplayBuffer owner;
        public final int phase;
        public final int packetId;
        public final byte[] payload;
        public long timestamp;
        private int state;

        private Record(ReplayBuffer owner, int phase, int packetId, int length) {
            this.owner = owner;
            this.phase = phase;
            this.packetId = packetId;
            this.payload = new byte[length];
        }
    }
}
