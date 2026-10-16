// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.replaymod.replaystudio.PacketData;
import com.replaymod.replaystudio.io.ReplayInputStream;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion;
import com.replaymod.replaystudio.protocol.PacketTypeRegistry;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.NetworkState;
import net.minecraft.network.packet.s2c.play.AdvancementUpdateS2CPacket;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Construct on the main thread, then call read on a bounded background executor. No active replay access. */
final class SplitMilestoneReader {
    static final int MAX_PACKET_BYTES = 16 * 1024 * 1024;
    static final long MAX_PACKET_COUNT = 20_000_000;
    static final long MAX_STREAM_BYTES = RaceRecording.MAX_FILE;
    private static final CompletedIndexCache<List<Milestones.Entry>> CACHE = new CompletedIndexCache<>(4, 1024 * 1024);
    private final int packetId;

    SplitMilestoneReader() {
        this(NetworkState.PLAY.getPacketId(NetworkSide.CLIENTBOUND, new AdvancementUpdateS2CPacket()));
    }

    // Allows isolated tests without initializing the game's packet registry on a worker.
    SplitMilestoneReader(int packetId) {
        if (packetId < 0) throw new IllegalArgumentException("Missing advancement packet ID");
        this.packetId = packetId;
    }

    /** Returns only a complete immutable list; cancellation throws InterruptedIOException, never an empty partial result. */
    List<Milestones.Entry> read(Path input, BooleanSupplier cancelled) throws IOException {
        Objects.requireNonNull(cancelled, "cancelled");
        checkCancelled(cancelled);
        Path path = input.toRealPath();
        BasicFileAttributes before = Files.readAttributes(path, BasicFileAttributes.class);
        require(before.isRegularFile() && before.size() <= RaceRecording.MAX_FILE, "Replay missing or too large");
        try (ZipFile zip = new ZipFile(path.toFile())) {
            validateArchive(zip);
            String identity = identity(zip);
            Header header = header(zip, cancelled);
            CompletedIndexCache.Key key = null;
            try { key = CompletedIndexCache.Key.read(path); }
            catch (IOException ignored) { /* Standard ReplayMod archives need not contain Rooms metadata. */ }
            if (key != null) require(key.signature.equals(CompletedIndexCache.Key.signature(zip)), "Replay changed before reading");
            checkCancelled(cancelled);
            List<Milestones.Entry> result = CACHE.get(key);
            if (result == null) {
                MilestonePacketTracker tracker = new MilestonePacketTracker(packetId);
                ZipEntry entry = zip.getEntry("recording.tmcpr");
                require(entry.getSize() >= 0 && entry.getSize() <= MAX_STREAM_BYTES, "Replay packet stream is too large");
                try (InputStream source = zip.getInputStream(entry);
                     PacketFrames frames = new PacketFrames(source, entry.getSize(), entry.getCrc(), header.duration, cancelled);
                     ReplayInputStream packets = new ReplayInputStream(
                             PacketTypeRegistry.get(ProtocolVersion.v1_16_1, State.LOGIN), frames, header.format, 736)) {
                    while (true) {
                        checkCancelled(cancelled);
                        PacketData data = packets.readPacket();
                        if (data == null) break;
                        try {
                            checkCancelled(cancelled);
                            tracker.packet(data);
                        } finally { data.release(); }
                    }
                    frames.verifyComplete();
                }
                result = tracker.snapshot();
            }
            checkCancelled(cancelled);
            BasicFileAttributes after = Files.readAttributes(path, BasicFileAttributes.class);
            require(before.size() == after.size() && before.lastModifiedTime().equals(after.lastModifiedTime())
                    && Objects.equals(before.fileKey(), after.fileKey()), "Replay changed while reading");
            try (ZipFile current = new ZipFile(path.toFile())) {
                validateArchive(current);
                require(identity.equals(identity(current)), "Replay changed while reading");
            }
            checkCancelled(cancelled);
            if (key != null) CACHE.put(key, result, 4096L + result.size() * 64L);
            return result;
        } catch (RuntimeException e) {
            throw new IOException("Invalid replay milestone data", e);
        }
    }

