// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Optional full-state frames, indexed on disk so playback memory does not grow with the recording. */
final class DetailedTrack implements Closeable {
    static final String ENTRY = "zsg-rooms/detail.bin";
    static final int MAGIC = 0x5A444554;
    static final int MAX_FRAME = 65536;
    static final long MAX_BYTES = 2L * 1024 * 1024 * 1024 - 1;
    private static final int PAGE = 256;
    private final Path dataPath, indexPath;
    private final RandomAccessFile data, index;
    private final int[] pageTimes = new int[PAGE];
    private final long[] pageOffsets = new long[PAGE];
    private final byte[] pageBytes = new byte[PAGE * 12];
    private final byte[] header = new byte[16];
    private int count, pageStart = -1, pageLength;
    private Frame cached;
    private int cachedEnd;
    private boolean closed;

    private DetailedTrack() throws IOException {
        dataPath = Files.createTempFile("zsg-replay-detail-", ".bin");
        Path createdIndex = null;
        RandomAccessFile createdData = null, createdFile = null;
        try {
            createdIndex = Files.createTempFile("zsg-replay-detail-", ".idx");
            createdData = new RandomAccessFile(dataPath.toFile(), "rw");
            createdFile = new RandomAccessFile(createdIndex.toFile(), "rw");
        } catch (IOException e) {
            if (createdData != null) createdData.close();
            if (createdFile != null) createdFile.close();
            Files.deleteIfExists(dataPath);
            if (createdIndex != null) Files.deleteIfExists(createdIndex);
            throw e;
        }
        indexPath = createdIndex;
        data = createdData;
        index = createdFile;
    }

    static DetailedTrack read(RaceRecording recording) throws IOException {
        try (ZipFile zip = new ZipFile(recording.path.toFile())) {
            ZipEntry entry = zip.getEntry(ENTRY);
            if (entry == null) return null;
            if (entry.getSize() > MAX_BYTES) throw new IOException("Detailed track is too large");
            try (InputStream input = zip.getInputStream(entry)) { return read(input, recording.duration); }
        }
    }

    static DetailedTrack read(InputStream input, int duration) throws IOException {
        DataInputStream source = new DataInputStream(new java.io.BufferedInputStream(input));
        if (source.readInt() != MAGIC || source.readInt() != 1) throw new IOException("Unknown detailed format");
        DetailedTrack track = new DetailedTrack();
        try (java.io.DataOutputStream frames = new java.io.DataOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(track.dataPath)));
             java.io.DataOutputStream offsets = new java.io.DataOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(track.indexPath)))) {
            byte[] buffer = new byte[MAX_FRAME];
            int previous = -1;
            long total = 8;
            while (true) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Closed");
                int first = source.read();
                if (first < 0) break;
                int time = first << 24 | source.readUnsignedByte() << 16
                        | source.readUnsignedByte() << 8 | source.readUnsignedByte();
                int world = source.readInt(), entity = source.readInt(), length = source.readInt();
                total += 16L + length;
                if (time <= previous || time > duration || world < -1 || entity < -1
                        || length < 0 || length > MAX_FRAME || total > MAX_BYTES)
                    throw new IOException("Invalid detailed frame");
                source.readFully(buffer, 0, length);
                offsets.writeInt(time);
                offsets.writeLong(total - 8 - 16 - length);
                frames.writeInt(time);
                frames.writeInt(world);
                frames.writeInt(entity);
                frames.writeInt(length);
                frames.write(buffer, 0, length);
                track.count++;
                previous = time;
            }
            return track;
        } catch (IOException | RuntimeException e) {
            try { track.close(); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
    }

    synchronized Frame at(int time, int world, int entity, int intervalStart) {
        if (closed) return null;
        try {
            if (cached == null || time < cached.time || time >= cachedEnd) {
                int low = 0, high = count;
                // Normal playback stays on the loaded index page; random seeks retain the full search.
                if (pageLength > 0 && time >= pageTimes[0]
                        && (pageStart + pageLength == count || time < pageTimes[pageLength - 1])) {
                    low = pageStart;
                    high = pageStart + pageLength;
                }
                while (low < high) {
                    int mid = (low + high) >>> 1;
                    if (timeAt(mid) <= time) low = mid + 1; else high = mid;
                }
                if (low == 0) return null;
                int slot = low - 1;
                cachedEnd = low == count ? Integer.MAX_VALUE : timeAt(low);
                page(slot);
                data.seek(pageOffsets[slot - pageStart]);
                data.readFully(header);
                java.nio.ByteBuffer fields = java.nio.ByteBuffer.wrap(header);
                int frameTime = fields.getInt(), frameWorld = fields.getInt(), frameEntity = fields.getInt();
                byte[] payload = new byte[fields.getInt()];
                data.readFully(payload);
                cached = new Frame(frameTime, frameWorld, frameEntity, payload);
            }
            return cached.world == world && cached.entity == entity && cached.time >= intervalStart
                    && time - cached.time <= 1500 && cached.payload.length > 0 ? cached : null;
        } catch (IOException e) {
            try { close(); } catch (IOException ignored) { }
            return null;
        }
    }

    private int timeAt(int slot) throws IOException { page(slot); return pageTimes[slot - pageStart]; }

    private void page(int slot) throws IOException {
        if (pageStart >= 0 && slot >= pageStart && slot < pageStart + pageLength) return;
        pageStart = slot / PAGE * PAGE;
        pageLength = Math.min(PAGE, count - pageStart);
        index.seek(pageStart * 12L);
        index.readFully(pageBytes, 0, pageLength * 12);
        java.nio.ByteBuffer fields = java.nio.ByteBuffer.wrap(pageBytes);
        for (int i = 0; i < pageLength; i++) {
            pageTimes[i] = fields.getInt();
            pageOffsets[i] = fields.getLong();
        }
    }

    int frameCount() { return count; }

    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        cached = null;
        IOException failure = null;
        try { data.close(); } catch (IOException e) { failure = e; }
        try { index.close(); } catch (IOException e) { failure = e; }
        try { Files.deleteIfExists(dataPath); } catch (IOException e) { failure = e; }
        try { Files.deleteIfExists(indexPath); } catch (IOException e) { failure = e; }
        if (failure != null) throw failure;
    }

    static final class Frame {
        final int time, world, entity;
        final byte[] payload;
        final int renderDistance;
        Frame(int time, int world, int entity, byte[] payload) {
            this.time = time; this.world = world; this.entity = entity; this.payload = payload;
            // The presentation header can be read without decoding inventory stacks.
            java.nio.ByteBuffer header = java.nio.ByteBuffer.wrap(payload);
            int distance = header.remaining() >= 5 && header.get() == 1 ? header.getInt() : -1;
            renderDistance = distance >= 2 && distance <= 128 ? distance : -1;
        }
    }
}
