package zsgrooms.modid;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Temporary, debug-only timing diagnostics for integrated-server saves.
 */
public final class ServerSaveProbe {
    private static final double SLOW_SAVE_THRESHOLD_MS = 50.0D;
    private static final int MAX_CALLER_FRAMES = 8;
    private static final String PREFIX = "[ZSG-Rooms/SaveProbe]";
    private static final ThreadLocal<Deque<ServerTiming>> SERVER_TIMINGS =
            new ThreadLocal<Deque<ServerTiming>>() {
                @Override
                protected Deque<ServerTiming> initialValue() {
                    return new ArrayDeque<ServerTiming>();
                }
            };
    private static final ThreadLocal<Deque<DimensionTiming>> DIMENSION_TIMINGS =
            new ThreadLocal<Deque<DimensionTiming>>() {
                @Override
                protected Deque<DimensionTiming> initialValue() {
                    return new ArrayDeque<DimensionTiming>();
                }
            };

    private ServerSaveProbe() {
    }

    public static void beginServerSave(
            MinecraftServer server,
            boolean suppressLogs,
            boolean flush,
            boolean force
    ) {
        if (!SeedDebugLog.isEnabled()) {
            return;
        }

        try {
            String serverName = safeServerName(server);
            RoomContext room = captureRoomContext();
            String caller = formatCaller(filterStackTrace(
                    Thread.currentThread().getStackTrace(), MAX_CALLER_FRAMES));
            ServerTiming timing = new ServerTiming(
                    System.nanoTime(), serverName, room, suppressLogs, flush, force, caller);
            SERVER_TIMINGS.get().push(timing);

            SeedDebugLog.info(
                    "{} BEGIN\n"
                            + "server={}\n"
                            + "thread={}\n"
                            + "suppressLogs={}\n"
                            + "flush={}\n"
                            + "force={}\n"
                            + "roomActive={}\n"
                            + "room={}\n"
                            + "raceActive={}\n"
                            + "seed={}\n"
                            + "caller={}",
                    PREFIX,
                    serverName,
                    Thread.currentThread().getName(),
                    suppressLogs,
                    flush,
                    force,
                    room.roomActive,
                    room.roomName,
                    room.raceActive,
                    room.seed,
                    caller);
        } catch (Throwable ignored) {
            // Diagnostics must never interfere with a save.
        }
    }

    public static void endServerSave() {
        try {
            Deque<ServerTiming> timings = SERVER_TIMINGS.get();
            ServerTiming timing = popLast(timings);
            if (timing == null) {
                removeIfEmpty(timings, SERVER_TIMINGS);
                return;
            }

            double elapsedMs = nanosToMilliseconds(System.nanoTime() - timing.startedAtNanos);
            String slowestDimension = timing.slowestDimension == null ? "none" : timing.slowestDimension;
            String slowestDimensionMs = timing.slowestDimension == null
                    ? "none"
                    : formatMilliseconds(timing.slowestDimensionMs);
            SeedDebugLog.info(
                    "{} END durationMs={} server={} thread={}",
                    PREFIX,
                    formatMilliseconds(elapsedMs),
                    timing.serverName,
                    Thread.currentThread().getName());
            if (isSlow(elapsedMs)) {
                SeedDebugLog.warn(
                        "{} SLOW_SAVE durationMs={} thresholdMs={} slowestDimension={} slowestDimensionMs={}",
                        PREFIX,
                        formatMilliseconds(elapsedMs),
                        formatMilliseconds(SLOW_SAVE_THRESHOLD_MS),
                        slowestDimension,
                        slowestDimensionMs);
            }
            removeIfEmpty(timings, SERVER_TIMINGS);
        } catch (Throwable ignored) {
            // Diagnostics must never interfere with a save.
        }
    }

