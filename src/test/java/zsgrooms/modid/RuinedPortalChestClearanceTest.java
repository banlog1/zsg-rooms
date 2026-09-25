package zsgrooms.modid;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RuinedPortalChestClearanceTest {
    private static final BlockPos CHEST = new BlockPos(15, 70, 15);
    private final Map<BlockPos, BlockState> blocks = new HashMap<BlockPos, BlockState>();

    private BlockState state(BlockPos pos) { return blocks.getOrDefault(pos, Blocks.AIR.getDefaultState()); }
    private List<BlockPos> plan(Direction direction) {
        return RuinedPortalChestClearance.plan(CHEST, direction, this::state, pos -> true, pos -> false);
    }

    @Test void fiveBlockPocketRotatesWithChestAndLeavesFloorAlone() {
        for (Direction facing : Direction.Type.HORIZONTAL) {
            blocks.clear();
            blocks.put(CHEST.up(), Blocks.SAND.getDefaultState());
            blocks.put(CHEST.down(), Blocks.STONE.getDefaultState());
            for (int i = 1; i <= 2; i++) {
                blocks.put(CHEST.offset(facing, i), Blocks.NETHERRACK.getDefaultState());
                blocks.put(CHEST.offset(facing, i).up(), Blocks.GRAVEL.getDefaultState());
            }
            List<BlockPos> remove = plan(facing);
            assertNotNull(remove);
            assertEquals(5, remove.size());
            assertFalse(remove.contains(CHEST.down()));
            assertFalse(remove.contains(CHEST));
            assertEquals(remove, plan(facing));
        }
    }

    @Test void airNeedsNoChanges() { assertTrue(plan(Direction.NORTH).isEmpty()); }

    @Test void protectedBlocksRejectEntirePocket() {
        blocks.put(CHEST.up(), Blocks.DIRT.getDefaultState());
        for (BlockState protectedState : new BlockState[]{Blocks.OBSIDIAN.getDefaultState(),
                Blocks.CRYING_OBSIDIAN.getDefaultState(), Blocks.CHEST.getDefaultState(),
                Blocks.STONE_BRICKS.getDefaultState(), Blocks.WATER.getDefaultState(),
                Blocks.LAVA.getDefaultState(), Blocks.OAK_PLANKS.getDefaultState()}) {
            blocks.put(CHEST.north(), protectedState);
            assertNull(plan(Direction.NORTH));
            assertEquals(Blocks.DIRT.getDefaultState(), state(CHEST.up()));
        }
    }

    @Test void doesNotReleaseNearbyWaterOrLava() {
        blocks.put(CHEST.up(), Blocks.STONE.getDefaultState());
        for (BlockState fluid : new BlockState[]{Blocks.WATER.getDefaultState(), Blocks.LAVA.getDefaultState()}) {
            blocks.put(CHEST.up().east(), fluid);
            assertNull(plan(Direction.NORTH));
        }
    }

    @Test void fallingOverhangAndBlockEntitiesAreNotDisturbed() {
        blocks.put(CHEST.up(), Blocks.STONE.getDefaultState());
        blocks.put(CHEST.up(2), Blocks.SAND.getDefaultState());
        assertNull(plan(Direction.NORTH));
        blocks.remove(CHEST.up(2));
        assertNull(RuinedPortalChestClearance.plan(CHEST, Direction.NORTH, this::state,
                pos -> true, pos -> pos.equals(CHEST.up())));
    }

    @Test void neverReadsUnloadedBlocksOrLoadsChunks() {
        BlockPos unloaded = CHEST.up().east();
        blocks.put(CHEST.up(), Blocks.STONE.getDefaultState());
        assertNull(RuinedPortalChestClearance.plan(CHEST, Direction.NORTH, pos -> {
            assertNotEquals(unloaded, pos);
            return state(pos);
        }, pos -> !pos.equals(unloaded), pos -> false));
    }
}
