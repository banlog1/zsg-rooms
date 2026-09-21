package zsgrooms.modid.replay;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;

/** Client-thread-only encoding cache. Copies detect in-place count, durability and NBT changes. */
final class ReplayItemSnapshot {
    private final ItemStack[] items = new ItemStack[42];
    private final byte[][] encoded = new byte[42][];
    private int bytes;

    void write(PacketByteBuf output, int slot, ItemStack stack) {
        if (items[slot] != null && ItemStack.areEqual(items[slot], stack)) {
            output.writeBytes(encoded[slot]);
            return;
        }
        int start = output.writerIndex();
        output.writeItemStack(stack);
        int length = output.writerIndex() - start;
        int retained = bytes + length - (encoded[slot] == null ? 0 : encoded[slot].length);
        if (retained > ReplayHudTrack.MAX_FRAME) throw new IllegalArgumentException("Oversized inventory snapshot");
        byte[] payload = new byte[length];
        output.getBytes(start, payload);
        items[slot] = stack.copy();
        encoded[slot] = payload;
        bytes = retained;
    }
}
