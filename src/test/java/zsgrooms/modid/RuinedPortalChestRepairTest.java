package zsgrooms.modid;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RuinedPortalChestRepairTest {
    private static final long SEED = 3818903892210348937L;
    private static final String PORTAL = SEED + "|structure:rpseedbank|iron:4|selection:rooms-mix";

    @AfterEach void clearLaunch() {
        StructureSpawnProximity.prepareNextLaunch(null);
    }

    @Test void randomModeUsesIncomingFilterBeforeRoomCommit() {
        StructureSpawnProximity.prepareNextLaunch(PORTAL);
        assertTrue(RuinedPortalChestRepair.isPortalWorld(SEED, "rooms-mix"));
        assertTrue(RuinedPortalChestRepair.isPortalWorld(SEED, "rooms-temple-v5"));
        assertFalse(RuinedPortalChestRepair.isPortalWorld(SEED + 1, "rpseedbank"));
    }

    @Test void spawnPreparationDoesNotConsumeRepairContext() {
        StructureSpawnProximity.prepareNextLaunch(PORTAL);
        assertEquals("rpseedbank", StructureSpawnProximity.consumeLaunchFilter(SEED, "rooms-mix"));
        assertTrue(RuinedPortalChestRepair.isPortalWorld(SEED, "rooms-mix"));
        StructureSpawnProximity.prepareNextLaunch(PORTAL);
        assertTrue(RuinedPortalChestRepair.isPortalWorld(SEED, "rpseedbank"));
    }

    @Test void nextNonPortalLaunchCannotInheritPreviousRepairSetting() {
        StructureSpawnProximity.prepareNextLaunch(PORTAL);
        StructureSpawnProximity.prepareNextLaunch(SEED + "|structure:rooms-temple-v5|selection:rooms-mix");
        assertFalse(RuinedPortalChestRepair.isPortalWorld(SEED, "rpseedbank"));
    }

    @Test void cancelledLaunchRestoresExistingRoomFilterFallback() {
        StructureSpawnProximity.prepareNextLaunch(PORTAL);
        StructureSpawnProximity.prepareNextLaunch(null);
        assertFalse(RuinedPortalChestRepair.isPortalWorld(SEED, "rooms-mix"));
        assertTrue(RuinedPortalChestRepair.isPortalWorld(SEED, "rpseedbank"));
        StructureSpawnProximity.prepareNextLaunch("pending-rooms-mix");
        assertFalse(RuinedPortalChestRepair.isPortalWorld(SEED, "rooms-mix"));
    }
}
