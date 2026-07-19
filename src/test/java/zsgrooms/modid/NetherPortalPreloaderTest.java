package zsgrooms.modid;

import net.minecraft.util.math.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NetherPortalPreloaderTest {
    @Test
    public void projectsOverworldCoordinatesIntoNetherChunks() {
        assertEquals(new ChunkPos(1, 2), NetherPortalPreloader.projectedNetherChunk(128.0D, 256.0D));
        assertEquals(new ChunkPos(-1, -1), NetherPortalPreloader.projectedNetherChunk(-1.0D, -1.0D));
    }

    @Test
    public void warmsCenterFirstAndEachThreeByThreeChunkOnce() {
        ChunkPos center = new ChunkPos(12, -8);
        List<ChunkPos> chunks = NetherPortalPreloader.chunkOrder(center);

        assertEquals((NetherPortalPreloader.PRELOAD_RADIUS * 2 + 1)
                * (NetherPortalPreloader.PRELOAD_RADIUS * 2 + 1), chunks.size());
        assertEquals(center, chunks.get(0));
        assertEquals(9, new HashSet<ChunkPos>(chunks).size());
        for (ChunkPos chunk : chunks) {
            assertTrue(Math.max(Math.abs(chunk.x - center.x), Math.abs(chunk.z - center.z)) <= 1);
        }
    }

    @Test
    public void boundsConcurrentChunkPreparation() {
        assertEquals(2, NetherPortalPreloader.MAX_IN_FLIGHT);
    }
}
