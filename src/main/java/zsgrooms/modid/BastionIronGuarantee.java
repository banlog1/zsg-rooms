package zsgrooms.modid;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class BastionIronGuarantee {
    static final int MINIMUM_IRON_UNITS = 27;
    private static final Set<Identifier> BASTION_LOOT_TABLES = new HashSet<Identifier>(Arrays.asList(
            new Identifier("minecraft", "chests/bastion_bridge"),
            new Identifier("minecraft", "chests/bastion_other"),
            new Identifier("minecraft", "chests/bastion_hoglin_stable"),
            new Identifier("minecraft", "chests/bastion_treasure")
    ));

    private static boolean enabled;
    private static boolean firstChestHandled;

    private BastionIronGuarantee() {
    }

    public static synchronized void configure(boolean shouldEnable) {
        enabled = shouldEnable;
        firstChestHandled = false;
    }

    public static synchronized boolean shouldInspect(Identifier lootTable) {
        return enabled && !firstChestHandled && lootTable != null && BASTION_LOOT_TABLES.contains(lootTable);
    }

    public static synchronized void topUpFirstChest(Inventory inventory, Identifier lootTable) {
        topUpFirstChest(inventory, lootTable, 0L);
    }

    public static synchronized void topUpFirstChest(Inventory inventory, Identifier lootTable, long lootSeed) {
        if (!shouldInspect(lootTable) || inventory == null) {
            return;
        }

        int currentIron = countIronUnits(inventory);
        int missingIron = missingIronUnits(currentIron);
        if (missingIron == 0) {
            firstChestHandled = true;
            ZsgRooms.LOGGER.info("First bastion chest already contains at least three iron ingots");
            return;
        }

        Item topUpItem = missingIron % 9 == 0 ? Items.IRON_INGOT : Items.IRON_NUGGET;
        int topUpCount = topUpItem == Items.IRON_INGOT ? missingIron / 9 : missingIron;
        if (availableCapacity(inventory, topUpItem) < topUpCount) {
            ZsgRooms.LOGGER.warn("Could not add the exact missing iron to a full bastion chest");
            return;
        }

        addItemsNaturally(inventory, topUpItem, topUpCount,
                new Random(lootSeed ^ ((long) lootTable.hashCode() << 32) ^ 0x5a534749524f4eL));
        inventory.markDirty();
        firstChestHandled = true;
        ZsgRooms.LOGGER.info("Added exactly {} {} to the first bastion chest ({} -> {} iron units)",
                topUpCount, topUpItem == Items.IRON_INGOT ? "iron ingots" : "iron nuggets",
                currentIron, MINIMUM_IRON_UNITS);
    }

    static int missingIronUnits(int currentIronUnits) {
        return Math.max(0, MINIMUM_IRON_UNITS - Math.max(0, currentIronUnits));
    }

    static int countIronUnits(Inventory inventory) {
        int total = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.getItem() == Items.IRON_INGOT) {
                total += stack.getCount() * 9;
            } else if (stack.getItem() == Items.IRON_NUGGET) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int availableCapacity(Inventory inventory, Item item) {
        int capacity = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) {
                capacity += item.getMaxCount();
            } else if (stack.getItem() == item) {
                capacity += Math.max(0, stack.getMaxCount() - stack.getCount());
            }
        }
        return capacity;
    }

    private static void addItemsNaturally(Inventory inventory, Item item, int amount, Random random) {
        List<Integer> emptySlots = new ArrayList<Integer>();
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isEmpty()) {
                emptySlots.add(slot);
            }
        }
        Collections.shuffle(emptySlots, random);

        int desiredStacks = item == Items.IRON_NUGGET && amount > 8 ? Math.min(3, amount) : 1;
        int stackCount = Math.min(desiredStacks, emptySlots.size());
        int remaining = amount;
        for (int index = 0; index < stackCount; index++) {
            int stacksLeft = stackCount - index;
            int added = (remaining + stacksLeft - 1) / stacksLeft;
            inventory.setStack(emptySlots.get(index), new ItemStack(item, added));
            remaining -= added;
        }

        if (remaining > 0) {
            mergeItems(inventory, item, remaining);
        }
    }

    private static void mergeItems(Inventory inventory, Item item, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.getItem() != item) {
                continue;
            }
            int added = Math.min(remaining, stack.getMaxCount() - stack.getCount());
            if (added > 0) {
                stack.increment(added);
                remaining -= added;
            }
        }
    }
}
