// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt in: ZSG_SUPPLEMENTAL_TRACK_BENCHMARK=1; run test --tests '*SupplementalTrackBenchmark' --rerun-tasks --info.
 * Headless data/registry bootstrap only: never calls MinecraftClient, playback bridges, sound engines or OpenAL.
 * Ten minutes (or ZSG_SUPPLEMENTAL_TRACK_MINUTES=1..30) at camera 240 Hz, presentation 60 Hz,
 * render 240 Hz, sound 4 events/s, context every 30 s.
 * Tagged held items exercise NBT decoding/equality; no packets, worlds, rendering, audio or device work is measured.
 * Open includes ZIP inflate, validation and spool/index construction, but not fixture generation or close/delete.
 * Lookup batches include loop/checksum overhead and warm OS file caches, not cold-disk or end-to-end FPS costs.
 * Each sequential batch covers about 17 seconds; longer fixtures increase index size, not playback duration.
 * Round results are batch means, not individual-frame latency percentiles. Paused times are integers.
 * Cached presentation mirrors PresentationPlayback's frame-identity decode reuse without its client bridge;
 * forced-decode is an explicitly artificial baseline, not the production paused path.
 * Thread allocation excludes other threads/native memory; CPU samples below 100 ms are flagged, not extrapolated.
 * An 80 s cooperative work budget and 90 s timeout bound work (not Gradle startup); partial batches are labeled.
 */
@EnabledIfEnvironmentVariable(named = "ZSG_SUPPLEMENTAL_TRACK_BENCHMARK", matches = "1")
class SupplementalTrackBenchmark {
    private static final int DURATION = duration(), CAMERA_HZ = 240, PRESENTATION_HZ = 60;
    private static final int CAMERA_FRAMES = DURATION / 1000 * CAMERA_HZ;
    private static final int PRESENTATION_FRAMES = DURATION / 1000 * PRESENTATION_HZ;
    private static final int SOUND_STEP = 250, SOUND_EVENTS = DURATION / SOUND_STEP - 1;
    private static final int QUERIES = 4096, ROUNDS = 3;
    private static final long MAX_TEMP = 100L * 1024 * 1024;
    private static volatile long sink;
    private long deadline;

