// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BarterStatsTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.getGameVersion();
        net.minecraft.Bootstrap.initialize();
    }

    @Test void aggregatesCompletedTradesWithoutFutureOrResetLeakage() throws Exception {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        BarterHistory history = history(event(100, 0, first, new ItemStack(Items.ENDER_PEARL, 4)),
                event(200, 0, first, new ItemStack(Items.ENDER_PEARL, 3), new ItemStack(Items.OBSIDIAN, 1)),
                event(300, 0, second, new ItemStack(Items.OBSIDIAN, 2)),
                event(400, 1, first, new ItemStack(Items.ENDER_PEARL, 8)));
        BarterStats early = new BarterStats(history, 0, 200);
        assertEquals(2, early.events.size());
        assertEquals(1, early.piglins);
        assertEquals(7, early.items.get(0).count);
        assertEquals(Items.ENDER_PEARL, early.items.get(0).item.getItem());
        BarterStats later = new BarterStats(history, 0, 500);
        assertEquals(3, later.events.size());
        assertEquals(2, later.piglins);
        assertEquals(3, later.items.get(1).count);
        assertEquals(8, new BarterStats(history, 1, 500).items.get(0).count);
        assertTrue(new BarterStats(history, 1, 399).events.isEmpty());
        assertTrue(new BarterStats(null, 0, 1000).events.isEmpty());
    }

    @Test void itemVariantsStaySeparateAndHistoryStacksAreNotMutated() throws Exception {
        ItemStack water = new ItemStack(Items.POTION), fire = new ItemStack(Items.POTION);
        water.getOrCreateTag().putString("Potion", "minecraft:water");
        fire.getOrCreateTag().putString("Potion", "minecraft:fire_resistance");
        BarterHistory history = history(event(10, 0, UUID.randomUUID(), water, fire));
        BarterStats stats = new BarterStats(history, 0, 10);
        assertEquals(2, stats.items.size());
        assertEquals(1, stats.events.size());
        stats.items.get(0).item.setCount(42);
        assertEquals(1, history.events.get(0).items.get(0).getCount());
        assertTrue(BarterStats.matches(water, "MINECRAFT:POTION"));
        assertFalse(BarterStats.matches(water, "ender_pearl"));
    }

    private static BarterHistory history(JsonObject... events) throws Exception {
        JsonObject track = new JsonObject();
        track.addProperty("version", 1); track.addProperty("protocol", 736); track.addProperty("truncated", false);
        JsonArray array = new JsonArray();
        for (JsonObject event : events) array.add(event);
        track.add("events", array);
        return BarterHistory.read(new ByteArrayInputStream(track.toString().getBytes(StandardCharsets.UTF_8)), 1000);
    }

    private static JsonObject event(int time, int world, UUID piglin, ItemStack... items) {
        JsonObject event = new JsonObject();
        event.addProperty("time", time); event.addProperty("world", world);
        event.addProperty("dimension", "minecraft:the_nether"); event.addProperty("piglin", piglin.toString());
        event.addProperty("x", 1); event.addProperty("y", 64); event.addProperty("z", 2);
        JsonArray encoded = new JsonArray();
        for (ItemStack item : items) {
            PacketByteBuf bytes = new PacketByteBuf(Unpooled.buffer());
            try {
                bytes.writeItemStack(item);
                byte[] copy = new byte[bytes.readableBytes()]; bytes.readBytes(copy);
                encoded.add(Base64.getEncoder().encodeToString(copy));
            } finally { bytes.release(); }
        }
        event.add("items", encoded);
        return event;
    }
}
