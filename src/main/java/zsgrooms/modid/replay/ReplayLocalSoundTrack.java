package zsgrooms.modid.replay;

import com.google.gson.Gson;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;

/** Optional, bounded local-audio spool. Any lost event invalidates authority for the entire sidecar. */
final class ReplayLocalSoundTrack implements AutoCloseable {
    static final int MAGIC = 0x5A534E44, VERSION = 1;
    static final int STEP = 1, MINING = 2, PLACEMENT = 4, BUCKET = 8, CONSUMPTION = 16, HURT = 32, FALL = 64;
    static final int CHANNELS = 127;
    static final int END = 0, CONTEXT = 1, EVENT = 2;
    static final int QUEUE_BYTES = 256 * 1024, QUEUE_RECORDS = 1024;
    static final long MAX_BYTES = 512L * 1024 * 1024;
    private final Path path;
    private final OutputStream sink;
    private final ArrayDeque<byte[]> queue = new ArrayDeque<>();
    private final Thread worker;
    private final int queueLimit;
    private final long byteLimit;
    private final long finishMillis;
    private int queuedBytes, peakQueuedBytes, peakQueuedRecords;
    private int lastTime = -1, world = -1, entity = -1, mask = -1;
    private int lastAvailableWorld = -1;
    private long reservedBytes = 12, events, contexts, rejected;
    private boolean sealed, done, deleteRequested;
    private String failure;

    static ReplayLocalSoundTrack create(Path directory) throws IOException {
        Path path = Files.createTempFile(directory, ".zsg-local-sounds-", ".bin");
        try {
            return new ReplayLocalSoundTrack(path, new BufferedOutputStream(Files.newOutputStream(path), 65536),
                    QUEUE_BYTES, MAX_BYTES, 5000);
        } catch (IOException | RuntimeException error) {
            try { Files.deleteIfExists(path); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }

    ReplayLocalSoundTrack(Path path, OutputStream sink, int queueLimit, long byteLimit, long finishMillis) {
        if (queueLimit < 32 || queueLimit > QUEUE_BYTES || byteLimit < 34 || byteLimit > MAX_BYTES || finishMillis < 1)
            throw new IllegalArgumentException("Invalid sound spool budgets");
        this.path = path;
        this.sink = sink;
        this.queueLimit = queueLimit;
        this.byteLimit = byteLimit;
        this.finishMillis = finishMillis;
        worker = new Thread(this::write, "ZSG replay local sound spool");
        worker.setDaemon(true);
        worker.start();
    }

    synchronized boolean accepting() { return !sealed && failure == null; }

    synchronized boolean context(long time, int world, int entity, boolean available) {
        if (!accepting()) return false;
        int next = available ? CHANNELS : 0;
        if (!validTime(time) || world < -1 || entity < -1 || available && (world < lastAvailableWorld || world < 0 || entity < 0)) {
            rejected++;
            fail("InvalidContext");
            return false;
        }
        if (this.world == world && this.entity == entity && mask == next) return true;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(17);
            DataOutputStream data = new DataOutputStream(bytes);
            data.writeByte(CONTEXT); data.writeInt((int) time); data.writeInt(world); data.writeInt(entity); data.writeInt(next);
            if (!enqueue(bytes.toByteArray())) return false;
            this.world = world; this.entity = entity; mask = next; lastTime = (int) time; contexts++;
            if (available) lastAvailableWorld = world;
            return true;
        } catch (IOException impossible) { fail("EncodingFailure"); return false; }
    }

    synchronized boolean offer(long time, int world, int entity, int channel, int category, String sound,
                               float volume, float pitch, double x, double y, double z) {
        if (!accepting()) return false;
        if (!validTime(time) || world != this.world || entity != this.entity || mask == 0 || mask == -1
                || (channel & CHANNELS) == 0 || Integer.bitCount(channel) != 1 || category < 0 || category > 9
                || !identifier(sound) || !Float.isFinite(volume) || volume < 0 || volume > 64
                || !Float.isFinite(pitch) || pitch <= 0 || pitch > 8 || !position(x) || !position(y) || !position(z)) {
            rejected++;
            // Do not publish a claim of exact capture after losing a supported local event.
            fail("InvalidEvent");
            return false;
        }
        try {
            byte[] id = sound.getBytes(StandardCharsets.US_ASCII);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(49 + id.length);
            DataOutputStream data = new DataOutputStream(bytes);
            data.writeByte(EVENT); data.writeInt((int) time); data.writeInt(world); data.writeInt(entity);
            data.writeByte(channel); data.writeByte(category); data.writeFloat(volume); data.writeFloat(pitch);
            data.writeDouble(x); data.writeDouble(y); data.writeDouble(z); data.writeShort(id.length); data.write(id);
            if (!enqueue(bytes.toByteArray())) return false;
            lastTime = (int) time; events++;
            return true;
        } catch (IOException impossible) { fail("EncodingFailure"); return false; }
    }

    static boolean identifier(String id) {
        if (id == null || id.length() < 3 || id.length() > 256) return false;
        int colon = id.indexOf(':');
        if (colon < 1 || colon == id.length() - 1) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (i == colon) continue;
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-' || c == '.' || i > colon && c == '/'))
                return false;
        }
        return true;
    }