    @Test @Timeout(90) void profileSupplementalTracks() throws Throwable {
        deadline = System.nanoTime() + 80_000_000_000L;
        // Same registry-only bootstrap as PresentationStateTest; this does not start a client or audio device.
        net.minecraft.SharedConstants.getGameVersion();
        net.minecraft.Bootstrap.initialize();
        Counters counters = new Counters();
        Path archive = Files.createTempFile("zsg-supplemental-benchmark-", ".mcpr");
        try {
            byte[][] payloads = {payload(70, "Survey pickaxe"), payload(90, "Mining pickaxe")};
            long presentationBytes = 8;
            for (int i = 0; i < PRESENTATION_FRAMES; i++) presentationBytes += 16L + payloads[(i / 120) & 1].length;
            long cameraBytes = 12L + CAMERA_FRAMES * 24L;
            long soundBytes = 12L + (DURATION / 30000L) * 17 + SOUND_EVENTS * (49L + soundId().length) + 5;
            long spoolBound = cameraBytes + presentationBytes + PRESENTATION_FRAMES * 12L
                    + soundBytes + SOUND_EVENTS * 12L;
            // Compressed ZIP bound is deliberately conservative: source bytes plus one MiB of overhead.
            long peakBound = 2 * (cameraBytes + presentationBytes + soundBytes) + PRESENTATION_FRAMES * 12L
                    + SOUND_EVENTS * 12L + 1024 * 1024;
            assertTrue(peakBound < MAX_TEMP, "Fixture plus all live track spools must stay below 100 MiB");
            writeArchive(archive, payloads);
            assertTrue(Files.size(archive) + spoolBound < MAX_TEMP);
            System.out.printf(Locale.ROOT,
                    "SUPPLEMENTAL scope=data-only duration-ms=%d camera-hz=%d presentation-hz=%d render-hz=240 "
                            + "sound-events=%d zip-bytes=%d peak-temp-bound=%d JVM=%s cpu=%s allocation=%s%n",
                    DURATION, CAMERA_HZ, PRESENTATION_HZ, SOUND_EVENTS, Files.size(archive), peakBound,
                    System.getProperty("java.version"), counters.cpuSupported, counters.allocatedBytes != null);
            RaceRecording recording = opened("metadata-open", counters, () -> RaceRecording.read(archive));
            try (CameraTrack camera = opened("camera-open-index", counters, () -> CameraTrack.read(recording));
                 PresentationTrack presentation = opened("presentation-open-index", counters, () -> PresentationTrack.read(recording));
                 LocalSoundTrack sound = opened("sound-open-index", counters, () -> LocalSoundTrack.read(recording))) {
                assertNotNull(camera); assertNotNull(presentation); assertNotNull(sound);
                assertEquals(CAMERA_FRAMES, camera.frameCount());
                assertEquals(PRESENTATION_FRAMES, presentation.frameCount());
                assertEquals(SOUND_EVENTS, sound.eventCount());
                verify(camera, presentation, sound);
                for (String mode : new String[] {"sequential", "paused", "random-seek"}) {
                    double[] times = times(mode);
                    float[] rotation = new float[2];
                    measure("camera-lookup-interpolate/" + mode, counters, i -> {
                        if (!camera.sample(times[i], 0, 7, 0, DURATION, rotation)) throw new AssertionError("Camera missing");
                        return Float.floatToIntBits(rotation[0]) + (long) Float.floatToIntBits(rotation[1]);
                    });
                    measure("presentation-lookup-only/" + mode, counters, i -> {
                        PresentationTrack.Pair pair = pair(presentation, times[i]);
                        return pair.before.time + pair.after.time + Double.doubleToLongBits(pair.fraction);
                    });
                    DecodedCache cache = new DecodedCache();
                    measure("presentation-cached-decode-interpolate/" + mode, counters, i -> {
                        if (mode.equals("random-seek")) cache.clear();
                        return cache.apply(pair(presentation, times[i]));
                    });
                    PresentationState blended = new PresentationState();
                    measure("presentation-forced-decode-interpolate/" + mode, counters, i -> {
                        PresentationTrack.Pair pair = pair(presentation, times[i]);
                        PresentationState a = PresentationState.decode(pair.before.payload);
                        PresentationState b = PresentationState.decode(pair.after.payload);
                        if (a == null || b == null) throw new AssertionError("Decode failed");
                        PresentationState.interpolate(a, b, pair.fraction, blended);
                        return Double.doubleToLongBits(blended.worldFov) + Float.floatToIntBits(blended.mainEquip);
                    });
                }
                measure("sound-event-sequential", counters, i -> digest(sound.event(i % SOUND_EVENTS)));
                int[] slots = new int[QUERIES];
                Random random = new Random(310927);
                for (int i = 0; i < slots.length; i++) slots[i] = random.nextInt(SOUND_EVENTS);
                measure("sound-after-plus-event/random-seek", counters, i -> {
                    int time = (slots[i] + 1) * SOUND_STEP - 1;
                    return digest(sound.event(sound.after(time)));
                });
                for (String mode : new String[] {"sequential", "paused", "random-seek"}) {
                    double[] times = times(mode);
                    LocalSoundCursor cursor = new LocalSoundCursor();
                    SilentConsumer consumer = new SilentConsumer();
                    measure("sound-cursor-frame/" + mode, counters, i -> {
                        cursor.frame(sound, (int) times[i], 0, 7, mode.equals("paused"),
                                mode.equals("random-seek"), consumer);
                        return consumer.digest + (cursor.covers(LocalSoundTrack.STEP, 7) ? 1 : 0);
                    });
                    if (!mode.equals("sequential")) assertEquals(0, consumer.count);
                }
                System.out.printf(Locale.ROOT, "SUPPLEMENTAL complete budget-exhausted=%s sink=%d%n", expired(), sink);
            }
        } finally { Files.deleteIfExists(archive); }
    }

