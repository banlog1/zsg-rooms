package zsgrooms.modid.replay;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;

/** Bounded heap scratch buffers per capture thread; queued payloads always own their bytes. */
final class ReplayEncodingBuffers {
    static final int MAX_PACKET_BYTES = 4 * 1024 * 1024;
    static final int RETAIN_LIMIT = 256 * 1024;
    private static final ThreadLocal<PacketByteBuf> IDLE = new ThreadLocal<>();
    private static final ThreadLocal<PacketByteBuf> HUD = new ThreadLocal<>();
    private static final ThreadLocal<PacketByteBuf> METADATA = new ThreadLocal<>();

    static PacketByteBuf acquire() {
        return acquire(IDLE, 256, MAX_PACKET_BYTES);
    }

    static PacketByteBuf acquireHud() { return acquire(HUD, 512, ReplayHudTrack.MAX_FRAME); }
    static void recycleHud(PacketByteBuf buffer) { recycle(HUD, buffer, 8192); }
    static PacketByteBuf acquireMetadata() { return acquire(METADATA, 256, Integer.MAX_VALUE); }
    static void recycleMetadata(PacketByteBuf buffer) { recycle(METADATA, buffer, RETAIN_LIMIT); }

    private static PacketByteBuf acquire(ThreadLocal<PacketByteBuf> idle, int initial, int maximum) {
        PacketByteBuf buffer = idle.get();
        idle.set(null); // Nested reset-cleanup capture must not overwrite its caller's bytes.
        if (buffer == null) return new PacketByteBuf(Unpooled.buffer(initial, maximum));
        buffer.clear();
        return buffer;
    }

    static void recycle(PacketByteBuf buffer) {
        recycle(IDLE, buffer, RETAIN_LIMIT);
    }

    private static void recycle(ThreadLocal<PacketByteBuf> idle, PacketByteBuf buffer, int limit) {
        if (buffer.capacity() <= limit && idle.get() == null) idle.set(buffer);
        else buffer.release();
    }
}
