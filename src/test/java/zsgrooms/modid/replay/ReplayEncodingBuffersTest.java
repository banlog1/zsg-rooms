package zsgrooms.modid.replay;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ReplayEncodingBuffersTest {
    @Test void mixedPacketSizesMatchFreshEncodingAndKeepOwnedPayloads() {
        byte[] previous = null;
        byte[] expectedPrevious = null;
        for (int size : new int[]{96, 65536, 1048576, 0, 1, 256}) {
            PacketByteBuf fresh = new PacketByteBuf(Unpooled.buffer(256, ReplayEncodingBuffers.MAX_PACKET_BYTES));
            PacketByteBuf reused = ReplayEncodingBuffers.acquire();
            try {
                assertEquals(fresh.maxCapacity(), reused.maxCapacity());
                for (PacketByteBuf buffer : new PacketByteBuf[]{fresh, reused}) {
                    buffer.writeVarInt(size);
                    for (int i = 0; i < size; i++) buffer.writeByte(i);
                }
                byte[] expected = new byte[fresh.readableBytes()];
                byte[] owned = new byte[reused.readableBytes()];
                fresh.getBytes(fresh.readerIndex(), expected);
                reused.getBytes(reused.readerIndex(), owned);
                assertArrayEquals(expected, owned);
                if (previous != null) assertArrayEquals(expectedPrevious, previous);
                previous = owned;
                expectedPrevious = expected;
            } finally {
                fresh.release();
                ReplayEncodingBuffers.recycle(reused);
            }
        }
        ReplayEncodingBuffers.acquire().release();
    }

    @Test void oversizedWriteKeepsPacketLimitAndNextCaptureStartsEmpty() {
        PacketByteBuf buffer = ReplayEncodingBuffers.acquire();
        try {
            buffer.writeInt(123);
            assertEquals(4 * 1024 * 1024, buffer.maxCapacity());
            assertThrows(IndexOutOfBoundsException.class,
                    () -> buffer.ensureWritable(ReplayEncodingBuffers.MAX_PACKET_BYTES));
        } finally {
            ReplayEncodingBuffers.recycle(buffer);
        }
        PacketByteBuf next = ReplayEncodingBuffers.acquire();
        try {
            assertEquals(0, next.readerIndex());
            assertEquals(0, next.writerIndex());
            next.writeInt(456);
            assertEquals(456, next.readInt());
        } finally {
            next.release();
        }
    }

    @Test void hudAndMetadataReuseKeepTheirOriginalBoundsAndSeparateStorage() {
        PacketByteBuf packet = ReplayEncodingBuffers.acquire();
        PacketByteBuf hud = ReplayEncodingBuffers.acquireHud();
        PacketByteBuf metadata = ReplayEncodingBuffers.acquireMetadata();
        assertNotSame(packet, hud);
        assertNotSame(hud, metadata);
        assertEquals(ReplayHudTrack.MAX_FRAME, hud.maxCapacity());
        assertEquals(Integer.MAX_VALUE, metadata.maxCapacity());
        hud.writeInt(42);
        metadata.writeInt(43);
        ReplayEncodingBuffers.recycleHud(hud);
        ReplayEncodingBuffers.recycleMetadata(metadata);
        assertSame(hud, ReplayEncodingBuffers.acquireHud());
        assertSame(metadata, ReplayEncodingBuffers.acquireMetadata());
        assertEquals(0, hud.writerIndex());
        assertEquals(0, metadata.writerIndex());
        hud.ensureWritable(8193);
        ReplayEncodingBuffers.recycleHud(hud);
        assertEquals(0, hud.refCnt());
        metadata.ensureWritable(ReplayEncodingBuffers.RETAIN_LIMIT + 1);
        ReplayEncodingBuffers.recycleMetadata(metadata);
        assertEquals(0, metadata.refCnt());
        packet.release();
    }

    @Test void reuseClearsIndexesButNeverAliasesQueuedBytes() {
        PacketByteBuf first = ReplayEncodingBuffers.acquire();
        first.writeInt(123);
        byte[] queued = new byte[4];
        first.readBytes(queued);
        ReplayEncodingBuffers.recycle(first);
        PacketByteBuf second = ReplayEncodingBuffers.acquire();
        assertSame(first, second);
        assertEquals(0, second.readerIndex());
        assertEquals(0, second.writerIndex());
        second.writeInt(456);
        assertArrayEquals(new byte[]{0, 0, 0, 123}, queued);
        second.release();
    }

    @Test void nestedCaptureHasIndependentScratchSpace() {
        PacketByteBuf outer = ReplayEncodingBuffers.acquire();
        outer.writeInt(123);
        PacketByteBuf inner = ReplayEncodingBuffers.acquire();
        assertNotSame(outer, inner);
        inner.writeInt(456);
        ReplayEncodingBuffers.recycle(inner);
        assertEquals(123, outer.getInt(0));
        ReplayEncodingBuffers.recycle(outer);
        assertEquals(0, outer.refCnt());
        ReplayEncodingBuffers.acquire().release();
    }

    @Test void largeBuffersAreNotRetained() {
        PacketByteBuf buffer = ReplayEncodingBuffers.acquire();
        buffer.ensureWritable(ReplayEncodingBuffers.RETAIN_LIMIT + 1);
        ReplayEncodingBuffers.recycle(buffer);
        assertEquals(0, buffer.refCnt());
    }

    @Test void detailScratchRetainsOnlySmallBuffersAndKeepsItsOriginalFrameCap() {
        ReplayEncodingBuffers.acquireDetail().release();
        PacketByteBuf detail = ReplayEncodingBuffers.acquireDetail();
        PacketByteBuf hud = ReplayEncodingBuffers.acquireHud();
        PacketByteBuf packet = ReplayEncodingBuffers.acquire();
        try {
            assertNotSame(hud, detail);
            assertNotSame(packet, detail);
            assertEquals(1024, detail.capacity());
            assertEquals(ReplayDetailedTrack.MAX_FRAME, detail.maxCapacity());
            detail.writeZero(8192);
            detail.readerIndex(4);
            ReplayEncodingBuffers.recycleDetail(detail);
            assertSame(detail, ReplayEncodingBuffers.acquireDetail());
            assertEquals(0, detail.readerIndex());
            assertEquals(0, detail.writerIndex());
            detail.writeZero(8193);
            ReplayEncodingBuffers.recycleDetail(detail);
            assertEquals(0, detail.refCnt());
        } finally {
            hud.release();
            packet.release();
            if (detail.refCnt() > 0) detail.release();
        }
    }

    @Test void detailOverflowDoesNotContaminateTheNextFrame() {
        PacketByteBuf fresh = new PacketByteBuf(Unpooled.buffer(1024, ReplayDetailedTrack.MAX_FRAME));
        PacketByteBuf detail = ReplayEncodingBuffers.acquireDetail();
        try {
            fresh.writeZero(ReplayDetailedTrack.MAX_FRAME);
            detail.writeZero(ReplayDetailedTrack.MAX_FRAME);
            RuntimeException expected = assertThrows(IndexOutOfBoundsException.class, () -> fresh.writeByte(1));
            RuntimeException actual = assertThrows(IndexOutOfBoundsException.class, () -> detail.writeByte(1));
            assertEquals(expected.getClass(), actual.getClass());
        } finally {
            fresh.release();
            ReplayEncodingBuffers.recycleDetail(detail);
        }
        assertEquals(0, detail.refCnt());
        PacketByteBuf next = ReplayEncodingBuffers.acquireDetail();
        try {
            assertEquals(1024, next.capacity());
            assertEquals(0, next.readerIndex());
            assertEquals(0, next.writerIndex());
            next.writeByte(1);
            assertEquals(1, next.readByte());
        } finally { next.release(); }
    }

    @Test void nestedDetailCaptureCannotOverwriteItsCallerOrAnOwnedPayload() {
        PacketByteBuf outer = ReplayEncodingBuffers.acquireDetail();
        outer.writeInt(123);
        byte[] owned = new byte[4];
        outer.getBytes(0, owned);
        PacketByteBuf inner = ReplayEncodingBuffers.acquireDetail();
        assertNotSame(outer, inner);
        inner.writeInt(456);
        ReplayEncodingBuffers.recycleDetail(inner);
        assertEquals(123, outer.getInt(0));
        ReplayEncodingBuffers.recycleDetail(outer);
        assertEquals(0, outer.refCnt());
        PacketByteBuf next = ReplayEncodingBuffers.acquireDetail();
        try {
            assertSame(inner, next);
            next.writeInt(789);
            assertArrayEquals(new byte[]{0, 0, 0, 123}, owned);
        } finally { next.release(); }
    }

    @Test void detailScratchIsThreadLocal() throws Exception {
        PacketByteBuf main = ReplayEncodingBuffers.acquireDetail();
        AtomicReference<PacketByteBuf> other = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            PacketByteBuf buffer = ReplayEncodingBuffers.acquireDetail();
            other.set(buffer);
            ReplayEncodingBuffers.recycleDetail(buffer);
            ReplayEncodingBuffers.acquireDetail().release();
        });
        try {
            thread.start();
            thread.join();
            assertNotNull(other.get());
            assertNotSame(main, other.get());
            assertEquals(0, other.get().refCnt());
        } finally { main.release(); }
    }

    @Test void threadsCannotShareScratchBuffers() throws Exception {
        PacketByteBuf main = ReplayEncodingBuffers.acquire();
        AtomicReference<PacketByteBuf> other = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            PacketByteBuf buffer = ReplayEncodingBuffers.acquire();
            other.set(buffer);
            buffer.release();
        });
        thread.start();
        thread.join();
        assertNotSame(main, other.get());
        main.release();
    }
}