    private static boolean position(double value) { return Double.isFinite(value) && Math.abs(value) <= 32000000; }
    private boolean validTime(long time) { return time >= 0 && time <= Integer.MAX_VALUE && time >= lastTime; }

    private boolean enqueue(byte[] bytes) {
        if (queue.size() >= QUEUE_RECORDS || queuedBytes + bytes.length > queueLimit) { fail("QueueOverflow"); return false; }
        if (reservedBytes + bytes.length + 5 > byteLimit) { fail("SizeLimit"); return false; }
        queue.add(bytes); queuedBytes += bytes.length; reservedBytes += bytes.length;
        peakQueuedBytes = Math.max(peakQueuedBytes, queuedBytes);
        peakQueuedRecords = Math.max(peakQueuedRecords, queue.size());
        notifyAll();
        return true;
    }

    private synchronized byte[] take() throws InterruptedException {
        while (queue.isEmpty() && !sealed) wait();
        byte[] bytes = queue.poll();
        if (bytes != null) queuedBytes -= bytes.length;
        return bytes;
    }

    private void write() {
        try (DataOutputStream output = new DataOutputStream(sink)) {
            output.writeInt(MAGIC); output.writeInt(VERSION); output.writeInt(CHANNELS);
            byte[] bytes;
            while ((bytes = take()) != null) output.write(bytes);
            int end;
            synchronized (this) { end = failure == null ? Math.max(0, lastTime) : -1; }
            if (end >= 0) { output.writeByte(END); output.writeInt(end); }
        } catch (IOException | RuntimeException | InterruptedException error) {
            synchronized (this) { fail(error.getClass().getSimpleName()); }
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            synchronized (this) { done = true; notifyAll(); }
            deleteIfRequested();
        }
    }

    synchronized void fail(String reason) {
        if (failure == null) failure = reason;
        sealed = true; queue.clear(); queuedBytes = 0; notifyAll();
    }

    void finish(long endMillis) throws InterruptedException {
        synchronized (this) {
            if (!sealed) {
                if (!validTime(endMillis)) fail("InvalidEndTime");
                else { lastTime = (int) endMillis; sealed = true; notifyAll(); }
            }
        }
        try { worker.join(finishMillis); }
        catch (InterruptedException error) { synchronized (this) { fail("SpoolInterrupted"); } worker.interrupt(); throw error; }
        synchronized (this) { if (!done) { fail("SpoolTimeout"); worker.interrupt(); } }
    }

    synchronized Path completedFile() { return done && failure == null && !deleteRequested ? path : null; }
    synchronized int lastTimestamp() { return lastTime; }
    synchronized String status() { return failure != null ? "failed" : done ? "complete" : "recording"; }
    synchronized String statusJson() {
        java.util.Map<String, Object> value = new java.util.LinkedHashMap<>();
        value.put("schemaVersion", 1); value.put("requested", true); value.put("supportedChannels", CHANNELS);
        value.put("status", status()); value.put("failure", failure); value.put("events", events); value.put("contexts", contexts);
        value.put("bytes", reservedBytes + (done && failure == null ? 5 : 0)); value.put("byteLimit", byteLimit);
        value.put("peakQueuedBytes", peakQueuedBytes); value.put("peakQueuedRecords", peakQueuedRecords); value.put("rejected", rejected);
        return new Gson().toJson(value);
    }

    @Override public void close() {
        synchronized (this) {
            deleteRequested = true; sealed = true; queue.clear(); queuedBytes = 0; notifyAll();
            if (!done) worker.interrupt();
        }
        deleteIfRequested();
    }

    private void deleteIfRequested() {
        synchronized (this) { if (!done || !deleteRequested) return; }
        try { Files.deleteIfExists(path); } catch (IOException | RuntimeException ignored) { }
    }
}
