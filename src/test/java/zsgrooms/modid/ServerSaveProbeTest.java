package zsgrooms.modid;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServerSaveProbeTest {
    @Test
    public void stackFilteringRemovesProbeMixinAndReflectionFrames() {
        StackTraceElement[] trace = {
                new StackTraceElement("java.lang.Thread", "getStackTrace", "Thread.java", 1),
                new StackTraceElement(
                        "zsgrooms.modid.mixin.MinecraftServerSaveProbeMixin",
                        "zsgRooms$beginSaveProbe",
                        "MinecraftServerSaveProbeMixin.java",
                        20),
                new StackTraceElement(
                        "java.lang.reflect.Method", "invoke", "Method.java", 498),
                new StackTraceElement(
                        "zsgrooms.modid.ZsgSeedBridge", "launchSeed", "ZsgSeedBridge.java", 412),
                new StackTraceElement(
                        "net.minecraft.server.MinecraftServer", "save", "MinecraftServer.java", 530)
        };

        List<String> filtered = ServerSaveProbe.filterStackTrace(trace, 4);

        assertEquals(2, filtered.size());
        assertTrue(filtered.get(0).contains("ZsgSeedBridge.launchSeed"));
        assertTrue(filtered.get(1).contains("MinecraftServer.save"));
    }

    @Test
    public void stackFilteringKeepsReflectionAsLastResort() {
        StackTraceElement[] trace = {
                new StackTraceElement(
                        "java.lang.reflect.Method", "invoke", "Method.java", 498)
        };

        List<String> filtered = ServerSaveProbe.filterStackTrace(trace, 4);

        assertEquals(1, filtered.size());
        assertTrue(filtered.get(0).contains("java.lang.reflect.Method.invoke"));
    }

    @Test
    public void nanosecondsConvertToFractionalMilliseconds() {
        assertEquals(12.5D, ServerSaveProbe.nanosToMilliseconds(12_500_000L), 0.0001D);
    }

    @Test
    public void slowThresholdIncludesExactlyFiftyMilliseconds() {
        assertFalse(ServerSaveProbe.isSlow(49.999D));
        assertTrue(ServerSaveProbe.isSlow(50.0D));
        assertTrue(ServerSaveProbe.isSlow(50.001D));
    }

    @Test
    public void missingRoomAndGameUseSafeFallbackText() {
        ServerSaveProbe.RoomContext context =
                ServerSaveProbe.safeRoomContext(false, null, false, " ");

        assertFalse(context.roomActive);
        assertFalse(context.raceActive);
        assertEquals("none", context.roomName);
        assertEquals("none", context.seed);
    }

    @Test
    public void nestedTimingEntriesPopInLastInFirstOutOrder() {
        Deque<String> timings = new ArrayDeque<String>();
        timings.push("outer");
        timings.push("inner");

        assertEquals("inner", ServerSaveProbe.popLast(timings));
        assertEquals("outer", ServerSaveProbe.popLast(timings));
        assertNull(ServerSaveProbe.popLast(timings));
    }
}