    public static void beginDimensionSave(
            ServerWorld world,
            boolean flush,
            boolean savingDisabled
    ) {
        if (!SeedDebugLog.isEnabled()) {
            return;
        }

        try {
            String dimension = safeDimensionName(world);
            DIMENSION_TIMINGS.get().push(
                    new DimensionTiming(System.nanoTime(), dimension, flush, savingDisabled));
            SeedDebugLog.info(
                    "{} DIMENSION_BEGIN dimension={} flush={} savingDisabled={} thread={}",
                    PREFIX,
                    dimension,
                    flush,
                    savingDisabled,
                    Thread.currentThread().getName());
        } catch (Throwable ignored) {
            // Diagnostics must never interfere with a save.
        }
    }

    public static void endDimensionSave() {
        try {
            Deque<DimensionTiming> timings = DIMENSION_TIMINGS.get();
            DimensionTiming timing = popLast(timings);
            if (timing == null) {
                removeIfEmpty(timings, DIMENSION_TIMINGS);
                return;
            }

            double elapsedMs = nanosToMilliseconds(System.nanoTime() - timing.startedAtNanos);
            Deque<ServerTiming> serverTimings = SERVER_TIMINGS.get();
            ServerTiming serverTiming = serverTimings.peek();
            if (serverTiming != null) {
                serverTiming.recordDimension(timing.dimension, elapsedMs);
            }
            SeedDebugLog.info(
                    "{} DIMENSION_END dimension={} durationMs={} flush={} savingDisabled={} thread={}",
                    PREFIX,
                    timing.dimension,
                    formatMilliseconds(elapsedMs),
                    timing.flush,
                    timing.savingDisabled,
                    Thread.currentThread().getName());
            removeIfEmpty(timings, DIMENSION_TIMINGS);
            removeIfEmpty(serverTimings, SERVER_TIMINGS);
        } catch (Throwable ignored) {
            // Diagnostics must never interfere with a save.
        }
    }

    static List<String> filterStackTrace(StackTraceElement[] stackTrace, int maximumFrames) {
        List<String> result = new ArrayList<String>();
        List<String> reflectionFallback = new ArrayList<String>();
        if (stackTrace == null || maximumFrames <= 0) {
            return result;
        }

        for (StackTraceElement frame : stackTrace) {
            if (frame == null) {
                continue;
            }
            String className = frame.getClassName();
            if (isReflectionFrame(className)) {
                if (reflectionFallback.size() < maximumFrames) {
                    reflectionFallback.add(formatFrame(frame));
                }
                continue;
            }
            if (isInternalFrame(className)) {
                continue;
            }
            result.add(formatFrame(frame));
            if (result.size() >= maximumFrames) {
                break;
            }
        }
        return result.isEmpty() ? reflectionFallback : result;
    }

    static double nanosToMilliseconds(long nanoseconds) {
        return nanoseconds / 1_000_000.0D;
    }

    static boolean isSlow(double elapsedMilliseconds) {
        return elapsedMilliseconds >= SLOW_SAVE_THRESHOLD_MS;
    }

    static RoomContext safeRoomContext(
            boolean roomActive,
            String roomName,
            boolean raceActive,
            String seed
    ) {
        return new RoomContext(
                roomActive,
                fallback(roomName),
                raceActive,
                fallback(seed));
    }

    static <T> T popLast(Deque<T> stack) {
        return stack == null || stack.isEmpty() ? null : stack.pop();
    }

    private static RoomContext captureRoomContext() {
        try {
            Room room = ZsgRooms.getActiveRoom();
            String roomName = ZsgRooms.getActiveRoomName();
            InGame game = roomName == null ? null : ZsgRooms.getGame(roomName);
            boolean raceActive = game != null && game.getIsInGame();
            String seed = game == null ? null : game.getSeed();
            return safeRoomContext(room != null, roomName, raceActive, seed);
        } catch (Throwable ignored) {
            return safeRoomContext(false, null, false, null);
        }
    }

    private static String safeServerName(MinecraftServer server) {
        try {
            if (server != null && server.getSaveProperties() != null) {
                return fallback(server.getSaveProperties().getLevelName());
            }
        } catch (Throwable ignored) {
            // Fall through to a stable diagnostic value.
        }
        return "unknown";
    }

