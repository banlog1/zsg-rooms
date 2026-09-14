package zsgrooms.modid.replay;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;

/** Client-thread-only cache for synthetic full player metadata, never incoming packets. */
final class ReplayTrackedState {
    private byte[] previous;
    private EntityTrackerUpdateS2CPacket lastPacket;

    void reset() {
        previous = null;
        lastPacket = null;
    }

    boolean unchanged(PacketByteBuf snapshot) {
        int length = snapshot.readableBytes();
        if (previous == null || previous.length != length) return false;
        int start = snapshot.readerIndex();
        for (int i = 0; i < length; i++) {
            if (snapshot.getByte(start + i) != previous[i]) return false;
        }
        return true;
    }

    void remember(PacketByteBuf snapshot, EntityTrackerUpdateS2CPacket packet) {
        previous = new byte[snapshot.readableBytes()];
        snapshot.getBytes(snapshot.readerIndex(), previous);
        lastPacket = packet;
    }

    void observe(EntityTrackerUpdateS2CPacket packet) {
        // An incoming partial update may overwrite our last full snapshot in playback.
        if (lastPacket != null && packet != lastPacket && packet.id() == lastPacket.id()) reset();
    }
}
