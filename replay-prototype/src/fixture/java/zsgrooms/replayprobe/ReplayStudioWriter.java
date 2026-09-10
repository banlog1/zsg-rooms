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

    public void finish(int selfId, boolean complete) throws IOException {
        if (closed) throw new IllegalStateException("Recording already closed");
        output.close();
        metadata.setSelfId(selfId);
        metadata.setDuration((int) lastTimestamp);
        file.writeMetaData(login, metadata);
        file.save();
        file.close();
        closed = true;
        if (!complete) {
            String name = path.getFileName().toString();
            Files.move(path, path.resolveSibling(name.substring(0, name.length() - 5) + ".incomplete.mcpr"));
        }
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
}
