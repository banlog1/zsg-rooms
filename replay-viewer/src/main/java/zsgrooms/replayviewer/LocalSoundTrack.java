// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Disk-backed optional original-local audio. Completion and every record are validated before publication. */
final class LocalSoundTrack implements Closeable {
    static final String ENTRY = "zsg-rooms/local-sounds.bin";
    static final int MAGIC = 0x5A534E44, VERSION = 1;
    static final int STEP = 1, MINING = 2, PLACEMENT = 4, BUCKET = 8, CONSUMPTION = 16, HURT = 32, FALL = 64;
    static final int CHANNELS = 127;
    static final long MAX_BYTES = 512L * 1024 * 1024;

    static boolean playerSoundSupported(int channel, String id) {
        if (!id.startsWith("minecraft:")) return false;
        switch (channel) {
            case STEP: return id.startsWith("minecraft:block.") && id.endsWith(".step");
            case FALL: return id.equals("minecraft:entity.player.big_fall") || id.equals("minecraft:entity.player.small_fall")
                    || id.startsWith("minecraft:block.") && id.endsWith(".fall");
            case CONSUMPTION: return id.equals("minecraft:entity.generic.eat") || id.equals("minecraft:entity.generic.drink")
                    || id.equals("minecraft:item.honey_bottle.drink");
            case HURT: return id.startsWith("minecraft:entity.player.hurt") || id.equals("minecraft:entity.player.death")
                    || id.equals("minecraft:enchant.thorns.hit") || id.equals("minecraft:item.shield.block")
                    || id.equals("minecraft:item.shield.break");
            default: return false;
        }
    }
    private final Path directory;
    private RandomAccessFile events, index, contexts;
    private int eventCount, contextCount, endTime;
    private int cachedContext = -1, contextStart, contextEnd, contextWorld, contextEntity, contextMask;
    private boolean closed;

