package zsgrooms.modid.replay;

import io.netty.buffer.Unpooled;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.LiteralText;
import zsgrooms.modid.ZsgRooms;
import java.lang.management.ManagementFactory;
import java.util.Arrays;

/** Development smoke-test microbenchmark; never runs in a normal recording. */
final class ReplayCaptureBenchmark {
    private static volatile byte[] sink;

    static void run() {
        incremental();
        for (int size : new int[]{96, 65536}) {
            byte[] payload = new byte[size];
            Arrays.fill(payload, (byte) 42);
            measure("packet-" + size, () -> packet(payload, false), () -> packet(payload, true));
        }
        ItemStack[] items = new ItemStack[42];
        Arrays.fill(items, ItemStack.EMPTY);
        items[0] = new ItemStack(Items.IRON_PICKAXE);
        items[1] = new ItemStack(Items.OBSIDIAN, 12);
        items[9] = new ItemStack(Items.ENDER_PEARL, 7);
        items[38] = new ItemStack(Items.IRON_CHESTPLATE);
        items[40] = new ItemStack(Items.SHIELD);
        ReplayItemSnapshot snapshot = new ReplayItemSnapshot();
        compare(items, snapshot);
        measure("inventory-typical", () -> inventory(items, null), () -> inventory(items, snapshot));
        items[0].setDamage(42);
        compare(items, snapshot);
        items[1].setCount(3);
        compare(items, snapshot);
        items[0].setCustomName(new LiteralText("Recorded tool"));
        compare(items, snapshot);
        items[0].getOrCreateTag().putString("capture_test", "first");
        compare(items, snapshot);
        items[0].getOrCreateTag().putString("capture_test", "changed in place");
        compare(items, snapshot);
        items[41] = new ItemStack(Items.DIAMOND, 3);
        compare(items, snapshot);
        items[1] = ItemStack.EMPTY;
        compare(items, snapshot);
        compare(items, new ReplayItemSnapshot());
        for (int i = 0; i < 36; i++) {
            items[i] = new ItemStack(Items.DIAMOND_PICKAXE);
            items[i].setCustomName(new LiteralText("Named tool " + i));
            items[i].addEnchantment(net.minecraft.enchantment.Enchantments.UNBREAKING, 3);
        }
        compare(items, snapshot);
        measure("inventory-nbt-heavy", () -> inventory(items, null), () -> inventory(items, snapshot));
        ZsgRooms.LOGGER.info("[ReplayCaptureBenchmark] PASS: cached bytes match vanilla encoding across count, damage, NBT, cursor and reset changes");
    }

    private static void incremental() {
        net.minecraft.entity.data.DataTracker tracker = net.minecraft.client.MinecraftClient.getInstance().player.getDataTracker();
        ReplayTrackedState before = new ReplayTrackedState(), after = new ReplayTrackedState();
        measure("metadata-previous-performance-to-new", () -> metadataBefore(tracker, before), () -> {
            try { ReplayPrototype.copyTrackedState(1, tracker, after); }
            catch (java.io.IOException error) { throw new IllegalStateException(error); }
        });
        for (int size : new int[]{256, 4096}) {
            HudWorkload baseline = new HudWorkload(size, false, false);
            HudWorkload optimized = new HudWorkload(size, true, false);
            measure("HUD-unchanged-" + size + "-previous-performance-to-new", baseline, optimized);
            baseline.compare(optimized);
        }
        HudWorkload baseline = new HudWorkload(256, false, true);
        HudWorkload optimized = new HudWorkload(256, true, true);
        measure("HUD-changing-256-previous-performance-to-new", baseline, optimized);
        baseline.compare(optimized);
        verifyHudCapture();
    }

