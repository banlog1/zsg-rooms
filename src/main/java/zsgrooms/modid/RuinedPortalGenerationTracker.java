package zsgrooms.modid;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ServerWorldAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class RuinedPortalGenerationTracker {
    private static final int MAX_PORTALS_PER_WORLD = 128;
    private static final Identifier RUINED_PORTAL_LOOT = new Identifier("minecraft", "chests/ruined_portal");
    private static final Map<ServerWorld, List<GeneratedPortal>> GENERATED_PORTALS =
            new WeakHashMap<ServerWorld, List<GeneratedPortal>>();

    private RuinedPortalGenerationTracker() {
    }

    public static synchronized void markGenerated(ServerWorldAccess worldAccess, BlockBox bounds) {
        if (worldAccess == null || bounds == null || !(worldAccess.getWorld() instanceof ServerWorld)) {
            return;
        }
        ServerWorld world = (ServerWorld) worldAccess.getWorld();
        List<GeneratedPortal> portals = GENERATED_PORTALS.get(world);
        if (portals == null) {
            portals = new ArrayList<GeneratedPortal>();
            GENERATED_PORTALS.put(world, portals);
        }
        BlockBox generated = new BlockBox(bounds);
        for (GeneratedPortal existing : portals) {
            if (sameBounds(existing.bounds, generated)) {
                return;
            }
        }
        if (portals.size() >= MAX_PORTALS_PER_WORLD) {
            portals.remove(0);
        }
        portals.add(new GeneratedPortal(generated));
    }

    public static synchronized void markGeneratedChest(
            ServerWorldAccess worldAccess, BlockBox bounds, BlockPos chestPos) {
        if (worldAccess == null || bounds == null || chestPos == null
                || !(worldAccess.getWorld() instanceof ServerWorld)) {
            return;
        }
        ServerWorld world = (ServerWorld) worldAccess.getWorld();
        BlockEntity blockEntity = worldAccess.getBlockEntity(chestPos);
        if (!(blockEntity instanceof ChestBlockEntity)) {
            return;
        }
        CompoundTag tag = blockEntity.toTag(new CompoundTag());
        if (!RUINED_PORTAL_LOOT.toString().equals(tag.getString("LootTable"))) {
            return;
        }
        long lootSeed = tag.getLong("LootTableSeed");
        List<GeneratedPortal> portals = GENERATED_PORTALS.get(world);
        if (portals == null) {
            return;
        }
        for (GeneratedPortal portal : portals) {
            if (!sameBounds(portal.bounds, bounds)) {
                continue;
            }
            BlockPos captured = chestPos.toImmutable();
            boolean alreadyCaptured = false;
            for (GeneratedChest chest : portal.chests) {
                if (chest.pos.equals(captured)) {
                    alreadyCaptured = true;
                    break;
                }
            }
            if (!alreadyCaptured) {
                portal.chests.add(new GeneratedChest(captured, lootSeed));
            }
            return;
        }
    }

    public static synchronized boolean wasGenerated(ServerWorld world, BlockBox bounds) {
        if (world == null || bounds == null) {
            return false;
        }
        List<GeneratedPortal> portals = GENERATED_PORTALS.get(world);
        if (portals == null) {
            return false;
        }
        for (GeneratedPortal generated : portals) {
            if (generated.bounds.intersects(bounds)) {
                return true;
            }
        }
        return false;
    }

    public static synchronized BlockPos findGeneratedChest(ServerWorld world, BlockBox bounds) {
        if (world == null || bounds == null) {
            return null;
        }
        List<GeneratedPortal> portals = GENERATED_PORTALS.get(world);
        if (portals == null) {
            return null;
        }
        BlockPos closest = null;
        long closestDistanceSquared = Long.MAX_VALUE;
        int centerX = bounds.minX + (bounds.maxX - bounds.minX) / 2;
        int centerZ = bounds.minZ + (bounds.maxZ - bounds.minZ) / 2;
        for (GeneratedPortal portal : portals) {
            if (!portal.bounds.intersects(bounds)) {
                continue;
            }
            for (GeneratedChest chest : portal.chests) {
                if (!isInside(bounds, chest.pos) || !hasSeedbankLoot(world, chest)) {
                    continue;
                }
                long x = (long) chest.pos.getX() - centerX;
                long z = (long) chest.pos.getZ() - centerZ;
                long distanceSquared = x * x + z * z;
                if (distanceSquared < closestDistanceSquared) {
                    closest = chest.pos;
                    closestDistanceSquared = distanceSquared;
                }
            }
        }
        return closest;
    }

    private static boolean hasSeedbankLoot(ServerWorld world, GeneratedChest chest) {
        if (chest.seedbankLoot != null) {
            return chest.seedbankLoot.booleanValue();
        }
        LootTable lootTable = world.getServer().getLootManager().getTable(RUINED_PORTAL_LOOT);
        LootContext context = new LootContext.Builder(world)
                .parameter(LootContextParameters.POSITION, chest.pos)
                .random(chest.lootSeed)
                .build(LootContextTypes.CHEST);
        for (ItemStack stack : lootTable.generateLoot(context)) {
            if (stack.getItem() == Items.GOLDEN_SWORD
                    && EnchantmentHelper.getLevel(Enchantments.LOOTING, stack) > 0) {
                chest.seedbankLoot = Boolean.TRUE;
                return true;
            }
        }
        chest.seedbankLoot = Boolean.FALSE;
        return false;
    }

    static boolean isInside(BlockBox bounds, BlockPos pos) {
        return bounds != null && pos != null
                && pos.getX() >= bounds.minX && pos.getX() <= bounds.maxX
                && pos.getY() >= bounds.minY && pos.getY() <= bounds.maxY
                && pos.getZ() >= bounds.minZ && pos.getZ() <= bounds.maxZ;
    }

    private static boolean sameBounds(BlockBox first, BlockBox second) {
        return first.minX == second.minX && first.minY == second.minY && first.minZ == second.minZ
                && first.maxX == second.maxX && first.maxY == second.maxY && first.maxZ == second.maxZ;
    }

    private static final class GeneratedPortal {
        private final BlockBox bounds;
        private final List<GeneratedChest> chests = new ArrayList<GeneratedChest>();

        private GeneratedPortal(BlockBox bounds) {
            this.bounds = bounds;
        }
    }

    private static final class GeneratedChest {
        private final BlockPos pos;
        private final long lootSeed;
        private Boolean seedbankLoot;

        private GeneratedChest(BlockPos pos, long lootSeed) {
            this.pos = pos;
            this.lootSeed = lootSeed;
        }
    }
}
