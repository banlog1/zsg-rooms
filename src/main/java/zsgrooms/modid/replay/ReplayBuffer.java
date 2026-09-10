package zsgrooms.modid.replay;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/** Accounts for packets awaiting the client thread, queued packets, and the active write. */
public final class ReplayBuffer {
    private static final int RECORD_OVERHEAD = 64;
    private final int byteLimit;
    private final int recordLimit;
    private final Deque<Record> ready = new ArrayDeque<>();
    private final Set<Record> pending = new HashSet<>();
    private int retainedBytes;
    private int retainedRecords;
    private boolean closed;

    public ReplayBuffer(int byteLimit, int recordLimit) {
        if (byteLimit <= RECORD_OVERHEAD || recordLimit <= 0) throw new IllegalArgumentException();
        this.byteLimit = byteLimit;
        this.recordLimit = recordLimit;
    }

    public synchronized Record reserve(int phase, int packetId, int length) {
        if (length < 0 || length > byteLimit - RECORD_OVERHEAD || closed
                || retainedRecords >= recordLimit || retainedBytes > byteLimit - RECORD_OVERHEAD - length) {
            return null;
        }
        Record record = new Record(this, phase, packetId, length);
        pending.add(record);
        retainedBytes += length + RECORD_OVERHEAD;
        retainedRecords++;
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
        record.state = 1;
        ready.addLast(record);
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
    }

    public synchronized Record take() throws InterruptedException {
        while (ready.isEmpty() && !closed) wait();
        Record record = ready.pollFirst();
        if (record != null) record.state = 2;
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

    private void requireOwner(Record record) {
        if (record.owner != this) throw new IllegalArgumentException("Wrong recording");
    }

    private void release(Record record) {
        if (record.state == 0) pending.remove(record);
        record.state = 3;
        retainedBytes -= record.payload.length + RECORD_OVERHEAD;
        retainedRecords--;
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
