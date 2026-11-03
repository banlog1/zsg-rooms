package zsgrooms.modid.replay;

import com.google.gson.Gson;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Optional camera-only spool. Offers allocate no objects and never perform disk I/O. */
final class ReplayCameraTrack implements AutoCloseable {
    static final int MAGIC = 0x5A43414D;
    static final int VERSION = 1;
    static final int HEADER_BYTES = 12, RECORD_BYTES = 24;
    static final int AVAILABLE = 1, GAP = 2;
    static final int QUEUE_SAMPLES = 4096;
    static final long MAX_BYTES = Integer.MAX_VALUE;
    private static final long FINISH_MILLIS = 5000;
    private final Path spool;
    private final DataOutputStream output;
    private final int hz, queueLimit;
    private final long byteLimit, finishMillis;
    private final int[] ring;
    private final Thread worker;
    private int head, queued, peakQueuedSamples;
    private int lastOffered = -1, lastWritten = -1;
    private int gapTime = -1, gapWorld, gapEntity;
    private long reservedBytes = HEADER_BYTES, bytesWritten, samplesWritten, capturedSamples, gaps, droppedSamples, rejectedSamples;
    private boolean sealed, capped, done, deleteRequested;
    private String failure;

    static ReplayCameraTrack create(Path directory, int hz) throws IOException {
        validate(hz, QUEUE_SAMPLES, MAX_BYTES, FINISH_MILLIS);
        Path spool = Files.createTempFile(directory, ".zsg-camera-", ".bin");
        OutputStream sink = null;
        try {
            sink = new BufferedOutputStream(Files.newOutputStream(spool), 65536);
            return new ReplayCameraTrack(spool, sink, hz, QUEUE_SAMPLES, MAX_BYTES, FINISH_MILLIS);
        } catch (IOException | RuntimeException error) {
            if (sink != null) try { sink.close(); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
            try { Files.deleteIfExists(spool); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }

    // Small budgets and an injected sink make pressure and stalled-disk tests deterministic.
    ReplayCameraTrack(Path spool, OutputStream sink, int hz, int queueSamples, long maxBytes, long finishMillis) {
        validate(hz, queueSamples, maxBytes, finishMillis);
        this.spool = Objects.requireNonNull(spool, "spool");
        this.output = new DataOutputStream(Objects.requireNonNull(sink, "sink"));
        this.hz = hz;
        this.queueLimit = queueSamples;
        this.byteLimit = maxBytes;
        this.finishMillis = finishMillis;
        ring = new int[queueSamples * 6];
        worker = new Thread(this::write, "ZSG replay camera spool");
        worker.setDaemon(true);
        worker.start();
    }

    private static void validate(int hz, int samples, long bytes, long finishMillis) {
        if (hz != 60 && hz != 120 && hz != 240 || samples < 1 || samples > QUEUE_SAMPLES
                || bytes < HEADER_BYTES + RECORD_BYTES || bytes > MAX_BYTES || finishMillis < 1)
            throw new IllegalArgumentException("Invalid camera budgets or rate");
    }

    synchronized boolean accepting() { return !sealed && failure == null; }

    /** Valid timestamps, including dropped offers, must increase. Invalid input does not consume time. */
    synchronized boolean offer(long time, int world, int entity, int flags, float yaw, float pitch) {
        if (!accepting()) return false;
        if (time < 0 || time > Integer.MAX_VALUE || time <= lastOffered || world < -1 || entity < -1
                || flags != 0 && flags != AVAILABLE && flags != GAP || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                || flags == AVAILABLE && (world < 0 || entity < 0 || pitch < -90 || pitch > 90)) {
            rejectedSamples++;
            return false;
        }
        lastOffered = (int) time;
        // Keep one record available for a terminal gap, including any already reserved pending gap.
        if (reservedBytes > byteLimit - 2L * RECORD_BYTES) {
            drop((int) time, world, entity);
            capped = true;
            sealed = true;
            notifyAll();
            return false;
        }
        if (gapTime >= 0) {
            if (queueLimit - queued < 2) {
                drop((int) time, world, entity);
                return false;
            }
            enqueue(gapTime, gapWorld, gapEntity, GAP, 0, 0);
            gapTime = -1;
        }
        if (queued == queueLimit) {
            drop((int) time, world, entity);
            return false;
        }
        enqueue((int) time, world, entity, flags, Float.floatToRawIntBits(yaw), Float.floatToRawIntBits(pitch));
        reservedBytes += RECORD_BYTES;
        if (flags == AVAILABLE) capturedSamples++;
        if (flags == GAP) gaps++;
        return true;
    }

    private void drop(int time, int world, int entity) {
        droppedSamples++;
        if (gapTime < 0) {
            gapTime = time;
            gapWorld = world;
            gapEntity = entity;
            reservedBytes += RECORD_BYTES;
            gaps++;
        }
        notifyAll();
    }

    private void enqueue(int time, int world, int entity, int flags, int yaw, int pitch) {
        int offset = ((head + queued) % queueLimit) * 6;
        ring[offset] = time;
        ring[offset + 1] = world;
        ring[offset + 2] = entity;
        ring[offset + 3] = flags;
        ring[offset + 4] = yaw;
        ring[offset + 5] = pitch;
        queued++;
        peakQueuedSamples = Math.max(peakQueuedSamples, queued);
        notifyAll();
    }

    private synchronized int take(ByteBuffer batch) throws InterruptedException {
        while (queued == 0 && gapTime < 0 && !sealed) wait();
        batch.clear();
        while (queued > 0 && batch.remaining() >= RECORD_BYTES) {
            int offset = head * 6;
            for (int field = 0; field < 6; field++) batch.putInt(ring[offset + field]);
            head = (head + 1) % queueLimit;
            queued--;
        }
        // The pending marker is newer than every queued record, and older than all future offers.
        if (queued == 0 && gapTime >= 0 && batch.remaining() >= RECORD_BYTES) {
            batch.putInt(gapTime).putInt(gapWorld).putInt(gapEntity).putInt(GAP).putInt(0).putInt(0);
            gapTime = -1;
        }
        return batch.position();
    }

    private void write() {
        ByteBuffer batch = ByteBuffer.allocate(256 * RECORD_BYTES);
        try (DataOutputStream stream = output) {
            stream.writeInt(MAGIC);
            stream.writeInt(VERSION);
            stream.writeInt(hz);
            synchronized (this) { bytesWritten = HEADER_BYTES; }
            int length;
            while ((length = take(batch)) != 0) {
                stream.write(batch.array(), 0, length);
                synchronized (this) {
                    bytesWritten += length;
                    samplesWritten += length / RECORD_BYTES;
                    lastWritten = batch.getInt(length - RECORD_BYTES);
                }
            }
        } catch (IOException | RuntimeException | InterruptedException error) {
            synchronized (this) {
                if (failure == null) failure = error.getClass().getSimpleName();
                sealed = true;
                queued = 0;
                gapTime = -1;
            }
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            synchronized (this) { done = true; notifyAll(); }
            deleteIfRequested();
        }
    }

    void finish() throws InterruptedException {
        synchronized (this) { sealed = true; notifyAll(); }
        try { worker.join(finishMillis); }
        catch (InterruptedException error) {
            failShutdown("SpoolInterrupted");
            throw error;
        }
        synchronized (this) { if (!done) failShutdown("SpoolTimeout"); }
    }

    private synchronized void failShutdown(String reason) {
        if (failure == null) failure = reason;
        sealed = true;
        queued = 0;
        gapTime = -1;
        worker.interrupt();
    }

    synchronized Path completedFile() { return done && failure == null && !deleteRequested ? spool : null; }

    synchronized int lastTimestamp() { return lastWritten; }

    /** Infrequent HUD observation, never called from a per-sample path. */
    synchronized long[] health() { return new long[]{capturedSamples, droppedSamples, queued, queueLimit}; }

    synchronized String status() {
        return failure != null ? "failed" : capped ? "capped" : done ? gaps == 0 ? "complete" : "gaps" : "recording";
    }

    synchronized String statusJson() { return new Gson().toJson(new Coverage(this)); }

    /** Rate zero describes the Standard (no separate camera track) setting. */
    static String unavailableStatus(String reason, boolean supported, int hz) {
        if (hz != 0 && hz != 60 && hz != 120 && hz != 240) throw new IllegalArgumentException("Invalid camera rate");
        Coverage coverage = new Coverage(hz);
        coverage.status = Objects.requireNonNull(reason, "reason");
        coverage.supported = supported;
        return new Gson().toJson(coverage);
    }

    @Override public void close() {
        synchronized (this) {
            sealed = true;
            deleteRequested = true;
            queued = 0;
            gapTime = -1;
            if (!done) worker.interrupt();
            notifyAll();
        }
        deleteIfRequested();
    }

    private void deleteIfRequested() {
        synchronized (this) { if (!done || !deleteRequested) return; }
        try { Files.deleteIfExists(spool); } catch (IOException | RuntimeException ignored) { }
    }

    private static final class Coverage {
        final int schemaVersion = 1;
        final boolean requested;
        boolean supported = true;
        final int hz;
        String status, failure;
        boolean capped;
        long byteLimit = MAX_BYTES, bytes, samples, gaps, droppedSamples, rejectedSamples;
        int lastReplayMillis = -1, queueSampleLimit = QUEUE_SAMPLES, peakQueuedSamples, peakQueuedBytes;
        Coverage(int hz) { this.hz = hz; requested = hz != 0; }
        Coverage(ReplayCameraTrack track) {
            this(track.hz);
            status = track.status(); failure = track.failure; capped = track.capped;
            byteLimit = track.byteLimit; bytes = track.bytesWritten; samples = track.samplesWritten;
            gaps = track.gaps; droppedSamples = track.droppedSamples; rejectedSamples = track.rejectedSamples;
            lastReplayMillis = track.lastWritten; queueSampleLimit = track.queueLimit;
            peakQueuedSamples = track.peakQueuedSamples; peakQueuedBytes = track.peakQueuedSamples * RECORD_BYTES;
        }
    }
}
