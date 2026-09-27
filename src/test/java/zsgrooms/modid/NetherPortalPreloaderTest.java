package zsgrooms.modid;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ChunkTicket;
import net.minecraft.server.world.ChunkTicketManager;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.util.collection.SortedArraySet;
import net.minecraft.world.chunk.ChunkStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NetherPortalPreloaderTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        net.minecraft.SharedConstants.getGameVersion();
        net.minecraft.Bootstrap.initialize();
    }

    @Test
    public void projectsOverworldCoordinatesIntoNetherChunks() {
        assertEquals(new ChunkPos(1, 2), NetherPortalPreloader.projectedNetherChunk(128.0D, 256.0D));
        assertEquals(new ChunkPos(-1, -1), NetherPortalPreloader.projectedNetherChunk(-1.0D, -1.0D));
    }

    @Test
    public void stagesTerrainBeforeRequestingAFullChunk() throws Exception {
        Tickets tickets = new Tickets();
        ChunkPos center = new ChunkPos(10, -10);
        UUID owner = new UUID(0, 1);
        ChunkTicketType<UUID> type = ChunkTicketType.create("preload_test", UUID::compareTo);
        tickets.addTicket(type, center, NetherPortalPreloader.TERRAIN_TICKET_RADIUS, owner);
        assertEquals(34, tickets.at(center).first().getLevel());
        assertEquals(ChunkStatus.FEATURES, ChunkHolder.getTargetGenerationStatus(tickets.at(center).first().getLevel()));
        tickets.addTicket(type, center, NetherPortalPreloader.FULL_TICKET_RADIUS, owner);
        assertEquals(33, tickets.at(center).first().getLevel());
        assertEquals(ChunkStatus.FULL, ChunkHolder.getTargetGenerationStatus(tickets.at(center).first().getLevel()));
        tickets.removeTicket(type, center, NetherPortalPreloader.FULL_TICKET_RADIUS, owner);
        assertEquals(34, tickets.at(center).first().getLevel());
        tickets.removeTicket(type, center, NetherPortalPreloader.TERRAIN_TICKET_RADIUS, owner);
        assertTrue(tickets.at(center) == null || tickets.at(center).isEmpty());
    }

    @Test
    public void aTicketLevelPassedAsRadiusReproducesTheOldBug() throws Exception {
        Tickets tickets = new Tickets();
        ChunkPos center = new ChunkPos(0, 0);
        ChunkTicketType<ChunkPos> type = ChunkTicketType.create("old_preload_test", (a, b) -> Long.compare(a.toLong(), b.toLong()));
        tickets.addTicket(type, center, 34, center);
        assertEquals(-1, tickets.at(center).first().getLevel());
        assertEquals(ChunkStatus.FULL, ChunkHolder.getTargetGenerationStatus(-1));
        tickets.removeTicket(type, center, 34, center);
    }

    @Test
    public void promotesOnlyWhenThereIsTimeAndServerHeadroom() {
        assertFalse(NetherPortalPreloader.shouldUpgradeToFull(19, 80, 10.0F));
        assertTrue(NetherPortalPreloader.shouldUpgradeToFull(20, 80, 35.0F));
        assertFalse(NetherPortalPreloader.shouldUpgradeToFull(61, 80, 10.0F));
        assertFalse(NetherPortalPreloader.shouldUpgradeToFull(40, 80, 35.1F));
        assertTrue(NetherPortalPreloader.shouldUpgradeToFull(60, 80, 10.0F));
        assertFalse(NetherPortalPreloader.shouldUpgradeToFull(0, 0, 10.0F));
    }

    private static final class Tickets extends ChunkTicketManager {
        Tickets() { super(Runnable::run, Runnable::run); }
        @Override protected boolean isUnloaded(long pos) { return true; }
        @Override protected ChunkHolder getChunkHolder(long pos) { return null; }
        @Override protected ChunkHolder setLevel(long pos, int level, ChunkHolder holder, int previous) {
            throw new AssertionError("Ticket inspection must not generate chunks");
        }
        @SuppressWarnings("unchecked")
        SortedArraySet<ChunkTicket<?>> at(ChunkPos pos) throws Exception {
            Field field = ChunkTicketManager.class.getDeclaredField("ticketsByPosition");
            field.setAccessible(true);
            return ((Map<Long, SortedArraySet<ChunkTicket<?>>>) field.get(this)).get(pos.toLong());
        }
    }
}
