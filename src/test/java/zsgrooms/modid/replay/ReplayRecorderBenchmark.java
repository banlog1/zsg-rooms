package zsgrooms.modid.replay;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in, paired experiments. Candidate code is test-only, not used by the recorder. */
@EnabledIfSystemProperty(named = "zsgrooms.recorderBenchmark", matches = "true")
class ReplayRecorderBenchmark {
    private static volatile byte[] sink;
    private static final com.sun.management.ThreadMXBean METRICS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    @Test void compareCostsAndBytes() throws Exception {
        assertTrue(METRICS.isThreadAllocatedMemorySupported());
        METRICS.setThreadAllocatedMemoryEnabled(true);
        METRICS.setThreadCpuTimeEnabled(true);
        System.out.println("Recorder benchmark JVM=" + System.getProperty("java.version")
                + "; paired alternating order, 4 warmup + 9 measured rounds; no production switch");
        for (int size : new int[]{96, 65536, 1048576}) {
            int operations = Math.min(10000, 32 * 1024 * 1024 / size);
            compare("packet-copy-" + size, new Packets(size, false), new Packets(size, true), operations);
        }
        for (int size : new int[]{256, 4096}) {
            for (boolean changing : new boolean[]{false, true}) {
                compare("HUD-" + size + (changing ? "-changing" : "-unchanged"),
                        new Hud(size, changing, 0), new Hud(size, changing, 1), 2000);
                compare("HUD-bulk-" + size + (changing ? "-changing" : "-unchanged"),
                        new Hud(size, changing, 0), new Hud(size, changing, 2), 2000);
            }
        }
        net.minecraft.SharedConstants.getGameVersion();
        net.minecraft.Bootstrap.initialize();
        for (int shape = 0; shape < 4; shape++) {
            compare("items-" + new String[]{"typical", "NBT-heavy", "all-changing", "one-changing"}[shape],
                    new Inventory(shape, false), new Inventory(shape, true), 2000);
        }
        hudEdges();
        packetEquivalence();
    }

    private static void compare(String name, Work before, Work after, int operations) throws Exception {
        long[][][] values = new long[2][3][9];
        long output = 0;
        for (int round = -4; round < 9; round++) {
            byte[][] result = new byte[2][];
            for (int order = 0; order < 2; order++) {
                int mode = (order + (round & 1)) & 1;
                Work work = mode == 0 ? before : after;
                work.prepare();
                long thread = Thread.currentThread().getId();
                long allocated = METRICS.getThreadAllocatedBytes(thread);
                long cpu = METRICS.getCurrentThreadCpuTime();
                long start = System.nanoTime();
                work.run(operations);
                long elapsed = System.nanoTime() - start;
                long usedCpu = METRICS.getCurrentThreadCpuTime() - cpu;
                long bytes = METRICS.getThreadAllocatedBytes(thread) - allocated;
                if (round >= 0) {
                    values[mode][0][round] = elapsed / operations;
                    values[mode][1][round] = usedCpu / operations;
                    values[mode][2][round] = bytes / operations;
                }
                result[mode] = work.finish();
                sink = result[mode];
            }
            assertArrayEquals(result[0], result[1], name + " changed output");
            output = result[0].length;
        }
        for (int mode = 0; mode < 2; mode++) {
            for (long[] metric : values[mode]) Arrays.sort(metric);
            System.out.printf(java.util.Locale.ROOT,
                    "%s %s ops=%d wall-ns/op=%d [%d..%d] cpu-mean-ns/op=%d allocated-B/op=%d output-B=%d%n",
                    name, mode == 0 ? "baseline" : "candidate", operations, values[mode][0][4],
                    values[mode][0][0], values[mode][0][8], Arrays.stream(values[mode][1]).sum() / 9, values[mode][2][4], output);
        }
    }

    private interface Work {
        void prepare();
        void run(int operations);
        byte[] finish() throws Exception;
    }

    private static final class Packets implements Work {
        final byte[] source;
        final boolean reuse;
        byte[] last;
        Packets(int size, boolean reuse) {
            source = new byte[size]; new Random(42).nextBytes(source); this.reuse = reuse;
        }
        public void prepare() { }
        public void run(int operations) {
            for (int i = 0; i < operations; i++) {
                PacketByteBuf buffer = reuse ? ReplayEncodingBuffers.acquire()
                        : new PacketByteBuf(Unpooled.buffer(256, ReplayEncodingBuffers.MAX_PACKET_BYTES));
                try {
                    buffer.writeVarInt(i);
                    buffer.writeBytes(source);
                    byte[] owned = new byte[buffer.readableBytes()];
                    buffer.getBytes(buffer.readerIndex(), owned);
                    sink = last = owned;
                } finally {
                    if (reuse) ReplayEncodingBuffers.recycle(buffer); else buffer.release();
                }
            }
        }
        public byte[] finish() { return last; }
    }

