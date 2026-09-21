package zsgrooms.modid.replay;

import io.netty.buffer.Unpooled;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.registry.Registry;

final class ReplayHudCapture {
    private static final byte[] UNAVAILABLE = new byte[0];
    private final boolean optimized;
    private ClientPlayerEntity previousPlayer;
    private ReplayItemSnapshot items;

    ReplayHudCapture(boolean optimized) { this.optimized = optimized; }

    void capture(ReplayHudTrack track, long time, int world, ClientPlayerEntity player) {
        int entity = player == null ? -1 : player.getEntityId();
        if (!track.due(time, world, entity)) return;
        if (player != previousPlayer) {
            previousPlayer = player;
            items = optimized && player != null ? new ReplayItemSnapshot() : null;
        }
        if (player == null) { track.add(time, world, entity, UNAVAILABLE); return; }
        PacketByteBuf data = optimized ? ReplayEncodingBuffers.acquireHud()
                : new PacketByteBuf(Unpooled.buffer(512, ReplayHudTrack.MAX_FRAME));
        try {
            data.writeFloat(player.getHealth());
            data.writeFloat(player.getMaxHealth());
            data.writeFloat(player.getAbsorptionAmount());
            data.writeInt(player.getHungerManager().getFoodLevel());
            data.writeInt(player.getArmor());
            data.writeInt(player.getAir());
            data.writeInt(player.experienceLevel);
            data.writeFloat(player.experienceProgress);
            data.writeByte(player.inventory.selectedSlot);
            data.writeBoolean(player.isOnFire());
            for (int i = 0; i < 42; i++) {
                net.minecraft.item.ItemStack stack = i == 41 ? player.inventory.getCursorStack() : player.inventory.getStack(i);
                if (items == null) data.writeItemStack(stack); else items.write(data, i, stack);
            }
            int count = Math.min(16, player.getStatusEffects().size());
            data.writeByte(count);
            for (StatusEffectInstance effect : player.getStatusEffects()) {
                if (count-- <= 0) break;
                data.writeString(Registry.STATUS_EFFECT.getId(effect.getEffectType()).toString());
                data.writeInt(effect.getDuration());
                data.writeInt(effect.getAmplifier());
            }
            byte[] bytes = new byte[data.readableBytes()];
            data.getBytes(0, bytes);
            track.add(time, world, entity, bytes);
        } catch (RuntimeException ignored) {
            // Oversized/modded item state must not abort an otherwise playable replay.
            track.add(time, world, entity, UNAVAILABLE);
        } finally {
            if (optimized) ReplayEncodingBuffers.recycleHud(data); else data.release();
        }
    }
}
