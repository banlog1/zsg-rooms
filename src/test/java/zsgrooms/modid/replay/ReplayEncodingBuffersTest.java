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
