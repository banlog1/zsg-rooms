package zsgrooms.modid.replay;

import io.netty.buffer.Unpooled;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReplayTrackedStateTest {
    @Test void onlyIdenticalSnapshotsAreSuppressedAndChangesRetainTheFullPayload() throws Exception {
        ReplayTrackedState cache = new ReplayTrackedState();
        DataTracker initial = tracker((byte) 0);
        EntityTrackerUpdateS2CPacket first = ReplayPrototype.copyTrackedState(1, initial, cache);
        assertNotNull(first);
        cache.observe(first);
        for (int i = 0; i < 400; i++) assertNull(ReplayPrototype.copyTrackedState(1, initial, cache));
        DataTracker changed = tracker((byte) 2);
        EntityTrackerUpdateS2CPacket update = ReplayPrototype.copyTrackedState(1, changed, cache);
        assertArrayEquals(bytes(ReplayPrototype.copyTrackedState(1, changed)), bytes(update));
        assertNotNull(ReplayPrototype.copyTrackedState(1, initial, cache));
    }

    @Test void disabledPathStillEmitsEverySnapshot() throws Exception {
        DataTracker tracker = tracker((byte) 0);
        for (int i = 0; i < 20; i++) {
            assertNotNull(ReplayPrototype.copyTrackedState(1, tracker, null));
        }
        assertFalse(tracker.isDirty());
    }

    @Test void worldChangesAndNewSessionsRequireAFullSnapshot() throws Exception {
        ReplayTrackedState cache = new ReplayTrackedState();
        DataTracker tracker = tracker((byte) 0);
        assertNotNull(ReplayPrototype.copyTrackedState(1, tracker, cache));
        assertNotNull(ReplayPrototype.copyTrackedState(2, tracker, cache));
        assertNull(ReplayPrototype.copyTrackedState(2, tracker, cache));
        cache.reset();
        assertNotNull(ReplayPrototype.copyTrackedState(2, tracker, cache));
        assertNotNull(ReplayPrototype.copyTrackedState(2, tracker, new ReplayTrackedState()));
    }

    @Test void incomingPlayerUpdatesInvalidateTheCacheButOtherEntitiesDoNot() throws Exception {
        ReplayTrackedState cache = new ReplayTrackedState();
        DataTracker tracker = tracker((byte) 0);
        ReplayPrototype.copyTrackedState(1, tracker, cache);
        cache.observe(ReplayPrototype.copyTrackedState(2, tracker((byte) 2)));
        assertNull(ReplayPrototype.copyTrackedState(1, tracker, cache));
        cache.observe(ReplayPrototype.copyTrackedState(1, tracker((byte) 2)));
        assertNotNull(ReplayPrototype.copyTrackedState(1, tracker, cache));
    }

    @Test void addedFieldsAreNotLost() throws Exception {
        ReplayTrackedState cache = new ReplayTrackedState();
        DataTracker tracker = tracker((byte) 0);
        ReplayPrototype.copyTrackedState(1, tracker, cache);
        tracker.startTracking(TrackedDataHandlerRegistry.STRING.create(1), "extra state");
        assertArrayEquals(bytes(ReplayPrototype.copyTrackedState(1, tracker)),
                bytes(ReplayPrototype.copyTrackedState(1, tracker, cache)));
    }

    private DataTracker tracker(byte flags) {
        DataTracker tracker = new DataTracker(null);
        tracker.startTracking(TrackedDataHandlerRegistry.BYTE.create(0), flags);
        return tracker;
    }

    private byte[] bytes(EntityTrackerUpdateS2CPacket packet) throws Exception {
        assertNotNull(packet);
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        try {
            packet.write(buffer);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally { buffer.release(); }
    }
}
