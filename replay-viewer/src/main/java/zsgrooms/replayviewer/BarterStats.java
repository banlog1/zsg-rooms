// SPDX-License-Identifier: GPL-3.0-or-later
package zsgrooms.replayviewer;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.registry.Registry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Snapshot statistics never include future trades or another reset's world. */
final class BarterStats {
    final List<BarterHistory.Event> events = new ArrayList<>();
    final List<Total> items = new ArrayList<>();
    final int piglins;

    BarterStats(BarterHistory history, int world, int time) {
        Set<UUID> contributors = new HashSet<>();
        Map<ItemKey, Total> quantities = new LinkedHashMap<>();
        if (history != null && history.available) for (BarterHistory.Event event : history.events) {
            if (event.time > time) break;
            if (event.world != world) continue;
            events.add(event);
            contributors.add(event.piglin);
            for (ItemStack item : event.items) {
                if (item.isEmpty()) continue;
                // The reader returns private stack copies, keeping NBT keys stable without another copy.
                Total found = quantities.computeIfAbsent(new ItemKey(item), key -> new Total(item));
                found.count += item.getCount();
            }
        }
        piglins = contributors.size();
        items.addAll(quantities.values());
        items.sort(java.util.Comparator.<Total>comparingLong(value -> value.count).reversed()
                .thenComparing(value -> Registry.ITEM.getId(value.item.getItem()).toString()));
    }

    static boolean matches(ItemStack item, String search) {
        String query = search.trim().toLowerCase(Locale.ROOT);
        return query.isEmpty() || item.getName().getString().toLowerCase(Locale.ROOT).contains(query)
                || Registry.ITEM.getId(item.getItem()).toString().contains(query);
    }

    static final class Total {
        final ItemStack item;
        long count;
        Total(ItemStack item) { this.item = item.copy(); }
    }

    private static final class ItemKey {
        private final Item item;
        private final CompoundTag tag;
        private final int hash;
        ItemKey(ItemStack stack) {
            item = stack.getItem(); tag = stack.getTag();
            hash = 31 * System.identityHashCode(item) + Objects.hashCode(tag);
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof ItemKey)) return false;
            ItemKey key = (ItemKey) other;
            return item == key.item && Objects.equals(tag, key.tag);
        }
    }
}
