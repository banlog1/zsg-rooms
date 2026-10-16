// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.replaymod.replaystudio.PacketData;
import com.replaymod.replaystudio.io.ReplayInputStream;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion;
import com.replaymod.replaystudio.protocol.PacketTypeRegistry;
import com.replaymod.replaystudio.replay.ReplayFile;
import zsgrooms.replayviewer.mixin.ReplayFileAccessor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Lazy viewer-only history. Never seeks the active replay or writes into its archive. */
final class TrailIndex implements AutoCloseable {
    static final int MAX_DURATION = 30_000, LOOKAHEAD = 5_000, MAX_SELECTED = 512;
    private static final int MAX_METADATA = 1024 * 1024;
    volatile String status = "Trail history not requested";
    private final Path source, scratchDirectory;
    private final String expectedIdentity, unavailable;
    private volatile boolean closed, failed;
    private volatile TrailDiskStore.Window window;
    private volatile long revision;
    private volatile Memo memo;
    private Request pending;
    private Thread worker;

    /** Construct on the client thread. Captures archive identity only; all disk scanning waits for request(). */
    TrailIndex(ReplayFile file) {
        Path path = null;
        String signature = null, reason = null;
        try {
            ReplayFileAccessor archive = RecordingIndex.archive(file);
            if (archive == null || archive.zsgViewer$getInput() == null || archive.zsgViewer$getZipFile() == null)
                throw new IOException("No saved replay archive");
            for (String name : new String[]{"recording.tmcpr", "metaData.json"}) {
                require(!archive.zsgViewer$getChangedEntries().containsKey(name)
                        && !archive.zsgViewer$getRemovedEntries().contains(name)
                        && !archive.zsgViewer$getOutputStreams().containsKey(name), "Save replay changes before indexing trails");
            }
            path = archive.zsgViewer$getInput().toPath();
            signature = identity(archive.zsgViewer$getZipFile());
        } catch (Exception e) { reason = "Trail history unavailable: " + message(e); }
        source = path; expectedIdentity = signature; unavailable = reason; scratchDirectory = null;
    }

    // Independent-file entry point for focused tests, without Minecraft mixin initialization.
    TrailIndex(Path source, Path scratchDirectory) {
        this.source = source; this.scratchDirectory = scratchDirectory;
        expectedIdentity = null; unavailable = null;
    }

    /** Cheap/coalesced, no file IO. Empty selections also start indexing so future entities are ready. */
    synchronized void request(int time, Set<UUID> entities, int durationMs) {
        if (closed || failed) return;
        if (!valid(time, entities)) { status = "Too many selected trail entities or invalid time"; return; }
        if (unavailable != null) { status = unavailable; failed = true; return; }
        int duration = duration(durationMs);
        TrailDiskStore.Window prepared = window;
        if (prepared != null && prepared.covers(time, entities, duration) && time < prepared.end - 1000) return;
        Request value = new Request(time, entities, duration);
        if (pending == null || !pending.equals(value)) pending = value;
        if (worker == null) {
            status = "Indexing trail history...";
            worker = new Thread(this::run, "ZSG replay trails");
            worker.setDaemon(true);
            worker.start();
        }
        notifyAll();
    }

    /**
     * Null means not covered/unsupported: keep live fallback. Non-null paths are immutable and never contain
     * future points. An absent UUID is unknown in this epoch; a present empty list is known with no usable points.
     * Exact requests reuse Snapshot identity. revision stays stable across time within a prepared window.
     */
    Snapshot snapshot(int time, Set<UUID> entities, int durationMs) {
        if (closed || failed || !valid(time, entities)) return null;
        TrailDiskStore.Window prepared = window;
        int duration = duration(durationMs);
        if (prepared == null || !prepared.covers(time, entities, duration)) return null;
        Memo cached = memo;
        if (cached != null && cached.window == prepared && cached.time == time && cached.duration == duration
                && cached.entities.equals(entities)) return cached.snapshot;
        Snapshot result = new Snapshot(prepared.snapshot(time, entities, duration), preparedRevision(prepared));
        memo = new Memo(prepared, time, entities, duration, result);
        return closed || failed ? null : result;
    }

    long revision() { return revision; }

    private synchronized long preparedRevision(TrailDiskStore.Window prepared) {
        // Old windows remain immutable if a publication races with this snapshot; a negative value forces refresh.
        return window == prepared ? revision : -1;
    }

    private void run() {
        try (TrailDiskStore store = new TrailDiskStore(scratchDirectory)) {
            scan(store);
            while (!closed) {
                Request work;
                synchronized (this) {
                    while (!closed && pending == null) wait();
                    if (closed) return;
                    work = pending; pending = null;
                }
                TrailDiskStore.Window prepared = window;
                if (prepared != null && prepared.covers(work.time, work.entities, work.duration)
                        && work.time < prepared.end - 1000) continue;
                synchronized (this) {
                    if (closed) return;
                    status = "Preparing trail history...";
                }
                int start = Math.max(0, work.time - MAX_DURATION);
                int end = (int) Math.min(Integer.MAX_VALUE, (long) work.time + LOOKAHEAD);
                TrailDiskStore.Window result = store.window(start, end, work.entities, () -> closed);
                synchronized (this) {
                    if (closed) return;
                    window = result; revision++; memo = null;
                    status = "";
                }
            }
        } catch (Exception e) {
            synchronized (this) {
                window = null; memo = null;
                if (!closed) { failed = true; status = "Trail history unavailable: " + message(e); }
            }
        }
    }

