package zsgrooms.modid.filter;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.ChunkRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class StagedFilterSearchTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.getGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void sisterPermutationCoversEveryUpperValueExactlyOnce() {
        for (int stride : new int[]{1, 173, 65535}) {
            Set<Long> seen = new HashSet<Long>();
            for (int i = 0; i < 65536; i++) {
                long seed = StagedFilterSearch.sisterSeed(321, 65000, stride, i);
                assertEquals(321, seed & StagedFilterSearch.MASK);
                assertTrue(seen.add(seed));
            }
            assertEquals(65536, seen.size());
        }
    }

    @Test
    void proposalRejectionKeepsTheFamilyAndSuccessCanRetireIt() {
        AtomicInteger gates = new AtomicInteger();
        StagedFilterSearch search = new StagedFilterSearch(FilterCandidateSearch.Type.SHIPWRECK,
                false, 123, 65536, (lower, main, bastion) -> { gates.incrementAndGet(); return true; });
        FilterCandidateSearch.Candidate first = next(search);
        long families = search.familiesChecked();
        int calls = gates.get();
        FilterCandidateSearch.Candidate second = next(search);
        assertNotEquals(first.seedForValidation(), second.seedForValidation());
        assertEquals(first.seedForValidation() & StagedFilterSearch.MASK, second.seedForValidation() & StagedFilterSearch.MASK);
        assertEquals(families, search.familiesChecked());
        assertEquals(calls, gates.get());
        search.skipRemainingFamily();
        FilterCandidateSearch.Candidate third = next(search);
        assertNotEquals(first.seedForValidation() & StagedFilterSearch.MASK, third.seedForValidation() & StagedFilterSearch.MASK);
    }

    @Test
    void boundedWorkSlicesResumeWithoutChangingTheCandidate() {
        StagedFilterSearch whole = session(FilterCandidateSearch.Type.SHIPWRECK, 100);
        StagedFilterSearch sliced = session(FilterCandidateSearch.Type.SHIPWRECK, 100);
        FilterCandidateSearch.Candidate expected = next(whole);
        FilterCandidateSearch.Result actual = null;
        for (int i = 0; i < 100000; i++) {
            actual = sliced.next(1, 60000, () -> false);
            if (actual.candidate().isPresent()) break;
            assertEquals(FilterCandidateSearch.Status.LIMIT_REACHED, actual.status);
        }
        assertNotNull(actual);
        assertTrue(actual.candidate().isPresent());
        assertEquals(expected.seedForValidation(), actual.candidate().get().seedForValidation());
        assertEquals(whole.familiesChecked(), sliced.familiesChecked());
        assertEquals(whole.sistersChecked(), sliced.sistersChecked());
    }

    @Test
    void familyRejectionsDoNotSpendSisterBudget() {
        AtomicInteger calls = new AtomicInteger();
        StagedFilterSearch search = new StagedFilterSearch(FilterCandidateSearch.Type.TEMPLE,
                false, 123, 100, (lower, main, bastion) -> { calls.incrementAndGet(); return false; });
        FilterCandidateSearch.Result result = search.next(10000, 60000, () -> false);
        assertEquals(FilterCandidateSearch.Status.LIMIT_REACHED, result.status);
        assertFalse(result.candidate().isPresent());
        assertEquals(10000, search.familiesChecked());
        assertEquals(0, search.sistersChecked());
        assertTrue(calls.get() > 0);
        assertEquals(calls.get(), search.familiesRejectedByGate());
    }

    @Test
    void gateReceivesTheSelectedBastionAndFailuresAreNotSilentlySkipped() {
        AtomicInteger calls = new AtomicInteger();
        StagedFilterSearch search = new StagedFilterSearch(FilterCandidateSearch.Type.TEMPLE,
                false, 123, 100, (lower, main, bastion) -> {
                    FilterCandidateSearch.StructureCheck expected = FilterCandidateSearch.checkGeometry(lower,
                            FilterCandidateSearch.Type.TEMPLE, false, new ChunkRandom());
                    assertEquals(expected.main, main);
                    assertEquals(expected.bastion, bastion);
                    calls.incrementAndGet();
                    throw new IllegalStateException("Fixture gate failure");
                });
        assertThrows(IllegalStateException.class, () -> search.next(10000, 60000, () -> false));
        assertEquals(1, calls.get());
        assertEquals(0, search.sistersChecked());
    }

    @Test
    void stagedCandidatesPassTheUncachedPredicatesForEveryType() {
        for (FilterCandidateSearch.Type type : FilterCandidateSearch.Type.values()) {
            StagedFilterSearch search = session(type, 100);
            FilterCandidateSearch.Candidate candidate = next(search);
            long seed = candidate.seedForValidation();
            FilterCandidateSearch.StructureCheck structure = FilterCandidateSearch.checkGeometry(seed, type, false, new ChunkRandom());
            assertNull(structure.rejection);
            long[] rejected = new long[FilterCandidateSearch.Rejection.values().length];
            FilterCandidateSearch.Candidate uncached = FilterCandidateSearch.checkSister(seed, type, false, structure, false, rejected, () -> false);
            assertNotNull(uncached);
            assertEquals(candidate.main, uncached.main);
            assertEquals(candidate.bastion, uncached.bastion);
            assertEquals(candidate.fortress, uncached.fortress);
            assertEquals(candidate.lakeAttempts, uncached.lakeAttempts);
            assertEquals(seed, next(session(type, 100)).seedForValidation());
        }
    }

    @Test
    void cachedGeometryNetherBiomeAndShipwreckRollsAreUpperBitInvariant() {
        ChunkRandom random = new ChunkRandom();
        int accepted = 0;
        for (int i = 0; i < 1000; i++) {
            long lower = (i * 0x9e3779b97f4a7c15L) & StagedFilterSearch.MASK;
            FilterCandidateSearch.StructureCheck base = FilterCandidateSearch.checkGeometry(lower,
                    FilterCandidateSearch.Type.SHIPWRECK, false, random);
            for (int upper : new int[]{1, 32768, 65535}) {
                long seed = lower | ((long) upper << 48);
                FilterCandidateSearch.StructureCheck sister = FilterCandidateSearch.checkGeometry(seed,
                        FilterCandidateSearch.Type.SHIPWRECK, false, random);
                assertEquals(base.rejection, sister.rejection);
                if (base.rejection != null) continue;
                accepted++;
                assertEquals(base.main, sister.main);
                assertEquals(base.bastion, sister.bastion);
                assertEquals(base.fortress, sister.fortress);
                assertEquals(FilterCandidateSearch.netherViable(lower, base), FilterCandidateSearch.netherViable(seed, sister));
                assertEquals(FilterCandidateSearch.shipwreckLayout(lower, base.main, random),
                        FilterCandidateSearch.shipwreckLayout(seed, sister.main, random));
                ChunkRandom a = new ChunkRandom();
                ChunkRandom b = new ChunkRandom();
                ChunkPos p = base.main;
                a.setDecoratorSeed(a.setPopulationSeed(lower, p.x << 4, p.z << 4), 1, 4);
                b.setDecoratorSeed(b.setPopulationSeed(seed, p.x << 4, p.z << 4), 1, 4);
                for (int chest = 0; chest < 4; chest++) assertEquals(a.nextLong(), b.nextLong());
            }
        }
        assertTrue(accepted > 0);
    }

    @Test
    void cancellationAndInterruptLeaveTheNextCandidateAvailable() {
        StagedFilterSearch search = session(FilterCandidateSearch.Type.SHIPWRECK, 100);
        FilterCandidateSearch.Candidate expected = next(session(FilterCandidateSearch.Type.SHIPWRECK, 100));
        assertEquals(FilterCandidateSearch.Status.CANCELLED, search.next(100, 1000, () -> true).status);
        assertEquals(0, search.familiesChecked());
        Thread.currentThread().interrupt();
        try {
            assertEquals(FilterCandidateSearch.Status.CANCELLED, search.next(100, 1000, () -> false).status);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertEquals(expected.seedForValidation(), next(search).seedForValidation());
    }

    @Test
    void exactSeedRecheckDoesNotDependOnLegacySearchOrder() {
        for (FilterCandidateSearch.Type type : FilterCandidateSearch.Type.values()) {
            FilterCandidateSearch.Candidate staged = next(session(type, 100));
            FilterCandidateSearch.Candidate checked = FilterCandidateSearch.checkExactSeed(staged.seedForValidation(), type, false);
            assertNotNull(checked);
            assertEquals(staged.seedForValidation(), checked.seedForValidation());
            assertEquals(staged.main, checked.main);
            assertEquals(staged.lakeAttempts, checked.lakeAttempts);
        }
        boolean rejected = false;
        for (long seed = 0; seed < 100; seed++) {
            if (FilterCandidateSearch.checkExactSeed(seed, FilterCandidateSearch.Type.TEMPLE, false) == null) {
                rejected = true;
                break;
            }
        }
        assertTrue(rejected);
    }

    @Test
    void invalidBudgetsFailExplicitly() {
        assertThrows(IllegalArgumentException.class, () -> session(FilterCandidateSearch.Type.TEMPLE, 0));
        assertThrows(IllegalArgumentException.class, () -> session(FilterCandidateSearch.Type.TEMPLE, 65537));
        assertThrows(IllegalArgumentException.class, () -> session(FilterCandidateSearch.Type.TEMPLE, 1).next(0, 1000, () -> false));
    }

    @Test
    void lateCompletionCannotRetireAnUnrelatedFamily() {
        StagedFilterSearch expected = session(FilterCandidateSearch.Type.SHIPWRECK, 1000);
        StagedFilterSearch actual = session(FilterCandidateSearch.Type.SHIPWRECK, 1000);
        long currentSeed = next(actual).seedForValidation();
        assertEquals(currentSeed, next(expected).seedForValidation());
        actual.skipRemainingFamily(currentSeed ^ 1L);
        assertEquals(next(expected).seedForValidation(), next(actual).seedForValidation());
    }

    @Test
    void matchingCompletionRetiresOnlyItsRemainingSisters() {
        StagedFilterSearch expected = session(FilterCandidateSearch.Type.SHIPWRECK, 1000);
        StagedFilterSearch actual = session(FilterCandidateSearch.Type.SHIPWRECK, 1000);
        long seed = next(actual).seedForValidation();
        assertEquals(seed, next(expected).seedForValidation());
        actual.skipRemainingFamily(seed);
        expected.skipRemainingFamily();
        assertEquals(next(expected).seedForValidation(), next(actual).seedForValidation());
    }

    private static StagedFilterSearch session(FilterCandidateSearch.Type type, int sisters) {
        return new StagedFilterSearch(type, false, 123, sisters, (lower, main, bastion) -> true);
    }

    private static FilterCandidateSearch.Candidate next(StagedFilterSearch search) {
        FilterCandidateSearch.Result result = search.next(1000000, 60000, () -> false);
        assertEquals(FilterCandidateSearch.Status.PRELIMINARY, result.status);
        return result.candidate().get();
    }
}
