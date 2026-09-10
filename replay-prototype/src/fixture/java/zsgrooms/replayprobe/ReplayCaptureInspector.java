package zsgrooms.replayprobe;

import com.google.gson.GsonBuilder;
import com.replaymod.replaystudio.PacketData;
import com.replaymod.replaystudio.io.ReplayInputStream;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion;
import com.replaymod.replaystudio.protocol.Packet;
import com.replaymod.replaystudio.protocol.PacketType;
import com.replaymod.replaystudio.protocol.PacketTypeRegistry;
import com.replaymod.replaystudio.protocol.packets.PacketJoinGame;
import com.replaymod.replaystudio.protocol.packets.PacketRespawn;
import com.replaymod.replaystudio.protocol.registry.Registries;
import com.replaymod.replaystudio.replay.ReplayMetaData;
import com.replaymod.replaystudio.replay.ZipReplayFile;
import com.replaymod.replaystudio.studio.ReplayStudio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Streaming inspection of the specific live smoke scenario, not a visual playback test. */
public final class ReplayCaptureInspector {
    private ReplayCaptureInspector() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) throw new IllegalArgumentException("Expected smoke recording path and optional reset count");
        if (args.length == 2 && "diagnose".equals(args[1])) {
            System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(diagnose(Paths.get(args[0]))));
            return;
        }
        System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(
                inspect(Paths.get(args[0]), args.length == 2 ? Integer.parseInt(args[1]) : 0)));
    }

    public static Map<String, Object> inspect(Path path) throws Exception {
        return inspect(path, 0);
    }

    /** Packet names/times only: no chat, player names, coordinates, or seeds. */
    private static Map<String, Object> diagnose(Path path) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Long> firstTimes = new LinkedHashMap<>();
        List<String> transitions = new ArrayList<>();
        long lastTime = -1L;
        int index = 0;
        int ownId = -1;
        boolean firstMove = true;
        Registries registries = new Registries();
        try (ZipReplayFile file = new ZipReplayFile(new ReplayStudio(), path.toFile())) {
            ReplayMetaData meta = file.getMetaData();
            result.put("durationMs", meta.getDuration());
            result.put("protocol", meta.getRawProtocolVersion());
            try (ReplayInputStream input = file.getPacketData(PacketTypeRegistry.get(ProtocolVersion.v1_16_1, State.LOGIN))) {
                PacketData data;
                while ((data = input.readPacket()) != null) {
                    try {
                        require(data.getTime() >= lastTime, "Timestamp moved backwards");
                        lastTime = data.getTime();
                        Packet packet = data.getPacket();
                        PacketType type = packet.getType();
                        counts.merge(type.name(), 1, Integer::sum);
                        firstTimes.putIfAbsent(type.name(), data.getTime());
                        boolean report = index < 25 || type == PacketType.PlayerPositionRotation
                                || type == PacketType.JoinGame || type == PacketType.Respawn || type == PacketType.SpawnPlayer;
                        if (type == PacketType.JoinGame) {
                            PacketJoinGame join = PacketJoinGame.read(packet, registries);
                            ownId = ~join.entityId;
                            registries = join.registries;
                            firstMove = true;
                        } else if (type == PacketType.Respawn) {
                            firstMove = true;
                        } else if (type == PacketType.EntityTeleport && firstMove) {
                            try (Packet.Reader reader = packet.reader()) {
                                if (reader.readVarInt() == ownId) {
                                    report = true;
                                    firstMove = false;
                                }
                            }
                        }
                        if (report && transitions.size() < 200) transitions.add(index + " @ " + data.getTime() + "ms: " + type.name());
                        index++;
                    } finally {
                        data.release();
                    }
                }
            }
        }
        result.put("initializationEvents", transitions);
        result.put("firstPacketTimeMs", firstTimes);
        result.put("packetCounts", counts);
        return result;
    }

    private static Map<String, Object> inspect(Path path, int resets) throws Exception {
        require(Files.isRegularFile(path), "Recording missing");
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<String> dimensions = new ArrayList<>();
        long lastTime = -1L;
        int ownSpawns = 0;
        int ownMoves = 0;
        boolean ownEntityReady = false;
        boolean joined = false;
        int ownId = -1;
        Registries registries = new Registries();
        try (ZipReplayFile file = new ZipReplayFile(new ReplayStudio(), path.toFile())) {
            ReplayMetaData meta = file.getMetaData();
            require(meta.getRawProtocolVersion() == 736, "Wrong protocol");
            require(meta.getSelfId() >= 0, "Missing local entity id");
            try (ReplayInputStream input = file.getPacketData(PacketTypeRegistry.get(ProtocolVersion.v1_16_1, State.LOGIN))) {
                PacketData data;
                while ((data = input.readPacket()) != null) {
                    try {
                        require(data.getTime() >= lastTime, "Timestamp moved backwards");
                        lastTime = data.getTime();
                        Packet packet = data.getPacket();
                        PacketType type = packet.getType();
                        counts.merge(type.name(), 1, Integer::sum);
                        if (type == PacketType.JoinGame) {
                            PacketJoinGame join = PacketJoinGame.read(packet, registries);
                            // ReplayInputStream complements the JoinGame id for the spectator camera.
                            if (!joined) require(join.entityId == ~meta.getSelfId(), "Wrong recorded player");
                            ownId = ~join.entityId;
                            registries = join.registries;
                            dimensions.add(join.dimension);
                            joined = true;
                            ownEntityReady = false;
                        } else if (type == PacketType.Respawn) {
                            require(joined, "Respawn before JoinGame");
                            dimensions.add(PacketRespawn.read(packet, registries).dimension);
                            ownEntityReady = false;
                        } else if (type == PacketType.SpawnPlayer || type == PacketType.EntityTeleport) {
                            try (Packet.Reader reader = packet.reader()) {
                                if (reader.readVarInt() == ownId) {
                                    require(joined, "Local entity before JoinGame");
                                    if (type == PacketType.SpawnPlayer) {
                                        ownSpawns++;
                                        ownEntityReady = true;
                                    } else {
                                        require(ownEntityReady, "Local movement before spawn after world change");
                                        ownMoves++;
                                    }
                                }
                            }
                        }
                    } finally {
                        data.release();
                    }
                }
            }
            require(meta.getDuration() == lastTime, "Duration does not match final event");
            result.put("durationMs", meta.getDuration());
        }
        require(counts.getOrDefault("LoginSuccess", 0) == 1, "Missing or duplicate login transition");
        require(counts.getOrDefault("JoinGame", 0) == 1 + resets, "Wrong number of world initializations");
        List<String> expectedDimensions = new ArrayList<>();
        for (int i = 0; i <= resets; i++) {
            expectedDimensions.add("minecraft:overworld");
            if (i > 0) expectedDimensions.add("minecraft:overworld"); // Playback player/camera refresh after JoinGame.
            expectedDimensions.add("minecraft:the_nether");
            expectedDimensions.add("minecraft:overworld");
        }
        require(dimensions.equals(expectedDimensions),
                "Unexpected dimension sequence");
        require(ownSpawns == 3 * (1 + resets) && ownMoves > 20, "Local player stream incomplete");
        require(counts.getOrDefault("Disconnect", 0) == 0, "Disconnect would end playback early");
        require(counts.getOrDefault("ChunkData", 0) > 0, "No chunks");
        require(counts.getOrDefault("SpawnMob", 0) > 0, "No entities");
        require(counts.getOrDefault("EntityEquipment", 0) > 0, "No equipment");
        require(counts.getOrDefault("EntityAnimation", 0) > 0, "No swing animation");
        require(counts.getOrDefault("BlockChange", 0) + counts.getOrDefault("MultiBlockChange", 0) > 0, "No block changes");
        require(counts.getOrDefault("PluginMessage", 0) == 0, "Custom payload leaked");
        result.put("dimensions", dimensions);
        result.put("localPlayerSpawns", ownSpawns);
        result.put("localPlayerMovementPackets", ownMoves);
        result.put("packetCounts", counts);
        result.put("captureCheck", "PASS (visual playback still unverified)");
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
