// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.replaymod.replaystudio.PacketData;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.packet.State;
import com.replaymod.replaystudio.lib.viaversion.api.protocol.version.ProtocolVersion;
import com.replaymod.replaystudio.protocol.PacketType;
import com.replaymod.replaystudio.protocol.PacketTypeRegistry;
import net.minecraft.network.PacketByteBuf;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Decodes only position-bearing 1.16.1 packets; packet IDs come from ReplayStudio's actual protocol registry. */
final class TrailPacketTracker {
    static final int MAX_ACTIVE = 16_384;
    private final TrailDiskStore store;
    private final boolean replayStudioCameraId;
    private final Map<Integer, Entity> active = new HashMap<>();
    private final Map<UUID, Integer> ids = new HashMap<>();
    private int playerId = Integer.MIN_VALUE;
    private boolean localBreak;

    TrailPacketTracker(TrailDiskStore store) { this(store, false); }

    TrailPacketTracker(TrailDiskStore store, boolean replayStudioCameraId) {
        this.store = store;
        this.replayStudioCameraId = replayStudioCameraId;
    }

    void packet(PacketData data) throws IOException {
        if (data.getPacket().getRegistry().getState() != State.PLAY) return;
        PacketType type = data.getPacket().getType();
        if (type != PacketType.JoinGame && type != PacketType.Respawn && type != PacketType.SpawnPlayer
                && type != PacketType.SpawnMob && type != PacketType.SpawnObject && type != PacketType.EntityPosition
                && type != PacketType.EntityPositionRotation && type != PacketType.EntityTeleport
                && type != PacketType.DestroyEntities && type != PacketType.PlayerPositionRotation) return;
        long timestamp = data.getTime();
        if (timestamp < 0 || timestamp > Integer.MAX_VALUE) throw new IOException("Invalid trail timestamp");
        int time = (int) timestamp;
        // Borrow a duplicate, never consume or retain the caller's packet.
        PacketByteBuf bytes = new PacketByteBuf(data.getPacket().getBuf().duplicate());
        try {
            if (type == PacketType.JoinGame || type == PacketType.Respawn) {
                if (type == PacketType.JoinGame) {
                    playerId = bytes.readInt();
                    // ReplayInputStream.fillBuffer rewrites JoinGame.entityId ^= -1 for its spectator camera.
                    if (replayStudioCameraId) playerId = ~playerId;
                }
                store.boundary(time);
                active.clear(); ids.clear(); localBreak = true;
            } else if (type == PacketType.SpawnPlayer || type == PacketType.SpawnMob) {
                int id = bytes.readVarInt();
                UUID uuid = bytes.readUuid();
                if (type == PacketType.SpawnMob && bytes.readVarInt() < 0) throw new IOException("Invalid mob type");
                double x = bytes.readDouble(), y = bytes.readDouble(), z = bytes.readDouble();
                bytes.skipBytes(type == PacketType.SpawnMob ? 9 : 2);
                position(x, y, z);
                remove(id, time);
                Integer previous = ids.get(uuid);
                if (previous != null) remove(previous, time);
                if (active.size() >= MAX_ACTIVE) throw new IOException("Too many active trail entities");
                Entity entity = new Entity(uuid, x, y, z);
                active.put(id, entity); ids.put(uuid, id);
                store.append(uuid, time, x, y, z, TrailDiskStore.BREAK);
                if (id == playerId) localBreak = false;
            } else if (type == PacketType.SpawnObject) {
                // An untracked object may reuse an ID previously occupied by a living entity.
                remove(bytes.readVarInt(), time);
            } else if (type == PacketType.DestroyEntities) {
                int count = bytes.readVarInt();
                if (count < 0 || count > MAX_ACTIVE || count > bytes.readableBytes())
                    throw new IOException("Invalid trail destroy count");
                for (int i = 0; i < count; i++) remove(bytes.readVarInt(), time);
            } else if (type == PacketType.PlayerPositionRotation) {
                double x = bytes.readDouble(), y = bytes.readDouble(), z = bytes.readDouble();
                bytes.skipBytes(8);
                int flags = bytes.readUnsignedByte();
                bytes.readVarInt();
                Entity entity = active.get(playerId);
                if (entity != null) {
                    if ((flags & 1) != 0) x += entity.x;
                    if ((flags & 2) != 0) y += entity.y;
                    if ((flags & 4) != 0) z += entity.z;
                    absolute(entity, time, x, y, z, true);
                } else position(x, y, z);
                localBreak = true;
            } else {
                int id = bytes.readVarInt();
                Entity entity = active.get(id);
                if (type == PacketType.EntityTeleport) {
                    double x = bytes.readDouble(), y = bytes.readDouble(), z = bytes.readDouble();
                    bytes.skipBytes(3);
                    position(x, y, z);
                    if (entity != null) {
                        double dx = entity.x - x, dy = entity.y - y, dz = entity.z - z;
                        // Rooms records local movement with this packet every tick. Other entities use it for sync/teleport.
                        boolean discontinuity = id != playerId || localBreak || dx * dx + dy * dy + dz * dz > 64;
                        absolute(entity, time, x, y, z, discontinuity);
                        if (id == playerId) localBreak = false;
                    }
                } else {
                    short dx = bytes.readShort(), dy = bytes.readShort(), dz = bytes.readShort();
                    bytes.skipBytes(type == PacketType.EntityPositionRotation ? 3 : 1);
                    if (entity != null) {
                        entity.tx += dx; entity.ty += dy; entity.tz += dz;
                        move(entity, time, entity.tx / 4096.0, entity.ty / 4096.0, entity.tz / 4096.0, false);
                    }
                }
            }
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            throw new IOException("Malformed trail packet " + type, e);
        }
    }

    static int packetId(PacketType type) {
        Integer id = PacketTypeRegistry.get(ProtocolVersion.v1_16_1, State.PLAY).getId(type);
        if (id == null) throw new IllegalArgumentException("No 1.16.1 packet " + type);
        return id;
    }

    private void absolute(Entity entity, int time, double x, double y, double z, boolean discontinuity) throws IOException {
        position(x, y, z);
        entity.tx = encode(x); entity.ty = encode(y); entity.tz = encode(z);
        move(entity, time, x, y, z, discontinuity);
    }

    private void move(Entity entity, int time, double x, double y, double z, boolean discontinuity) throws IOException {
        position(x, y, z);
        if (discontinuity || entity.x != x || entity.y != y || entity.z != z)
            store.append(entity.uuid, time, x, y, z, discontinuity ? TrailDiskStore.BREAK : 0);
        entity.x = x; entity.y = y; entity.z = z;
    }

    private void remove(int id, int time) throws IOException {
        Entity entity = active.remove(id);
        if (entity != null) {
            ids.remove(entity.uuid);
            store.append(entity.uuid, time, entity.x, entity.y, entity.z, TrailDiskStore.END);
        }
    }

    private static void position(double x, double y, double z) throws IOException {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > 3.2E7 || Math.abs(y) > 3.2E7 || Math.abs(z) > 3.2E7)
            throw new IOException("Invalid trail coordinates");
    }

    private static long encode(double coordinate) { return (long) Math.floor(coordinate * 4096.0); }

    private static final class Entity {
        final UUID uuid;
        double x, y, z;
        long tx, ty, tz;
        Entity(UUID uuid, double x, double y, double z) {
            this.uuid = uuid; this.x = x; this.y = y; this.z = z;
            tx = encode(x); ty = encode(y); tz = encode(z);
        }
    }
}
