package zsgrooms.modid.net;

import zsgrooms.modid.ZsgSeedBridge;
import zsgrooms.modid.ZsgRoomsSeedMode;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;

public final class HostSeedPrefetchManager {
    public static final String STATUS_PREPARING = "Preparing next seed...";
    public static final String STATUS_READY = "Next seed ready";
    public static final String STATUS_FAILED = "Seed preparation failed - will retry";

    private static final HostSeedPrefetchManager INSTANCE =
            new HostSeedPrefetchManager(ZsgSeedBridge::requestExactSeedForRoom);

    private final Object lock = new Object();
    private final SeedRequester requester;
    private final IntSupplier filterRoll;
    private long generation;
    private String currentRoom = "";
    private String currentSpecification = "";
    private String resolvedSpecification;
    private CompletableFuture<String> pending;
    private CompletableFuture<String> activeConsumption;
    private String preparedSeed;
    private String status = "";

    HostSeedPrefetchManager(SeedRequester requester) {
        this(requester, () -> ThreadLocalRandom.current().nextInt(100));
    }

    HostSeedPrefetchManager(SeedRequester requester, IntSupplier filterRoll) {
        this.requester = requester;
        this.filterRoll = filterRoll;
    }

    public static HostSeedPrefetchManager getInstance() {
        return INSTANCE;
    }

    public void prefetch(String roomName, String seedSpecification) {
        CompletableFuture<String> stale;
        synchronized (this.lock) {
            SeedKey key = SeedKey.of(roomName, seedSpecification);
            stale = selectLocked(key);
            if (this.preparedSeed == null && this.pending == null && this.activeConsumption == null) {
                startRequestLocked(key);
            }
        }
        detach(stale);
    }

    public CompletableFuture<String> consumeOrRequest(String roomName, String seedSpecification) {
        CompletableFuture<String> stale;
        CompletableFuture<String> result;
        synchronized (this.lock) {
            SeedKey key = SeedKey.of(roomName, seedSpecification);
            stale = selectLocked(key);
            if (this.activeConsumption != null) {
                result = this.activeConsumption;
            } else if (this.preparedSeed != null) {
                String seed = this.preparedSeed;
                this.preparedSeed = null;
                result = CompletableFuture.completedFuture(seed);
                this.activeConsumption = result;
            } else {
                result = this.pending == null ? startRequestLocked(key) : this.pending;
                this.activeConsumption = result.isCompletedExceptionally() ? null : result;
            }
        }
        detach(stale);
        return result;
    }

    public void invalidate() {
        CompletableFuture<String> stale;
        synchronized (this.lock) {
            this.generation++;
            stale = this.pending;
            clearLocked();
        }
        detach(stale);
    }

    public void onSeedConsumed(String roomName, String seedSpecification) {
        synchronized (this.lock) {
            SeedKey key = SeedKey.of(roomName, seedSpecification);
            if (!matchesLocked(key) || this.activeConsumption == null) {
                return;
            }
            this.generation++;
            this.pending = null;
            this.activeConsumption = null;
            this.preparedSeed = null;
            this.resolvedSpecification = null;
            startRequestLocked(key);
        }
    }

    public String getStatus() {
        synchronized (this.lock) {
            return this.status;
        }
    }

    public void onLaunchFailed(String roomName, String seedSpecification) {
        synchronized (this.lock) {
            if (!matchesLocked(SeedKey.of(roomName, seedSpecification))) return;
            if (this.activeConsumption != null && this.activeConsumption.isDone()
                    && !this.activeConsumption.isCompletedExceptionally()) {
                this.preparedSeed = this.activeConsumption.getNow(null);
                this.activeConsumption = null;
                this.status = this.preparedSeed == null ? STATUS_FAILED : STATUS_READY;
            }
        }
    }

    public boolean isCurrentSelection(String roomName, String seedSpecification) {
        synchronized (this.lock) {
            return matchesLocked(SeedKey.of(roomName, seedSpecification));
        }
    }

    private CompletableFuture<String> selectLocked(SeedKey key) {
        if (matchesLocked(key)) {
            return null;
        }
        this.generation++;
        CompletableFuture<String> stale = this.pending;
        clearLocked();
        this.currentRoom = key.roomName;
        this.currentSpecification = key.seedSpecification;
        return stale;
    }