    private static String safeDimensionName(ServerWorld world) {
        try {
            if (world != null && world.getRegistryKey() != null
                    && world.getRegistryKey().getValue() != null) {
                return world.getRegistryKey().getValue().toString();
            }
        } catch (Throwable ignored) {
            // Fall through to a stable diagnostic value.
        }
        return "unknown";
    }

    private static boolean isInternalFrame(String className) {
        return className == null
                || className.equals(Thread.class.getName())
                || className.equals(ServerSaveProbe.class.getName())
                || className.equals("zsgrooms.modid.mixin.MinecraftServerSaveProbeMixin")
                || className.equals("zsgrooms.modid.mixin.ServerWorldSaveProbeMixin")
                || className.startsWith("org.spongepowered.asm.mixin.");
    }

    private static boolean isReflectionFrame(String className) {
        return className != null
                && (className.startsWith("java.lang.reflect.")
                || className.startsWith("sun.reflect.")
                || className.startsWith("jdk.internal.reflect."));
    }

    private static String formatFrame(StackTraceElement frame) {
        String location = frame.getFileName() == null ? "Unknown Source" : frame.getFileName();
        if (frame.getLineNumber() >= 0) {
            location += ":" + frame.getLineNumber();
        }
        return frame.getClassName() + "." + frame.getMethodName() + "(" + location + ")";
    }

    private static String formatCaller(List<String> callerFrames) {
        if (callerFrames.isEmpty()) {
            return "unavailable";
        }
        StringBuilder result = new StringBuilder();
        for (String frame : callerFrames) {
            if (result.length() > 0) {
                result.append(" <- ");
            }
            result.append(frame);
        }
        return result.toString();
    }

    private static String formatMilliseconds(double milliseconds) {
        return String.format(Locale.ROOT, "%.2f", milliseconds);
    }

    private static String fallback(String value) {
        return value == null || value.trim().isEmpty() ? "none" : value;
    }

    private static <T> void removeIfEmpty(Deque<T> stack, ThreadLocal<Deque<T>> owner) {
        if (stack != null && stack.isEmpty()) {
            owner.remove();
        }
    }

    static final class RoomContext {
        final boolean roomActive;
        final String roomName;
        final boolean raceActive;
        final String seed;

        private RoomContext(boolean roomActive, String roomName, boolean raceActive, String seed) {
            this.roomActive = roomActive;
            this.roomName = roomName;
            this.raceActive = raceActive;
            this.seed = seed;
        }
    }

    private static final class ServerTiming {
        private final long startedAtNanos;
        private final String serverName;
        private final RoomContext room;
        private final boolean suppressLogs;
        private final boolean flush;
        private final boolean force;
        private final String caller;
        private String slowestDimension;
        private double slowestDimensionMs = -1.0D;

        private ServerTiming(
                long startedAtNanos,
                String serverName,
                RoomContext room,
                boolean suppressLogs,
                boolean flush,
                boolean force,
                String caller
        ) {
            this.startedAtNanos = startedAtNanos;
            this.serverName = serverName;
            this.room = room;
            this.suppressLogs = suppressLogs;
            this.flush = flush;
            this.force = force;
            this.caller = caller;
        }

        private void recordDimension(String dimension, double elapsedMs) {
            if (elapsedMs > this.slowestDimensionMs) {
                this.slowestDimension = dimension;
                this.slowestDimensionMs = elapsedMs;
            }
        }
    }

    private static final class DimensionTiming {
        private final long startedAtNanos;
        private final String dimension;
        private final boolean flush;
        private final boolean savingDisabled;

        private DimensionTiming(
                long startedAtNanos,
                String dimension,
                boolean flush,
                boolean savingDisabled
        ) {
            this.startedAtNanos = startedAtNanos;
            this.dimension = dimension;
            this.flush = flush;
            this.savingDisabled = savingDisabled;
        }
    }
}