    private static void verify(CameraTrack camera, PresentationTrack presentation, LocalSoundTrack sound) {
        float[] rotation = new float[2];
        assertTrue(camera.sample(1000.5, 0, 7, 0, DURATION, rotation));
        assertEquals(60.03125, rotation[0], .0001);
        PresentationTrack.Pair pair = pair(presentation, 1000.5);
        assertEquals(1000, pair.before.time);
        PresentationState state = PresentationState.decode(pair.before.payload);
        assertNotNull(state); assertEquals(Items.DIAMOND_PICKAXE, state.main.getItem());
        assertNotNull(state.main.getTag());
        PresentationState next = PresentationState.decode(pair.after.payload);
        assertNotNull(next);
        PresentationState blended = new PresentationState();
        PresentationState.interpolate(state, next, pair.fraction, blended);
        long expected = Double.doubleToLongBits(blended.worldFov) + Float.floatToIntBits(blended.mainEquip);
        DecodedCache cache = new DecodedCache();
        assertEquals(expected, cache.apply(pair));
        assertEquals(expected, cache.apply(pair));
        SilentConsumer consumer = new SilentConsumer();
        LocalSoundCursor cursor = new LocalSoundCursor();
        cursor.frame(sound, 0, 0, 7, false, false, consumer);
        for (int time = 1; time <= 1000; time++) cursor.frame(sound, time, 0, 7, false, false, consumer);
        assertEquals(4, consumer.count);
        cursor.frame(sound, 1000, 0, 7, true, false, consumer);
        cursor.frame(sound, DURATION / 2, 0, 7, false, true, consumer);
        assertEquals(4, consumer.count);
    }

    private static PresentationTrack.Pair pair(PresentationTrack track, double time) {
        PresentationTrack.Pair pair = track.at(time, 0, 7, 0, DURATION);
        if (pair == null || pair.after == null) throw new AssertionError("Presentation missing at " + time);
        return pair;
    }

    private static double[] times(String mode) {
        double[] times = new double[QUERIES];
        Random random = new Random(715907);
        for (int i = 0; i < times.length; i++) times[i] = mode.equals("paused") ? DURATION / 3.0
                : mode.equals("random-seek") ? 1000 + random.nextInt(DURATION - 2000) + .5
                : 1000 + i * (1000.0 / 240) + .5;
        return times;
    }

    private boolean expired() { return Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline; }

    private void checkBudget() {
        if (expired()) throw new AssertionError("Supplemental fixture/open exceeded the 80 s work budget");
    }

    private void measure(String name, Counters counters, Operation operation) throws Throwable {
        if (expired()) { System.out.println("SUPPLEMENTAL skipped-budget " + name); return; }
        batch(name, -1, counters, operation);
        for (int round = 0; round < ROUNDS && !expired(); round++) batch(name, round, counters, operation);
    }

    private void batch(String name, int round, Counters counters, Operation operation) throws Throwable {
        long allocated = counters.allocated(), cpu = counters.cpu(), wall = System.nanoTime(), digest = 0;
        int operations = 0;
        for (; operations < QUERIES; operations++) {
            if ((operations & 63) == 0 && expired()) break;
            digest += operation.run(operations);
        }
        long usedWall = System.nanoTime() - wall, usedCpu = counters.deltaCpu(cpu), usedBytes = counters.deltaAllocated(allocated);
        sink = digest;
        if (round >= 0 && operations > 0) report(name, round, operations, usedWall, usedCpu, usedBytes, operations != QUERIES);
    }

    private <T> T opened(String name, Counters counters, Factory<T> factory) throws Throwable {
        checkBudget();
        long allocated = counters.allocated(), cpu = counters.cpu(), wall = System.nanoTime();
        T value = factory.open();
        long usedWall = System.nanoTime() - wall, usedCpu = counters.deltaCpu(cpu), usedBytes = counters.deltaAllocated(allocated);
        report(name + "/single-open-not-cold-disk", 0, 1, usedWall, usedCpu, usedBytes, false);
        return value;
    }

