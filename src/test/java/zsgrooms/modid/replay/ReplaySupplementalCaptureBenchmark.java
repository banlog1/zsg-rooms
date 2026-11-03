package zsgrooms.modid.replay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Headless opt-in measurements. No client, renderer, sound engine or production configuration is started. */
@EnabledIfEnvironmentVariable(named = "ZSG_SUPPLEMENTAL_CAPTURE_BENCHMARK", matches = "1")
class ReplaySupplementalCaptureBenchmark {
    private static final int WARMUPS = 2, ROUNDS = 5, OPERATIONS = 16384;
    private static final com.sun.management.ThreadMXBean METRICS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    @TempDir Path directory;

    @BeforeAll static void bootstrap() {
        ReplayDetailedCaptureTest.bootstrap();
        assertTrue(METRICS.isThreadAllocatedMemorySupported());
        METRICS.setThreadAllocatedMemoryEnabled(true);
        METRICS.setThreadCpuTimeEnabled(true);
    }

    @Test @Timeout(120) void profileProductionEncodersAndSpools() throws Throwable {
        System.out.println("CAPTURE JVM=" + System.getProperty("java.version")
                + " processors=" + Runtime.getRuntime().availableProcessors()
                + " max-heap=" + Runtime.getRuntime().maxMemory()
                + " warmups=" + WARMUPS + " rounds=" + ROUNDS + " operations=" + OPERATIONS);
        System.out.println("CAPTURE scope=production sampler/encoder/queue with real buffered disk spools; "
                + "drain waits, construction and finalization excluded from producer timing. "
                + "Presentation includes pre-observed-state setters, method handles and item mutation, not live render hooks. "
                + "Synthetic timestamps are not wall-clock pacing; batch drains exclude sustained pressure. "
                + "Writer CPU is sampled before finish, excludes final in-flight work and flush; Windows CPU samples are coarse. "
                + "ZIP timing is sidecar-only, not complete MCPR finalization. No audio is played.");
        for (int hz : new int[]{60, 120, 240}) profile("camera-" + hz + "Hz-at-480-render-Hz", () -> new Camera(hz));
        for (int shape = 0; shape < 4; shape++) {
            int selected = shape;
            profile("presentation-" + new String[]{"empty", "tools", "NBT1024-stable", "NBT1024-changing"}[shape],
                    () -> new Presentation(selected));
        }
        profile("sound-events", Sounds::new);
        disabledHooks();
    }

    private void profile(String name, Factory factory) throws Throwable {
        double[][] values = new double[7][ROUNDS];
        long bytes = 0;
        for (int round = -WARMUPS; round < ROUNDS; round++) {
            try (Fixture fixture = factory.create()) {
                long workerId = ((Thread) field(fixture.track().getClass(), "worker").get(fixture.track())).getId();
                long writerCpu = METRICS.getThreadCpuTime(workerId);
                long writerAllocated = METRICS.getThreadAllocatedBytes(workerId);
                long wall = 0, cpu = 0, allocated = 0;
                for (int start = 0; start < OPERATIONS; start += 32) {
                    long beforeAllocated = METRICS.getThreadAllocatedBytes(Thread.currentThread().getId());
                    long beforeCpu = METRICS.getCurrentThreadCpuTime();
                    long before = System.nanoTime();
                    for (int i = start; i < start + 32; i++) fixture.offer(i);
                    wall += System.nanoTime() - before;
                    cpu += METRICS.getCurrentThreadCpuTime() - beforeCpu;
                    allocated += METRICS.getThreadAllocatedBytes(Thread.currentThread().getId()) - beforeAllocated;
                    fixture.drain();
                }
                writerCpu = METRICS.getThreadCpuTime(workerId) - writerCpu;
                writerAllocated = METRICS.getThreadAllocatedBytes(workerId) - writerAllocated;
                long beforeFinish = System.nanoTime();
                Path path = fixture.finish();
                long finish = System.nanoTime() - beforeFinish;
                assertNotNull(path);
                bytes = Files.size(path);
                fixture.verify();
                if (round >= 0) {
                    values[0][round] = wall / (double) OPERATIONS;
                    values[1][round] = cpu / (double) OPERATIONS;
                    values[2][round] = allocated / (double) OPERATIONS;
                    values[3][round] = writerCpu / 1e6;
                    values[4][round] = writerAllocated / (double) OPERATIONS;
                    values[5][round] = finish / 1e6;
                    values[6][round] = fixture.simulatedHz();
                }
                if (round == ROUNDS - 1) {
                    System.out.println("CAPTURE " + name + " coverage=" + fixture.status());
                    zip(name, path);
                }
            }
        }
        for (double[] metric : values) Arrays.sort(metric);
        System.out.printf(Locale.ROOT,
                "CAPTURE %s producer-wall-median-ns/op=%.1f producer-wall-range=[%.1f..%.1f] "
                        + "producer-cpu-mean-ns/op=%.1f producer-allocation-median-B/op=%.1f "
                        + "writer-cpu-median-ms=%.3f writer-allocation-median-B/input=%.1f "
                        + "queue-empty-finish-median-ms=%.3f file-bytes=%d extrapolated-raw-MiB/hour=%.2f%n",
                name, values[0][ROUNDS / 2], values[0][0], values[0][ROUNDS - 1],
                Arrays.stream(values[1]).average().orElse(0), values[2][ROUNDS / 2],
                values[3][ROUNDS / 2], values[4][ROUNDS / 2], values[5][ROUNDS / 2], bytes,
                bytes * values[6][0] * 3600.0 / OPERATIONS / 1048576);
    }

