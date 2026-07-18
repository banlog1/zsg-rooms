package zsgrooms.modid.net;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

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

    private static final class FakeRequester implements HostSeedPrefetchManager.SeedRequester {
        private final List<CompletableFuture<String>> requests = new ArrayList<CompletableFuture<String>>();

        @Override
        public CompletableFuture<String> request(String roomName, String seedSpecification) {
            CompletableFuture<String> request = new CompletableFuture<String>();
            this.requests.add(request);
            return request;
        }
    }
}
