package zsgrooms.replayprobe;

import com.github.steveice10.netty.buffer.ByteBuf;
import com.github.steveice10.netty.buffer.Unpooled;
import com.replaymod.replaystudio.io.ReplayOutputStream;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion;
import com.replaymod.replaystudio.protocol.Packet;
import com.replaymod.replaystudio.protocol.PacketTypeRegistry;
import com.replaymod.replaystudio.replay.ReplayMetaData;
import com.replaymod.replaystudio.replay.ZipReplayFile;
import com.replaymod.replaystudio.studio.ReplayStudio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** All methods run on the recorder's writer thread; only JDK types cross the loader boundary. */
public final class ReplayStudioWriter implements AutoCloseable {
    public static void verifyDependencies() {
        PacketTypeRegistry.get(ProtocolVersion.v1_16_1, State.LOGIN);
        PacketTypeRegistry.get(ProtocolVersion.v1_16_1, State.PLAY);
        new com.github.steveice10.opennbt.tag.builtin.CompoundTag();
    }

    private final Path path;
    private final ReplayMetaData metadata = new ReplayMetaData();
    private final PacketTypeRegistry login = PacketTypeRegistry.get(ProtocolVersion.v1_16_1, State.LOGIN);
    private final PacketTypeRegistry play = PacketTypeRegistry.get(ProtocolVersion.v1_16_1, State.PLAY);
    private final ZipReplayFile file;
    private final ReplayOutputStream output;
    private long lastTimestamp;
    private long duration;
    private boolean closed;

    public ReplayStudioWriter(Path path, long epochMillis) throws IOException {
        this.path = path;
        if (Files.exists(path)) throw new IOException("Replay already exists");
        metadata.setSingleplayer(true);
        metadata.setMcVersion("1.16.1");
        metadata.setGenerator("ZSG Rooms experimental recorder");
        metadata.setServerName("ZSG replay prototype");
        metadata.setDate(epochMillis);
        file = new ZipReplayFile(new ReplayStudio(), null, path.toFile());
        ReplayOutputStream stream;
        try {
            file.writeMetaData(login, metadata);
            stream = file.writePacketData();
        } catch (IOException | RuntimeException e) {
            file.close();
            throw e;
        }
        output = stream;
    }

    public void write(int phase, int packetId, long timestamp, byte[] payload) throws IOException {
        if (closed || timestamp < lastTimestamp || timestamp > Integer.MAX_VALUE || packetId < 0
                || phase != 0 && phase != 2) throw new IllegalArgumentException("Invalid replay event");
        ByteBuf bytes = Unpooled.wrappedBuffer(payload);
        try {
            output.write(timestamp, new Packet(phase == 2 ? login : play, packetId, bytes));
            lastTimestamp = timestamp;
        } finally {
            if (bytes.refCnt() > 0) bytes.release();
        }
    }

    public void writeRaceManifest(String json) throws IOException {
        if (closed) throw new IllegalStateException("Writer closed");
        byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > 2 * 1024 * 1024) throw new IllegalArgumentException("Race manifest too large");
        try (java.io.OutputStream entry = file.write("zsg-rooms/races.json")) {
            entry.write(bytes);
        }
    }

    /** Optional, independent track; absent in older writer libraries. */
    public void writeBarters(String json) throws IOException {
        if (closed) throw new IllegalStateException("Writer closed");
        if (json.length() > 4 * 1024 * 1024) throw new IllegalArgumentException("Barter history too large");
        byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > 4 * 1024 * 1024) throw new IllegalArgumentException("Barter history too large");
        try (java.io.OutputStream entry = file.write("zsg-rooms/barters.json")) {
            entry.write(bytes);
        } catch (IOException | RuntimeException error) {
            try { file.remove("zsg-rooms/barters.json"); }
            catch (IOException | RuntimeException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }

    /** Optional API: extends metadata coverage without inventing a network packet. */
    public void setDuration(long millis) {
        if (closed) throw new IllegalStateException("Writer closed");
        if (millis < lastTimestamp || millis < 0 || millis > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid recording duration");
        duration = millis;
    }

    /** Copies the optional disk spool using fixed memory; copy failure only disables this track. */
    public void writeDetailedCapture(Path spool, String coverageJson) throws IOException {
        if (closed) throw new IllegalStateException("Writer closed");
        if (coverageJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 16384)
            throw new IllegalArgumentException("Detail coverage too large");
        com.google.gson.JsonObject coverage = new com.google.gson.JsonParser().parse(coverageJson).getAsJsonObject();
        if (spool != null) {
            try {
                long size = Files.size(spool);
                if (size < 8 || size > Integer.MAX_VALUE || !Files.isRegularFile(spool))
                    throw new IllegalArgumentException("Invalid detail spool");
                try (java.io.InputStream input = Files.newInputStream(spool);
                     java.io.OutputStream entry = file.write("zsg-rooms/detail.bin")) {
                    byte[] buffer = new byte[65536];
                    long copied = 0;
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (copied + count > size) throw new IOException("Detail spool changed while copying");
                        entry.write(buffer, 0, count);
                        copied += count;
                    }
                    if (copied != size) throw new IOException("Detail spool truncated while copying");
                }
            } catch (IOException | RuntimeException error) {
                file.remove("zsg-rooms/detail.bin");
                coverage.addProperty("status", "failed");
                coverage.addProperty("failure", error.getClass().getSimpleName());
            }
        }
        try (java.io.OutputStream entry = file.write("zsg-rooms/detail.json")) {
            entry.write(coverage.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    public void finish(int selfId, boolean complete) throws IOException {
        if (closed) throw new IllegalStateException("Recording already closed");
        output.close();
        metadata.setSelfId(selfId);
        metadata.setDuration((int) Math.max(lastTimestamp, duration));
        file.writeMetaData(login, metadata);
        file.save();
        file.close();
        closed = true;
        if (!complete) {
            String name = path.getFileName().toString();
            Files.move(path, path.resolveSibling(name.substring(0, name.length() - 5) + ".incomplete.mcpr"));
        }
    }

    public void writePlayerHud(byte[] data) throws IOException {
        if (closed) throw new IllegalStateException("Writer closed");
        if (data.length > 16 * 1024 * 1024) throw new IllegalArgumentException("Player HUD too large");
        try (java.io.OutputStream entry = file.write("zsg-rooms/player-hud.bin")) { entry.write(data); }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            try {
                output.close();
            } finally {
                file.close();
            }
        }
    }

    public void discard() throws IOException {
        if (closed) throw new IllegalStateException("Recording already closed");
        close();
        // ReplayStudio retains the recording hash cache even without save().
        Path cache = path.resolveSibling(path.getFileName() + ".cache");
        Files.deleteIfExists(cache.resolve("recording.tmcpr.crc32"));
        Files.deleteIfExists(cache);
    }
}