    private static void validateArchive(ZipFile zip) throws IOException {
        require(zip.size() <= 4096, "Too many archive entries");
        Set<String> names = new HashSet<>();
        java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) require(names.add(entries.nextElement().getName()), "Duplicate archive entries");
    }

    private static String identity(ZipFile zip) throws IOException {
        return CompletedIndexCache.Key.entry(zip, "recording.tmcpr") + ":"
                + CompletedIndexCache.Key.entry(zip, "metaData.json") + ":"
                + (zip.getEntry("zsg-rooms/races.json") == null ? "absent"
                : CompletedIndexCache.Key.entry(zip, "zsg-rooms/races.json"));
    }

    private static Header header(ZipFile zip, BooleanSupplier cancelled) throws IOException {
        int protocol = -1, format = -1, duration = -1;
        String fileFormat = null;
        try (JsonReader json = json(zip, "metaData.json", cancelled)) {
            Set<String> fields = new HashSet<>();
            json.beginObject();
            while (json.hasNext()) {
                checkCancelled(cancelled);
                String field = json.nextName();
                require(fields.add(field), "Duplicate replay header field");
                switch (field) {
                    case "protocol": protocol = json.nextInt(); break;
                    case "fileFormatVersion": format = json.nextInt(); break;
                    case "duration": duration = json.nextInt(); break;
                    case "fileFormat": fileFormat = json.nextString(); break;
                    default: json.skipValue();
                }
            }
            json.endObject();
            require(json.peek() == JsonToken.END_DOCUMENT, "Trailing replay metadata");
        }
        require(protocol == 736, "Milestones require a Minecraft 1.16.1 recording");
        require(format == 14 && "MCPR".equals(fileFormat) && duration >= 0, "Unsupported replay header");
        if (zip.getEntry("zsg-rooms/races.json") != null) {
            boolean complete = false, truncated = true;
            try (JsonReader json = json(zip, "zsg-rooms/races.json", cancelled)) {
                Set<String> fields = new HashSet<>();
                json.beginObject();
                while (json.hasNext()) {
                    checkCancelled(cancelled);
                    String field = json.nextName();
                    require(fields.add(field), "Duplicate race metadata field");
                    if (field.equals("complete")) complete = json.nextBoolean();
                    else if (field.equals("truncated")) truncated = json.nextBoolean();
                    else json.skipValue();
                }
                json.endObject();
                require(json.peek() == JsonToken.END_DOCUMENT, "Trailing race metadata");
            }
            require(complete && !truncated, "Incomplete replay metadata");
        }
        return new Header(format, duration);
    }

    private static JsonReader json(ZipFile zip, String name, BooleanSupplier cancelled) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        require(entry != null && entry.getSize() >= 0 && entry.getSize() <= RaceRecording.MAX_JSON, "Missing or oversized replay metadata");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CRC32 crc = new CRC32();
        try (InputStream source = zip.getInputStream(entry)) {
            byte[] buffer = new byte[8192];
            int count;
            while (true) {
                checkCancelled(cancelled);
                count = source.read(buffer);
                if (count < 0) break;
                require(bytes.size() + count <= RaceRecording.MAX_JSON, "Oversized replay metadata");
                bytes.write(buffer, 0, count);
                crc.update(buffer, 0, count);
            }
        }
        require(bytes.size() == entry.getSize() && crc.getValue() == entry.getCrc(), "Corrupt replay metadata");
        return new JsonReader(new StringReader(new String(bytes.toByteArray(), StandardCharsets.UTF_8)));
    }

    private static final class Header {
        final int format, duration;
        Header(int format, int duration) { this.format = format; this.duration = duration; }
    }

    static void checkCancelled(BooleanSupplier cancelled) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean())
            throw new InterruptedIOException("Milestone reading cancelled");
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }

    /** Validate a whole frame before ReplayStudio allocates its ByteBuf, including headers it would silently drop. */
    static final class PacketFrames extends InputStream {
        private final DataInputStream source;
        private final long expectedBytes, expectedCrc;
        private final int duration;
        private final BooleanSupplier cancelled;
        private final CRC32 crc = new CRC32();
        private final byte[] header = new byte[8];
        private byte[] payload = new byte[0];
        private int position = 8, length, previousTime;
        private long bytes, count;
        private boolean eof;

        PacketFrames(InputStream source, long expectedBytes, long expectedCrc, int duration, BooleanSupplier cancelled) {
            this.source = new DataInputStream(source);
            this.expectedBytes = expectedBytes; this.expectedCrc = expectedCrc;
            this.duration = duration; this.cancelled = cancelled;
        }

        private boolean next() throws IOException {
            if (position < length + 8) return true;
            if (eof) return false;
            checkCancelled(cancelled);
            int first = source.read();
            if (first < 0) { eof = true; return false; }
            header[0] = (byte) first;
            source.readFully(header, 1, 7);
            int time = integer(header, 0), size = integer(header, 4);
            require(time >= previousTime && time <= duration && size >= 0 && size <= MAX_PACKET_BYTES, "Invalid replay packet header");
            require(++count <= MAX_PACKET_COUNT && bytes + 8L + size <= MAX_STREAM_BYTES
                    && bytes + 8L + size <= expectedBytes, "Replay packet limits exceeded");
            if (payload.length < size) payload = new byte[size];
            // Cancellation during prefetch is safe: ReplayStudio has not allocated this frame yet.
            for (int offset = 0; offset < size;) {
                checkCancelled(cancelled);
                int part = Math.min(8192, size - offset);
                source.readFully(payload, offset, part);
                offset += part;
            }
            if (size > 0) {
                int idBytes = 0;
                while (idBytes < size && idBytes < 5 && (payload[idBytes] & 128) != 0) idBytes++;
                require(idBytes < size && idBytes < 5 && (idBytes < 4 || (payload[4] & 248) == 0), "Invalid replay packet ID");
            }
            crc.update(header); crc.update(payload, 0, size);
            bytes += 8L + size;
            previousTime = time; position = 0; length = size;
            return true;
        }

        @Override public int read() throws IOException {
            return next() ? (position < 8 ? header[position++] : payload[position++ - 8]) & 255 : -1;
        }

        @Override public int read(byte[] buffer, int offset, int size) throws IOException {
            Objects.requireNonNull(buffer);
            if (offset < 0 || size < 0 || size > buffer.length - offset) throw new IndexOutOfBoundsException();
            if (size == 0) return 0;
            if (!next()) return -1;
            int amount = Math.min(size, (position < 8 ? 8 : length + 8) - position);
            System.arraycopy(position < 8 ? header : payload, position < 8 ? position : position - 8, buffer, offset, amount);
            position += amount;
            return amount;
        }

        void verifyComplete() throws IOException {
            require(eof && bytes == expectedBytes && crc.getValue() == expectedCrc, "Incomplete or corrupt replay packet stream");
        }

        private static int integer(byte[] value, int offset) {
            return (value[offset] & 255) << 24 | (value[offset + 1] & 255) << 16
                    | (value[offset + 2] & 255) << 8 | value[offset + 3] & 255;
        }

        @Override public void close() throws IOException { source.close(); }
    }
}
