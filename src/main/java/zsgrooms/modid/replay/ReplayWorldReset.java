package zsgrooms.modid.replay;

import io.netty.buffer.Unpooled;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Replay-only cleanup for state held by the playback connection rather than its ClientWorld. */
final class ReplayWorldReset {
    private final Set<UUID> bossBars = new HashSet<>();
    private final Set<UUID> players = new HashSet<>();

    void observe(Packet<?> packet) {
        if (packet instanceof BossBarS2CPacket) {
            BossBarS2CPacket bar = (BossBarS2CPacket) packet;
            if (bar.getType() == BossBarS2CPacket.Type.ADD) bossBars.add(bar.getUuid());
            else if (bar.getType() == BossBarS2CPacket.Type.REMOVE) bossBars.remove(bar.getUuid());
        } else if (packet instanceof PlayerListS2CPacket) {
            PlayerListS2CPacket list = (PlayerListS2CPacket) packet;
            for (PlayerListS2CPacket.Entry entry : list.getEntries()) {
                if (list.getAction() == PlayerListS2CPacket.Action.ADD_PLAYER) players.add(entry.getProfile().getId());
                else if (list.getAction() == PlayerListS2CPacket.Action.REMOVE_PLAYER) players.remove(entry.getProfile().getId());
            }
        }
    }

    List<Packet<?>> cleanup() throws IOException {
        List<Packet<?>> packets = new ArrayList<>();
        // These packet types have no UUID-only constructors in 1.16.1.
        for (UUID id : bossBars) {
            PacketByteBuf data = new PacketByteBuf(Unpooled.buffer());
            try {
                data.writeUuid(id);
                data.writeEnumConstant(BossBarS2CPacket.Type.REMOVE);
                BossBarS2CPacket packet = new BossBarS2CPacket();
                packet.read(data);
                packets.add(packet);
            } finally {
                data.release();
            }
        }
        if (!players.isEmpty()) {
            PacketByteBuf data = new PacketByteBuf(Unpooled.buffer());
            try {
                data.writeEnumConstant(PlayerListS2CPacket.Action.REMOVE_PLAYER);
                data.writeVarInt(players.size());
                for (UUID id : players) data.writeUuid(id);
                PlayerListS2CPacket packet = new PlayerListS2CPacket();
                packet.read(data);
                packets.add(packet);
            } finally {
                data.release();
            }
        }
        packets.add(new StopSoundS2CPacket(null, null));
        return packets;
    }

    static PlayerRespawnS2CPacket refreshPlayer(GameJoinS2CPacket join) {
        // A second JoinGame replaces ClientWorld but reuses the old camera/player. Respawn
        // recreates it in the new world, even when both worlds use the same dimension key.
        return new PlayerRespawnS2CPacket(join.method_29444(), join.getDimensionId(), join.getSha256Seed(),
                join.getGameMode(), join.method_30116(), join.isDebugWorld(), join.isFlatWorld(), false);
    }
}
