package zsgrooms.modid.net;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class RaceSeedPreparationTest {
    @Test void preparationKeepsOrderSharesRequestsAndRetainsPartialResultsAfterFailure() {
        List<CompletableFuture<String>> requests = new ArrayList<>();
        HostSeedPrefetchManager manager = new HostSeedPrefetchManager((room, filter) -> {
            CompletableFuture<String> future = new CompletableFuture<>();
            requests.add(future);
            return future;
        });
        RaceSeedPreparation preparation = new RaceSeedPreparation(manager);
        CompletableFuture<List<String>> first = preparation.prepare("room", "zsg", 3);
        assertSame(first, preparation.prepare("room", "zsg", 3));
        requests.get(0).complete("one");
        requests.get(1).completeExceptionally(new IllegalStateException("Temporary failure"));
        assertTrue(first.isCompletedExceptionally());
        CompletableFuture<List<String>> retry = preparation.prepare("room", "zsg", 3);
        requests.get(2).complete("two");
        requests.get(3).complete("three");
        assertEquals(Arrays.asList("one", "two", "three"), retry.join());
        assertEquals(retry.join(), preparation.prepare("room", "zsg", 3).join());
        preparation.clear();
        CompletableFuture<List<String>> next = preparation.prepare("room", "zsg", 1);
        requests.get(4).complete("four");
        assertEquals(Arrays.asList("four"), next.join());
    }

    @Test void changingSelectionCancelsOldPreparationWithoutLeakingItsSeeds() {
        List<CompletableFuture<String>> requests = new ArrayList<>();
        HostSeedPrefetchManager manager = new HostSeedPrefetchManager((room, filter) -> {
            CompletableFuture<String> future = new CompletableFuture<>();
            requests.add(future);
            return future;
        });
        RaceSeedPreparation preparation = new RaceSeedPreparation(manager);
        CompletableFuture<List<String>> stale = preparation.prepare("room", "zsg", 2);
        CompletableFuture<List<String>> next = preparation.prepare("room", "rpseedbank", 1);
        requests.get(0).complete("old");
        requests.get(1).complete("new");
        assertTrue(stale.isCompletedExceptionally());
        assertEquals(Arrays.asList("new"), next.join());
    }
}
