package zsgrooms.modid.filter;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTables;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.DesertTempleGenerator;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.ChunkStatus;

/** Reads actual generated temple loot without opening or rewriting the saved chests. */
final class TempleLootProbe {
    int chests;
    int iron;
    int diamonds;

    static TempleLootProbe read(ServerWorld world, StructureStart<?> start) {
        if (!world.getServer().isOnThread()) throw new IllegalStateException("Temple probe requires the server thread");
        TempleLootProbe result = new TempleLootProbe();
        if (start == null || !start.hasChildren()) return result;
        if (start.getChildren().size() != 1 || !(start.getChildren().get(0) instanceof DesertTempleGenerator)) {
            throw new IllegalStateException("Unexpected temple piece layout");
        }
        StructurePiece piece = start.getChildren().get(0);
        BlockBox box = piece.getBoundingBox();
        if (box.maxX - box.minX != 20 || box.maxZ - box.minZ != 20) {
            throw new IllegalStateException("Unexpected temple dimensions");
        }
        final BlockBox generationBox = box;
        ValidationTrace.run("temple.generate", () -> {
            for (int x = (generationBox.minX >> 4) - 1; x <= (generationBox.maxX >> 4) + 1; x++) {
                for (int z = (generationBox.minZ >> 4) - 1; z <= (generationBox.maxZ >> 4) + 1; z++) {
                    world.getChunk(x, z, ChunkStatus.FULL);
                }
            }
        });
        // The four positions form a rotation-invariant cross in the 21x21 piece, below its declared box.
        box = piece.getBoundingBox();
        BlockPos center = new BlockPos(box.minX + 10, box.minY - 11, box.minZ + 10);
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos position = center.offset(direction, 2);
            BlockEntity entity = world.getBlockEntity(position);
            if (!(entity instanceof ChestBlockEntity)) continue;
            CompoundTag tag = entity.toTag(new CompoundTag());
            if (!LootTables.DESERT_PYRAMID_CHEST.toString().equals(tag.getString("LootTable"))) continue;
            // Vanilla treats a zero loot seed as unseeded; never promise deterministic loot for that case.
            if (tag.getLong("LootTableSeed") == 0L) continue;
            ChestBlockEntity copy = new ChestBlockEntity();
            copy.fromTag(world.getBlockState(position), tag);
            copy.setLocation(world, position);
            ValidationTrace.run("loot.resolve", () -> copy.checkLootInteraction(null));
            result.chests++;
            for (int slot = 0; slot < copy.size(); slot++) {
                ItemStack stack = copy.getStack(slot);
                if (stack.getItem() == Items.IRON_INGOT) result.iron += stack.getCount();
                else if (stack.getItem() == Items.DIAMOND) result.diamonds += stack.getCount();
            }
            if (!tag.equals(entity.toTag(new CompoundTag()))) {
                throw new AssertionError("Temple loot probe changed the saved chest");
            }
        }
        return result;
    }
}
