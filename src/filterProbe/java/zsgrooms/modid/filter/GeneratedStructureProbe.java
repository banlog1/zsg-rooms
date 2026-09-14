package zsgrooms.modid.filter;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.feature.StructureFeature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

final class GeneratedStructureProbe {
    static StructureStart<?> start(ServerWorld world, StructureFeature<?> feature, ChunkPos pos) {
        StructureStart<?> start = world.getStructureAccessor().getStructureStart(ChunkSectionPos.from(pos.x, 0, pos.z),
                feature, world.getChunk(pos.x, pos.z, ChunkStatus.STRUCTURE_STARTS));
        return start != null && start.hasChildren() ? start : null;
    }

    static List<String> templates(StructureStart<?> start) {
        List<String> result = new ArrayList<String>();
        if (start == null) return result;
        for (StructurePiece piece : start.getChildren()) {
            if (piece instanceof PoolStructurePiece) {
                CompoundTag element = piece.getTag().getCompound("pool_element");
                if (element.contains("location", 8)) result.add(element.getString("location"));
            }
        }
        return result;
    }

    static List<WorldChunk> generate(ServerWorld world, StructureStart<?> start) {
        BlockBox box = start.getBoundingBox();
        if (box.maxX - box.minX > 512 || box.maxZ - box.minZ > 512) {
            throw new IllegalStateException("Structure exceeds offline validation bounds");
        }
        for (int x = (box.minX >> 4) - 1; x <= (box.maxX >> 4) + 1; x++) {
            for (int z = (box.minZ >> 4) - 1; z <= (box.maxZ >> 4) + 1; z++) world.getChunk(x, z, ChunkStatus.FULL);
        }
        List<WorldChunk> chunks = new ArrayList<WorldChunk>();
        for (int x = box.minX >> 4; x <= box.maxX >> 4; x++) {
            for (int z = box.minZ >> 4; z <= box.maxZ >> 4; z++) chunks.add(world.getWorldChunk(new BlockPos(x << 4, 0, z << 4)));
        }
        return chunks;
    }

    static final class Loot {
        final Map<Item, Integer> items = new HashMap<Item, Integer>();
        int chests;
        int count(Item item) { return items.getOrDefault(item, 0); }
    }

    static Loot loot(ServerWorld world, StructureStart<?> start, List<WorldChunk> chunks, Predicate<String> tables) {
        Loot result = new Loot();
        for (WorldChunk chunk : chunks) {
            for (BlockPos pos : new ArrayList<BlockPos>(chunk.getBlockEntityPositions())) {
                if (!start.getBoundingBox().contains(pos)) continue;
                BlockEntity entity = world.getBlockEntity(pos);
                if (!(entity instanceof ChestBlockEntity)) continue;
                CompoundTag tag = entity.toTag(new CompoundTag());
                if (!tables.test(tag.getString("LootTable")) || tag.getLong("LootTableSeed") == 0) continue;
                ChestBlockEntity copy = new ChestBlockEntity();
                copy.fromTag(world.getBlockState(pos), tag);
                copy.setLocation(world, pos);
                ValidationTrace.run("loot.resolve", () -> copy.checkLootInteraction(null));
                for (int slot = 0; slot < copy.size(); slot++) {
                    ItemStack stack = copy.getStack(slot);
                    if (!stack.isEmpty()) result.items.merge(stack.getItem(), stack.getCount(), Integer::sum);
                }
                result.chests++;
                if (!tag.equals(entity.toTag(new CompoundTag()))) throw new AssertionError("Copied loot changed a saved chest");
            }
        }
        return result;
    }

    static boolean hasGolem(StructureStart<?> start, List<WorldChunk> chunks) {
        for (WorldChunk chunk : chunks) {
            for (Iterable<Entity> section : chunk.getEntitySectionArray()) {
                for (Entity entity : section) {
                    if (entity instanceof IronGolemEntity && start.getBoundingBox().contains(entity.getBlockPos())) return true;
                }
            }
        }
        return false;
    }
}
