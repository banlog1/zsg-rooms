package zsgrooms.modid.replay;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ReplayFinalizationTest {
    private static final UUID PLAYER = new UUID(1, 2);

    @Test void manifestCaptureReturnsDuringSerializationAndRepeatedFinishesStayOrdered() throws Exception {
        ReplayRaceManifest expected = manifest();
        String firstJson = expected.finish(1000, true);
        String secondJson = expected.finish(500, false);
        ReplayRaceManifest actual = manifest();
        // Pause Gson's list traversal, without adding any test hooks to production capture.
        PausedIteration<?> pause = pauseIteration(field(actual, "data"), "timingSamples");
        ExecutorService workers = Executors.newFixedThreadPool(3);
        try {
            Future<String> first = workers.submit(() -> actual.finish(1000, true));
            assertTrue(pause.entered.await(5, TimeUnit.SECONDS));
            workers.submit(() -> {
                actual.closeInterval(50);
                actual.recordLoading(50, true);
                actual.recordScreen(50, 2);
                actual.allowTemplePrediction(null);
                actual.recordStewOrder(Collections.singletonList("late"));
                actual.recordChest(50, 99, 99, 99, "late", 0, 0, 0, 0);
                actual.recordChestLoot(50, 99, 99, loot());
                actual.startRace("late", PLAYER, 50);
                actual.startRace("later", PLAYER, 50, "late", 99);
                actual.finishRace("second", 50, 50);
                actual.openInterval(50, 99, 99, "late");
                actual.recordTiming(50, 99, 99, 99, 99);
                assertFalse(actual.needsTiming(2000, 99, 99));
            }).get(5, TimeUnit.SECONDS);
            CountDownLatch secondStarted = new CountDownLatch(1);
            Future<String> second = workers.submit(() -> {
                secondStarted.countDown();
                return actual.finish(500, false);
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(100, TimeUnit.MILLISECONDS));
            pause.release.countDown();
            assertEquals(firstJson, first.get(5, TimeUnit.SECONDS));
            assertEquals(secondJson, second.get(5, TimeUnit.SECONDS));
            assertEquals(secondJson, actual.finish(500, false));
        } finally {
            pause.release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void hudCaptureReturnsDuringSerializationAndOnlyFirstFinishContainsFrames() throws Exception {
        ReplayHudTrack actual = new ReplayHudTrack();
        actual.add(0, 0, 1, new byte[]{1, 2, 3});
        actual.add(200, 0, 1, new byte[]{4, 5, 6});
        ReplayHudTrack expected = new ReplayHudTrack();
        expected.add(0, 0, 1, new byte[]{1, 2, 3});
        expected.add(200, 0, 1, new byte[]{4, 5, 6});
        byte[] firstBytes = expected.finish(200);
        byte[] secondBytes = expected.finish(1000);
        PausedIteration<?> pause = pauseIteration(actual, "frames");
        ExecutorService workers = Executors.newFixedThreadPool(3);
        try {
            Future<byte[]> first = workers.submit(() -> actual.finish(200));
            assertTrue(pause.entered.await(5, TimeUnit.SECONDS));
            workers.submit(() -> {
                assertFalse(actual.due(400, 0, 1));
                actual.add(400, 0, 1, new byte[]{7, 8, 9});
            }).get(5, TimeUnit.SECONDS);
            CountDownLatch secondStarted = new CountDownLatch(1);
            Future<byte[]> second = workers.submit(() -> {
                secondStarted.countDown();
                return actual.finish(1000);
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(100, TimeUnit.MILLISECONDS));
            pause.release.countDown();
            assertArrayEquals(firstBytes, first.get(5, TimeUnit.SECONDS));
            assertArrayEquals(secondBytes, second.get(5, TimeUnit.SECONDS));
            assertArrayEquals(secondBytes, actual.finish(1000));
        } finally {
            pause.release.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test void hudBytesMatchStreamFormatAtDurationBoundaries() throws Exception {
        for (long duration : new long[]{-1, 0, 199, 200, 5000, Long.MAX_VALUE}) {
            ReplayHudTrack track = new ReplayHudTrack();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream expected = new DataOutputStream(bytes);
            expected.writeInt(ReplayHudTrack.MAGIC);
            expected.writeInt(1);
            Random random = new Random(928);
            for (int i = 0; i < 100; i++) {
                byte[] payload = new byte[i % 9 == 0 ? 0 : i * 3];
                random.nextBytes(payload);
                int time = i * 200, world = i / 10, entity = i % 3 == 0 ? -1 : i;
                track.add(time, world, entity, payload);
                if (time <= duration) writeFrame(expected, time, world, entity, payload);
            }
            assertArrayEquals(bytes.toByteArray(), track.finish(duration), "duration=" + duration);
        }
    }

    @Test void hudAtByteLimitRetainsAllAcceptedFrames() throws Exception {
        ReplayHudTrack track = new ReplayHudTrack();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream expected = new DataOutputStream(bytes);
        expected.writeInt(ReplayHudTrack.MAGIC);
        expected.writeInt(1);
        byte[] payload = new byte[ReplayHudTrack.MAX_FRAME];
        new Random(928).nextBytes(payload);
        int count = (ReplayHudTrack.MAX_BYTES - 8) / (16 + payload.length);
        for (int i = 0; i <= count; i++) {
            track.add(i * 200L, i, i, payload);
            if (i < count) writeFrame(expected, i * 200, i, i, payload);
        }
        assertFalse(track.due((count + 1) * 200L, 0, 0));
        assertArrayEquals(bytes.toByteArray(), track.finish(Long.MAX_VALUE));
    }

    static void writeFrame(DataOutputStream output, int time, int world, int entity, byte[] payload)
            throws Exception {
        output.writeInt(time);
        output.writeInt(world);
        output.writeInt(entity);
        output.writeInt(payload.length);
        output.write(payload);
    }

    private static ReplayRaceManifest manifest() {
        ReplayRaceManifest result = new ReplayRaceManifest(new UUID(3, 4), PLAYER, "Recorder", 0);
        result.startRace("first", PLAYER, 10000000);
        result.finishRace("first", 100, 90);
        result.startRace("second", PLAYER, 100000000, "group", 1);
        result.openInterval(20, 0, 1, "minecraft:overworld");
        result.closeInterval(500);
        result.openInterval(600, 1, 2, "minecraft:the_nether");
        result.recordTiming(10, 0, 0, 1, 1);
        result.recordTiming(900, 1, 1, 800, 700);
        result.recordTiming(1500, 2, 2, -1, -1);
        result.recordScreen(100, 1);
        result.recordLoading(200, true);
        result.recordLoading(300, false);
        result.recordScreen(600, 2);
        result.recordLoading(800, true);
        result.recordScreen(950, 1);
        result.allowTemplePrediction(123L);
        result.recordStewOrder(Collections.singletonList("minecraft:saturation"));
        for (int time : new int[]{100, 900, 1500}) {
            result.recordChest(time, 1, 0, 2, "minecraft:overworld", 3, 4, 5, 6);
            result.recordChestLoot(time, 1, 0, loot());
        }
        return result;
    }

    private static List<ReplayChestLootPacket.Loot> loot() {
        return Collections.singletonList(new ReplayChestLootPacket.Loot(
                123, 456, "minecraft:the_nether", "minecraft:chests/bastion_other", Long.MIN_VALUE));
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static PausedIteration<?> pauseIteration(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        PausedIteration<?> paused = new PausedIteration<>((List<?>) field.get(owner));
        field.set(owner, paused);
        return paused;
    }

    private static final class PausedIteration<E> extends ArrayList<E> {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final AtomicBoolean first = new AtomicBoolean(true);

        PausedIteration(List<E> source) { super(source); }

        @Override public Iterator<E> iterator() {
            if (first.getAndSet(false)) {
                entered.countDown();
                try {
                    if (!release.await(15, TimeUnit.SECONDS)) throw new AssertionError("Finish was not released");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }
            return super.iterator();
        }
    }
}
