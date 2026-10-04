package zsgrooms.modid.rng;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/** Characterizes audit findings, not the desired gameplay policy. */
class FortressSpawnAuditTest {
    @AfterEach void clearPass() { FortressSpawnProtection.clear(); }

    @Test void referenceOrderDecidesWhichFortressGetsTheOnlyAllowance() {
        FortressSpawnProtectionState first = new FortressSpawnProtectionState();
        FortressSpawnProtectionState reversed = new FortressSpawnProtectionState();
        assertTrue(FortressSpawnProtection.beginPass(first, false, Arrays.asList(12L, 13L)));
        assertTrue(FortressSpawnProtection.beginPass(reversed, false, Arrays.asList(13L, 12L)));
        assertEquals(12L, first.toTag(new CompoundTag()).getLong("FirstFortress"));
        assertEquals(13L, reversed.toTag(new CompoundTag()).getLong("FirstFortress"));
        assertFalse(first.hasRemaining(13L));
        assertFalse(reversed.hasRemaining(12L));
    }

    @Test void rejectedOriginsCanExhaustProtectionWithoutOnePackSelection() {
        FortressSpawnProtectionState state = new FortressSpawnProtectionState();
        for (int i = 0; i < FortressSpawnProtectionState.MAX_INITIAL_CYCLES; i++) {
            assertTrue(FortressSpawnProtection.beginPass(state, false, Collections.singletonList(12L)));
            // Vanilla may return immediately for a solid initial block, before beginPack/selection.
            FortressSpawnProtection.clear();
        }
        CompoundTag entry = state.toTag(new CompoundTag()).getList("Fortresses", 10).getCompound(0);
        assertEquals(0, entry.getInt("Used"));
        assertEquals(4096, entry.getInt("Cycles"));
        assertTrue(state.isFinished());
        assertFalse(FortressSpawnProtection.beginPass(state, false, Collections.singletonList(12L)));
    }

    @Test void extraLoadedFortressChunksSpendTheSharedEvaluationBudgetFaster() {
        FortressSpawnProtectionState smallView = new FortressSpawnProtectionState();
        FortressSpawnProtectionState largeView = new FortressSpawnProtectionState();
        for (int tick = 0; tick < 512; tick++) {
            smallView.beginCycle(12L);
            for (int chunk = 0; chunk < 8; chunk++) largeView.beginCycle(12L);
        }
        assertTrue(smallView.hasRemaining(12L));
        assertTrue(largeView.isFinished());
        assertEquals(0, largeView.toTag(new CompoundTag()).getList("Fortresses", 10).getCompound(0).getInt("Used"));
    }
}
