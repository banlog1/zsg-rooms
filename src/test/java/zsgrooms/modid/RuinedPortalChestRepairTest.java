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

    @Test void roomsPortalBankUsesAutomaticRepairsAndPortalStructure() {
        StructureSpawnProximity.prepareNextLaunch(null);
        assertTrue(RuinedPortalChestRepair.shouldRepair(SEED, "rooms-ruined-portal-v5", false));
        assertEquals("ruined_portal", StructureSpawnProximity.structureKeyForFilter("rooms-ruined-portal-v5"));
        StructureSpawnProximity.prepareNextLaunch(SEED + "|structure:rooms-ruined-portal-v5|iron:4");
        assertTrue(RuinedPortalChestRepair.isPortalWorld(SEED, "rooms-temple-v5"));
        assertFalse(RuinedPortalChestRepair.isPortalWorld(SEED + 1, "rooms-ruined-portal-v5"));
        assertEquals("rooms-ruined-portal-v5", StructureSpawnProximity.consumeLaunchFilter(SEED, "room"));
        assertTrue(RuinedPortalChestRepair.isPortalWorld(SEED, "rooms-ruined-portal-v5"));
    }

    @Test void onlyExternalPortalSeedbankRequiresLootingForSpawnLookup() {
        assertTrue(StructureSpawnProximity.requiresSeedbankLoot("rpseedbank"));
        assertTrue(StructureSpawnProximity.requiresSeedbankLoot("Ruined Portal Seedbank"));
        assertFalse(StructureSpawnProximity.requiresSeedbankLoot("rooms-ruined-portal-v5"));
        assertFalse(StructureSpawnProximity.requiresSeedbankLoot("ZSG Rooms Ruined Portal"));
    }

    @Test void randomModeUsesIncomingFilterBeforeRoomCommit() {
        StructureSpawnProximity.prepareNextLaunch(PORTAL);
        assertTrue(RuinedPortalChestRepair.isPortalWorld(SEED, "rooms-mix"));
        assertTrue(RuinedPortalChestRepair.isPortalWorld(SEED, "rooms-temple-v5"));
        assertFalse(RuinedPortalChestRepair.isPortalWorld(SEED + 1, "rpseedbank"));
    }

    @Test void portalRoomsAreAutomaticAndOtherWorldsRequireTestingOverride() {
        StructureSpawnProximity.prepareNextLaunch(null);
        assertTrue(RuinedPortalChestRepair.shouldRepair(SEED, "rpseedbank", false));
        assertFalse(RuinedPortalChestRepair.shouldRepair(SEED, "rooms-temple-v5", false));
        assertFalse(RuinedPortalChestRepair.shouldRepair(SEED, null, false));
        assertTrue(RuinedPortalChestRepair.shouldRepair(SEED, "rooms-temple-v5", true));
        assertTrue(RuinedPortalChestRepair.shouldRepair(SEED, null, true));
        StructureSpawnProximity.prepareNextLaunch(PORTAL);
        assertTrue(RuinedPortalChestRepair.shouldRepair(SEED, "rooms-mix", false));
        assertFalse(RuinedPortalChestRepair.shouldRepair(SEED + 1, "rpseedbank", false));
        assertFalse(RuinedPortalChestRepair.shouldRepair(SEED, null, false));
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
