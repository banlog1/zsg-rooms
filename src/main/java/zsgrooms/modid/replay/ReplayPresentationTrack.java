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

/** Optional render-only states, with bounded client work and no disk I/O on the render thread. */
public final class ReplayPresentationTrack implements AutoCloseable {
    public static final int MAGIC = 0x5A505253, VERSION = 1, MAX_FRAME = 65536;
    public static final long MAX_BYTES = Integer.MAX_VALUE;
    public static final int AVAILABLE = 1, GAP = 2;
    public static final int BOB = 4, HANDS = 8, USING = 16, RIPTIDE = 32;
    public static final int LEFT_MAIN = 64, SWING_OFF = 128, ACTIVE_OFF = 256;
    public static final int SHOW_MAIN = 512, SHOW_OFF = 1024;
    public static final int QUEUE_BYTES = 4 * 1024 * 1024, QUEUE_FRAMES = 128;
    private static final byte[] GAP_PAYLOAD = ByteBuffer.allocate(4).putInt(GAP).array();
    private static final int GAP_BYTES = 20;
    private final Path spool;
    private final DataOutputStream output;
    private final Deque<Frame> queue = new ArrayDeque<>();
    private final int queueLimit, frameLimit;
    private final long byteLimit;
    private final Thread worker;
    private int queuedBytes, peakBytes, peakFrames, lastOffered = -1, lastWritten = -1;
    private long reserved = 8, bytes = 8, frames, gaps, dropped;
    private Frame pendingGap;
    private boolean sealed, capped, done, deleteRequested;
    private String failure;

    public static ReplayPresentationTrack create(Path directory) throws IOException {
        Path path = Files.createTempFile(directory, ".zsg-presentation-", ".bin");
        try { return new ReplayPresentationTrack(path, new BufferedOutputStream(Files.newOutputStream(path), 65536),
                QUEUE_BYTES, QUEUE_FRAMES, MAX_BYTES); }
        catch (IOException | RuntimeException error) {
            try { Files.deleteIfExists(path); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }

    ReplayPresentationTrack(Path spool, OutputStream sink, int queueLimit, int frameLimit, long byteLimit) {
        if (queueLimit < GAP_BYTES || frameLimit < 1 || byteLimit < 8 + GAP_BYTES || byteLimit > MAX_BYTES)
            throw new IllegalArgumentException("Invalid presentation budgets");
        this.spool = spool;
        output = new DataOutputStream(sink);
        this.queueLimit = queueLimit;
        this.frameLimit = frameLimit;
        this.byteLimit = byteLimit;
        worker = new Thread(this::write, "ZSG replay presentation spool");
        worker.setDaemon(true);
        worker.start();
    }

    public synchronized boolean accepting() { return !sealed && failure == null; }

    /** Transfers payload ownership. Full states after a gap never depend on earlier frames. */
    public synchronized boolean offer(long time, int world, int entity, byte[] payload) {
        if (!accepting() || time < 0 || time > Integer.MAX_VALUE || time <= lastOffered
                || world < -1 || entity < -1 || payload == null) return false;
        lastOffered = (int) time;
        int size = 16 + payload.length;
        if (payload.length < 4 || payload.length > MAX_FRAME || size > queueLimit) {
            gap((int) time, world, entity);
            return false;
        }
        if (reserved + size + GAP_BYTES > byteLimit) {
            gap((int) time, world, entity);
            capped = true;
            sealed = true;
            notifyAll();
            return false;
        }
        if (pendingGap != null) {
            if (!room(pendingGap.size()) || queue.size() + 1 >= frameLimit
                    || queuedBytes + pendingGap.size() + size > queueLimit) {
                dropped++;
                return false;
            }
            enqueue(pendingGap, false);
            pendingGap = null;
        }
        if (!room(size)) {
            gap((int) time, world, entity);
            return false;
        }
        enqueue(new Frame((int) time, world, entity, payload), true);
        if (payload.length == 4 && ByteBuffer.wrap(payload).getInt() == GAP) gaps++;
        return true;
    }

    private boolean room(int size) { return queue.size() < frameLimit && size <= queueLimit - queuedBytes; }

    private void gap(int time, int world, int entity) {
        dropped++;
        if (pendingGap == null && reserved <= byteLimit - GAP_BYTES) {
            pendingGap = new Frame(time, world, entity, GAP_PAYLOAD);
            reserved += GAP_BYTES;
            gaps++;
        }
        notifyAll();
    }

    private void enqueue(Frame frame, boolean reserve) {
        queue.addLast(frame);
        queuedBytes += frame.size();
        if (reserve) reserved += frame.size();
        peakBytes = Math.max(peakBytes, queuedBytes);
        peakFrames = Math.max(peakFrames, queue.size());
        notifyAll();
    }

    private synchronized Frame take() throws InterruptedException {
        while (queue.isEmpty() && pendingGap == null && !sealed) wait();
        Frame next = queue.pollFirst();
        if (next != null) queuedBytes -= next.size();
        else if (pendingGap != null) { next = pendingGap; pendingGap = null; }
        return next;
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
                    bytes += frame.size();
                    frames++;
                }
            }
        } catch (IOException | RuntimeException | InterruptedException error) {
            synchronized (this) { failure = error.getClass().getSimpleName(); sealed = true; queue.clear();
                queuedBytes = 0; pendingGap = null; }
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            synchronized (this) { done = true; notifyAll(); }
            cleanup();
        }
    }

    public void finish() throws InterruptedException {
        synchronized (this) { sealed = true; notifyAll(); }
        try { worker.join(5000); }
        catch (InterruptedException error) { fail("SpoolInterrupted"); throw error; }
        synchronized (this) { if (!done) fail("SpoolTimeout"); }
    }

    private synchronized void fail(String reason) {
        if (failure == null) failure = reason;
        sealed = true;
        queue.clear();
        queuedBytes = 0;
        pendingGap = null;
        worker.interrupt();
    }

    public synchronized Path completedFile() { return done && failure == null && !deleteRequested ? spool : null; }
    public synchronized int lastTimestamp() { return lastWritten; }
    public synchronized String status() {
        return failure != null ? "failed" : capped ? "capped" : done ? gaps == 0 ? "complete" : "gaps" : "recording";
    }
    public synchronized String statusJson() { return new Gson().toJson(new Coverage(this)); }
    public static String unavailableStatus(String reason, boolean supported) {
        Coverage status = new Coverage(); status.status = reason; status.supported = supported;
        return new Gson().toJson(status);
    }

    @Override public void close() {
        synchronized (this) { sealed = true; deleteRequested = true; queue.clear(); queuedBytes = 0;
            pendingGap = null; if (!done) worker.interrupt(); notifyAll(); }
        cleanup();
    }
    private void cleanup() {
        synchronized (this) { if (!done || !deleteRequested) return; }
        try { Files.deleteIfExists(spool); } catch (IOException | RuntimeException ignored) { }
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
        final int schemaVersion = 1, maxHz = 60;
        final boolean requested = true;
        boolean supported = true, capped;
        String status, failure;
        long byteLimit = MAX_BYTES, bytes, frames, gaps, droppedFrames;
        int lastReplayMillis = -1, peakQueuedBytes, peakQueuedFrames;
        Coverage() { }
        Coverage(ReplayPresentationTrack track) {
            status = track.status(); failure = track.failure; capped = track.capped;
            byteLimit = track.byteLimit; bytes = track.bytes; frames = track.frames; gaps = track.gaps;
            droppedFrames = track.dropped; lastReplayMillis = track.lastWritten;
            peakQueuedBytes = track.peakBytes; peakQueuedFrames = track.peakFrames;
        }
    }
}
