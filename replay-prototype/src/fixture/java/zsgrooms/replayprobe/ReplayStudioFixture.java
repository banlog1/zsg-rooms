package zsgrooms.replayprobe;

import com.github.steveice10.netty.buffer.ByteBuf;
import com.github.steveice10.netty.buffer.Unpooled;
import com.replaymod.replaystudio.PacketData;
import com.replaymod.replaystudio.io.ReplayInputStream;
import com.replaymod.replaystudio.io.ReplayOutputStream;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion;
import com.replaymod.replaystudio.protocol.Packet;
import com.replaymod.replaystudio.protocol.PacketType;
import com.replaymod.replaystudio.protocol.PacketTypeRegistry;
import com.replaymod.replaystudio.replay.ReplayMetaData;
import com.replaymod.replaystudio.replay.ZipReplayFile;
import com.replaymod.replaystudio.studio.ReplayStudio;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Library-only fixture, loaded outside the application class loader. Not a race recorder. */
public final class ReplayStudioFixture {
    private ReplayStudioFixture() {
    }

    public static boolean write(Path path) throws Exception {
        PacketTypeRegistry play = PacketTypeRegistry.get(ProtocolVersion.v1_16_1, State.PLAY);
        PacketTypeRegistry login = PacketTypeRegistry.get(ProtocolVersion.v1_16_1, State.LOGIN);
        ReplayMetaData metadata = new ReplayMetaData();
        metadata.setSingleplayer(true);
        metadata.setServerName("ZSG library probe (not a playable world)");
        metadata.setMcVersion("1.16.1");
        metadata.setGenerator("ZSG library probe");
        metadata.setDate(0L);
        metadata.setDuration(65000);

        ByteBuf first = Unpooled.buffer(16).writeLong(100L).writeLong(5000L);
        ByteBuf second = Unpooled.buffer(16).writeLong(200L).writeLong(6000L);
        ByteBuf third = Unpooled.buffer(16).writeLong(300L).writeLong(7000L);
        ByteBuf keepAlive = Unpooled.buffer(8).writeLong(42L);
        try (ZipReplayFile file = new ZipReplayFile(new ReplayStudio(), null, path.toFile())) {
            file.writeMetaData(login, metadata);
            try (ReplayOutputStream output = file.writePacketData()) {
                // ReplayStudio adds the login transition before the first PLAY packet.
                output.write(0L, new Packet(play, PacketType.UpdateTime, first));
                output.write(0L, new Packet(play, PacketType.KeepAlive, keepAlive));
                output.write(0L, new Packet(play, PacketType.UpdateTime, second));
                output.write(65000L, new Packet(play, PacketType.UpdateTime, third));
            }
            file.save();
            return first.refCnt() == 0 && second.refCnt() == 0 && third.refCnt() == 0 && keepAlive.refCnt() == 0;
        } finally {
            // Writes consume buffers; failures before a write still need cleanup.
            if (first.refCnt() > 0) first.release();
            if (second.refCnt() > 0) second.release();
            if (third.refCnt() > 0) third.release();
            if (keepAlive.refCnt() > 0) keepAlive.release();
        }
    }

    public static Map<String, Object> read(Path path) throws Exception {
        PacketTypeRegistry registry = PacketTypeRegistry.get(ProtocolVersion.v1_16_1, State.LOGIN);
        Map<String, Object> result = new LinkedHashMap<>();
        List<Long> timestamps = new ArrayList<>();
        List<String> types = new ArrayList<>();
        List<String> payloads = new ArrayList<>();
        try (ZipReplayFile file = new ZipReplayFile(new ReplayStudio(), path.toFile())) {
            ReplayMetaData metadata = file.getMetaData();
            result.put("protocol", metadata.getRawProtocolVersion());
            result.put("format", metadata.getFileFormatVersion());
            result.put("minecraft", metadata.getMcVersion());
            result.put("duration", metadata.getDuration());
            result.put("selfId", metadata.getSelfId());
            try (ReplayInputStream input = file.getPacketData(registry)) {
                PacketData data;
                while ((data = input.readPacket()) != null) {
                    try {
                        ByteBuf buffer = data.getPacket().getBuf();
                        byte[] bytes = new byte[buffer.readableBytes()];
                        buffer.getBytes(buffer.readerIndex(), bytes);
                        timestamps.add(data.getTime());
                        types.add(data.getPacket().getType().name());
                        payloads.add(Base64.getEncoder().encodeToString(bytes));
                    } finally {
                        data.release();
                    }
                }
            }
        }
        result.put("timestamps", timestamps);
        result.put("types", types);
        result.put("payloads", payloads);
        return result;
    }

    public static int timePacketId() {
        Packet packet = new Packet(PacketTypeRegistry.get(ProtocolVersion.v1_16_1, State.PLAY),
                PacketType.UpdateTime, Unpooled.buffer(0));
        try {
            return packet.getId();
        } finally {
            packet.release();
        }
    }

    public static boolean nbtRoundTrip() throws Exception {
        com.github.steveice10.opennbt.tag.builtin.CompoundTag tag =
                new com.github.steveice10.opennbt.tag.builtin.CompoundTag();
        tag.put("nested", new com.github.steveice10.opennbt.tag.builtin.CompoundTag());
        Packet packet = new Packet(PacketTypeRegistry.get(ProtocolVersion.v1_16_1, State.PLAY),
                PacketType.UpdateTileEntity, Unpooled.buffer());
        try {
            try (Packet.Writer writer = packet.overwrite()) {
                writer.writeNBT(tag);
            }
            try (Packet.Reader reader = packet.reader()) {
                return reader.readNBT().get("nested") instanceof com.github.steveice10.opennbt.tag.builtin.CompoundTag;
            }
        } finally {
            packet.release();
        }
    }
}
