package zsgrooms.modid;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.network.ServerSidePacketRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class ZsgRoomNetworking {
    public static final Identifier ROOM_ACTION = ZsgRooms.id("room_action");

    private ZsgRoomNetworking() {
    }

    public static void registerServer() {
        ServerSidePacketRegistry.INSTANCE.register(ROOM_ACTION, (context, buffer) -> {
            String action = buffer.readString(64);
            String roomName = buffer.readString(64);
            buffer.readString(64);
            String value = buffer.readString(256);
            PlayerEntity player = context.getPlayer();
            String playerName = player == null ? "player" : player.getEntityName();

            context.getTaskQueue().execute(() -> {
                if (player instanceof ServerPlayerEntity) {
                    broadcast((ServerPlayerEntity) player, action, roomName, playerName, value);
                }
            });
        });
    }

    public static PacketByteBuf packet(String action, String roomName, String playerName, String value) {
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        buffer.writeString(safe(action), 64);
        buffer.writeString(safe(roomName), 64);
        buffer.writeString(safe(playerName), 64);
        buffer.writeString(safe(value), 256);
        return buffer;
    }

    private static void broadcast(ServerPlayerEntity sender, String action, String roomName, String playerName, String value) {
        for (ServerPlayerEntity player : sender.server.getPlayerManager().getPlayerList()) {
            if (ServerSidePacketRegistry.INSTANCE.canPlayerReceive(player, ROOM_ACTION)) {
                ServerSidePacketRegistry.INSTANCE.sendToPlayer(player, ROOM_ACTION, packet(action, roomName, playerName, value));
            }
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
