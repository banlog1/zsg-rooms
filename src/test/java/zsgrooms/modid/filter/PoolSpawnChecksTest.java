package zsgrooms.modid.filter;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PoolSpawnChecksTest {
    @Test
    void includesStructureAndBoundaryButRejectsOutsideCircleExpandedBySpawnWindow() {
        assertTrue(PoolSpawnChecks.canReachPoolArea(BlockPos.ORIGIN, BlockPos.ORIGIN));
        assertTrue(PoolSpawnChecks.canReachPoolArea(new BlockPos(128, 500, 32), BlockPos.ORIGIN));
        assertFalse(PoolSpawnChecks.canReachPoolArea(new BlockPos(129, 0, 0), BlockPos.ORIGIN));
        assertFalse(PoolSpawnChecks.canReachPoolArea(new BlockPos(128, 0, 33), BlockPos.ORIGIN));
        assertFalse(PoolSpawnChecks.canReachPoolArea(new BlockPos(128, 0, 128), BlockPos.ORIGIN));
    }

    @Test
    void everyAllowedPoolAndSpawnCornerSurvivesTheEnvelope() {
        BlockPos structure = new BlockPos(-400, 64, 250);
        for (int x = -96; x <= 96; x++) for (int z = -96; z <= 96; z++) {
            if (x * x + z * z > 96 * 96) continue;
            for (int dx : new int[]{-32, 0, 32}) for (int dz : new int[]{-32, 0, 32}) {
                assertTrue(PoolSpawnChecks.canReachPoolArea(structure.add(x + dx, 0, z + dz), structure));
            }
        }
    }

    @Test
    void largeCoordinatesDoNotOverflow() {
        assertFalse(PoolSpawnChecks.canReachPoolArea(new BlockPos(Integer.MIN_VALUE, 0, Integer.MIN_VALUE),
                new BlockPos(Integer.MAX_VALUE, 0, Integer.MAX_VALUE)));
        assertTrue(PoolSpawnChecks.canReachPoolArea(new BlockPos(-29999000, 0, -29999000),
                new BlockPos(-29999001, 0, -29999001)));
    }
}