    private static void verifyHudCapture() {
        ReplayHudCapture baseline = new ReplayHudCapture(false), optimized = new ReplayHudCapture(true);
        ReplayHudTrack before = new ReplayHudTrack(), after = new ReplayHudTrack();
        for (int i = 0; i < 1000; i++) {
            net.minecraft.client.network.ClientPlayerEntity player = i % 200 < 10 ? null
                    : net.minecraft.client.MinecraftClient.getInstance().player;
            baseline.capture(before, i * 50L, i / 200, player);
            optimized.capture(after, i * 50L, i / 200, player);
        }
        try {
            if (!Arrays.equals(before.finish(49950), after.finish(49950))) {
                throw new IllegalStateException("Performance mode changed the full player HUD track");
            }
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }

    private static void metadataBefore(net.minecraft.entity.data.DataTracker tracker, ReplayTrackedState cache) {
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(1);
            net.minecraft.entity.data.DataTracker.entriesToPacket(tracker.getAllEntries(), buffer);
            if (cache.unchanged(buffer)) return;
            net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket packet = new net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket();
            packet.read(buffer);
            buffer.readerIndex(0);
            cache.remember(buffer, packet);
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        finally { buffer.release(); }
    }

    private static final class HudWorkload implements Runnable {
        final ReplayHudTrack track = new ReplayHudTrack();
        final byte[] payload;
        final boolean optimized, changing;
        int sample;
        HudWorkload(int size, boolean optimized, boolean changing) {
            this.payload = new byte[size];
            this.optimized = optimized;
            this.changing = changing;
        }
        @Override public void run() {
            PacketByteBuf buffer = optimized ? ReplayEncodingBuffers.acquireHud()
                    : new PacketByteBuf(Unpooled.buffer(512, ReplayHudTrack.MAX_FRAME));
            try {
                int time = sample++ * 200;
                if (changing) payload[0] = (byte) sample;
                buffer.writeBytes(payload);
                byte[] bytes = new byte[buffer.readableBytes()];
                buffer.getBytes(0, bytes);
                track.add(time, 0, 1, bytes);
            } finally {
                if (optimized) ReplayEncodingBuffers.recycleHud(buffer); else buffer.release();
            }
        }
        void compare(HudWorkload other) {
            try {
                if (!Arrays.equals(track.finish(Integer.MAX_VALUE), other.track.finish(Integer.MAX_VALUE))) {
                    throw new IllegalStateException("Incremental HUD optimization changed the recorded track");
                }
            } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        }
    }

    private static void compare(ItemStack[] items, ReplayItemSnapshot snapshot) {
        if (!Arrays.equals(inventory(items, null), inventory(items, snapshot))) {
            throw new IllegalStateException("Cached item encoding differs from vanilla");
        }
    }

    private static byte[] inventory(ItemStack[] items, ReplayItemSnapshot snapshot) {
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer(512, ReplayHudTrack.MAX_FRAME));
        try {
            for (int i = 0; i < items.length; i++) {
                if (snapshot == null) buffer.writeItemStack(items[i]); else snapshot.write(buffer, i, items[i]);
            }
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(0, bytes);
            return sink = bytes;
        } finally { buffer.release(); }
    }

    private static byte[] packet(byte[] payload, boolean optimized) {
        PacketByteBuf buffer = optimized ? ReplayEncodingBuffers.acquire()
                : new PacketByteBuf(Unpooled.buffer(256, ReplayEncodingBuffers.MAX_PACKET_BYTES));
        try {
            buffer.writeVarInt(42);
            buffer.writeBytes(payload);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(0, bytes);
            return sink = bytes;
        } finally {
            if (optimized) ReplayEncodingBuffers.recycle(buffer); else buffer.release();
        }
    }

    private static void measure(String name, Runnable baseline, Runnable optimized) {
        for (int i = 0; i < 2000; i++) { baseline.run(); optimized.run(); }
        long[][] ns = new long[2][5], bytes = new long[2][5];
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        com.sun.management.ThreadMXBean allocation = bean instanceof com.sun.management.ThreadMXBean
                ? (com.sun.management.ThreadMXBean) bean : null;
        if (allocation != null && allocation.isThreadAllocatedMemorySupported()) allocation.setThreadAllocatedMemoryEnabled(true);
        else allocation = null;
        for (int run = 0; run < 5; run++) {
            for (int order = 0; order < 2; order++) {
                int mode = (order + run) % 2;
                Runnable action = mode == 0 ? baseline : optimized;
                long id = Thread.currentThread().getId();
                long before = allocation == null ? 0 : allocation.getThreadAllocatedBytes(id);
                long start = System.nanoTime();
                for (int i = 0; i < 1000; i++) action.run();
                ns[mode][run] = (System.nanoTime() - start) / 1000;
                bytes[mode][run] = allocation == null ? -1 : (allocation.getThreadAllocatedBytes(id) - before) / 1000;
            }
        }
        for (int mode = 0; mode < 2; mode++) { Arrays.sort(ns[mode]); Arrays.sort(bytes[mode]); }
        ZsgRooms.LOGGER.info("[ReplayCaptureBenchmark] {} median ns/op {} -> {}; allocated bytes/op {} -> {} (baseline -> performance)",
                name, ns[0][2], ns[1][2], bytes[0][2], bytes[1][2]);
    }
}