    private LocalSoundTrack() throws IOException {
        directory = Files.createTempDirectory("zsg-replay-local-sounds-");
        try {
            events = new RandomAccessFile(directory.resolve("events.bin").toFile(), "rw");
            index = new RandomAccessFile(directory.resolve("events.idx").toFile(), "rw");
            contexts = new RandomAccessFile(directory.resolve("contexts.idx").toFile(), "rw");
        } catch (IOException error) {
            try { close(); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }

    static LocalSoundTrack read(RaceRecording recording) throws IOException {
        try (ZipFile zip = new ZipFile(recording.path.toFile())) {
            ZipEntry entry = zip.getEntry(ENTRY);
            if (entry == null) return null;
            if (entry.getSize() > MAX_BYTES) throw new IOException("Local sound track is too large");
            try (InputStream input = zip.getInputStream(entry)) { return read(input, recording.duration); }
        }
    }

    static LocalSoundTrack read(InputStream input, int duration) throws IOException {
        DataInputStream source = new DataInputStream(new BufferedInputStream(input));
        if (source.readInt() != MAGIC || source.readInt() != VERSION) throw new IOException("Unknown local sound format");
        int supported = source.readInt();
        if (duration < 0 || supported <= 0 || (supported & ~CHANNELS) != 0) throw new IOException("Invalid local sound header");
        LocalSoundTrack track = new LocalSoundTrack();
        try {
            long bytes = 12;
            int previous = -1, world = -1, entity = -1, mask = 0, lastWorld = -1;
            while (true) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Closed");
                int type = source.readUnsignedByte(), time = source.readInt();
                bytes += 5;
                if (time < 0 || time < previous || time > duration || bytes > MAX_BYTES) throw new IOException("Invalid local sound time/size");
                previous = time;
                if (type == 0) {
                    if (source.read() != -1) throw new IOException("Data after local sound completion");
                    track.endTime = time;
                    return track;
                }
                int nextWorld = source.readInt(), nextEntity = source.readInt();
                bytes += 8;
                if (nextWorld < -1 || nextEntity < -1) throw new IOException("Invalid local sound identity");
                if (type == 1) {
                    int nextMask = source.readInt();
                    bytes += 4;
                    if ((nextMask & ~supported) != 0 || nextMask != 0 && (nextWorld < 0 || nextEntity < 0 || nextWorld < lastWorld))
                        throw new IOException("Invalid local sound context");
                    if (nextMask != 0) lastWorld = nextWorld;
                    world = nextWorld; entity = nextEntity; mask = nextMask;
                    track.contexts.writeInt(time); track.contexts.writeInt(world);
                    track.contexts.writeInt(entity); track.contexts.writeInt(mask);
                    track.contextCount++;
                } else if (type == 2) {
                    int channel = source.readUnsignedByte(), category = source.readUnsignedByte();
                    float volume = source.readFloat(), pitch = source.readFloat();
                    double x = source.readDouble(), y = source.readDouble(), z = source.readDouble();
                    int length = source.readUnsignedShort();
                    bytes += 36L + length;
                    if (length < 3 || length > 256 || bytes > MAX_BYTES || nextWorld != world || nextEntity != entity
                            || (channel & mask) == 0 || Integer.bitCount(channel) != 1 || category > 9
                            || !Float.isFinite(volume) || volume < 0 || volume > 64
                            || !Float.isFinite(pitch) || pitch <= 0 || pitch > 8 || !position(x) || !position(y) || !position(z))
                        throw new IOException("Invalid local sound event");
                    byte[] id = new byte[length]; source.readFully(id);
                    String identifier = new String(id, StandardCharsets.US_ASCII);
                    if (!identifier(identifier)) throw new IOException("Invalid local sound identifier");
                    track.index.writeInt(time); track.index.writeLong(track.events.getFilePointer());
                    track.events.writeInt(time); track.events.writeInt(world); track.events.writeInt(entity);
                    track.events.writeByte(channel); track.events.writeByte(category);
                    track.events.writeFloat(volume); track.events.writeFloat(pitch);
                    track.events.writeDouble(x); track.events.writeDouble(y); track.events.writeDouble(z);
                    track.events.writeShort(length); track.events.write(id);
                    track.eventCount++;
                } else throw new IOException("Unknown local sound record");
            }
        } catch (IOException | RuntimeException error) {
            try { track.close(); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }

    private static boolean identifier(String id) {
        int colon = id.indexOf(':');
        if (colon < 1 || colon == id.length() - 1) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (i == colon) continue;
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-' || c == '.' || i > colon && c == '/')) return false;
        }
        return true;
    }
    private static boolean position(double value) { return Double.isFinite(value) && Math.abs(value) <= 32000000; }

    synchronized int coverage(int time, int world, int entity) {
        if (closed || time < 0 || time >= endTime || world < 0 || entity < 0) return 0;
        try {
            if (cachedContext < 0 || time < contextStart || time >= contextEnd) {
                int low = 0, high = contextCount;
                while (low < high) {
                    int mid = (low + high) >>> 1;
                    contexts.seek(mid * 16L);
                    if (contexts.readInt() <= time) low = mid + 1; else high = mid;
                }
                if (low == 0) return 0;
                cachedContext = low - 1;
                contexts.seek(cachedContext * 16L);
                contextStart = contexts.readInt(); contextWorld = contexts.readInt();
                contextEntity = contexts.readInt(); contextMask = contexts.readInt();
                contextEnd = endTime;
                if (low < contextCount) { contexts.seek(low * 16L); contextEnd = contexts.readInt(); }
            }
            return contextWorld == world && contextEntity == entity ? contextMask : 0;
        } catch (IOException error) { unavailable(); return 0; }
    }

    synchronized int after(int time) throws IOException {
        if (closed) throw new IOException("Closed local sound track");
        int low = 0, high = eventCount;
        while (low < high) {
            int mid = (low + high) >>> 1;
            index.seek(mid * 12L);
            if (index.readInt() <= time) low = mid + 1; else high = mid;
        }
        return low;
    }

    synchronized Event event(int slot) throws IOException {
        if (closed || slot < 0 || slot >= eventCount) throw new IOException("Invalid local sound slot");
        index.seek(slot * 12L + 4);
        events.seek(index.readLong());
        Event event = new Event();
        event.time = events.readInt(); event.world = events.readInt(); event.entity = events.readInt();
        event.channel = events.readUnsignedByte(); event.category = events.readUnsignedByte();
        event.volume = events.readFloat(); event.pitch = events.readFloat();
        event.x = events.readDouble(); event.y = events.readDouble(); event.z = events.readDouble();
        byte[] id = new byte[events.readUnsignedShort()]; events.readFully(id);
        event.sound = new String(id, StandardCharsets.US_ASCII);
        return event;
    }

    int eventCount() { return eventCount; }
    int endTime() { return endTime; }
    private void unavailable() { try { close(); } catch (IOException ignored) { } }

    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        IOException failure = null;
        for (RandomAccessFile file : new RandomAccessFile[] {events, index, contexts}) {
            if (file != null) try { file.close(); } catch (IOException error) { failure = error; }
        }
        for (String name : new String[] {"events.bin", "events.idx", "contexts.idx"}) {
            try { Files.deleteIfExists(directory.resolve(name)); } catch (IOException error) { failure = error; }
        }
        try { Files.deleteIfExists(directory); } catch (IOException error) { failure = error; }
        if (failure != null) throw failure;
    }

    static final class Event {
        int time, world, entity, channel, category;
        String sound;
        float volume, pitch;
        double x, y, z;
    }
}
