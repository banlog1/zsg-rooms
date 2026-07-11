package zsgrooms.modid;

import net.minecraft.client.MinecraftClient;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ZsgSeedBridge {
    public static final String[] FSG_FILTER_IDS = new String[]{
            "zsg",
            "zsgop",
            "zsgvillage",
            "zsgvillageop",
            "zsgshipwreck",
            "zsgshipwreckop",
            "zsgtemple",
            "zsgtempleop",
            "zsgjungletemple",
            "zsgjungletempleop",
            "rpseedbank"
    };

    private static final Map<String, String> FSG_FILTER_LABELS = new LinkedHashMap<String, String>();
    private static String lastSeedSource = "unknown";
    private static final String LOG_FILE = "zsg-rooms-seed-detection.log";
    private static final ScheduledExecutorService SEED_TIMEOUTS = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ZSG Room Seed Timeout");
        thread.setDaemon(true);
        return thread;
    });
    private static final ExecutorService SEED_REQUESTS = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ZSG Room FSG Request");
        thread.setDaemon(true);
        return thread;
    });

    static {
        FSG_FILTER_LABELS.put("zsg", "ZSG Mapless");
        FSG_FILTER_LABELS.put("zsgop", "ZSG Mapless (OP)");
        FSG_FILTER_LABELS.put("zsgvillage", "ZSG Village");
        FSG_FILTER_LABELS.put("zsgvillageop", "ZSG Village (OP)");
        FSG_FILTER_LABELS.put("zsgshipwreck", "ZSG Shipwreck");
        FSG_FILTER_LABELS.put("zsgshipwreckop", "ZSG Shipwreck (OP)");
        FSG_FILTER_LABELS.put("zsgtemple", "ZSG Desert Temple");
        FSG_FILTER_LABELS.put("zsgtempleop", "ZSG Desert Temple (OP)");
        FSG_FILTER_LABELS.put("zsgjungletemple", "ZSG Jungle Temple");
        FSG_FILTER_LABELS.put("zsgjungletempleop", "ZSG Jungle Temple (OP)");
        FSG_FILTER_LABELS.put("rpseedbank", "Ruined Portal Seedbank");

        try {
            Files.deleteIfExists(Paths.get(LOG_FILE));
        } catch (IOException ignored) {}
    }

    public static String getLastSeedSource() {
        return lastSeedSource;
    }

    private static void logToFile(String message) {
        try (FileWriter fw = new FileWriter(LOG_FILE, true)) {
            fw.write(message + "\n");
            fw.flush();
        } catch (IOException ignored) {}
    }
    public static String normalizeSeed(String seed) {
        if (seed == null || seed.trim().isEmpty()) {
            return "zsg-room-" + System.nanoTime();
        }
        return seed.trim();
    }

    public static String buildSeedForStructure(String seed, String structureType, int requiredIron) {
        String normalized = normalizeSeed(seed);
        String structure = normalizeSeedType(structureType);
        int ironCount = Math.max(1, requiredIron);
        return normalized + "|structure:" + structure + "|iron:" + ironCount;
    }

    public static String normalizeSeedType(String seedType) {
        if (seedType == null || seedType.trim().isEmpty()) {
            return "zsg";
        }
        String normalized = seedType.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("manual:")) {
            return "manual";
        }
        if ("filtered".equals(normalized) || "fsg-filter".equals(normalized) || "fsg".equals(normalized)) {
            return "zsg";
        }
        if ("rsg".equals(normalized)) {
            return "random";
        }
        if ("room-code".equals(normalized) || "roomname".equals(normalized)) {
            return "room";
        }
        if (FSG_FILTER_LABELS.containsKey(normalized) || "random".equals(normalized) || "room".equals(normalized) || "manual".equals(normalized)) {
            return normalized;
        }
        for (Map.Entry<String, String> entry : FSG_FILTER_LABELS.entrySet()) {
            if (entry.getValue().toLowerCase(Locale.ROOT).equals(normalized)) {
                return entry.getKey();
            }
        }
        return normalized;
    }

    public static String seedTypeLabel(String seedType) {
        String normalized = normalizeSeedType(seedType);
        if (FSG_FILTER_LABELS.containsKey(normalized)) {
            return FSG_FILTER_LABELS.get(normalized);
        }
        if ("random".equals(normalized)) {
            return "Random Seed";
        }
        if ("room".equals(normalized)) {
            return "Room Code";
        }
        if ("manual".equals(normalized)) {
            return "Manual Seed";
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    public static boolean isFsgFilterSeedType(String seedType) {
        return FSG_FILTER_LABELS.containsKey(normalizeSeedType(seedType));
    }

    public static String extractMinecraftSeed(String seed) {
        if (seed == null) {
            return "";
        }
        int metadataStart = seed.indexOf("|structure:");
        if (metadataStart < 0) {
            return seed.trim();
        }
        return seed.substring(0, metadataStart).trim();
    }

    public static String resolveStructure(String seed) {
        if (seed == null) {
            return "generic";
        }
        String[] parts = seed.split("\\|");
        for (String part : parts) {
            if (part.startsWith("structure:")) {
                return part.substring("structure:".length());
            }
        }
        return "generic";
    }

    public static int resolveRequiredIron(String seed) {
        if (seed == null) {
            return 4;
        }
        String[] parts = seed.split("\\|");
        for (String part : parts) {
            if (part.startsWith("iron:")) {
                try {
                    return Integer.parseInt(part.substring("iron:".length()));
                } catch (NumberFormatException ignored) {
                    return 4;
                }
            }
        }
        return 4;
    }

    public static String fetchSeedForRoom(String roomName, String structureType) {
        String seedType = normalizeSeedType(structureType);
        logToFile("=== ZSG-Rooms Seed Detection ===");
        logToFile("Room: " + roomName + ", Seed Type: " + seedTypeLabel(seedType));
        
        String systemSeed = System.getProperty("zsgrooms.seed");
        if (systemSeed != null && !systemSeed.trim().isEmpty()) {
            lastSeedSource = "system-property";
            logToFile("FOUND: System property (zsgrooms.seed) = " + systemSeed);
            return buildSeedForStructure(systemSeed, seedType, 4);
        }
        logToFile("SKIP: System property not set");

        String envSeed = System.getenv("ZSG_SEED");
        if (envSeed != null && !envSeed.trim().isEmpty()) {
            lastSeedSource = "environment-variable";
            logToFile("FOUND: Environment variable (ZSG_SEED) = " + envSeed);
            return buildSeedForStructure(envSeed, seedType, 4);
        }
        logToFile("SKIP: Environment variable not set");

        String manualSeed = extractManualSeed(structureType);
        if ("manual".equals(seedType) && manualSeed != null && !manualSeed.trim().isEmpty()) {
            lastSeedSource = "manual";
            logToFile("FOUND: Manual seed = " + manualSeed);
            return buildSeedForStructure(manualSeed, seedType, 4);
        }

        if ("random".equals(seedType)) {
            String randomSeed = String.valueOf(new Random().nextLong());
            lastSeedSource = "local-random";
            logToFile("FOUND: Local random seed = " + randomSeed);
            return buildSeedForStructure(randomSeed, seedType, 4);
        }

        if ("room".equals(seedType)) {
            String roomSeed = roomName == null || roomName.trim().isEmpty() ? "zsg-room" : roomName.trim();
            lastSeedSource = "room-code";
            logToFile("FOUND: Room code seed = " + roomSeed);
            return buildSeedForStructure(roomSeed, seedType, 4);
        }

        if (isFsgFilterSeedType(seedType)) {
            lastSeedSource = "fsg-filter-pending";
            logToFile("PENDING: FSG filter will be requested by Atum/FSG when the race starts: " + seedTypeLabel(seedType));
            return buildSeedForStructure("pending-" + seedType, seedType, 4);
        }

        if (isSpeedrunApiAvailable()) {
            logToFile("FOUND: SpeedrunAPI mod is loaded; requesting seed through FSG provider stack");
        } else {
            logToFile("SKIP: SpeedrunAPI classes not found");
        }

        String fsgSeed = tryFsgSeed();
        if (fsgSeed != null && !fsgSeed.trim().isEmpty()) {
            lastSeedSource = isSpeedrunApiAvailable() ? "fsg-mod-speedrunapi" : "fsg-mod";
            logToFile("FOUND: FSG-Mod seed = " + fsgSeed);
            return buildSeedForStructure(fsgSeed, seedType, 4);
        }
        logToFile("SKIP: FSG-Mod not detected");

        lastSeedSource = "fallback-room-name";
        logToFile("FALLBACK: Using room name = " + roomName);
        String fallback = roomName == null || roomName.trim().isEmpty() ? "zsg-room" : roomName.trim();
        return buildSeedForStructure(fallback, seedType, 4);
    }

    public static CompletableFuture<String> requestExactSeedForRoom(String roomName, String structureType) {
        String seedType = normalizeSeedType(structureType);
        if (!isFsgFilterSeedType(seedType)) {
            return CompletableFuture.completedFuture(fetchSeedForRoom(roomName, structureType));
        }

        CompletableFuture<String> exactSeed = new CompletableFuture<String>();
        ScheduledFuture<?> timeout = SEED_TIMEOUTS.schedule(
                () -> exactSeed.completeExceptionally(new IllegalStateException("FSG seed request timed out")),
                75,
                TimeUnit.SECONDS
        );
        exactSeed.whenComplete((seed, error) -> timeout.cancel(false));
        try {
            configureFsgFilter(seedType);
            beginRoomSeedRequest();
            Class<?> seedManagerClass = Class.forName("me.duncanruns.fsgmod.SeedManager");
            Method runFilter = seedManagerClass.getMethod("runFilter");
            SEED_REQUESTS.execute(() -> {
                try {
                    String seed = requestFsgSeedWithRetries(runFilter);
                    lastSeedSource = isSpeedrunApiAvailable() ? "fsg-mod-speedrunapi" : "fsg-mod";
                    String prepared = buildSeedForStructure(seed.trim(), seedType, 4);
                    logToFile("FOUND: Exact room seed from FSG = " + seed.trim());
                    exactSeed.complete(prepared);
                } catch (Exception error) {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    logToFile("FSG seed request failed: " + cause.getMessage());
                    exactSeed.completeExceptionally(cause);
                }
            });
            logToFile("REQUESTED: Exact FSG seed for " + seedTypeLabel(seedType));
        } catch (Exception exception) {
            logToFile("Could not request exact FSG seed: " + exception.getClass().getSimpleName() + " - " + exception.getMessage());
            exactSeed.completeExceptionally(exception);
        }
        return exactSeed;
    }

    private static String requestFsgSeedWithRetries(Method runFilter) throws Exception {
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                Object filterResult = runFilter.invoke(null);
                String seed = extractSeedFromFilterResult(filterResult);
                if (seed != null && !seed.trim().isEmpty()) {
                    return seed.trim();
                }
                lastFailure = new IllegalStateException("FSG returned an empty seed");
            } catch (Exception exception) {
                lastFailure = exception;
            }

            logToFile("FSG seed attempt " + attempt + " failed; retrying");
            if (attempt < 3) {
                Thread.sleep(750L * attempt);
            }
        }
        throw lastFailure == null ? new IllegalStateException("FSG returned an empty seed") : lastFailure;
    }

    public static boolean launchFsgFilterWithAtum(String seedType) {
        String filterId = normalizeSeedType(seedType);
        if (!isFsgFilterSeedType(filterId)) {
            return false;
        }

        try {
            configureFsgFilter(filterId);
            closeDebugOverlay();
            Class<?> atumClass = Class.forName("me.voidxwalker.autoreset.Atum");
            atumClass.getMethod("createNewWorld").invoke(null);
            lastSeedSource = isSpeedrunApiAvailable() ? "fsg-mod-speedrunapi" : "fsg-mod";
            logToFile("Launched Atum world creation with FSG filter: " + seedTypeLabel(filterId) + " (" + filterId + ")");
            return true;
        } catch (Exception e) {
            logToFile("Failed to launch FSG filter with Atum: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return false;
        }
    }

    private static void configureFsgFilter(String filterId) throws Exception {
        Class<?> configClass = Class.forName("me.duncanruns.fsgmod.FSGModConfig");
        Object config = configClass.getMethod("getInstance").invoke(null);
        if (config == null) {
            configClass.getMethod("tryLoad").invoke(null);
            config = configClass.getMethod("getInstance").invoke(null);
        }
        if (config == null) {
            throw new IllegalStateException("FSG config is not available");
        }

        Set<String> selectedFilters = new HashSet<String>(Arrays.asList(filterId));
        Field selectedOnlineFilters = configClass.getField("selectedOnlineFilters");
        selectedOnlineFilters.set(config, selectedFilters);

        Field selectedOnlineFilterName = configClass.getField("selectedOnlineFilterName");
        selectedOnlineFilterName.set(config, seedTypeLabel(filterId));

        Field practiceMode = configClass.getField("practiceMode");
        practiceMode.setBoolean(config, true);

        try {
            configClass.getMethod("trySave").invoke(null);
        } catch (Exception e) {
            logToFile("Could not save FSG filter selection: " + e.getMessage());
        }
    }

    private static String extractManualSeed(String seedType) {
        if (seedType == null) {
            return null;
        }
        String trimmed = seedType.trim();
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith("manual:")) {
            return null;
        }
        return trimmed.substring("manual:".length()).trim();
    }

    public static boolean launchSeedWithAtum(String seed) {
        String minecraftSeed = extractMinecraftSeed(seed);
        if (minecraftSeed.isEmpty()) {
            logToFile("Cannot launch Atum world: seed is empty");
            return false;
        }

        try {
            closeDebugOverlay();
            Class<?> atumClass = Class.forName("me.voidxwalker.autoreset.Atum");
            Class<?> seedProviderClass = Class.forName("me.voidxwalker.autoreset.api.seedprovider.SeedProvider");
            Method getSeedProvider = atumClass.getMethod("getSeedProvider");
            Object previousProvider = getSeedProvider.invoke(null);
            Field seedProviderField = atumClass.getDeclaredField("seedProvider");
            seedProviderField.setAccessible(true);

            Object roomProvider = Proxy.newProxyInstance(
                    seedProviderClass.getClassLoader(),
                    new Class<?>[]{seedProviderClass},
                    new OneShotSeedProvider(seedProviderField, previousProvider, minecraftSeed)
            );

            seedProviderField.set(null, roomProvider);
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.world != null) {
                beginRoomSeedRequest();
                RoomResetAuthorization.allowNextReset();
                try {
                    atumClass.getMethod("scheduleReset").invoke(null);
                } catch (Exception exception) {
                    RoomResetAuthorization.clear();
                    throw exception;
                }
                logToFile("Scheduled an in-world Atum reset with room seed: " + minecraftSeed);
            } else {
                atumClass.getMethod("createNewWorld").invoke(null);
                logToFile("Launched Atum world creation with room seed: " + minecraftSeed);
            }
            return true;
        } catch (Exception e) {
            logToFile("Failed to launch Atum world: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return false;
        }
    }

    public static void releaseRoomControl() {
        try {
            Class<?> atumClass = Class.forName("me.voidxwalker.autoreset.Atum");
            atumClass.getMethod("stopRunning").invoke(null);
            logToFile("Released room control of Atum reset flow");
        } catch (Exception exception) {
            logToFile("Could not release Atum room control: " + exception.getMessage());
        }
    }

    private static void beginRoomSeedRequest() throws Exception {
        Class<?> atumClass = Class.forName("me.voidxwalker.autoreset.Atum");
        Field runningField = atumClass.getDeclaredField("running");
        runningField.setAccessible(true);
        runningField.setBoolean(null, true);

        Field shouldResetField = atumClass.getDeclaredField("shouldReset");
        shouldResetField.setAccessible(true);
        shouldResetField.setBoolean(null, false);
        logToFile("Initialized Atum room state before the first FSG request");
    }

    private static void closeDebugOverlay() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) {
            return;
        }
        client.options.debugEnabled = false;
        client.options.debugProfilerEnabled = false;
        client.options.debugTpsEnabled = false;
        logToFile("Closed the debug overlay before room world generation");
    }

    private static boolean isSpeedrunApiAvailable() {
        try {
            Class.forName("me.contaria.speedrunapi.SpeedrunAPI");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static String tryFsgSeed() {
        logToFile("  Attempting FSG-Mod detection...");
        
        try {
            Class<?> seedManagerClass = Class.forName("me.duncanruns.fsgmod.SeedManager");

            try {
                Method requestSeed = seedManagerClass.getMethod("requestSeed", boolean.class, CompletableFuture.class);
                CompletableFuture<String> future = new CompletableFuture<String>();
                logToFile("    Calling SeedManager.requestSeed(mainThread=true, future)");
                requestSeed.invoke(null, true, future);
                String result = future.get(30, TimeUnit.SECONDS);
                if (result != null && !result.trim().isEmpty()) {
                    return result.trim();
                }
            } catch (NoSuchMethodException e) {
                logToFile("    requestSeed(boolean, CompletableFuture) not available");
            } catch (Exception e) {
                logToFile("    requestSeed failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }

            try {
                Method runFilter = seedManagerClass.getMethod("runFilter");
                logToFile("    Calling SeedManager.runFilter() fallback");
                Object filterResult = runFilter.invoke(null);
                String result = extractSeedFromFilterResult(filterResult);
                if (result != null && !result.trim().isEmpty()) {
                    return result.trim();
                }
            } catch (Exception e) {
                logToFile("    runFilter fallback failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
        } catch (ClassNotFoundException e) {
            logToFile("    SeedManager class not found");
        } catch (Exception e) {
            logToFile("    Error accessing SeedManager: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        
        logToFile("  FSG-Mod detection complete: NOT FOUND");
        return null;
    }

    private static String extractSeedFromFilterResult(Object filterResult) {
        if (filterResult == null) {
            return null;
        }
        try {
            Field seedField = filterResult.getClass().getField("seed");
            Object seed = seedField.get(filterResult);
            return seed == null ? null : seed.toString();
        } catch (Exception e) {
            logToFile("    Could not read FSGFilterResult.seed: " + e.getMessage());
        }
        return null;
    }

    private static class OneShotSeedProvider implements InvocationHandler {
        private final Field seedProviderField;
        private final Object previousProvider;
        private final String seed;
        private boolean used;

        private OneShotSeedProvider(Field seedProviderField, Object previousProvider, String seed) {
            this.seedProviderField = seedProviderField;
            this.previousProvider = previousProvider;
            this.seed = seed;
            this.used = false;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if ("requestSeed".equals(methodName)) {
                if (!used) {
                    used = true;
                    seedProviderField.set(null, previousProvider);
                    return CompletableFuture.completedFuture(seed);
                }
                return method.invoke(previousProvider, args);
            }
            if ("shouldShowSeed".equals(methodName)) {
                return Boolean.TRUE;
            }
            if ("getWaitingScreen".equals(methodName)) {
                return Optional.empty();
            }
            if ("onFail".equals(methodName)) {
                if (previousProvider != null) {
                    return method.invoke(previousProvider, args);
                }
                return null;
            }
            return method.invoke(previousProvider, args);
        }
    }
}