    private CompletableFuture<String> startRequestLocked(SeedKey key) {
        long requestGeneration = ++this.generation;
        CompletableFuture<String> exposed = new CompletableFuture<String>();
        this.pending = exposed;
        this.status = STATUS_PREPARING;

        CompletableFuture<String> source;
        try {
            // Keep the draw private and stable across retries for this slot.
            if (this.resolvedSpecification == null) {
                this.resolvedSpecification = ZsgRoomsSeedMode.SPECIFICATION.equals(key.seedSpecification)
                        ? ZsgRoomsSeedMode.filterForRoll(this.filterRoll.getAsInt()) : key.seedSpecification;
            }
            String resolved = this.resolvedSpecification;
            source = this.requester.request(key.roomName, resolved);
            if (source == null) {
                source = failedFuture(new IllegalStateException("Seed request did not start"));
            } else if (ZsgRoomsSeedMode.SPECIFICATION.equals(key.seedSpecification)) {
                source = source.thenApply(seed -> {
                    if (seed == null || seed.trim().isEmpty()
                            || ZsgSeedBridge.extractMinecraftSeed(seed).startsWith("pending-")
                            || !resolved.equals(ZsgSeedBridge.resolveStructure(seed))) {
                        throw new IllegalStateException("Prepared seed did not match requested filter");
                    }
                    return seed + "|selection:" + ZsgRoomsSeedMode.SPECIFICATION;
                });
            }
        } catch (Throwable error) {
            source = failedFuture(error);
        }
        source.whenComplete((seed, error) -> completeRequest(
                requestGeneration, key, exposed, seed, error));
        return exposed;
    }

    private void completeRequest(long requestGeneration, SeedKey key, CompletableFuture<String> exposed,
            String seed, Throwable error) {
        Throwable completionError = error;
        boolean stale;
        synchronized (this.lock) {
            stale = requestGeneration != this.generation
                    || !matchesLocked(key)
                    || this.pending != exposed;
            if (!stale) {
                this.pending = null;
                if (completionError != null || seed == null || seed.trim().isEmpty()) {
                    if (completionError == null) {
                        completionError = new IllegalStateException("Seed request returned no seed");
                    }
                    if (this.activeConsumption == exposed) {
                        this.activeConsumption = null;
                    }
                    this.preparedSeed = null;
                    this.status = STATUS_FAILED;
                } else {
                    this.preparedSeed = seed;
                    this.status = STATUS_READY;
                }
            }
        }

        if (stale) {
            exposed.completeExceptionally(new StaleSeedRequestException());
        } else if (completionError != null) {
            exposed.completeExceptionally(completionError);
        } else {
            exposed.complete(seed);
        }
    }

    private boolean matchesLocked(SeedKey key) {
        return this.currentRoom.equals(key.roomName)
                && this.currentSpecification.equals(key.seedSpecification);
    }

    private void clearLocked() {
        this.currentRoom = "";
        this.currentSpecification = "";
        this.resolvedSpecification = null;
        this.pending = null;
        this.activeConsumption = null;
        this.preparedSeed = null;
        this.status = "";
    }

    private static void detach(CompletableFuture<String> stale) {
        if (stale != null && !stale.isDone()) {
            stale.completeExceptionally(new StaleSeedRequestException());
        }
    }

    private static CompletableFuture<String> failedFuture(Throwable error) {
        CompletableFuture<String> future = new CompletableFuture<String>();
        future.completeExceptionally(error);
        return future;
    }

    @FunctionalInterface
    interface SeedRequester {
        CompletableFuture<String> request(String roomName, String seedSpecification);
    }

    private static final class SeedKey {
        private final String roomName;
        private final String seedSpecification;

        private SeedKey(String roomName, String seedSpecification) {
            this.roomName = roomName;
            this.seedSpecification = seedSpecification;
        }

        private static SeedKey of(String roomName, String seedSpecification) {
            return new SeedKey(
                    roomName == null ? "" : roomName.trim(),
                    ZsgSeedBridge.normalizeSeedSpecification(seedSpecification));
        }
    }

    public static final class StaleSeedRequestException extends IllegalStateException {
        private StaleSeedRequestException() {
            super("Seed selection changed");
        }
    }
}
