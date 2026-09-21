package zsgrooms.modid.replay;

import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ReplayEncodingBuffersTest {
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
