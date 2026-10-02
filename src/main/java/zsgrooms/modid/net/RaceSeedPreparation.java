package zsgrooms.modid.net;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Retains a partially prepared sequence across fetch/launch failures. */
final class RaceSeedPreparation {
    private final HostSeedPrefetchManager manager;
    private final List<String> seeds = new ArrayList<>();
    private String key = "";
    private long generation;
    private CompletableFuture<List<String>> pending;

    RaceSeedPreparation() { this(HostSeedPrefetchManager.getInstance()); }
    RaceSeedPreparation(HostSeedPrefetchManager manager) { this.manager = manager; }

    synchronized CompletableFuture<List<String>> prepare(String room, String filter, int count) {
        String nextKey = room + "\n" + filter + "\n" + count;
        if (!key.equals(nextKey)) { clear(); key = nextKey; }
        if (pending != null) return pending;
        CompletableFuture<List<String>> result = new CompletableFuture<>();
        pending = result;
        fetch(room, filter, count, generation, result);
        return result;
    }

    private synchronized void fetch(String room, String filter, int count, long ticket,
                                    CompletableFuture<List<String>> result) {
        if (ticket != generation) { result.completeExceptionally(new IllegalStateException("Selection changed")); return; }
        if (seeds.size() >= count) {
            pending = null;
            result.complete(new ArrayList<>(seeds));
            return;
        }
        manager.consumeOrRequest(room, filter).whenComplete((seed, error) -> {
            synchronized (RaceSeedPreparation.this) {
                if (ticket != generation) { result.completeExceptionally(new IllegalStateException("Selection changed")); return; }
                if (error != null || seed == null || seed.isEmpty()) {
                    pending = null;
                    result.completeExceptionally(error == null ? new IllegalStateException("No seed") : error);
                    return;
                }
                seeds.add(seed);
                manager.onSeedConsumed(room, filter);
                fetch(room, filter, count, ticket, result);
            }
        });
    }

    synchronized void clear() {
        generation++;
        key = "";
        seeds.clear();
        if (pending != null) pending.completeExceptionally(new IllegalStateException("Selection changed"));
        pending = null;
    }
}