    private static final class Hud implements Work {
        final int size;
        final boolean changing;
        final int mode;
        PacketByteBuf buffer;
        ReplayHudTrack baseline;
        CompareFirstHud proposed;
        Hud(int size, boolean changing, int mode) {
            this.size = size; this.changing = changing; this.mode = mode;
        }
        public void prepare() {
            buffer = new PacketByteBuf(Unpooled.buffer(size));
            buffer.writeZero(size);
            baseline = new ReplayHudTrack(); proposed = new CompareFirstHud(mode == 2);
        }
        public void run(int operations) {
            for (int i = 0; i < operations; i++) {
                buffer.setInt(0, changing ? i : 0);
                if (mode != 0) proposed.add(i * 200L, 0, 1, buffer);
                else {
                    byte[] owned = new byte[buffer.readableBytes()];
                    buffer.getBytes(buffer.readerIndex(), owned);
                    baseline.add(i * 200L, 0, 1, owned);
                }
            }
        }
        public byte[] finish() throws Exception {
            try { return mode != 0 ? proposed.finish(Integer.MAX_VALUE) : baseline.finish(Integer.MAX_VALUE); }
            finally { buffer.release(); }
        }
    }

    private static final class Inventory implements Work {
        final int shape;
        final boolean cached;
        final net.minecraft.item.ItemStack[] items = new net.minecraft.item.ItemStack[42];
        ReplayItemSnapshot snapshot;
        PacketByteBuf buffer;
        Inventory(int shape, boolean cached) { this.shape = shape; this.cached = cached; }
        public void prepare() {
            buffer = ReplayEncodingBuffers.acquireHud();
            snapshot = new ReplayItemSnapshot();
            Arrays.fill(items, net.minecraft.item.ItemStack.EMPTY);
            if (shape == 0) {
                items[0] = new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_PICKAXE);
                items[1] = new net.minecraft.item.ItemStack(net.minecraft.item.Items.OBSIDIAN, 12);
                items[9] = new net.minecraft.item.ItemStack(net.minecraft.item.Items.ENDER_PEARL, 7);
                items[38] = new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_CHESTPLATE);
            } else {
                for (int i = 0; i < items.length; i++) {
                    items[i] = new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_PICKAXE);
                    items[i].setCustomName(new net.minecraft.text.LiteralText("Named tool " + i));
                    items[i].addEnchantment(net.minecraft.enchantment.Enchantments.UNBREAKING, 3);
                }
            }
        }
        public void run(int operations) {
            for (int i = 0; i < operations; i++) {
                buffer.clear();
                for (int slot = 0; slot < items.length; slot++) {
                    if (shape == 2 || shape == 3 && slot == 0) items[slot].getOrCreateTag().putInt("changing", i);
                    if (cached) snapshot.write(buffer, slot, items[slot]); else buffer.writeItemStack(items[slot]);
                }
            }
        }
        public byte[] finish() {
            byte[] result = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), result);
            ReplayEncodingBuffers.recycleHud(buffer);
            return result;
        }
    }

    private static void packetEquivalence() throws Exception {
        Random random = new Random(42);
        for (int i = 0; i < 100; i++) {
            byte[] input = new byte[i % 2 == 0 ? i * 17 : 65536]; random.nextBytes(input);
            PacketByteBuf before = new PacketByteBuf(Unpooled.buffer(256, ReplayEncodingBuffers.MAX_PACKET_BYTES));
            PacketByteBuf after = ReplayEncodingBuffers.acquire();
            try {
                before.writeVarInt(i); before.writeBytes(input);
                after.writeVarInt(i); after.writeBytes(input);
                byte[] a = new byte[before.readableBytes()], b = new byte[after.readableBytes()];
                before.getBytes(0, a); after.getBytes(0, b);
                assertArrayEquals(a, b);
                after.setZero(0, after.writerIndex());
                assertArrayEquals(a, b, "Queued payload changed after scratch reuse");
            } finally { before.release(); ReplayEncodingBuffers.recycle(after); }
        }
        System.out.println("Packet equivalence PASS: payload bytes and ownership across scratch reuse");
    }

    private static void hudEdges() throws Exception {
        for (int pattern = 0; pattern < 6; pattern++) {
            ReplayHudTrack baseline = new ReplayHudTrack();
            CompareFirstHud candidate = new CompareFirstHud(pattern >= 3);
            PacketByteBuf scratch = new PacketByteBuf(Unpooled.buffer());
            Random random = new Random(20260923);
            try {
                int iterations = pattern % 3 == 2 ? 75000 : 2000;
                for (int i = 0; i < iterations; i++) {
                    int length = pattern % 3 == 0 ? 80 : pattern % 3 == 1 ? 65536 : 0;
                    byte[] bytes = new byte[length];
                    if (length > 0 && i % 7 == 0) random.nextBytes(bytes);
                    if (pattern % 3 == 0 && i == 1000) bytes = new byte[65537];
                    int world = i / 300, entity = i % 300 < 5 ? -1 : 1;
                    long time = i * 200L;
                    baseline.add(time, world, entity, bytes);
                    scratch.clear(); scratch.writeInt(42); scratch.writeBytes(bytes); scratch.readerIndex(4);
                    candidate.add(time, world, entity, scratch);
                    assertEquals(4, scratch.readerIndex());
                    scratch.setZero(0, scratch.writerIndex());
                    assertEquals(baseline.due(time + 1, world, entity), candidate.due(time + 1, world, entity));
                }
                assertArrayEquals(baseline.finish(9000000), candidate.finish(9000000));
            } finally { scratch.release(); }
        }
        System.out.println("HUD equivalence PASS: snapshots, heartbeat, world/entity changes, ownership, limits and finish clamp");
    }

    /** Prototype of compare-before-copy; deliberately kept out of main sources. */
    private static final class CompareFirstHud {
        final boolean bulk;
        io.netty.buffer.ByteBuf previousView;
        CompareFirstHud(boolean bulk) { this.bulk = bulk; }
        final List<Frame> frames = new ArrayList<>();
        int bytes = 8;
        boolean sealed;
        long sampled = -1;
        int sampledWorld = -1, sampledEntity = -1;
        synchronized boolean due(long time, int world, int entity) {
            if (sealed || bytes >= ReplayHudTrack.MAX_BYTES || frames.size() >= 72000 || time < 0 || time > Integer.MAX_VALUE) return false;
            return sampled < 0 || time > sampled && (time - sampled >= 200 || sampledWorld != world || sampledEntity != entity);
        }
        synchronized void add(long time, int world, int entity, PacketByteBuf scratch) {
            if (!due(time, world, entity)) return;
            sampled = time; sampledWorld = world; sampledEntity = entity;
            int length = scratch.readableBytes();
            byte[] payload = null;
            if (!frames.isEmpty()) {
                Frame last = frames.get(frames.size() - 1);
                if (last.world == world && last.entity == entity && equal(scratch, last.payload)) {
                    if (time - last.time < 1000) return;
                    payload = last.payload;
                }
            }
            if (length > ReplayHudTrack.MAX_FRAME || bytes + length + 16 > ReplayHudTrack.MAX_BYTES) {
                sealed = true; return;
            }
            if (payload == null) {
                payload = new byte[length]; scratch.getBytes(scratch.readerIndex(), payload);
                if (bulk) {
                    if (previousView != null) previousView.release();
                    previousView = Unpooled.wrappedBuffer(payload);
                }
            }
            frames.add(new Frame((int) time, world, entity, payload));
            bytes += length + 16;
        }
        private boolean equal(PacketByteBuf buffer, byte[] bytes) {
            if (buffer.readableBytes() != bytes.length) return false;
            if (bulk) return io.netty.buffer.ByteBufUtil.equals(buffer, buffer.readerIndex(), previousView, 0, bytes.length);
            int start = buffer.readerIndex();
            for (int i = 0; i < bytes.length; i++) if (buffer.getByte(start + i) != bytes[i]) return false;
            return true;
        }
        synchronized byte[] finish(long duration) throws Exception {
            sealed = true;
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(bytes);
            DataOutputStream out = new DataOutputStream(buffer);
            out.writeInt(ReplayHudTrack.MAGIC); out.writeInt(1);
            for (Frame frame : frames) {
                if (frame.time > duration) break;
                out.writeInt(frame.time); out.writeInt(frame.world); out.writeInt(frame.entity);
                out.writeInt(frame.payload.length); out.write(frame.payload);
            }
            frames.clear();
            if (previousView != null) { previousView.release(); previousView = null; }
            return buffer.toByteArray();
        }
    }
    private static final class Frame {
        final int time, world, entity;
        final byte[] payload;
        Frame(int time, int world, int entity, byte[] payload) {
            this.time = time; this.world = world; this.entity = entity; this.payload = payload;
        }
    }
}