    private void scan(TrailDiskStore store) throws IOException {
        SplitMilestoneReader.checkCancelled(() -> closed);
        Path path = source.toRealPath();
        BasicFileAttributes before = Files.readAttributes(path, BasicFileAttributes.class);
        require(before.isRegularFile() && before.size() <= RaceRecording.MAX_FILE, "Replay missing or too large");
        String signature;
        try (ZipFile zip = new ZipFile(path.toFile())) {
            require(zip.size() <= 4096, "Too many replay archive entries");
            Set<String> names = new HashSet<>();
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) require(names.add(entries.nextElement().getName()), "Duplicate replay archive entries");
            signature = identity(zip);
            require(expectedIdentity == null || expectedIdentity.equals(signature), "Replay changed before trail indexing");
            int duration = header(zip);
            ZipEntry entry = zip.getEntry("recording.tmcpr");
            require(entry.getSize() <= SplitMilestoneReader.MAX_STREAM_BYTES, "Replay packet stream too large");
            TrailPacketTracker tracker = new TrailPacketTracker(store, true);
            try (InputStream source = zip.getInputStream(entry);
                 SplitMilestoneReader.PacketFrames frames = new SplitMilestoneReader.PacketFrames(
                         source, entry.getSize(), entry.getCrc(), duration, () -> closed);
                 ReplayInputStream packets = new ReplayInputStream(
                         PacketTypeRegistry.get(ProtocolVersion.v1_16_1, State.LOGIN), frames, 14, 736)) {
                PacketData data;
                while (true) {
                    SplitMilestoneReader.checkCancelled(() -> closed);
                    data = packets.readPacket();
                    if (data == null) break;
                    try { tracker.packet(data); } finally { data.release(); }
                }
                frames.verifyComplete();
            }
        }
        SplitMilestoneReader.checkCancelled(() -> closed);
        BasicFileAttributes after = Files.readAttributes(path, BasicFileAttributes.class);
        require(before.size() == after.size() && before.lastModifiedTime().equals(after.lastModifiedTime())
                && Objects.equals(before.fileKey(), after.fileKey()), "Replay changed while indexing trails");
        try (ZipFile current = new ZipFile(path.toFile())) {
            require(signature.equals(identity(current)), "Replay changed while indexing trails");
        }
        store.finish();
    }

    private int header(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry("metaData.json");
        require(entry != null && entry.getSize() >= 0 && entry.getSize() <= MAX_METADATA, "Invalid replay metadata");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CRC32 crc = new CRC32();
        try (InputStream input = zip.getInputStream(entry)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = input.read(buffer)) >= 0) {
                SplitMilestoneReader.checkCancelled(() -> closed);
                require(bytes.size() + n <= MAX_METADATA, "Replay metadata too large");
                bytes.write(buffer, 0, n); crc.update(buffer, 0, n);
            }
        }
        require(bytes.size() == entry.getSize() && crc.getValue() == entry.getCrc(), "Corrupt replay metadata");
        int protocol = -1, version = -1, duration = -1;
        String format = null;
        try (JsonReader json = new JsonReader(new StringReader(new String(bytes.toByteArray(), StandardCharsets.UTF_8)))) {
            Set<String> fields = new HashSet<>();
            json.beginObject();
            while (json.hasNext()) {
                String key = json.nextName();
                require(fields.add(key), "Duplicate replay metadata field");
                switch (key) {
                    case "protocol": protocol = json.nextInt(); break;
                    case "fileFormatVersion": version = json.nextInt(); break;
                    case "duration": duration = json.nextInt(); break;
                    case "fileFormat": format = json.nextString(); break;
                    default: json.skipValue();
                }
            }
            json.endObject();
            require(json.peek() == JsonToken.END_DOCUMENT, "Trailing replay metadata");
        }
        require(protocol == 736, "Requires a Minecraft 1.16.1 recording");
        require(version == 14 && "MCPR".equals(format) && duration >= 0, "Unsupported replay format");
        return duration;
    }

    private static String identity(ZipFile zip) throws IOException {
        return CompletedIndexCache.Key.entry(zip, "recording.tmcpr") + ":" + CompletedIndexCache.Key.entry(zip, "metaData.json");
    }

    private static boolean valid(int time, Set<UUID> entities) {
        if (time < 0 || entities == null || entities.size() > MAX_SELECTED) return false;
        for (UUID entity : entities) if (entity == null) return false;
        return true;
    }

    private static int duration(int duration) { return Math.max(0, Math.min(MAX_DURATION, duration)); }
    private static String message(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
    private static void require(boolean value, String message) throws IOException { if (!value) throw new IOException(message); }

    @Override public synchronized void close() {
        closed = true; pending = null; window = null; memo = null; status = "Trail history closed";
        if (worker != null) worker.interrupt();
        notifyAll();
    }

    static final class Snapshot {
        final Map<UUID, List<TrailPoint>> paths;
        final long revision;
        Snapshot(Map<UUID, List<TrailPoint>> paths, long revision) { this.paths = paths; this.revision = revision; }
    }

    private static final class Request {
        final int time, duration;
        final Set<UUID> entities;
        Request(int time, Set<UUID> entities, int duration) {
            this.time = time; this.duration = duration;
            this.entities = Collections.unmodifiableSet(new HashSet<>(entities));
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Request)) return false;
            Request value = (Request) other;
            return time == value.time && duration == value.duration && entities.equals(value.entities);
        }
        @Override public int hashCode() { return Objects.hash(time, duration, entities); }
    }

    private static final class Memo {
        final TrailDiskStore.Window window;
        final int time, duration;
        final Set<UUID> entities;
        final Snapshot snapshot;
        Memo(TrailDiskStore.Window window, int time, Set<UUID> entities, int duration, Snapshot snapshot) {
            this.window = window; this.time = time; this.entities = new HashSet<>(entities);
            this.duration = duration; this.snapshot = snapshot;
        }
    }
}
