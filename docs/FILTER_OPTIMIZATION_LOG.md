# Filter Optimization Notes

This guide records retained model-search optimizations, their validation and
the limits of the evidence. For current acceptance rules and search defaults,
see [Model Seed Finder](MODEL_SEED_FINDER.md). For production runs and recovery,
see [Overnight Seed Bank](OVERNIGHT_SEED_BANK.md).

## Equivalence Requirements

Keep production search model-only. A behavior-preserving optimization must retain
every acceptance criterion, selected structure/terrain anchor, candidate order
and Nether rule. Full-seed biome state cannot become a lower48-only gate merely
because terrain-noise initialization can be reused.

Compare completed fixed-family work, not just elapsed-time searches or accepted
counts. Require matching family/sister counts, all-decision digests and
byte-identical accepted JSONL. Use reversed execution order and independent
holdout offsets. These are sampled regression checks, not a proof for all seeds.
Keep seeds, family identities, executables and per-session reports private under
ignored `run/`; tracing remains opt-in.

## Retained Search Changes

### Surface Noise And Early Rejection

Cubiomes surface-noise initialization depends on the lower48 Java RNG state and
can be reused across sisters. Full-seed Overworld biomes are still reseeded,
with unchanged height calculations. Reference tests cover raw noise and
biome-dependent heights bitwise.

Wooded-patch checks stop after three misses make seven of nine successes
impossible. Pool checks stop only after a monotonic height/spread condition
cannot recover. Search order and the first acceptable anchor remain unchanged.

Candidate storage resets only populated counts and loot after geometry passes,
rather than clearing the entire family record on every failed attempt.
Consumers must never read array entries beyond their populated counts.

Paired fixed-work measurements on one development machine:

| Profile | Baseline mean | Optimized mean | Interpretation |
| --- | ---: | ---: | --- |
| Temple | 16.74 s | 13.94 s | Improvement on the measured workload |
| Shipwreck | 86.18 s | 34.59 s | Largest gain from rejecting impossible wood patches |
| Village | 24.21 s | 23.39 s | Mixed pairs; no established meaningful gain |

Nine completed development/holdout pairs matched decisions, counts and private
bank hashes. The village holdout contained only rejections, so it did not
validate accepted-village quality. Candidate-array reset was not isolated as a
separate timing experiment. These are small samples, not universal throughput.

### Shipwreck Biome Batching

The wooded-land scan generates one 45x45 scale-four biome map with the pinned
Cubiomes API, then reads the original nine samples per candidate. It preserves
the seven-of-nine rule, circular search radius, height checks and first-match
x/z order, including signed and non-grid-aligned anchors.

The first qualifying deep-ocean ravine is found before searching for wooded
land. These gates are independent; the selected ravine and wooded anchor remain
the same, and no wood search is needed if no ravine passes.

| Fixed-work shipwreck sample | Before | After |
| --- | ---: | ---: |
| Development | 32.28 s | 9.58 s |
| Reverse-order development | 31.89 s | 8.92 s |
| Independent holdout | 21.32 s | 11.27 s |

Batching and reordered checks were measured together, not separately. All nine
pairs across temple, shipwreck and village matched decision digests, counts and
private bank hashes. Reference tests compare original point queries and search
order, including successful selections and empty ravine lists. No speedup is
claimed for unchanged temple/village paths.

Native executables are Gradle test inputs, so rebuilding C invalidates relevant
test results. No model/library replacement or relaxed acceptance threshold is
part of these changes. A modeled lava opportunity remains distinct from a
guarantee of final Minecraft lava blocks.

## Sister Budgets And Family Caps

Attempt budgets and accepted-sister caps control different things. Caps of one
to four limit successful sisters; they do not force a failed family to produce
seeds. Native callers omitting a cap retain one. Operator defaults and explicit
overrides live in [the search script](../scripts/Search-FilterCandidates.ps1);
saved overnight banks retain their frozen policy.

Two independent prefix sweeps compared six budgets from 64 to 65,536 attempts
and caps of one, two and four. Completed observations exclude partially searched
final families. Prefix estimates include shared rejected-family work but cannot
recreate alternative JVM warmup/cache histories or I/O.

Bounded direct trials favored 4,096 attempts/cap two for temple and
16,384/cap four for shipwreck over their old full-sister/cap-one policies.
Village samples were too sparse to establish an optimum. These are policy
tradeoffs between yield and distinct families, not equivalent-speedup claims
or guaranteed overnight rates.

Private records retain lower48 family metadata for deduplication and serving.
Do not merge overlapping trial banks without validation. Family-aware service
selection reduces recent repeats; it is not a guarantee against repeats across
rooms or sessions. See [seed-service selection](../seed-service/README.md).

## Reproduce And Extend

Use the existing bounded tools, from the repository root:

- [Benchmark-ModelFinder.ps1](../scripts/Benchmark-ModelFinder.ps1): fixed-work
  baseline/candidate comparisons and private result artifacts.
- [Measure-ModelSearchPolicies.ps1](../scripts/Measure-ModelSearchPolicies.ps1):
  prefix-policy measurements.
- [Benchmark-ModelSearchPolicies.ps1](../scripts/Benchmark-ModelSearchPolicies.ps1):
  direct policy comparisons.
- [Test-ModelSearchCaps.ps1](../scripts/Test-ModelSearchCaps.ps1): cap exhaustion,
  exact global targets, deterministic prefixes and family metadata.

Read each tool's parameters before launching a run. Preserve baseline binaries
and allocate independent ranges; do not reset the shared production cursor.
Acceptance quality, worker scaling and rare-event throughput need separate
measurements. Do not weaken criteria or change gameplay behavior to report
a search optimization.
