package zsgrooms.modid.replay;

import com.google.gson.Gson;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/** Optional disk-spooled full states. The client never waits for disk or the packet writer. */
final class ReplayDetailedTrack implements AutoCloseable {
    static final int MAGIC = 0x5A444554;
    static final int VERSION = 1;
    static final int MAX_FRAME = 65536;
    static final long MAX_BYTES = 2L * 1024 * 1024 * 1024 - 1;
    static final int QUEUE_BYTES = 4 * 1024 * 1024;
    static final int QUEUE_FRAMES = 128;
    static final int FRESHNESS_MILLIS = 1500;
    static final int AVAILABLE = 1, LOADING = 2, GAP = 4;
    private static final byte[] GAP_PAYLOAD = unavailable(GAP);
    private static final int GAP_BYTES = 16 + GAP_PAYLOAD.length;
    private final Path spool;
    private final DataOutputStream output;
    private final int queueByteLimit, queueFrameLimit;
    private final long byteLimit;
    private final Deque<Frame> queue = new ArrayDeque<>();
    private final Thread worker;
    private int queuedBytes, peakQueuedBytes, peakQueuedFrames;
    private long reservedBytes = 8, framesWritten, bytesWritten = 8;
    private int lastOffered = -1, lastWritten = -1;
    private long gaps;
    private Frame pendingGap;
    private boolean sealed, capped, done, deleteRequested;
    private String failure;

