package zsgrooms.modid.seedbank;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import zsgrooms.modid.InGame;
import zsgrooms.modid.ZsgSeedBridge;

import static org.junit.jupiter.api.Assertions.*;

class SeedStructureTargetTest {
    private static final long SEED = 935645362601973608L;
    private static final String FILTER = "rooms-village-v5";
    private static final String SPEC = SEED + "|structure:" + FILTER + "|iron:4|target:32,192|selection:rooms-mix";

    @Test void mixedRoomTargetRemainsBoundToExactSeedAndResolvedFilter() {
        assertEquals(new BlockPos(32, 0, 192), SeedStructureTarget.parse(SPEC, SEED, FILTER));
        assertNull(SeedStructureTarget.parse(SPEC, SEED + 1, FILTER));
        assertNull(SeedStructureTarget.parse(SPEC, SEED, "rooms-temple-v5"));
        assertNull(SeedStructureTarget.parse(SPEC, SEED, "manual"));
        assertNull(SeedStructureTarget.parse(SPEC + "|target:1,2", SEED, FILTER));
        assertNull(SeedStructureTarget.parse(SPEC.replace("32,192", "30000001,192"), SEED, FILTER));
        assertNull(SeedStructureTarget.parse(SPEC.replace("|target:32,192", ""), SEED, FILTER));
    }

    @Test void rebuildingSameSeedPreservesTargetButChangedSeedsAndFiltersDoNot() {
        InGame game = new InGame(SPEC, "test", InGame.SeedType.FIXED, false);
        game.seedModification(FILTER, 4);
        assertEquals(new BlockPos(32, 0, 192), SeedStructureTarget.parse(game.getSeed(), SEED, FILTER));
        String otherSeed = ZsgSeedBridge.buildSeedForStructure("123", FILTER, 4);
        assertEquals(otherSeed, SeedStructureTarget.preserve(SPEC, otherSeed));
        String manual = ZsgSeedBridge.buildSeedForStructure(Long.toString(SEED), "manual", 4);
        assertEquals(manual, SeedStructureTarget.preserve(SPEC, manual));
    }

    @Test void bankLaunchNeverSilentlyFallsBackWhenOldHostOmitsCoordinates() {
        assertTrue(SeedStructureTarget.hasRequiredTarget(SPEC));
        assertFalse(SeedStructureTarget.hasRequiredTarget(SPEC.replace("|target:32,192", "")));
        assertFalse(SeedStructureTarget.hasRequiredTarget(SPEC + "|target:1,2"));
        assertFalse(SeedStructureTarget.hasRequiredTarget(SPEC.replace(Long.toString(SEED), "pending-village")));
        assertTrue(SeedStructureTarget.hasRequiredTarget("123|structure:manual"));
        assertTrue(SeedStructureTarget.hasRequiredTarget("123|structure:zsg"));
    }
}
