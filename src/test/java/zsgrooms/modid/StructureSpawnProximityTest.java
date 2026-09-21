package zsgrooms.modid;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StructureSpawnProximityTest {
    @Test void closeRoutesUse48BlockBoundaryAndDeterministic24To48Targets() {
        for (String filter : new String[]{"zsg", "zsgop", "rpseedbank", "rooms-temple-v5", "rooms-village-v5"}) {
            assertFalse(StructureSpawnProximity.needsRelocation(new BlockPos(48, 200, 0), BlockPos.ORIGIN, filter));
            assertTrue(StructureSpawnProximity.needsRelocation(new BlockPos(48, 0, 1), BlockPos.ORIGIN, filter));
            assertTrue(StructureSpawnProximity.needsRelocation(new BlockPos(-49, 0, 0), BlockPos.ORIGIN, filter));
            for (long seed = -100; seed < 100; seed++) {
                int distance = StructureSpawnProximity.targetDistance(seed, filter);
                assertTrue(distance >= 24 && distance <= 48);
                assertEquals(distance, StructureSpawnProximity.targetDistance(seed, filter));
            }
        }
    }

    @Test void allShipwreckVariantsRetainPreviousDistancesAndThreshold() {
        for (String filter : new String[]{"rooms-shipwreck-v5", "zsgshipwreck", "zsgshipwreckop"}) {
            assertFalse(StructureSpawnProximity.needsRelocation(new BlockPos(140, 0, 0), BlockPos.ORIGIN, filter));
            assertTrue(StructureSpawnProximity.needsRelocation(new BlockPos(141, 0, 0), BlockPos.ORIGIN, filter));
            assertEquals(70, StructureSpawnProximity.minimumTargetDistance(filter));
            assertEquals(128, StructureSpawnProximity.maximumTargetDistance(filter));
            for (long seed = -100; seed < 100; seed++) {
                int distance = StructureSpawnProximity.targetDistance(seed, filter);
                assertTrue(distance >= 70 && distance <= 128);
            }
        }
    }

    @Test void earlyPreparationUsesExactLaunchMetadataNotPreviousMixedFilter() {
        StructureSpawnProximity.prepareNextLaunch("123|structure:rooms-village-v5|selection:rooms-mix");
        assertEquals("rooms-village-v5", StructureSpawnProximity.consumeLaunchFilter(123, "zsg"));
        assertEquals("zsg", StructureSpawnProximity.consumeLaunchFilter(123, "zsg"));
        StructureSpawnProximity.prepareNextLaunch("456|structure:zsgshipwreck");
        assertEquals("manual", StructureSpawnProximity.consumeLaunchFilter(789, "manual"));
        StructureSpawnProximity.prepareNextLaunch(null);
        assertEquals("", StructureSpawnProximity.consumeLaunchFilter(456, ""));
    }
}
