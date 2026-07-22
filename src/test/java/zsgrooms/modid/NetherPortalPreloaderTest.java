package zsgrooms.modid;

import net.minecraft.util.math.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NetherPortalPreloaderTest {
    @Test
    public void projectsOverworldCoordinatesIntoNetherChunks() {
        assertEquals(new ChunkPos(1, 2), NetherPortalPreloader.projectedNetherChunk(128.0D, 256.0D));
        assertEquals(new ChunkPos(-1, -1), NetherPortalPreloader.projectedNetherChunk(-1.0D, -1.0D));
    }

    @Test
    public void stagesTerrainBeforeRequestingAFullChunk() {
        assertEquals(34, NetherPortalPreloader.TERRAIN_TICKET_LEVEL);
        assertEquals(33, NetherPortalPreloader.FULL_TICKET_LEVEL);
    }

    @Test
    public void promotesOnlyWhenThereIsTimeAndServerHeadroom() {
        assertFalse(NetherPortalPreloader.shouldUpgradeToFull(19, 80, 10.0F));
        assertTrue(NetherPortalPreloader.shouldUpgradeToFull(20, 80, 35.0F));
        assertFalse(NetherPortalPreloader.shouldUpgradeToFull(61, 80, 10.0F));
        assertFalse(NetherPortalPreloader.shouldUpgradeToFull(40, 80, 35.1F));
    }
}
