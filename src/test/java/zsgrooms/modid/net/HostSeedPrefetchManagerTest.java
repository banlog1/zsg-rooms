package zsgrooms.modid.net;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HostSeedPrefetchManagerTest {
    @Test
    public void matchingPrefetchIsReusedAndConsumptionRefillsTheSingleSlot() {
        FakeRequester requester = new FakeRequester();
        HostSeedPrefetchManager manager = new HostSeedPrefetchManager(requester);

        manager.prefetch("room", "zsg");
        manager.prefetch("room", "ZSG Mapless");
        assertEquals(1, requester.requests.size());
        assertEquals(HostSeedPrefetchManager.STATUS_PREPARING, manager.getStatus());

        requester.requests.get(0).complete("first|structure:zsg|iron:4");
        assertEquals(HostSeedPrefetchManager.STATUS_READY, manager.getStatus());
        assertEquals("first|structure:zsg|iron:4", manager.consumeOrRequest("room", "zsg").join());

        manager.onSeedConsumed("room", "zsg");
        assertEquals(2, requester.requests.size());
        assertEquals(HostSeedPrefetchManager.STATUS_PREPARING, manager.getStatus());
    }

    @Test
    public void duplicateConsumersShareOnePendingRequest() {
        FakeRequester requester = new FakeRequester();
        HostSeedPrefetchManager manager = new HostSeedPrefetchManager(requester);

        CompletableFuture<String> first = manager.consumeOrRequest("room", "zsg");
        CompletableFuture<String> second = manager.consumeOrRequest("room", "zsg");

        assertSame(first, second);
        assertEquals(1, requester.requests.size());
    }

    @Test
    public void filterChangeDetachesOldRequestAndIgnoresItsCompletion() {
        FakeRequester requester = new FakeRequester();
        HostSeedPrefetchManager manager = new HostSeedPrefetchManager(requester);

        CompletableFuture<String> oldConsumption = manager.consumeOrRequest("room", "zsg");
        manager.prefetch("room", "rpseedbank");

        assertEquals(2, requester.requests.size());
        assertThrows(CompletionException.class, oldConsumption::join);
        requester.requests.get(0).complete("stale|structure:zsg|iron:4");
        assertEquals(HostSeedPrefetchManager.STATUS_PREPARING, manager.getStatus());

        requester.requests.get(1).complete("current|structure:rpseedbank|iron:4");
        assertEquals("current|structure:rpseedbank|iron:4",
                manager.consumeOrRequest("room", "rpseedbank").join());
    }

    @Test
    public void failedRequestRetriesOnlyWhenAnotherSeedIsNeeded() {
        FakeRequester requester = new FakeRequester();
        HostSeedPrefetchManager manager = new HostSeedPrefetchManager(requester);

        manager.prefetch("room", "zsg");
        requester.requests.get(0).completeExceptionally(new IllegalStateException("offline"));
        assertEquals(HostSeedPrefetchManager.STATUS_FAILED, manager.getStatus());
        assertEquals(1, requester.requests.size());

        CompletableFuture<String> retry = manager.consumeOrRequest("room", "zsg");
        assertEquals(2, requester.requests.size());
        requester.requests.get(1).complete("retry|structure:zsg|iron:4");
        assertEquals("retry|structure:zsg|iron:4", retry.join());
    }

    @Test
    public void exactManualSpecificationsDoNotSharePrefetchState() {
        FakeRequester requester = new FakeRequester();
        HostSeedPrefetchManager manager = new HostSeedPrefetchManager(requester);

        manager.prefetch("room", "manual:123");
        assertTrue(manager.isCurrentSelection("room", "manual:123"));
        assertFalse(manager.isCurrentSelection("room", "manual:456"));
        manager.prefetch("room", "manual:456");
        assertEquals(2, requester.requests.size());
    }

    @Test
    public void publicStatusNeverContainsPreparedSeedValue() {
        FakeRequester requester = new FakeRequester();
        HostSeedPrefetchManager manager = new HostSeedPrefetchManager(requester);

        manager.prefetch("room", "zsg");
        requester.requests.get(0).complete("987654321|structure:zsg|iron:4");

        assertEquals(HostSeedPrefetchManager.STATUS_READY, manager.getStatus());
        assertFalse(manager.getStatus().contains("987654321"));
    }

    @Test
    public void bankAliasesReusePrivatePrefetchAndChangingBankDiscardsTheOldResult() {
        FakeRequester requester = new FakeRequester();
        HostSeedPrefetchManager manager = new HostSeedPrefetchManager(requester);
        manager.prefetch("room", "rooms-temple-v5");
        manager.prefetch("room", "ZSG Rooms Desert Temple");
        assertEquals(1, requester.requests.size());
        manager.prefetch("room", "rooms-village-v5");
        requester.requests.get(0).complete("111|structure:rooms-temple-v5|iron:4");
        assertEquals(HostSeedPrefetchManager.STATUS_PREPARING, manager.getStatus());
        requester.requests.get(1).complete("222|structure:rooms-village-v5|iron:4");
        assertEquals(HostSeedPrefetchManager.STATUS_READY, manager.getStatus());
        assertFalse(manager.getStatus().contains("222"));
        assertEquals("222|structure:rooms-village-v5|iron:4", manager.consumeOrRequest("room", "rooms-village-v5").join());
        manager.onSeedConsumed("room", "rooms-village-v5");
        assertEquals(3, requester.requests.size());
    }

    @Test
    public void mixedDrawIsSharedByPrefetchVotesAndConsumersThenRefilledOnce() {
        FakeRequester requester = new FakeRequester();
        AtomicInteger draws = new AtomicInteger();
        HostSeedPrefetchManager manager = new HostSeedPrefetchManager(requester, () -> draws.getAndIncrement() == 0 ? 0 : 65);
        manager.prefetch("room", "rooms-mix");
        manager.prefetch("room", "ZSG Rooms Mode");
        CompletableFuture<String> launch = manager.consumeOrRequest("room", "rooms-mix");
        assertSame(launch, manager.consumeOrRequest("room", "rooms-mix"));
        assertEquals(1, draws.get());
        assertEquals("rooms-temple-v5", requester.specifications.get(0));
        requester.requests.get(0).complete("111|structure:rooms-temple-v5|iron:4");
        assertEquals("111|structure:rooms-temple-v5|iron:4|selection:rooms-mix", launch.join());
        manager.prefetch("room", "rooms-mix");
        assertEquals(1, requester.requests.size());
        manager.onSeedConsumed("room", "rooms-mix");
        manager.onSeedConsumed("room", "rooms-mix");
        assertEquals(2, draws.get());
        assertEquals("rooms-ruined-portal-v5", requester.specifications.get(1));
        assertEquals(HostSeedPrefetchManager.STATUS_PREPARING, manager.getStatus());
    }

    @Test
    public void requestAndLaunchFailuresKeepTheSameDraw() {
        FakeRequester requester = new FakeRequester();
        AtomicInteger draws = new AtomicInteger();
        HostSeedPrefetchManager manager = new HostSeedPrefetchManager(requester, () -> { draws.incrementAndGet(); return 95; });
        manager.prefetch("room", "rooms-mix");
        requester.requests.get(0).completeExceptionally(new IllegalStateException("offline"));
        CompletableFuture<String> retry = manager.consumeOrRequest("room", "rooms-mix");
        assertEquals(1, draws.get());
        assertEquals("rooms-buried-treasure-v5", requester.specifications.get(1));
        requester.requests.get(1).complete("222|structure:rooms-buried-treasure-v5|iron:4");
        String seed = retry.join();
        manager.onLaunchFailed("room", "rooms-mix");
        assertEquals(seed, manager.consumeOrRequest("room", "rooms-mix").join());
        assertEquals(2, requester.requests.size());
        assertEquals(1, draws.get());
        assertFalse(manager.getStatus().contains("222"));
    }

    @Test
    public void synchronousFailureCanRetryWithoutBeingStuckOnAFailedConsumption() {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger draws = new AtomicInteger();
        HostSeedPrefetchManager manager = new HostSeedPrefetchManager((room, specification) -> {
            if (calls.getAndIncrement() == 0) throw new IllegalStateException("unavailable");
            return CompletableFuture.completedFuture("123|structure:" + specification + "|iron:4");
        }, () -> { draws.incrementAndGet(); return 20; });
        assertThrows(CompletionException.class, () -> manager.consumeOrRequest("room", "rooms-mix").join());
        assertTrue(manager.consumeOrRequest("room", "rooms-mix").join().contains("structure:rooms-village-v5"));
        assertEquals(1, draws.get());
    }

    @Test
    public void changingSelectionRejectsStaleMixedResultsAndExplicitFiltersDoNotDraw() {
        FakeRequester requester = new FakeRequester();
        AtomicInteger draws = new AtomicInteger();
        HostSeedPrefetchManager manager = new HostSeedPrefetchManager(requester, () -> { draws.incrementAndGet(); return 0; });
        CompletableFuture<String> stale = manager.consumeOrRequest("room", "rooms-mix");
        manager.prefetch("room", "zsg");
        requester.requests.get(0).complete("111|structure:rooms-temple-v5|iron:4");
        assertThrows(CompletionException.class, stale::join);
        requester.requests.get(1).complete("222|structure:zsg|iron:4");
        assertEquals("222|structure:zsg|iron:4", manager.consumeOrRequest("room", "zsg").join());
        assertEquals(1, draws.get());
        manager.invalidate();
        manager.prefetch("room", "rooms-mix");
        assertEquals(2, draws.get());
    }

    @Test
    public void consecutiveIdenticalDrawsAreAllowedAndWrongFilterResponsesAreRejected() {
        FakeRequester requester = new FakeRequester();
        HostSeedPrefetchManager manager = new HostSeedPrefetchManager(requester, () -> 45);
        CompletableFuture<String> bad = manager.consumeOrRequest("room", "rooms-mix");
        requester.requests.get(0).complete("123|structure:zsg|iron:4");
        assertThrows(CompletionException.class, bad::join);
        CompletableFuture<String> good = manager.consumeOrRequest("room", "rooms-mix");
        requester.requests.get(1).complete("456|structure:rooms-shipwreck-v5|iron:4");
        good.join();
        manager.onSeedConsumed("room", "rooms-mix");
        assertEquals(requester.specifications.get(1), requester.specifications.get(2));
    }

    private static final class FakeRequester implements HostSeedPrefetchManager.SeedRequester {
        private final List<CompletableFuture<String>> requests = new ArrayList<CompletableFuture<String>>();
        private final List<String> specifications = new ArrayList<String>();

        @Override
        public CompletableFuture<String> request(String roomName, String seedSpecification) {
            CompletableFuture<String> request = new CompletableFuture<String>();
            this.requests.add(request);
            this.specifications.add(seedSpecification);
            return request;
        }
    }
}
