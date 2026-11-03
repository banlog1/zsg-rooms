// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Complete optional presentation states. Only an index page and two frames reside in memory. */
public final class PresentationTrack implements Closeable {
    public static final String ENTRY = "zsg-rooms/presentation.bin";
    public static final int MAGIC = 0x5A505253, MAX_FRAME = 65536;
    public static final long MAX_BYTES = Integer.MAX_VALUE;
    static final int AVAILABLE = 1, GAP = 2, BOB = 4, HANDS = 8, USING = 16, RIPTIDE = 32;
    static final int LEFT_MAIN = 64, SWING_OFF = 128, ACTIVE_OFF = 256;
    static final int SHOW_MAIN = 512, SHOW_OFF = 1024;
    private static final int PAGE = 256, MAX_GAP = 100;
    private final Path dataPath, indexPath;
    private final RandomAccessFile data, index;
    private final byte[] page = new byte[PAGE * 12];
    private final ByteBuffer pageFields = ByteBuffer.wrap(page);
    private int count, pageStart = -1, pageLength;
    private boolean closed, loaded;
    private final Pair pair = new Pair();

    private PresentationTrack() throws IOException {
        dataPath = Files.createTempFile("zsg-replay-presentation-", ".bin");
        Path createdPath = null;
        RandomAccessFile createdData = null, createdIndex = null;
        try {
            createdPath = Files.createTempFile("zsg-replay-presentation-", ".idx");
            createdData = new RandomAccessFile(dataPath.toFile(), "r");
            createdIndex = new RandomAccessFile(createdPath.toFile(), "r");
        } catch (IOException error) {
            if (createdData != null) createdData.close();
            if (createdIndex != null) createdIndex.close();
            Files.deleteIfExists(dataPath);
            if (createdPath != null) Files.deleteIfExists(createdPath);
            throw error;
        }
        indexPath = createdPath; data = createdData; index = createdIndex;
    }

    public static PresentationTrack read(RaceRecording recording) throws IOException {
        try (ZipFile zip = new ZipFile(recording.path.toFile())) {
            ZipEntry entry = zip.getEntry(ENTRY);
            if (entry == null) return null;
            if (entry.getSize() > MAX_BYTES) throw new IOException("Presentation track too large");
            try (InputStream input = zip.getInputStream(entry)) { return read(input, recording.duration); }
        }
    }

