package zsgrooms.modid;

import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import zsgrooms.modid.ui.RoomRulePreset;

import static org.junit.jupiter.api.Assertions.*;

class TempleSpawnControlTest {
    @Test void coversTempleAndUndergroundChamberWithoutProtectingNearbyCavesOrDesert() {
        for (int x : new int[]{-160, 32}) {
            BlockBox box = new BlockBox(x, 64, -32, x + 20, 78, -12);
            assertTrue(TempleSpawnControl.contains(box, new BlockPos(x, 64, -32)));
            assertTrue(TempleSpawnControl.contains(box, new BlockPos(x + 20, 78, -12)));
            assertTrue(TempleSpawnControl.contains(box, new BlockPos(x + 10, 53, -22)));
            assertTrue(TempleSpawnControl.contains(box, new BlockPos(x + 7, 50, -19)));
            assertFalse(TempleSpawnControl.contains(box, new BlockPos(x - 1, 65, -22)));
            assertFalse(TempleSpawnControl.contains(box, new BlockPos(x + 21, 65, -22)));
            assertFalse(TempleSpawnControl.contains(box, new BlockPos(x + 10, 79, -22)));
            assertFalse(TempleSpawnControl.contains(box, new BlockPos(x + 10, 49, -22)));
            assertFalse(TempleSpawnControl.contains(box, new BlockPos(x + 2, 53, -22)));
        }
    }

    @Test void standardPresetsEnableItAndRawLeavesItOff() {
        assertTrue(RoomRulePreset.STANDARD_ZSG_ROOMS.preventsTempleHostileSpawns());
        assertTrue(RoomRulePreset.STANDARD_ZSG_VANILLA_BARTERS.preventsTempleHostileSpawns());
        assertFalse(RoomRulePreset.REGULAR_VERIFIABLE_ZSG.preventsTempleHostileSpawns());
        assertFalse(RoomRulePreset.CUSTOM.preventsTempleHostileSpawns());
    }
}
