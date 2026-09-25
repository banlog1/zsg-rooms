package zsgrooms.modid;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

final class RuinedPortalChestClearance {
    private RuinedPortalChestClearance() { }

    /** Null means unsafe: preflight the entire pocket before changing any blocks. */
    static List<BlockPos> plan(BlockPos chest, Direction facing, Function<BlockPos, BlockState> states,
                               Predicate<BlockPos> loaded, Predicate<BlockPos> blockEntity) {
        List<BlockPos> pocket = new ArrayList<BlockPos>(5);
        pocket.add(chest.up());
        for (int distance = 1; distance <= 2; distance++) {
            pocket.add(chest.offset(facing, distance));
            pocket.add(chest.offset(facing, distance).up());
        }
        List<BlockPos> remove = new ArrayList<BlockPos>(5);
        for (BlockPos pos : pocket) {
            if (!loaded.test(pos)) return null;
            BlockState state = states.apply(pos);
            if (state.isAir()) continue;
            if (!state.getFluidState().isEmpty() || blockEntity.test(pos) || !isTerrain(state.getBlock())) return null;
            for (Direction direction : Direction.values()) {
                BlockPos adjacent = pos.offset(direction);
                if (!loaded.test(adjacent) || !states.apply(adjacent).getFluidState().isEmpty()) return null;
            }
            if (!pocket.contains(pos.up()) && states.apply(pos.up()).getBlock() instanceof FallingBlock) return null;
            remove.add(pos);
        }
        return remove;
    }

    private static boolean isTerrain(Block block) {
        return block == Blocks.STONE || block == Blocks.GRANITE || block == Blocks.DIORITE
                || block == Blocks.ANDESITE || block == Blocks.DIRT || block == Blocks.COARSE_DIRT
                || block == Blocks.GRASS_BLOCK || block == Blocks.PODZOL || block == Blocks.MYCELIUM
                || block == Blocks.SAND || block == Blocks.RED_SAND || block == Blocks.GRAVEL
                || block == Blocks.SANDSTONE || block == Blocks.RED_SANDSTONE || block == Blocks.CLAY
                || block == Blocks.NETHERRACK || block == Blocks.SNOW || block == Blocks.SNOW_BLOCK;
    }
}