    private static void report(String name, int round, int operations, long wall, long cpu, long bytes, boolean partial) {
        System.out.printf(Locale.ROOT,
                "SUPPLEMENTAL %s round=%d ops=%d wall-ms=%.3f wall-ns/op=%.1f thread-cpu-ns/op=%s "
                        + "thread-allocated-B/op=%s cpu-sample-short=%s partial=%s%n",
                name, round, operations, wall / 1e6, wall / (double) operations,
                cpu < 0 ? "unavailable" : String.format(Locale.ROOT, "%.1f", cpu / (double) operations),
                bytes < 0 ? "unavailable" : String.format(Locale.ROOT, "%.1f", bytes / (double) operations),
                cpu < 0 || cpu < 100_000_000L, partial);
    }

    private void writeArchive(Path archive, byte[][] payloads) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(archive)))) {
            zip.setLevel(1);
            JsonObject metadata = RaceRecordingTest.data("00000000-0000-0000-0000-000000000003", 0, null);
            metadata.addProperty("durationMillis", DURATION);
            metadata.getAsJsonArray("races").get(0).getAsJsonObject().addProperty("endOffsetNanos", DURATION * 1000000L);
            JsonObject interval = new JsonObject();
            interval.addProperty("startReplayMillis", 0); interval.addProperty("endReplayMillis", DURATION);
            interval.addProperty("worldIndex", 0);
            JsonArray intervals = new JsonArray(); intervals.add(interval); metadata.add("intervals", intervals);
            entry(zip, "zsg-rooms/races.json", metadata.toString());
            entry(zip, "metaData.json", "{\"protocol\":736,\"duration\":" + DURATION + "}");
            entry(zip, "recording.tmcpr", "");
            DataOutputStream out = new DataOutputStream(zip);
            zip.putNextEntry(new ZipEntry(CameraTrack.ENTRY));
            out.writeInt(CameraTrack.MAGIC); out.writeInt(1); out.writeInt(CAMERA_HZ);
            for (int i = 0; i < CAMERA_FRAMES; i++) {
                if ((i & 4095) == 0) checkBudget();
                out.writeInt(time(i, CAMERA_HZ)); out.writeInt(0); out.writeInt(7); out.writeInt(CameraTrack.AVAILABLE);
                out.writeFloat((i % 6000) * .25F); out.writeFloat((i % 240) / 4F - 30);
            }
            zip.closeEntry(); zip.putNextEntry(new ZipEntry(PresentationTrack.ENTRY));
            out.writeInt(PresentationTrack.MAGIC); out.writeInt(1);
            for (int i = 0; i < PRESENTATION_FRAMES; i++) {
                if ((i & 4095) == 0) checkBudget();
                byte[] payload = payloads[(i / 120) & 1];
                out.writeInt(time(i, PRESENTATION_HZ)); out.writeInt(0); out.writeInt(7);
                out.writeInt(payload.length); out.write(payload);
            }
            zip.closeEntry(); zip.putNextEntry(new ZipEntry(LocalSoundTrack.ENTRY));
            out.writeInt(LocalSoundTrack.MAGIC); out.writeInt(1); out.writeInt(LocalSoundTrack.CHANNELS);
            byte[] id = soundId();
            for (int now = 0; now < DURATION; now += SOUND_STEP) {
                if (now % 30000 == 0) {
                    checkBudget(); out.writeByte(1); out.writeInt(now); out.writeInt(0); out.writeInt(7);
                    out.writeInt(LocalSoundTrack.CHANNELS);
                }
                if (now == 0) continue;
                out.writeByte(2); out.writeInt(now); out.writeInt(0); out.writeInt(7);
                out.writeByte(LocalSoundTrack.STEP); out.writeByte(7); out.writeFloat(.15F); out.writeFloat(1.25F);
                out.writeDouble(now / 1000.0); out.writeDouble(64); out.writeDouble(-90.75);
                out.writeShort(id.length); out.write(id);
            }
            out.writeByte(0); out.writeInt(DURATION); zip.closeEntry();
        }
    }

    private static int time(int slot, int hz) { return (int) (slot * 1000L / hz); }
    private static int duration() {
        String minutes = System.getenv("ZSG_SUPPLEMENTAL_TRACK_MINUTES");
        int value = minutes == null ? 10 : Integer.parseInt(minutes);
        if (value < 1 || value > 30) throw new IllegalArgumentException("Benchmark minutes must be 1..30");
        return value * 60000;
    }
    private static byte[] soundId() { return "minecraft:block.stone.step".getBytes(StandardCharsets.US_ASCII); }

    private static void entry(ZipOutputStream zip, String name, String contents) throws Exception {
        zip.putNextEntry(new ZipEntry(name)); zip.write(contents.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
    }

    private static byte[] payload(double fov, String name) {
        PacketByteBuf bytes = new PacketByteBuf(Unpooled.buffer());
        try {
            bytes.writeBytes(PresentationTrackTest.payload(fov), 0, 80);
            ItemStack main = new ItemStack(Items.DIAMOND_PICKAXE);
            main.getOrCreateTag().putString("benchmark", name);
            main.getOrCreateTag().putInt("Damage", 19);
            bytes.writeItemStack(main); bytes.writeItemStack(new ItemStack(Items.SHIELD)); bytes.writeItemStack(ItemStack.EMPTY);
            byte[] payload = new byte[bytes.writerIndex()]; bytes.getBytes(0, payload);
            // Vary a scalar between the two complete states as well as the item tag.
            ByteBuffer.wrap(payload).putFloat(68, fov == 70 ? .3F : .7F);
            return payload;
        } finally { bytes.release(); }
    }

    private static long digest(LocalSoundTrack.Event event) {
        return event.time + event.channel + event.sound.hashCode() + Double.doubleToLongBits(event.x);
    }

    private static final class SilentConsumer implements Consumer<LocalSoundTrack.Event> {
        long count, digest;
        @Override public void accept(LocalSoundTrack.Event event) { count++; digest += digest(event); }
    }

    /** Only the client-independent caching portion of PresentationPlayback.update. */
    private static final class DecodedCache {
        PresentationTrack.Frame before, after;
        PresentationState a, b;
        final PresentationState blended = new PresentationState();
        void clear() { before = after = null; a = b = null; }
        long apply(PresentationTrack.Pair pair) {
            if (pair.before != before) {
                a = pair.before == after ? b : PresentationState.decode(pair.before.payload);
                before = pair.before;
            }
            if (pair.after != after) { after = pair.after; b = after == null ? null : PresentationState.decode(after.payload); }
            if (a == null || pair.fraction != 0 && b == null) throw new AssertionError("Cached decode failed");
            PresentationState.interpolate(a, pair.fraction == 0 ? a : b, pair.fraction, blended);
            return Double.doubleToLongBits(blended.worldFov) + Float.floatToIntBits(blended.mainEquip);
        }
    }

    private interface Operation { long run(int i) throws Throwable; }
    private interface Factory<T> { T open() throws Throwable; }

    private static final class Counters {
        final ThreadMXBean thread = ManagementFactory.getThreadMXBean();
        final long id = Thread.currentThread().getId();
        final boolean cpuSupported;
        final MethodHandle allocatedBytes;
        Counters() throws ReflectiveOperationException {
            cpuSupported = thread.isCurrentThreadCpuTimeSupported();
            if (cpuSupported && !thread.isThreadCpuTimeEnabled()) thread.setThreadCpuTimeEnabled(true);
            MethodHandle counter = null;
            // Reflection preserves --release 8 compatibility with the optional HotSpot management API.
            try {
                Class<?> extended = Class.forName("com.sun.management.ThreadMXBean");
                if (extended.isInstance(thread) && Boolean.TRUE.equals(extended.getMethod("isThreadAllocatedMemorySupported").invoke(thread))) {
                    extended.getMethod("setThreadAllocatedMemoryEnabled", boolean.class).invoke(thread, true);
                    counter = MethodHandles.publicLookup().unreflect(extended.getMethod("getThreadAllocatedBytes", long.class)).bindTo(thread);
                }
            } catch (ClassNotFoundException ignored) { }
            allocatedBytes = counter;
        }
        long cpu() { return cpuSupported ? thread.getCurrentThreadCpuTime() : -1; }
        long allocated() throws Throwable { return allocatedBytes == null ? -1 : (long) allocatedBytes.invokeExact(id); }
        long deltaCpu(long start) { long end = cpu(); return start < 0 || end < 0 ? -1 : end - start; }
        long deltaAllocated(long start) throws Throwable { long end = allocated(); return start < 0 || end < 0 ? -1 : end - start; }
    }
}
