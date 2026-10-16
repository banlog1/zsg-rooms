package zsgrooms.modid.replay;

import io.netty.buffer.Unpooled;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReplayDetailedCaptureTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.getGameVersion();
        net.minecraft.Bootstrap.initialize();
    }

    @Test void cachedStacksDetectInPlaceCountsAndNbtWithoutMutatingTheLiveItem() {
        ReplayDetailedCapture.CachedItem cached = new ReplayDetailedCapture.CachedItem();
        ItemStack stack = new ItemStack(Items.STONE, 12);
        stack.getOrCreateTag().putString("marker", "first");
        ItemStack original = stack.copy();
        byte[] first = encode(cached, stack);
        assertTrue(ItemStack.areEqual(original, stack));
        assertArrayEquals(first, encode(cached, stack));
        stack.setCount(11);
        stack.getOrCreateTag().putString("marker", "second");
        byte[] second = encode(cached, stack);
        assertFalse(java.util.Arrays.equals(first, second));
        assertEquals(11, stack.getCount());
        assertEquals("second", stack.getTag().getString("marker"));
        assertArrayEquals(second, encode(cached, stack));
        assertEquals(12, original.getCount());
        assertEquals("first", original.getTag().getString("marker"));
        PacketByteBuf input = new PacketByteBuf(Unpooled.wrappedBuffer(second));
        try { assertTrue(ItemStack.areEqual(stack, input.readItemStack())); }
        finally { input.release(); }
    }

    @Test void emptyCursorUsesVanillaEmptyItemStackEncoding() {
        byte[] bytes = encode(new ReplayDetailedCapture.CachedItem(), ItemStack.EMPTY);
        assertArrayEquals(new byte[]{0}, bytes);
        assertTrue(ItemStack.EMPTY.isEmpty());
    }

    private static byte[] encode(ReplayDetailedCapture.CachedItem item, ItemStack stack) {
        PacketByteBuf output = new PacketByteBuf(Unpooled.buffer(256, ReplayDetailedTrack.MAX_FRAME));
        try {
            item.write(output, stack);
            byte[] bytes = new byte[output.readableBytes()];
            output.getBytes(0, bytes);
            return bytes;
        } finally { output.release(); }
    }
}
