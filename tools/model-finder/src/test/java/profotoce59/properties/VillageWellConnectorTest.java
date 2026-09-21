package profotoce59.properties;

import com.seedfinding.mccore.util.block.BlockDirection;
import com.seedfinding.mccore.util.pos.BPos;
import com.seedfinding.mccore.version.MCVersion;
import org.junit.jupiter.api.Test;
import profotoce59.enumType.VillageType;
import profotoce59.reecriture.VillagePools.CommonVillageJigsawBlocks;
import profotoce59.reecriture.VillagePools.VillageStructureSize;

import static org.junit.jupiter.api.Assertions.*;

/** Guards the production connector against Minecraft 1.16.1 template data. */
class VillageWellConnectorTest {
    private static final String WELL = "common/well_bottom";

    @Test
    void sharedConnectorMatchesTheMinecraft1161Template() {
        // Confirmed by exporting the jigsaw block from Minecraft's bundled template.
        var connectors = CommonVillageJigsawBlocks.JIGSAW_BLOCKS.get(WELL);
        assertEquals(1, connectors.size());
        var connector = connectors.get(0);
        assertEquals(new BPos(3, 2, 0), connector.getSecond());
        assertEquals(BlockDirection.UP, connector.getFirst().getThird().getFirst());
        assertEquals(BlockDirection.NORTH, connector.getFirst().getThird().getSecond());
        assertTrue(inside(connector.getSecond(), VillageStructureSize.STRUCTURE_SIZE.get(WELL)));
    }

    @Test
    void plainsConnectorMatchesTheSharedVanillaConnectorAndStaysInsideTemplate() {
        var connector = VillageType.PLAINS.getJigsawBlocks(MCVersion.v1_16_1).get(WELL).get(0);
        assertEquals(new BPos(3, 2, 0), connector.getSecond());
        assertTrue(inside(connector.getSecond(), VillageStructureSize.STRUCTURE_SIZE.get(WELL)));
        assertEquals(CommonVillageJigsawBlocks.JIGSAW_BLOCKS.get(WELL).get(0).getSecond(), connector.getSecond());
        assertEquals(BlockDirection.UP, connector.getFirst().getThird().getFirst());
        assertEquals(BlockDirection.NORTH, connector.getFirst().getThird().getSecond());
    }

    private static boolean inside(BPos position, BPos size) {
        return position.getX() >= 0 && position.getX() < size.getX()
                && position.getY() >= 0 && position.getY() < size.getY()
                && position.getZ() >= 0 && position.getZ() < size.getZ();
    }
}