    static PresentationTrack read(InputStream input, int duration) throws IOException {
        DataInputStream source = new DataInputStream(new BufferedInputStream(input));
        if (duration < 0 || source.readInt() != MAGIC || source.readInt() != 1)
            throw new IOException("Unknown presentation format");
        PresentationTrack track = new PresentationTrack();
        try (DataOutputStream frames = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(track.dataPath)));
             DataOutputStream offsets = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(track.indexPath)))) {
            byte[] payload = new byte[MAX_FRAME];
            int previous = -1;
            long total = 8;
            while (true) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Closed");
                int first = source.read();
                if (first < 0) break;
                int time = first << 24 | source.readUnsignedByte() << 16 | source.readUnsignedByte() << 8 | source.readUnsignedByte();
                int world = source.readInt(), entity = source.readInt(), length = source.readInt();
                if (time <= previous || time > duration || world < -1 || entity < -1
                        || length < 4 || length > MAX_FRAME || total > MAX_BYTES - 16 - length)
                    throw new IOException("Invalid presentation frame");
                source.readFully(payload, 0, length);
                validate(payload, length, world, entity);
                offsets.writeInt(time); offsets.writeLong(total - 8);
                frames.writeInt(time); frames.writeInt(world); frames.writeInt(entity); frames.writeInt(length);
                frames.write(payload, 0, length);
                total += 16L + length;
                previous = time; track.count++;
            }
            return track;
        } catch (IOException | RuntimeException error) {
            try { track.close(); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }

    static void validate(byte[] payload, int length, int world, int entity) throws IOException {
        ByteBuffer fields = ByteBuffer.wrap(payload, 0, length);
        int flags = fields.getInt();
        if ((flags & AVAILABLE) == 0) {
            if (flags != GAP || length != 4) throw new IOException("Invalid presentation gap");
            return;
        }
        if ((flags & ~2047) != 0 || (flags & GAP) != 0 || world < 0 || entity < 0 || length < 83)
            throw new IOException("Invalid presentation state");
        for (int i = 0; i < 2; i++) {
            double fov = fields.getDouble();
            if (!Double.isFinite(fov) || fov <= 0 || fov >= 360) throw new IOException("Invalid presentation FOV");
        }
        for (int i = 0; i < 15; i++) {
            float value = fields.getFloat();
            boolean range = i == 0 || i == 3 ? value > 0 && value <= 1024
                    : i == 1 || i == 2 || i == 4 || i == 5 ? Math.abs(value) <= 1024
                    : i == 7 ? Math.abs(value) <= 8
                    : i >= 11 && i <= 13 ? value >= 0 && value <= 1
                    : i == 14 ? value >= 0 && value <= 72001 : Math.abs(value) <= 1000000000;
            if (!Float.isFinite(value) || !range) throw new IOException("Invalid presentation scalar");
        }
    }

    synchronized Pair at(double time, int world, int entity, int start, int end) {
        if (closed || !Double.isFinite(time) || time < start || time >= end || world < 0 || entity < 0) return null;
        try {
            if (!loaded || time < pair.before.time || pair.after != null && time >= pair.after.time
                    || pair.after == null && time != pair.before.time) {
                int low = 0, high = count;
                if (pageLength > 0 && time >= pageFields.getInt(0)
                        && (pageStart + pageLength == count || time < pageFields.getInt((pageLength - 1) * 12))) {
                    low = pageStart; high = pageStart + pageLength;
                }
                while (low < high) {
                    int mid = (low + high) >>> 1;
                    if (timeAt(mid) <= time) low = mid + 1; else high = mid;
                }
                loaded = false;
                if (low == 0) return null;
                Frame before = pair.after != null && pair.after.time == timeAt(low - 1) ? pair.after : load(low - 1);
                pair.before = before; pair.after = low < count ? load(low) : null;
                loaded = true;
            }
            if (!pair.before.matches(world, entity) || pair.before.time < start) return null;
            pair.fraction = 0;
            if (time != pair.before.time) {
                if (pair.after == null || !pair.after.matches(world, entity) || pair.after.time >= end
                        || pair.after.time - pair.before.time > MAX_GAP) return null;
                pair.fraction = (time - pair.before.time) / (pair.after.time - pair.before.time);
            }
            return pair;
        } catch (IOException error) {
            try { close(); } catch (IOException ignored) { }
            return null;
        }
    }

    private Frame load(int slot) throws IOException {
        page(slot);
        data.seek(pageFields.getLong((slot - pageStart) * 12 + 4));
        int time = data.readInt(), world = data.readInt(), entity = data.readInt(), length = data.readInt();
        byte[] payload = new byte[length]; data.readFully(payload);
        return new Frame(time, world, entity, payload);
    }
    private int timeAt(int slot) throws IOException { page(slot); return pageFields.getInt((slot - pageStart) * 12); }
    private void page(int slot) throws IOException {
        if (pageStart >= 0 && slot >= pageStart && slot < pageStart + pageLength) return;
        pageStart = slot / PAGE * PAGE; pageLength = Math.min(PAGE, count - pageStart);
        index.seek(pageStart * 12L); index.readFully(page, 0, pageLength * 12);
    }
    int frameCount() { return count; }

    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true; loaded = false; pair.before = pair.after = null;
        IOException failure = null;
        try { data.close(); } catch (IOException error) { failure = error; }
        try { index.close(); } catch (IOException error) { failure = error; }
        try { Files.deleteIfExists(dataPath); } catch (IOException error) { failure = error; }
        try { Files.deleteIfExists(indexPath); } catch (IOException error) { failure = error; }
        if (failure != null) throw failure;
    }

    static final class Frame {
        final int time, world, entity, flags;
        final byte[] payload;
        Frame(int time, int world, int entity, byte[] payload) {
            this.time = time; this.world = world; this.entity = entity; this.payload = payload;
            flags = ByteBuffer.wrap(payload).getInt();
        }
        boolean matches(int world, int entity) {
            return flags != GAP && (flags & AVAILABLE) != 0 && this.world == world && this.entity == entity;
        }
    }
    static final class Pair { Frame before, after; double fraction; }
}
