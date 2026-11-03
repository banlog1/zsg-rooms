package zsgrooms.modid.replay;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.Arrays;

/** Client-thread observer only. Copies are never installed back into the live screen or player. */
final class ReplayDetailedCapture {
    private final ReplayHudCapture hud = new ReplayHudCapture(true);
    private long sampled = -1, accepted = -1;
    private int lastWorld = Integer.MIN_VALUE, lastEntity = Integer.MIN_VALUE;
    private byte[] previous;
    private Screen previousScreen;
    private ScreenHandler previousHandler;
    private ClientPlayerEntity previousPlayer;
    private int generation;
    private CachedItem[] slots = new CachedItem[0];
    private CachedItem cursor = new CachedItem();

    void capture(ReplayDetailedTrack track, long time, int world, MinecraftClient client, boolean loading) {
        if (!track.accepting() || time < 0 || time > Integer.MAX_VALUE || time <= sampled
                || sampled >= 0 && time - sampled < 50) return;
        sampled = time;
        ClientPlayerEntity player = client.player;
        int entity = player == null ? -1 : player.getEntityId();
        byte[] payload;
        try {
            payload = snapshot(client, loading);
        } catch (RuntimeException error) {
            // A malformed/modded stack or unsupported layout makes detail unknown, never fatal.
            payload = ReplayDetailedTrack.unavailable(ReplayDetailedTrack.GAP | (loading ? ReplayDetailedTrack.LOADING : 0));
        }
        if (accepted >= 0 && world == lastWorld && entity == lastEntity && Arrays.equals(previous, payload)
                && time - accepted < 1000) return;
        if (track.offer(time, world, entity, payload)) {
            previous = payload;
            accepted = time;
            lastWorld = world;
            lastEntity = entity;
        }
    }

    byte[] snapshot(MinecraftClient client, boolean loading) {
        ClientPlayerEntity player = client.player;
        Screen screen = client.currentScreen;
        int kind = kind(screen);
        ScreenHandler handler = kind >= 1 && kind <= 3 ? ((HandledScreen<?>) screen).getScreenHandler() : null;
        if (handler != null && handler.slots.size() > 256) throw new IllegalArgumentException("Too many screen slots");
        if (screen != previousScreen || handler != previousHandler || player != previousPlayer) {
            generation++;
            previousScreen = screen;
            previousHandler = handler;
            previousPlayer = player;
            slots = new CachedItem[handler == null ? 0 : handler.slots.size()];
            cursor = new CachedItem();
        }
        boolean available = !loading && player != null && client.world != null;
        if (!available) handler = null;
        byte[] hudBytes = available ? hud.snapshot(player) : new byte[0];
        PacketByteBuf data = ReplayEncodingBuffers.acquireDetail();
        try {
            data.writeByte((available ? ReplayDetailedTrack.AVAILABLE : 0) | (loading ? ReplayDetailedTrack.LOADING : 0));
            data.writeInt(client.options.viewDistance);
            data.writeByte(client.options.perspective);
            data.writeBoolean(client.options.hudHidden);
            data.writeBoolean(client.isPaused());
            float cooldown0 = available ? player.getAttackCooldownProgress(0) : 0;
            data.writeFloat(cooldown0);
            data.writeFloat(available ? player.getAttackCooldownProgress(1) : 0);
            data.writeByte(client.options.attackIndicator.getId());
            data.writeBoolean(available && client.targetedEntity instanceof LivingEntity
                    && ((LivingEntity) client.targetedEntity).isAlive()
                    && player.getAttackCooldownProgressPerTick() > 5 && cooldown0 >= 1);
            data.writeInt(hudBytes.length);
            data.writeBytes(hudBytes);
            data.writeByte(available ? kind : 0);
            data.writeInt(generation);
            data.writeInt(handler == null ? -1 : handler.syncId);
            String title = handler == null ? "" : screen.getTitle().getString();
            if (title.length() > 256) throw new IllegalArgumentException("Oversized screen title");
            data.writeString(title);
            data.writeShort(handler == null ? 0 : handler.slots.size());
            if (handler != null) {
                if (slots.length != handler.slots.size()) slots = new CachedItem[handler.slots.size()];
                for (int i = 0; i < handler.slots.size(); i++) {
                    Slot slot = handler.slots.get(i);
                    if (slot.id < 0 || slot.id > Short.MAX_VALUE || slot.x < Short.MIN_VALUE || slot.x > Short.MAX_VALUE
                            || slot.y < Short.MIN_VALUE || slot.y > Short.MAX_VALUE)
                        throw new IllegalArgumentException("Invalid screen slot");
                    data.writeShort(slot.id);
                    data.writeShort(slot.x);
                    data.writeShort(slot.y);
                    if (slots[i] == null) slots[i] = new CachedItem();
                    slots[i].write(data, slot.getStack());
                }
            }
            cursor.write(data, available ? player.inventory.getCursorStack() : ItemStack.EMPTY);
            byte[] bytes = new byte[data.readableBytes()];
            data.getBytes(0, bytes);
            return bytes;
        } finally { ReplayEncodingBuffers.recycleDetail(data); }
    }

    static int kind(Screen screen) {
        if (screen instanceof InventoryScreen) return 1;
        if (screen instanceof CraftingScreen) return 2;
        if (screen instanceof GenericContainerScreen) return 3;
        return screen instanceof HandledScreen ? 4 : 0;
    }

    static final class CachedItem {
        private ItemStack copy;
        private byte[] bytes;
        void write(PacketByteBuf output, ItemStack stack) {
            if (copy != null && ItemStack.areEqual(copy, stack)) {
                output.writeBytes(bytes);
                return;
            }
            int start = output.writerIndex();
            output.writeItemStack(stack);
            bytes = new byte[output.writerIndex() - start];
            output.getBytes(start, bytes);
            copy = stack.copy();
        }
    }
}
