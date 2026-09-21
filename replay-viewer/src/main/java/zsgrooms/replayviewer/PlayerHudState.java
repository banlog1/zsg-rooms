// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import io.netty.buffer.Unpooled;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

final class PlayerHudState {
    float health, maxHealth, absorption, experience;
    int food, armor, air, level, selected;
    boolean burning;
    final ItemStack[] items = new ItemStack[42];
    Effect[] effects;

    static PlayerHudState decode(byte[] bytes) {
        PacketByteBuf data = new PacketByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            PlayerHudState state = new PlayerHudState();
            state.health = finite(data.readFloat(), 0, 2048);
            state.maxHealth = finite(data.readFloat(), 1, 2048);
            state.absorption = finite(data.readFloat(), 0, 2048);
            state.food = bounded(data.readInt(), 0, 20);
            state.armor = bounded(data.readInt(), 0, 30);
            state.air = bounded(data.readInt(), -1000, 100000);
            state.level = bounded(data.readInt(), 0, Integer.MAX_VALUE);
            state.experience = finite(data.readFloat(), 0, 1);
            state.selected = bounded(data.readUnsignedByte(), 0, 8);
            state.burning = data.readBoolean();
            for (int i = 0; i < state.items.length; i++) state.items[i] = data.readItemStack();
            state.effects = new Effect[bounded(data.readUnsignedByte(), 0, 16)];
            for (int i = 0; i < state.effects.length; i++) {
                state.effects[i] = new Effect(new Identifier(data.readString(128)), bounded(data.readInt(), 0, Integer.MAX_VALUE),
                        bounded(data.readInt(), 0, 255));
            }
            if (data.isReadable()) throw new IllegalArgumentException("Trailing HUD data");
            return state;
        } catch (RuntimeException invalid) { return null; }
        finally { data.release(); }
    }

    private static int bounded(int value, int min, int max) {
        if (value < min || value > max) throw new IllegalArgumentException("Invalid HUD value");
        return value;
    }
    private static float finite(float value, float min, float max) {
        if (!Float.isFinite(value) || value < min || value > max) throw new IllegalArgumentException("Invalid HUD value");
        return value;
    }
    static final class Effect {
        final Identifier id;
        final int ticks, amplifier;
        Effect(Identifier id, int ticks, int amplifier) { this.id = id; this.ticks = ticks; this.amplifier = amplifier; }
    }
}
