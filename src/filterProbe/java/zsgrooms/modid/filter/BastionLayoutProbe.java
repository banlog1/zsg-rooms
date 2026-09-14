package zsgrooms.modid.filter;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.Structure;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;

final class BastionLayoutProbe {
    static void verifyTemplates(ServerWorld world) {
        for (net.minecraft.util.BlockRotation rotation : net.minecraft.util.BlockRotation.values()) {
            StructurePlacementData placement = new StructurePlacementData().setRotation(rotation);
            Structure gap = world.getServer().getStructureManager().getStructureOrBlank(
                    new Identifier("bastion/hoglin_stable/walls/side_wall_1"));
            Structure triple = world.getServer().getStructureManager().getStructureOrBlank(
                    new Identifier("bastion/hoglin_stable/ramparts/ramparts_1"));
            if (gap.getInfosForBlock(net.minecraft.util.math.BlockPos.ORIGIN, placement, Blocks.GOLD_BLOCK).isEmpty()
                    || triple.getInfosForBlock(net.minecraft.util.math.BlockPos.ORIGIN, placement, Blocks.CHEST).size() != 3) {
                throw new AssertionError("Vanilla stables template contract failed for " + rotation);
            }
        }
    }

    static boolean intactStables(ServerWorld world, StructureStart<?> start) {
        boolean gap = false;
        boolean triple = false;
        ValidationTrace.measure("stables.generate", () -> GeneratedStructureProbe.generate(world, start));
        for (StructurePiece piece : start.getChildren()) {
            if (!(piece instanceof PoolStructurePiece)) continue;
            String name = piece.getTag().getCompound("pool_element").getString("location");
            PoolStructurePiece pool = (PoolStructurePiece) piece;
            if (name.equals("minecraft:bastion/hoglin_stable/walls/side_wall_1")) {
                gap |= ValidationTrace.measure("stables.gold", () -> intact(world, pool, name, Blocks.GOLD_BLOCK, 1));
            }
            if (name.equals("minecraft:bastion/hoglin_stable/ramparts/ramparts_1")) {
                triple |= ValidationTrace.measure("stables.chests", () -> intact(world, pool, name, Blocks.CHEST, 3));
            }
        }
        return gap && triple;
    }

    private static boolean intact(ServerWorld world, PoolStructurePiece piece, String name, Block block, int minimum) {
        Structure structure = world.getServer().getStructureManager().getStructureOrBlank(new Identifier(name));
        java.util.List<Structure.StructureBlockInfo> blocks = structure.getInfosForBlock(piece.getPos(),
                new StructurePlacementData().setRotation(piece.getRotation()), block);
        if (blocks.size() < minimum) throw new IllegalStateException("Bastion template does not match the filter definition");
        for (Structure.StructureBlockInfo info : blocks) {
            if (!world.getBlockState(info.pos).isOf(block)) return false;
            if (block == Blocks.CHEST && !(world.getBlockEntity(info.pos) instanceof ChestBlockEntity)) return false;
        }
        return true;
    }
}
