package zsgrooms.modid.replay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReplayBarterCaptureTest {
    private static final UUID PIGLIN = UUID.fromString("00000000-0000-0000-0000-000000000123");

    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.getGameVersion();
        net.minecraft.Bootstrap.initialize();
    }

    @Test void snapshotsActualCountsAndNbtWithoutSharingOrMutatingLiveStacks() {
        ReplayBarterCapture track = track(0);
        ItemStack pearls = new ItemStack(Items.ENDER_PEARL, 8);
        ItemStack potion = new ItemStack(Items.POTION);
        potion.getOrCreateTag().putString("Potion", "minecraft:fire_resistance");
        ItemStack original = potion.copy();
        ReplayBarterCapture.Outcome outcome = ReplayBarterCapture.snapshot(12, PIGLIN, 1.25, 65, -4.5,
                Arrays.asList(pearls, potion));
        assertTrue(ItemStack.areEqual(original, potion));
        pearls.setCount(1);
        potion.getTag().putString("Potion", "minecraft:water");
        add(track, 5, outcome);
        JsonObject json = parse(track.finish(10));
        assertFalse(json.get("truncated").getAsBoolean());
        JsonObject event = json.getAsJsonArray("events").get(0).getAsJsonObject();
        assertEquals(0, event.get("world").getAsInt());
        assertEquals(PIGLIN.toString(), event.get("piglin").getAsString());
        assertEquals(1.25, event.get("x").getAsDouble());
        assertEquals(8, decode(event.getAsJsonArray("items").get(0).getAsString()).getCount());
        assertTrue(ItemStack.areEqual(original, decode(event.getAsJsonArray("items").get(1).getAsString())));
        assertEquals(1, pearls.getCount());
        assertEquals("minecraft:water", potion.getTag().getString("Potion"));
    }

    @Test void rejectsCallbacksCrossingDimensionResetWorldOrFinishAndCannotCompleteTwice() {
        ReplayBarterCapture track = track(0);
        ReplayBarterCapture.Ticket first = track.reserve(track.binding());
        track.invalidate();
        track.complete(first, 1, empty());
        assertNull(track.reserve(first.binding));
        track.bind(new Object(), new Object(), new Object(), 0, "minecraft:overworld");
        ReplayBarterCapture.Ticket dimension = track.reserve(track.binding());
        track.bind(new Object(), new Object(), new Object(), 1, "minecraft:the_nether");
        track.complete(dimension, 2, empty());
        try (ReplayBarterCapture.Ticket valid = track.reserve(track.binding())) {
            track.complete(valid, 3, empty());
            track.complete(valid, 3, empty());
        }
        JsonArray events = parse(track.finish(3)).getAsJsonArray("events");
        assertEquals(1, events.size());
        assertEquals(1, events.get(0).getAsJsonObject().get("world").getAsInt());
        track.bind(new Object(), new Object(), new Object(), 2, "minecraft:overworld");
        assertNull(track.binding());
        assertNull(track.reserve(first.binding));
    }

    @Test void pendingCallbackBudgetIsGlobalAndReservationsAreReleasedExactlyOnce() {
        ReplayBarterCapture first = track(0), second = track(0);
        List<ReplayBarterCapture.Ticket> tickets = new ArrayList<>();
        try {
            for (int i = 0; i < ReplayBarterCapture.MAX_PENDING; i++) {
                ReplayBarterCapture.Ticket ticket = first.reserve(first.binding());
                assertNotNull(ticket);
                tickets.add(ticket);
            }
            assertNull(second.reserve(second.binding()));
            assertTrue(parse(second.finish(10)).get("truncated").getAsBoolean());
        } finally {
            for (ReplayBarterCapture.Ticket ticket : tickets) { ticket.close(); ticket.close(); }
        }
        ReplayBarterCapture next = track(1);
        try (ReplayBarterCapture.Ticket ticket = next.reserve(next.binding())) { assertNotNull(ticket); }
        assertFalse(parse(next.finish(10)).get("truncated").getAsBoolean());
    }

    @Test void invalidationReleasesDroppedCallbacksAndPreservesPartialHistory() {
        ReplayBarterCapture track = track(0);
        add(track, 1, empty());
        reserveFullBudget(track);
        track.invalidate();
        track.invalidate();
        assertFullBudgetAvailable();
        JsonObject result = parse(track.finish(10));
        assertTrue(result.get("truncated").getAsBoolean());
        assertEquals(1, result.getAsJsonArray("events").size());
    }

    @Test void rebindingReleasesDroppedCallbacksAndAllowsTheNewEpochToContinue() {
        ReplayBarterCapture track = track(0);
        ReplayBarterCapture.Binding old = track.binding();
        reserveFullBudget(track);
        track.bind(new Object(), new Object(), new Object(), 1, "minecraft:overworld");
        assertNull(track.reserve(old));
        assertFullBudgetAvailable();
        add(track, 2, empty());
        JsonObject result = parse(track.finish(10));
        assertTrue(result.get("truncated").getAsBoolean());
        assertEquals(1, result.getAsJsonArray("events").size());
        assertEquals(1, result.getAsJsonArray("events").get(0).getAsJsonObject().get("world").getAsInt());
    }

    @Test void finishReleasesDroppedCallbacksWithoutRelyingOnTheirClose() {
        ReplayBarterCapture track = track(0);
        reserveFullBudget(track);
        String result = track.finish(10);
        assertTrue(parse(result).get("truncated").getAsBoolean());
        assertEquals(0, parse(result).getAsJsonArray("events").size());
        assertNull(track.binding());
        assertEquals(result, track.finish(10));
        assertFullBudgetAvailable();
    }

    @Test void lateCallbackAfterCancellationCannotCommitOrReleaseAnotherSessionsBudget() {
        ReplayBarterCapture old = track(0);
        ReplayBarterCapture.Ticket late = old.reserve(old.binding());
        assertNotNull(late);
        old.invalidate();
        old.bind(new Object(), new Object(), new Object(), 1, "minecraft:overworld");
        ReplayBarterCapture next = track(2), overflow = track(3);
        try {
            reserveFullBudget(next);
            old.complete(late, 1, empty());
            late.close();
            late.close();
            old.invalidate();
            assertNull(overflow.reserve(overflow.binding()), "Late cleanup must not release new reservations");
            JsonObject result = parse(old.finish(10));
            assertTrue(result.get("truncated").getAsBoolean());
            assertEquals(0, result.getAsJsonArray("events").size());
        } finally {
            old.invalidate();
            next.invalidate();
            overflow.invalidate();
        }
        assertFullBudgetAvailable();
    }

    @Test void completedCallbacksAwaitingCloseDoNotMakeHistoryPartialOnFinish() {
        ReplayBarterCapture track = track(0);
        for (ReplayBarterCapture.Ticket ticket : reserveFullBudget(track)) track.complete(ticket, 1, empty());
        JsonObject result = parse(track.finish(10));
        assertFalse(result.get("truncated").getAsBoolean());
        assertEquals(ReplayBarterCapture.MAX_PENDING, result.getAsJsonArray("events").size());
        assertFullBudgetAvailable();
    }

    @Test void eventLimitAndDurationAreExplicitlyTruncatedWithoutBreakingJson() {
        ReplayBarterCapture track = track(0);
        ReplayBarterCapture.Outcome empty = empty();
        for (int i = 0; i < ReplayBarterCapture.MAX_EVENTS; i++) add(track, i, empty);
        assertNull(track.reserve(track.binding()));
        String json = track.finish(100);
        assertTrue(json.getBytes(StandardCharsets.UTF_8).length <= ReplayBarterCapture.MAX_BYTES);
        assertTrue(parse(json).get("truncated").getAsBoolean());
        assertEquals(101, parse(json).getAsJsonArray("events").size());
    }

    @Test void byteBudgetTruncatesAndItemBudgetIsBounded() {
        ReplayBarterCapture track = track(0);
        ItemStack potion = new ItemStack(Items.POTION);
        potion.getOrCreateTag().putString("large", String.join("", Collections.nCopies(7000, "a")));
        ReplayBarterCapture.Outcome big = ReplayBarterCapture.snapshot(1, PIGLIN, 0, 0, 0, Collections.singletonList(potion));
        for (int i = 0; i < 1000; i++) {
            ReplayBarterCapture.Ticket ticket = track.reserve(track.binding());
            if (ticket == null) break;
            try { track.complete(ticket, i, big); } finally { ticket.close(); }
        }
        String json = track.finish(1000);
        assertTrue(json.getBytes(StandardCharsets.UTF_8).length <= ReplayBarterCapture.MAX_BYTES);
        assertTrue(parse(json).get("truncated").getAsBoolean());
        ReplayBarterCapture itemTrack = track(0);
        ReplayBarterCapture.Outcome many = ReplayBarterCapture.snapshot(1, PIGLIN, 0, 0, 0,
                Collections.nCopies(ReplayBarterCapture.MAX_ITEMS, new ItemStack(Items.GRAVEL, 8)));
        for (int i = 0; i <= ReplayBarterCapture.MAX_TOTAL_ITEMS / ReplayBarterCapture.MAX_ITEMS; i++) add(itemTrack, i, many);
        JsonObject result = parse(itemTrack.finish(1000));
        assertTrue(result.get("truncated").getAsBoolean());
        assertEquals(512, result.getAsJsonArray("events").size());
    }

    @Test void oversizedOrCyclicNbtIsRejectedBeforeCopyingAndLateFinishIsTruncated() {
        ItemStack potion = new ItemStack(Items.POTION);
        CompoundTag cyclic = new CompoundTag();
        cyclic.put("cycle", cyclic);
        potion.setTag(cyclic);
        assertThrows(IllegalArgumentException.class, () -> ReplayBarterCapture.snapshot(1, PIGLIN, 0, 0, 0, Collections.singletonList(potion)));
        assertThrows(IllegalArgumentException.class, () -> ReplayBarterCapture.snapshot(1, PIGLIN, Double.NaN, 0, 0, Collections.emptyList()));
        assertThrows(IllegalArgumentException.class, () -> ReplayBarterCapture.snapshot(1, PIGLIN, 0, 0, 0,
                Collections.nCopies(65, ItemStack.EMPTY)));
        ReplayBarterCapture track = track(0);
        try (ReplayBarterCapture.Ticket pending = track.reserve(track.binding())) {
            assertTrue(parse(track.finish(10)).get("truncated").getAsBoolean());
            track.complete(pending, 1, empty());
        }
        assertEquals(0, parse(track.finish(10)).getAsJsonArray("events").size());
    }

    private static ReplayBarterCapture track(int world) {
        ReplayBarterCapture track = new ReplayBarterCapture();
        track.bind(new Object(), new Object(), new Object(), world, "minecraft:the_nether");
        return track;
    }
    private static List<ReplayBarterCapture.Ticket> reserveFullBudget(ReplayBarterCapture track) {
        List<ReplayBarterCapture.Ticket> tickets = new ArrayList<>();
        for (int i = 0; i < ReplayBarterCapture.MAX_PENDING; i++) {
            ReplayBarterCapture.Ticket ticket = track.reserve(track.binding());
            assertNotNull(ticket, "Full global budget must be available, slot " + i);
            tickets.add(ticket);
        }
        return tickets;
    }
    private static void assertFullBudgetAvailable() {
        ReplayBarterCapture next = track(10), overflow = track(11);
        try {
            List<ReplayBarterCapture.Ticket> tickets = reserveFullBudget(next);
            assertNull(overflow.reserve(overflow.binding()), "Budget must not exceed the global limit");
            for (ReplayBarterCapture.Ticket ticket : tickets) {
                next.complete(ticket, 1, empty());
                ticket.close();
            }
            assertFalse(parse(next.finish(10)).get("truncated").getAsBoolean());
        } finally {
            next.invalidate();
            overflow.invalidate();
        }
    }
    private static ReplayBarterCapture.Outcome empty() {
        return ReplayBarterCapture.snapshot(1, PIGLIN, 0, 64, 0, Collections.emptyList());
    }
    private static void add(ReplayBarterCapture track, int time, ReplayBarterCapture.Outcome outcome) {
        try (ReplayBarterCapture.Ticket ticket = track.reserve(track.binding())) {
            assertNotNull(ticket);
            track.complete(ticket, time, outcome);
        }
    }
    private static JsonObject parse(String json) { return new JsonParser().parse(json).getAsJsonObject(); }
    private static ItemStack decode(String payload) {
        PacketByteBuf data = new PacketByteBuf(Unpooled.wrappedBuffer(Base64.getDecoder().decode(payload)));
        try { return data.readItemStack(); } finally { data.release(); }
    }
}
