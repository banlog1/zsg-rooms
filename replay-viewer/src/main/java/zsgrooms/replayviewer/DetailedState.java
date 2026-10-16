// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import io.netty.buffer.Unpooled;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;

final class DetailedState {
    int distance, perspective, indicator, screen, generation;
    boolean hidden, paused, ready;
    float cooldown0, cooldown1;
    PlayerHudState hud;
    String title;
    ItemStack[] slots;
    ItemStack cursor;

    static DetailedState decode(byte[] payload) {
        PacketByteBuf data = new PacketByteBuf(Unpooled.wrappedBuffer(payload));
        try {
            int flags = bounded(data.readUnsignedByte(), 0, 7);
            DetailedState state = new DetailedState();
            state.distance = bounded(data.readInt(), 2, 128);
            state.perspective = bounded(data.readUnsignedByte(), 0, 2);
            state.hidden = data.readBoolean();
            state.paused = data.readBoolean();
            state.cooldown0 = cooldown(data.readFloat());
            state.cooldown1 = cooldown(data.readFloat());
            state.indicator = bounded(data.readUnsignedByte(), 0, 2);
            state.ready = data.readBoolean();
            byte[] hud = new byte[bounded(data.readInt(), 0, 65536)];
            data.readBytes(hud);
            state.hud = hud.length == 0 ? null : PlayerHudState.decode(hud);
            state.screen = bounded(data.readUnsignedByte(), 0, 4);
            state.generation = bounded(data.readInt(), 0, Integer.MAX_VALUE);
            bounded(data.readInt(), -1, Integer.MAX_VALUE);
            state.title = data.readString(256);
            int count = bounded(data.readUnsignedShort(), 0, 256);
            state.slots = new ItemStack[count];
            boolean[] seen = new boolean[256];
            RecordedScreenLayout layout = RecordedScreenLayout.create(state.screen, count);
            boolean compatible = layout != null;
            for (int i = 0; i < count; i++) {
                int id = bounded(data.readShort(), 0, 255);
                if (seen[id]) throw new IllegalArgumentException("Duplicate slot");
                seen[id] = true;
                int x = bounded(data.readShort(), -2048, 2048), y = bounded(data.readShort(), -2048, 2048);
                if (layout != null && (id != i || x != layout.x[i] || y != layout.y[i])) compatible = false;
                state.slots[i] = data.readItemStack();
            }
            state.cursor = data.readItemStack();
            if (data.isReadable() || (flags & 1) == 0 || (flags & 6) != 0 || state.hud == null) return null;
            if (state.screen != 0 && !compatible) state.screen = 4;
            return state;
        } catch (RuntimeException invalid) { return null; }
        finally { data.release(); }
    }

    ItemStack[] inventory() {
        ItemStack[] inventory = new ItemStack[46];
        java.util.Arrays.fill(inventory, ItemStack.EMPTY);
        for (int i = 9; i < 36; i++) inventory[i] = hud.items[i];
        for (int i = 0; i < 9; i++) inventory[36 + i] = hud.items[i];
        for (int i = 0; i < 4; i++) inventory[5 + i] = hud.items[39 - i];
        inventory[45] = hud.items[40];
        return inventory;
    }

    private static int bounded(int value, int min, int max) {
        if (value < min || value > max) throw new IllegalArgumentException("Invalid detailed state");
        return value;
    }
    private static float cooldown(float value) {
        if (!Float.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Invalid cooldown");
        return value;
    }
}
