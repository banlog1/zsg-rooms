// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Optional camera samples with a fixed-size disk index and a bounded playback page. */
final class CameraTrack implements Closeable {
    static final String ENTRY = "zsg-rooms/camera.bin";
    static final int MAGIC = 0x5A43414D;
    static final int AVAILABLE = 1, GAP = 2, RECORD_BYTES = 24, MAX_GAP = 100;
    static final long MAX_BYTES = 2L * 1024 * 1024 * 1024 - 1;
    private static final int PAGE = 256;
    final int hz;
    private final Path path;
    private final RandomAccessFile data;
    private final byte[] pageBytes = new byte[PAGE * RECORD_BYTES];
    private final ByteBuffer fields = ByteBuffer.wrap(pageBytes);
    private final Sample before = new Sample(), after = new Sample();
    private int count, pageStart = -1, pageLength;
    private boolean pairLoaded, hasAfter, closed;

    private CameraTrack(int hz) throws IOException {
        this.hz = hz;
        path = Files.createTempFile("zsg-replay-camera-", ".bin");
        try { data = new RandomAccessFile(path.toFile(), "r"); }
        catch (IOException error) {
            try { Files.deleteIfExists(path); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }

    static CameraTrack read(RaceRecording recording) throws IOException {
        try (ZipFile zip = new ZipFile(recording.path.toFile())) {
            ZipEntry entry = zip.getEntry(ENTRY);
            if (entry == null) return null;
            if (entry.getSize() > MAX_BYTES) throw new IOException("Camera track is too large");
            try (InputStream input = zip.getInputStream(entry)) { return read(input, recording.duration); }
        }
    }

    static CameraTrack read(InputStream input, int duration) throws IOException {
        DataInputStream source = new DataInputStream(new BufferedInputStream(input));
        if (source.readInt() != MAGIC || source.readInt() != 1) throw new IOException("Unknown camera format");
        int hz = source.readInt();
        if (duration < 0 || hz != 60 && hz != 120 && hz != 240) throw new IOException("Invalid camera rate/duration");
        CameraTrack track = new CameraTrack(hz);
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(track.path))) {
            byte[] bytes = new byte[RECORD_BYTES];
            ByteBuffer record = ByteBuffer.wrap(bytes);
            int previous = -1;
            long total = 12;
            while (true) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Closed");
                int first = source.read();
                if (first < 0) break;
                bytes[0] = (byte) first;
                source.readFully(bytes, 1, RECORD_BYTES - 1);
                record.rewind();
                int time = record.getInt(), world = record.getInt(), entity = record.getInt(), flags = record.getInt();
                float yaw = record.getFloat(), pitch = record.getFloat();
                total += RECORD_BYTES;
                if (time < 0 || time <= previous || time > duration || world < -1 || entity < -1
                        || flags != 0 && flags != AVAILABLE && flags != GAP || total > MAX_BYTES
                        || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                        || flags == AVAILABLE && (world < 0 || entity < 0 || pitch < -90 || pitch > 90))
                    throw new IOException("Invalid camera sample");
                output.write(bytes);
                previous = time;
                track.count++;
            }
            return track;
        } catch (IOException | RuntimeException error) {
            try { track.close(); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }

    synchronized boolean sample(double time, int world, int entity, int start, int end, float[] rotation) {
        if (closed || !Double.isFinite(time) || time < start || time >= end || world < 0 || entity < 0) return false;
        try {
            if (!pairLoaded || time < before.time || hasAfter && time >= after.time || !hasAfter && time != before.time) {
                int low = 0, high = count;
                if (pageLength > 0 && time >= timeOnPage(0)
                        && (pageStart + pageLength == count || time < timeOnPage(pageLength - 1))) {
                    low = pageStart;
                    high = pageStart + pageLength;
                }
                while (low < high) {
                    int mid = (low + high) >>> 1;
                    if (timeAt(mid) <= time) low = mid + 1; else high = mid;
                }
                pairLoaded = false;
                if (low == 0) return false;
                load(low - 1, before);
                hasAfter = low < count;
                if (hasAfter) load(low, after);
                pairLoaded = true;
            }
            if (!before.matches(world, entity) || before.time < start) return false;
            double fraction = 0;
            if (time != before.time) {
                if (!hasAfter || !after.matches(world, entity)
                        || after.time >= end || after.time - before.time > MAX_GAP) return false;
                fraction = (time - before.time) / (after.time - before.time);
            }
            double yawDelta = ((double) after.yaw - before.yaw) % 360;
            if (yawDelta >= 180) yawDelta -= 360;
            if (yawDelta < -180) yawDelta += 360;
            rotation[0] = fraction == 0 ? before.yaw : (float) (before.yaw + fraction * yawDelta);
            rotation[1] = fraction == 0 ? before.pitch : (float) (before.pitch + fraction * (after.pitch - before.pitch));
            return true;
        } catch (IOException error) {
            try { close(); } catch (IOException ignored) { }
            return false;
        }
    }

    private int timeOnPage(int slot) { return fields.getInt(slot * RECORD_BYTES); }
    private int timeAt(int slot) throws IOException { page(slot); return timeOnPage(slot - pageStart); }

    private void load(int slot, Sample sample) throws IOException {
        page(slot);
        int offset = (slot - pageStart) * RECORD_BYTES;
        sample.time = fields.getInt(offset);
        sample.world = fields.getInt(offset + 4);
        sample.entity = fields.getInt(offset + 8);
        sample.flags = fields.getInt(offset + 12);
        sample.yaw = fields.getFloat(offset + 16);
        sample.pitch = fields.getFloat(offset + 20);
    }

    private void page(int slot) throws IOException {
        if (pageStart >= 0 && slot >= pageStart && slot < pageStart + pageLength) return;
        pageStart = slot / PAGE * PAGE;
        pageLength = Math.min(PAGE, count - pageStart);
        data.seek(pageStart * (long) RECORD_BYTES);
        data.readFully(pageBytes, 0, pageLength * RECORD_BYTES);
    }

    int frameCount() { return count; }

    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        pairLoaded = false;
        IOException failure = null;
        try { data.close(); } catch (IOException error) { failure = error; }
        try { Files.deleteIfExists(path); } catch (IOException error) { failure = error; }
        if (failure != null) throw failure;
    }

    private static final class Sample {
        int time, world, entity, flags;
        float yaw, pitch;
        boolean matches(int world, int entity) {
            return (flags & AVAILABLE) != 0 && this.world == world && this.entity == entity;
        }
    }
}