    private void zip(String name, Path input) throws Exception {
        Path archive = directory.resolve("sidecar.zip");
        byte[] buffer = new byte[65536];
        long before = System.nanoTime();
        try (ZipOutputStream output = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(archive)));
             InputStream source = Files.newInputStream(input)) {
            output.putNextEntry(new ZipEntry("track.bin"));
            int count;
            while ((count = source.read(buffer)) != -1) output.write(buffer, 0, count);
            output.closeEntry();
        }
        System.out.printf(Locale.ROOT, "CAPTURE %s single-warm-sidecar-zip-ms=%.3f zip-bytes=%d%n",
                name, (System.nanoTime() - before) / 1e6, Files.size(archive));
        Files.delete(archive);
    }

    private static void disabledHooks() {
        for (int round = -WARMUPS; round < ROUNDS; round++) {
            int operations = 1_000_000;
            long allocated = METRICS.getThreadAllocatedBytes(Thread.currentThread().getId());
            long before = System.nanoTime();
            for (int i = 0; i < operations; i++) {
                ReplayPresentationCapture.beginFrame(.5F);
                ReplayPresentationCapture.fov(true, 70, 1, 0, 0);
                ReplayPresentationCapture.hands(null, null, .5F);
                ReplayPresentationCapture.endFrame();
                ReplayLocalSoundCapture.mining(null, null, 1, 1, null);
            }
            long wall = System.nanoTime() - before;
            allocated = METRICS.getThreadAllocatedBytes(Thread.currentThread().getId()) - allocated;
            if (round >= 0) System.out.printf(Locale.ROOT,
                    "CAPTURE disabled-API-hooks round=%d wall-ns/group=%.1f allocation-B/group=%.3f "
                            + "excludes-transformed-Mixin-Args=true%n", round, wall / (double) operations,
                    allocated / (double) operations);
        }
    }

    private abstract static class Fixture implements AutoCloseable {
        final Object track;
        final Field pending;
        Fixture(Object track, String pending) throws Exception {
            this.track = track;
            this.pending = field(track.getClass(), pending);
        }
        Object track() { return track; }
        void drain() throws Exception {
            long deadline = System.nanoTime() + 5_000_000_000L;
            for (;;) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Benchmark interrupted");
                synchronized (track) { if (pending.getInt(track) == 0) return; }
                if (System.nanoTime() >= deadline) fail("Spool did not drain within five seconds");
                // Sub-millisecond parks can round to a full Windows timer tick; pacing is untimed.
                Thread.yield();
            }
        }
        abstract void offer(int frame) throws Throwable;
        abstract Path finish() throws Exception;
        abstract void verify();
        abstract String status();
        abstract int simulatedHz();
        @Override public abstract void close();
    }

    private final class Camera extends Fixture {
        final ReplayCameraTrack camera;
        final ReplayCameraSampler sampler;
        final int hz;
        Camera(int hz) throws Exception {
            super(ReplayCameraTrack.create(directory, hz), "queued");
            camera = (ReplayCameraTrack) track; sampler = new ReplayCameraSampler(hz); this.hz = hz;
        }
        void offer(int frame) {
            // Include the outer acceptance check that the production render integration currently performs.
            if (camera.accepting()) sampler.capture(camera, frame * 1_000_000_000L / 480, 0, 7, true,
                    (frame % 1440) * .25F, (frame % 160) - 80);
        }
        Path finish() throws Exception { camera.finish(); return camera.completedFile(); }
        void verify() {
            JsonObject data = new JsonParser().parse(status()).getAsJsonObject();
            assertEquals("complete", camera.status());
            assertEquals((OPERATIONS - 1L) * hz / 480 + 1, data.get("samples").getAsLong());
            assertEquals(0, data.get("droppedSamples").getAsLong());
        }
        String status() { return camera.statusJson(); }
        int simulatedHz() { return 480; }
        public void close() { camera.close(); }
    }

    private final class Presentation extends Fixture {
        final ReplayPresentationTrack presentation;
        final Object capture;
        final MethodHandle end, collecting, time, main, off;
        final ItemStack held, other;
        final int shape;
        Presentation(int shape) throws Exception {
            super(ReplayPresentationTrack.create(directory), "queuedBytes");
            presentation = (ReplayPresentationTrack) track; this.shape = shape;
            ReplayPresentationCapture.start(presentation, new ReplayPresentationCapture.Context() {
                public long replayNanos() { return 0; }
                public int worldIndex() { return 0; }
                public int entityId() { return 7; }
                public boolean available() { return true; }
            });
            capture = field(ReplayPresentationCapture.class, "active").get(null);
            Class<?> type = capture.getClass();
            Method method = type.getDeclaredMethod("end"); method.setAccessible(true);
            end = MethodHandles.lookup().unreflect(method).asType(MethodType.methodType(void.class, Object.class));
            collecting = setter(type, "collecting", boolean.class); time = setter(type, "time", int.class);
            main = setter(type, "main", ItemStack.class); off = setter(type, "off", ItemStack.class);
            field(type, "entity").setInt(capture, 7);
            field(type, "flags").setInt(capture, ReplayPresentationTrack.AVAILABLE | ReplayPresentationTrack.HANDS
                    | ReplayPresentationTrack.BOB | ReplayPresentationTrack.SHOW_MAIN | ReplayPresentationTrack.SHOW_OFF);
            for (String name : new String[]{"worldFovSeen", "handFovSeen"}) field(type, name).setBoolean(capture, true);
            for (String name : new String[]{"worldFov", "handFov"}) field(type, name).setDouble(capture, 70);
            for (String name : new String[]{"worldZoom", "handZoom"}) field(type, name).setFloat(capture, 1);
            held = shape == 0 ? ItemStack.EMPTY : new ItemStack(Items.DIAMOND_PICKAXE);
            other = shape == 0 ? ItemStack.EMPTY : new ItemStack(Items.SHIELD);
            if (shape >= 2) {
                byte[] nbt = new byte[1024];
                new java.util.Random(1234).nextBytes(nbt);
                held.getOrCreateTag().putByteArray("benchmark", nbt);
            }
        }
        void offer(int frame) throws Throwable {
            if (shape == 3) held.setDamage(frame & 255);
            time.invokeExact(capture, (int) (frame * 1000L / 60));
            collecting.invokeExact(capture, true);
            main.invokeExact(capture, held); off.invokeExact(capture, other);
            end.invokeExact(capture);
        }
        Path finish() throws Exception { presentation.finish(); return presentation.completedFile(); }
        void verify() {
            JsonObject data = new JsonParser().parse(status()).getAsJsonObject();
            assertEquals("complete", presentation.status());
            assertEquals(OPERATIONS, data.get("frames").getAsLong());
            assertEquals(0, data.get("droppedFrames").getAsLong());
        }
        String status() { return presentation.statusJson(); }
        int simulatedHz() { return 60; }
        public void close() { ReplayPresentationCapture.stop(presentation); presentation.close(); }
    }

    private final class Sounds extends Fixture {
        final ReplayLocalSoundTrack sounds;
        Sounds() throws Exception {
            super(ReplayLocalSoundTrack.create(directory), "queuedBytes");
            sounds = (ReplayLocalSoundTrack) track;
            assertTrue(sounds.context(0, 0, 7, true));
        }
        void offer(int frame) {
            if (!sounds.offer(frame * 250L, 0, 7, ReplayLocalSoundTrack.STEP, 7,
                    "minecraft:block.stone.step", .15F, 1.25F, frame * .5, 64, -90.75))
                throw new AssertionError("Sound event lost");
        }
        Path finish() throws Exception { sounds.finish(OPERATIONS * 250L); return sounds.completedFile(); }
        void verify() {
            JsonObject data = new JsonParser().parse(status()).getAsJsonObject();
            assertEquals("complete", sounds.status());
            assertEquals(OPERATIONS, data.get("events").getAsLong());
            assertEquals(0, data.get("rejected").getAsLong());
        }
        String status() { return sounds.statusJson(); }
        int simulatedHz() { return 4; }
        public void close() { sounds.close(); }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }

    private static MethodHandle setter(Class<?> type, String name, Class<?> value) throws IllegalAccessException, NoSuchFieldException {
        return MethodHandles.lookup().unreflectSetter(field(type, name))
                .asType(MethodType.methodType(void.class, Object.class, value));
    }

    private interface Factory { Fixture create() throws Exception; }
}
