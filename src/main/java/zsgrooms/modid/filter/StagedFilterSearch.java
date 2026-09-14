package zsgrooms.modid.filter;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.ChunkRandom;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Offline, single-owner search session. Returning a proposal does not discard its remaining sisters. */
public final class StagedFilterSearch {
    static final long MASK = (1L << 48) - 1;
    private final FilterCandidateSearch.Type type;
    private final boolean overpowered;
    private final int sisterLimit;
    private final FamilyGate gate;
    private final SplittableRandom order;
    private final ChunkRandom random = new ChunkRandom();
    private final long stride;
    private long nextLower;
    private long lower;
    private long families;
    private long sisters;
    private long gateRejected;
    private int upperStart, upperStride, sisterIndex;
    private FilterCandidateSearch.StructureCheck current;

    /** Only use predicates whose result is invariant across all upper-16 values. */
    public interface FamilyGate {
        boolean allows(long lower48, ChunkPos main, ChunkPos bastion);
    }

    public StagedFilterSearch(FilterCandidateSearch.Type type, boolean overpowered, long searchSeed,
                              int sisterLimit, FamilyGate gate) {
        if (type == null || gate == null || sisterLimit < 1 || sisterLimit > 65536) {
            throw new IllegalArgumentException("A type, family gate and 1-65536 sister budget are required");
        }
        this.type = type;
        this.overpowered = overpowered;
        this.sisterLimit = sisterLimit;
        this.gate = gate;
        order = new SplittableRandom(searchSeed);
        nextLower = order.nextLong() & MASK;
        stride = (order.nextLong() & MASK) | 1L;
    }

    public FilterCandidateSearch.Result next(long maxWork, long timeoutMillis, BooleanSupplier cancelled) {
        if (cancelled == null || maxWork < 1 || timeoutMillis < 1 || timeoutMillis > 300000) {
            throw new IllegalArgumentException("Positive work budget and 1-300000 ms timeout required");
        }
        long started = System.nanoTime();
        long timeout = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        BooleanSupplier stop = () -> Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()
                || System.nanoTime() - started >= timeout;
        long[] rejected = new long[FilterCandidateSearch.Rejection.values().length];
        long work = 0;
        while (work < maxWork && !stop.getAsBoolean()) {
            if (current == null) {
                if (families == 1L << 48) break;
                lower = nextLower;
                nextLower = (nextLower + stride) & MASK;
                families++;
                work++;
                FilterCandidateSearch.StructureCheck structure = FilterCandidateSearch.checkGeometry(lower, type, overpowered, random);
                if (structure.rejection != null) { rejected[structure.rejection.ordinal()]++; continue; }
                if (type == FilterCandidateSearch.Type.SHIPWRECK
                        && !FilterCandidateSearch.shipwreckLayout(lower, structure.main, random)) {
                    rejected[FilterCandidateSearch.Rejection.SHIPWRECK_LAYOUT.ordinal()]++;
                    continue;
                }
                if (!FilterCandidateSearch.netherViable(lower, structure)) {
                    rejected[FilterCandidateSearch.Rejection.NETHER_BIOME.ordinal()]++;
                    continue;
                }
                if (!gate.allows(lower, structure.main, structure.bastion)) { gateRejected++; continue; }
                current = structure;
                upperStart = order.nextInt(65536);
                upperStride = order.nextInt(65536) | 1;
                sisterIndex = 0;
                continue;
            }
            long seed = sisterSeed(lower, upperStart, upperStride, sisterIndex);
            work++;
            FilterCandidateSearch.Candidate candidate = FilterCandidateSearch.checkSister(seed, type, overpowered,
                    current, true, rejected, stop);
            // A cancelled lake scan must be retried, not silently consume a sister.
            if (stop.getAsBoolean()) break;
            sisters++;
            sisterIndex++;
            if (sisterIndex == sisterLimit) current = null;
            if (candidate != null) return new FilterCandidateSearch.Result(FilterCandidateSearch.Status.PRELIMINARY,
                    work, started, candidate, rejected);
        }
        FilterCandidateSearch.Status status = Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()
                ? FilterCandidateSearch.Status.CANCELLED : System.nanoTime() - started >= timeout
                ? FilterCandidateSearch.Status.TIMED_OUT : FilterCandidateSearch.Status.LIMIT_REACHED;
        return new FilterCandidateSearch.Result(status, work, started, null, rejected);
    }

    /** Call only after a staged success, not after a hint or native validation rejection. */
    public void skipRemainingFamily() {
        current = null;
    }

    /** An asynchronous result must not retire the newer family currently being searched. */
    public void skipRemainingFamily(long completedSeed) {
        if ((completedSeed & MASK) == lower) current = null;
    }

    public long familiesChecked() { return families; }
    public long sistersChecked() { return sisters; }
    public long familiesRejectedByGate() { return gateRejected; }

    static long sisterSeed(long lower, int start, int stride, int index) {
        return (lower & MASK) | ((long) ((start + stride * index) & 65535) << 48);
    }
}
