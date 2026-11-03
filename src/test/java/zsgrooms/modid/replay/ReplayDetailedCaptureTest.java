package zsgrooms.modid.replay;

import io.netty.buffer.Unpooled;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundTag;
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

    @Test void cachedStacksDetectNbtOnlyNestedMutationsAgainstVanillaEncoding() {
        ReplayDetailedCapture.CachedItem cached = new ReplayDetailedCapture.CachedItem();
        ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
        stack.setDamage(5);
        CompoundTag nested = new CompoundTag();
        nested.putInt("value", 1);
        stack.getOrCreateTag().put("nested", nested);
        ItemStack original = stack.copy();
        byte[] first = encode(cached, stack);
        for (int value = 2; value < 8; value++) {
            nested.putInt("value", value); // Same stack, tag, count and damage; only nested NBT changes.
            byte[] actual = encode(cached, stack);
            PacketByteBuf vanilla = new PacketByteBuf(Unpooled.buffer(1024, ReplayDetailedTrack.MAX_FRAME));
            try {
                vanilla.writeItemStack(stack);
                byte[] expected = new byte[vanilla.readableBytes()];
                vanilla.getBytes(0, expected);
                assertArrayEquals(expected, actual);
                assertFalse(java.util.Arrays.equals(first, actual));
            } finally { vanilla.release(); }
            assertEquals(1, stack.getCount());
            assertEquals(5, stack.getDamage());
            assertEquals(value, nested.getInt("value"));
        }
        assertEquals(1, original.getTag().getCompound("nested").getInt("value"));
        assertArrayEquals(first, encode(new ReplayDetailedCapture.CachedItem(), original));
    }

    @Test void pooledDetailEncodingMatchesFreshBuffersAcrossSizesScreensAndMutableNbt() {
        for (int count : new int[]{0, 46, 54, 90, 256}) {
            for (int nbtSize : new int[]{0, 64}) {
                DetailFixture baseline = new DetailFixture(count, nbtSize);
                DetailFixture candidate = new DetailFixture(count, nbtSize);
                byte[] previous = null, expectedPrevious = null;
                for (int frame = 0; frame < 48; frame++) {
                    baseline.frame = candidate.frame = frame;
                    baseline.changeItems(frame, frame % 3 == 0);
                    candidate.changeItems(frame, frame % 3 == 0);
                    byte[] expected = baseline.encode(false);
                    byte[] actual = candidate.encode(true);
                    assertArrayEquals(expected, actual, "slots=" + count + " NBT=" + nbtSize + " frame=" + frame);
                    assertArrayEquals(expected, candidate.encode(true));
                    if (previous != null) assertArrayEquals(expectedPrevious, previous);
                    previous = actual;
                    expectedPrevious = expected;
                }
            }
        }
        ReplayEncodingBuffers.acquireDetail().release();
    }

    @Test void failedDetailEncodingMatchesFreshFailureAndRecoversWithOwnedBytes() {
        DetailFixture baseline = new DetailFixture(54, 0), candidate = new DetailFixture(54, 0);
        byte[] expected = baseline.encode(false), owned = candidate.encode(true);
        String oversizedTitle = repeated('x', 257);
        baseline.title = candidate.title = oversizedTitle;
        RuntimeException freshFailure = assertThrows(IllegalArgumentException.class, () -> baseline.encode(false));
        RuntimeException pooledFailure = assertThrows(IllegalArgumentException.class, () -> candidate.encode(true));
        assertEquals(freshFailure.getClass(), pooledFailure.getClass());
        assertEquals(freshFailure.getMessage(), pooledFailure.getMessage());
        baseline.title = candidate.title = repeated('\u03A9', 256);
        assertArrayEquals(baseline.encode(false), candidate.encode(true));
        assertArrayEquals(expected, owned);

        DetailFixture largeBaseline = new DetailFixture(256, 512), largeCandidate = new DetailFixture(256, 512);
        RuntimeException freshOverflow = assertThrows(RuntimeException.class, () -> largeBaseline.encode(false));
        RuntimeException pooledOverflow = assertThrows(RuntimeException.class, () -> largeCandidate.encode(true));
        assertEquals(freshOverflow.getClass(), pooledOverflow.getClass());
        assertArrayEquals(new DetailFixture(0, 0).encode(false), new DetailFixture(0, 0).encode(true));
        ReplayEncodingBuffers.acquireDetail().release();
    }

    /** Captured-value fixture, not a live client. Field/item writes match the original detail envelope.
     * HUD/player observation is deliberately outside this scratch-buffer comparison. */
    static final class DetailFixture {
        final ItemStack[] stacks;
        final ReplayDetailedCapture.CachedItem[] slots;
        final ReplayDetailedCapture.CachedItem cursor = new ReplayDetailedCapture.CachedItem();
        final byte[] hud;
        final ItemStack cursorStack = new ItemStack(Items.OBSIDIAN, 12);
        int frame = 1;
        String title = "Container";

        DetailFixture(int count, int nbtSize) {
            stacks = new ItemStack[count];
            slots = new ReplayDetailedCapture.CachedItem[count];
            String nbt = repeated('n', nbtSize);
            for (int i = 0; i < count; i++) {
                slots[i] = new ReplayDetailedCapture.CachedItem();
                stacks[i] = nbtSize == 0 && i % 9 != 0 ? ItemStack.EMPTY
                        : new ItemStack(nbtSize == 0 ? Items.STONE : Items.DIAMOND_PICKAXE);
                if (!stacks[i].isEmpty()) {
                    CompoundTag nested = new CompoundTag();
                    nested.putInt("value", i);
                    stacks[i].getOrCreateTag().put("nested", nested);
                    stacks[i].getOrCreateTag().putString("payload", nbt);
                }
            }
            PacketByteBuf data = new PacketByteBuf(Unpooled.buffer(512, ReplayHudTrack.MAX_FRAME));
            try {
                data.writeFloat(20); data.writeFloat(20); data.writeFloat(0);
                data.writeInt(20); data.writeInt(7); data.writeInt(300); data.writeInt(12);
                data.writeFloat(0.5f); data.writeByte(2); data.writeBoolean(false);
                for (int i = 0; i < 42; i++)
                    data.writeItemStack(i == 41 ? cursorStack : count == 0 ? ItemStack.EMPTY : stacks[i % count]);
                data.writeByte(0);
                hud = new byte[data.readableBytes()];
                data.getBytes(0, hud);
            } finally { data.release(); }
        }

        void changeItems(int value, boolean all) {
            for (int i = 0; i < stacks.length; i++) {
                if (!stacks[i].isEmpty()) {
                    stacks[i].setCount(1 + value % 64);
                    stacks[i].setDamage(value);
                    stacks[i].getTag().getCompound("nested").putInt("value", value);
                    if (!all) break;
                }
            }
            cursorStack.setCount(1 + value % 64);
            cursorStack.getOrCreateTag().putInt("value", value);
        }

        byte[] encode(boolean pooled) {
            // Baseline is the original per-snapshot allocation with exactly its old bounds.
            PacketByteBuf data = pooled ? ReplayEncodingBuffers.acquireDetail()
                    : new PacketByteBuf(Unpooled.buffer(1024, ReplayDetailedTrack.MAX_FRAME));
            try {
                boolean loading = frame % 19 == 0, available = !loading && frame % 13 != 0;
                int kind = stacks.length == 0 ? 0 : 1 + frame % 4;
                boolean handler = available && kind >= 1 && kind <= 3;
                data.writeByte((available ? ReplayDetailedTrack.AVAILABLE : 0)
                        | (loading ? ReplayDetailedTrack.LOADING : 0));
                data.writeInt(2 + frame % 24);
                data.writeByte(frame % 3);
                data.writeBoolean(frame % 2 == 0);
                data.writeBoolean(frame % 5 == 0);
                data.writeFloat(available ? 0.625f : 0);
                data.writeFloat(available ? 0.75f : 0);
                data.writeByte(frame % 3);
                data.writeBoolean(false);
                data.writeInt(available ? hud.length : 0);
                if (available) data.writeBytes(hud);
                data.writeByte(available ? kind : 0);
                data.writeInt(1 + frame / 12);
                data.writeInt(handler ? 7 + frame / 12 : -1);
                String screenTitle = handler ? title : "";
                if (screenTitle.length() > 256) throw new IllegalArgumentException("Oversized screen title");
                data.writeString(screenTitle);
                data.writeShort(handler ? stacks.length : 0);
                if (handler) {
                    for (int i = 0; i < stacks.length; i++) {
                        data.writeShort(i);
                        data.writeShort(8 + i % 9 * 18);
                        data.writeShort(18 + i / 9 * 18);
                        slots[i].write(data, stacks[i]);
                    }
                }
                cursor.write(data, available ? cursorStack : ItemStack.EMPTY);
                byte[] bytes = new byte[data.readableBytes()];
                data.getBytes(0, bytes);
                return bytes;
            } finally {
                if (pooled) ReplayEncodingBuffers.recycleDetail(data); else data.release();
            }
        }
    }

    private static String repeated(char value, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, value);
        return new String(chars);
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
