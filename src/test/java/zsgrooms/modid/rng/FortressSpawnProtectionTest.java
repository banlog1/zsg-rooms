package zsgrooms.modid.rng;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class FortressSpawnProtectionTest {
    private final FortressSpawnProtectionState state = new FortressSpawnProtectionState();

    @AfterEach
    public void clearContext() {
        FortressSpawnProtection.clear();
    }

    private boolean begin(boolean vanillaAllowed, Long... references) {
        return FortressSpawnProtection.beginPass(this.state, vanillaAllowed, Arrays.asList(references));
    }

    private int select(Long fortress) {
        FortressSpawnProtection.beginPack();
        FortressSpawnProtection.beginSelection();
        FortressSpawnProtection.observeFortress(fortress);
        return FortressSpawnProtection.finishSelection();
    }

    @Test
    public void bothCapPathsConsumeTheSameFiniteAllowanceRegardlessOfSuccess() {
        for (int i = 1; i <= 8; i++) {
            assertTrue(begin(i % 2 == 0, 12L));
            assertEquals(i, select(12L));
            assertTrue(FortressSpawnProtection.allowsPack());
            // No successful spawn is reported: rejected packs still consume their opportunity.
        }
        assertFalse(this.state.hasRemaining(12L));
        assertTrue(this.state.isFinished());
        assertFalse(begin(false, 12L));
        assertTrue(begin(true, 12L));
        assertEquals(-1, select(12L));
        assertTrue(FortressSpawnProtection.allowsPack());
    }

    @Test
    public void eighthPackCanFinishButNinthCannotBypassCapInSameCycle() {
        assertTrue(begin(false, 12L));
        for (int i = 1; i <= 8; i++) {
            assertEquals(i, select(12L));
        }
        assertFalse(this.state.hasRemaining(12L));
        for (int member = 0; member < 4; member++) {
            FortressSpawnProtection.observeFortress(12L);
            assertTrue(FortressSpawnProtection.allowsCandidate());
            assertTrue(FortressSpawnProtection.allowsPack());
        }
        assertEquals(-1, select(12L));
        assertFalse(FortressSpawnProtection.allowsPack());
    }

    @Test
    public void onePackConsumesExactlyOneOpportunity() {
        begin(false, 12L);
        assertEquals(1, select(12L));
        assertEquals(-1, FortressSpawnProtection.finishSelection());
        assertEquals(2, select(12L));
    }

    @Test
    public void biomePacksCannotUseCapExceptionEvenAfterWalkingIntoFortress() {
        begin(false, 12L);
        assertEquals(-1, select(null));
        FortressSpawnProtection.observeFortress(12L);
        assertFalse(FortressSpawnProtection.allowsPack());
        assertFalse(FortressSpawnProtection.allowsCandidate());
        assertEquals(1, select(12L));
    }

    @Test
    public void protectedPackCannotLeakIntoBiomeOrAnotherFortress() {
        begin(false, 12L, 13L);
        select(12L);
        FortressSpawnProtection.observeFortress(null);
        assertFalse(FortressSpawnProtection.allowsCandidate());
        FortressSpawnProtection.observeFortress(13L);
        assertFalse(FortressSpawnProtection.allowsCandidate());
        FortressSpawnProtection.observeFortress(12L);
        assertTrue(FortressSpawnProtection.allowsCandidate());
        assertEquals(-1, select(13L));
        assertFalse(FortressSpawnProtection.allowsPack());
    }

    @Test
    public void vanillaAdmissionKeepsOrdinaryAndCrossBoundaryPacksAllowed() {
        assertTrue(begin(true));
        select(null);
        assertTrue(FortressSpawnProtection.allowsPack());
        assertTrue(FortressSpawnProtection.allowsCandidate());
        assertEquals(1, select(12L));
        FortressSpawnProtection.observeFortress(null);
        assertTrue(FortressSpawnProtection.allowsCandidate());
    }

    @Test
    public void noFortressReferenceMeansNoCapBypass() {
        assertFalse(begin(false));
        assertTrue(this.state.hasRemaining(12L));
    }

    @Test
    public void savedAllowancesDoNotRefillAndWorldsAreIndependent() {
        begin(false, 12L);
        for (int i = 1; i <= 8; i++) {
            select(12L);
        }
        FortressSpawnProtectionState restored = new FortressSpawnProtectionState();
        restored.fromTag(this.state.toTag(new CompoundTag()));
        assertFalse(restored.hasRemaining(12L));
        assertTrue(restored.isFinished());
        assertNull(restored.beginCycle(13L));
        assertEquals(1, new FortressSpawnProtectionState().beginCycle(12L).consumeOpportunity());
    }

    @Test
    public void firstActivationIsPersistedEvenBeforeAnyPackSelection() {
        assertFalse(this.state.isFinished());
        assertTrue(begin(false, 12L));
        FortressSpawnProtectionState restored = new FortressSpawnProtectionState();
        restored.fromTag(this.state.toTag(new CompoundTag()));
        assertFalse(restored.isFinished());
        assertTrue(restored.hasRemaining(12L));
        assertFalse(restored.hasRemaining(13L));
        assertNull(restored.beginCycle(13L));
        assertEquals(1, restored.beginCycle(12L).consumeOpportunity());
    }

    @Test
    public void partiallyUsedFirstFortressResumesWithoutRefilling() {
        begin(true, 12L);
        select(12L);
        FortressSpawnProtectionState restored = new FortressSpawnProtectionState();
        restored.fromTag(this.state.toTag(new CompoundTag()));
        assertEquals(2, restored.beginCycle(12L).consumeOpportunity());
        assertNull(restored.beginCycle(13L));
    }

    @Test
    public void exhaustedFastPathDoesNotEvenIterateReferences() {
        begin(false, 12L);
        for (int i = 0; i < 8; i++) {
            select(12L);
        }
        Iterable<Long> unexpectedReferences = () -> {
            throw new AssertionError("Exhausted protection must not inspect references");
        };
        assertFalse(FortressSpawnProtection.beginPass(this.state, false, unexpectedReferences));
        assertTrue(FortressSpawnProtection.beginPass(this.state, true, unexpectedReferences));
        assertTrue(FortressSpawnProtection.allowsPack());
        assertEquals(-1, select(99L));
    }

    @Test
    public void oldSaveWithUnknownActivationOrderCannotStartAnotherWindow() {
        begin(true, 12L);
        select(12L);
        CompoundTag legacy = this.state.toTag(new CompoundTag());
        legacy.remove("FirstFortress");
        ListTag entries = legacy.getList("Fortresses", 10);
        CompoundTag second = entries.getCompound(0).copy();
        second.putLong("StartChunk", 13L);
        entries.add(second);
        FortressSpawnProtectionState restored = new FortressSpawnProtectionState();
        restored.fromTag(legacy);
        assertTrue(restored.isFinished());
        assertNull(restored.beginCycle(12L));
        assertNull(restored.beginCycle(13L));
        FortressSpawnProtectionState reloaded = new FortressSpawnProtectionState();
        reloaded.fromTag(restored.toTag(new CompoundTag()));
        assertTrue(reloaded.isFinished());
    }

    @Test
    public void emptyLegacySaveStillAllowsFirstActivation() {
        this.state.fromTag(new CompoundTag());
        assertFalse(this.state.isFinished());
        assertNotNull(this.state.beginCycle(12L));
    }

    @Test
    public void singleFortressLegacySaveKeepsItsExistingAllowance() {
        begin(true, 12L);
        select(12L);
        CompoundTag legacy = this.state.toTag(new CompoundTag());
        legacy.remove("FirstFortress");
        FortressSpawnProtectionState restored = new FortressSpawnProtectionState();
        restored.fromTag(legacy);
        assertFalse(restored.isFinished());
        assertNull(restored.beginCycle(13L));
        assertEquals(2, restored.beginCycle(12L).consumeOpportunity());
        assertEquals(12L, restored.toTag(new CompoundTag()).getLong("FirstFortress"));
    }

    @Test
    public void unproductiveSearchHasFinitePersistedCycleBudget() {
        for (int i = 0; i < FortressSpawnProtectionState.MAX_INITIAL_CYCLES - 1; i++) {
            assertNotNull(this.state.beginCycle(12L));
        }
        FortressSpawnProtectionState restored = new FortressSpawnProtectionState();
        restored.fromTag(this.state.toTag(new CompoundTag()));
        FortressSpawnProtectionState.Evaluation last = restored.beginCycle(12L);
        assertNotNull(last);
        assertFalse(restored.hasRemaining(12L));
        assertNull(restored.beginCycle(12L));
        assertEquals(1, last.consumeOpportunity());
    }

    @Test
    public void clearedOrInactiveContextDoesNotFilterVanilla() {
        begin(false, 12L);
        select(null);
        assertFalse(FortressSpawnProtection.allowsPack());
        FortressSpawnProtection.clear();
        assertTrue(FortressSpawnProtection.allowsPack());
        assertTrue(FortressSpawnProtection.allowsCandidate());
        FortressSpawnProtection.beginPass(this.state, true, Collections.emptyList());
        assertTrue(FortressSpawnProtection.allowsPack());
    }
}