    static ReplayDetailedTrack create(Path directory) throws IOException {
        Path spool = Files.createTempFile(directory, ".zsg-detail-", ".bin");
        try { return new ReplayDetailedTrack(spool, QUEUE_BYTES, QUEUE_FRAMES, MAX_BYTES); }
        catch (IOException | RuntimeException error) {
            try { Files.deleteIfExists(spool); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }

    ReplayDetailedTrack(Path spool, int queueBytes, int queueFrames, long maxBytes) throws IOException {
        this(spool, new BufferedOutputStream(Files.newOutputStream(spool), 65536), queueBytes, queueFrames, maxBytes);
    }

    ReplayDetailedTrack(Path spool, OutputStream sink, int queueBytes, int queueFrames, long maxBytes) {
        if (queueBytes < GAP_BYTES || queueFrames < 1 || maxBytes < 8 + GAP_BYTES || maxBytes > MAX_BYTES)
            throw new IllegalArgumentException("Invalid detail budgets");
        this.spool = spool;
        this.output = new DataOutputStream(sink);
        this.queueByteLimit = queueBytes;
        this.queueFrameLimit = queueFrames;
        this.byteLimit = maxBytes;
        worker = new Thread(this::write, "ZSG replay detail spool");
        worker.setDaemon(true);
        worker.start();
    }

    synchronized boolean accepting() { return !sealed && failure == null; }

    /** Transfers ownership of payload: callers must not modify it after offering. */
    synchronized boolean offer(long time, int world, int entity, byte[] payload) {
        if (!accepting() || time < 0 || time > Integer.MAX_VALUE || time <= lastOffered) return false;
        lastOffered = (int) time;
        int size = payload.length + 16;
        if (payload.length > MAX_FRAME) {
            gap((int) time, world, entity);
            return false;
        }
        if (reservedBytes + size + GAP_BYTES + (pendingGap == null ? 0 : GAP_BYTES) > byteLimit) {
            gap((int) time, world, entity);
            capped = true;
            sealed = true;
            notifyAll();
            return false;
        }
        if (pendingGap != null) {
            if (!room(pendingGap.size())) return false;
            enqueue(pendingGap);
            pendingGap = null;
        }
        if (!room(size)) {
            gap((int) time, world, entity);
            return false;
        }
        enqueue(new Frame((int) time, world, entity, payload));
        if (payload.length > 0 && (payload[0] & GAP) != 0) gaps++;
        return true;
    }

    private boolean room(int bytes) {
        return queue.size() < queueFrameLimit && queuedBytes <= queueByteLimit - bytes;
    }

    private void gap(int time, int world, int entity) {
        if (pendingGap == null) {
            pendingGap = new Frame(time, world, entity, GAP_PAYLOAD);
            gaps++;
        }
        notifyAll();
    }

    private void enqueue(Frame frame) {
        queue.addLast(frame);
        queuedBytes += frame.size();
        reservedBytes += frame.size();
        peakQueuedBytes = Math.max(peakQueuedBytes, queuedBytes);
        peakQueuedFrames = Math.max(peakQueuedFrames, queue.size());
        notifyAll();
    }

    private synchronized Frame take() throws InterruptedException {
        while (queue.isEmpty() && pendingGap == null && !sealed) wait();
        Frame frame = queue.pollFirst();
        if (frame != null) queuedBytes -= frame.size();
        else if (pendingGap != null) {
            frame = pendingGap;
            pendingGap = null;
            reservedBytes += frame.size();
        }
        return frame;
    }

    private void write() {
        try (DataOutputStream stream = output) {
            stream.writeInt(MAGIC);
            stream.writeInt(VERSION);
            Frame frame;
            while ((frame = take()) != null) {
                stream.writeInt(frame.time);
                stream.writeInt(frame.world);
                stream.writeInt(frame.entity);
                stream.writeInt(frame.payload.length);
                stream.write(frame.payload);
                synchronized (this) {
                    lastWritten = frame.time;
                    bytesWritten += frame.size();
                    framesWritten++;
                }
            }
        } catch (IOException | RuntimeException | InterruptedException error) {
            synchronized (this) {
                failure = error.getClass().getSimpleName();
                sealed = true;
                queue.clear();
                queuedBytes = 0;
                pendingGap = null;
            }
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            synchronized (this) { done = true; notifyAll(); }
            deleteIfRequested();
        }
    }

    void finish() throws InterruptedException {
        synchronized (this) { sealed = true; notifyAll(); }
        worker.join(5000);
        synchronized (this) {
            if (!done) {
                failure = "SpoolTimeout";
                queue.clear();
                queuedBytes = 0;
                pendingGap = null;
                worker.interrupt();
            }
        }
    }

    synchronized Path completedFile() { return done && failure == null ? spool : null; }

    synchronized String status() {
        return failure != null ? "failed" : capped ? "capped" : done ? gaps == 0 ? "complete" : "gaps" : "recording";
    }

    synchronized String statusJson() { return new Gson().toJson(new Coverage(this)); }

    static String unavailableStatus(String reason, boolean supported) {
        Coverage result = new Coverage();
        result.supported = supported;
        result.status = reason;
        return new Gson().toJson(result);
    }

    @Override public void close() {
        synchronized (this) { sealed = true; deleteRequested = true; notifyAll(); }
        deleteIfRequested();
    }

    private void deleteIfRequested() {
        synchronized (this) { if (!done || !deleteRequested) return; }
        try { Files.deleteIfExists(spool); } catch (IOException ignored) { }
    }

    // Empty PacketByteBuf string and ItemStack are each encoded as one zero byte.
    static byte[] unavailable(int flags) {
        return ByteBuffer.allocate(35).put((byte) flags).putInt(0).put((byte) 0)
                .put((byte) 0).put((byte) 0).putFloat(0).putFloat(0).put((byte) 0).put((byte) 0)
                .putInt(0).put((byte) 0).putInt(0).putInt(-1).put((byte) 0).putShort((short) 0).put((byte) 0).array();
    }

    private static final class Frame {
        final int time, world, entity;
        final byte[] payload;
        Frame(int time, int world, int entity, byte[] payload) {
            this.time = time; this.world = world; this.entity = entity; this.payload = payload;
        }
        int size() { return 16 + payload.length; }
    }

    private static final class Coverage {
        final int schemaVersion = 1;
        final boolean requested = true;
        boolean supported = true;
        String status, failure;
        final int freshnessMillis = FRESHNESS_MILLIS;
        long byteLimit = MAX_BYTES, bytes, frames, gaps;
        int lastReplayMillis = -1, peakQueuedBytes, peakQueuedFrames;
        Coverage() { }
        Coverage(ReplayDetailedTrack track) {
            status = track.status(); failure = track.failure; byteLimit = track.byteLimit;
            bytes = track.bytesWritten; frames = track.framesWritten; gaps = track.gaps;
            lastReplayMillis = track.lastWritten;
            peakQueuedBytes = track.peakQueuedBytes; peakQueuedFrames = track.peakQueuedFrames;
        }
    }
}
