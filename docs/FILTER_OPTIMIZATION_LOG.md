# Bounded Filter Optimization Pass

Started: 2026-09-13 04:28:32 UTC.
Hard deadline: 2026-09-13 06:28:32 UTC (two hours maximum, not a target).
Completed: 2026-09-13 04:56:40 UTC, approximately 28 minutes after starting.
Stopped early after three contained optimizations and validation; no ongoing searches.

## Constraints

- Model-only production search; no Minecraft fallback.
- Preserve every acceptance criterion, selected anchor, candidate order, and ZSG Nether rule.
- No gameplay changes, dependency upgrades, pushes, releases, or relay deployment.
- Preserve the pre-existing dirty worktree. Keep experiments small and reviewable.
- Stop early when measured improvements are exhausted. Benchmark loops have finite budgets.
- Keep numeric seeds in private artifacts only; public reports contain counts and hashes.

## Work Log

1. Complete: saved original executable and fixed-work baseline, with opt-in all-decision digest.
2. Complete: surface cache bitwise tests and development equivalence; provisional improvement retained.
3. Complete: equivalent early-rejection tests and count-bounded candidate-storage reset.
4. Complete: paired/alternating development runs and independent-sample equivalence.
5. Complete: final standalone build, all 15 tests, and normal-mode smoke tests for all three profiles.

## Initial Evidence

The recent ten-temple batch spent 46.71 seconds in biome checks, 38.75 in the
surface proxy, and 4.91 in the Nether-model call path out of 94.10 seconds.
Micro-optimizing loot or IPC is not the first priority for that workload.

Cubiomes `initSurfaceNoise` calls its 48-bit Java RNG initializer and creates
60 octave permutations. The current caller repeats this for each sister that
passes structure-biome checks. Reusing only that noise state is a candidate
equivalent optimization; full-seed Overworld biome state must still be reseeded.
The proposal will be checked against the unchanged executable and direct model
tests before it is retained.

## Development Results So Far

All cases use fixed family budgets and require completion before the time cap.
Private accepted JSONL files must be byte-identical and the digest of every
visited family/sister decision must match. This is much stronger than comparing
the number of accepted seeds alone, but remains sampled regression evidence.

| Profile | Baseline seconds | Cache-only seconds | Accepted |
| --- | ---: | ---: | ---: |
| Temple, 300,000 families | 16.98 | 14.29 | 4 |
| Shipwreck, 40,000,000 families | 86.70 | 73.78 | 3 |
| Village, 1,000,000 families | 23.11 | 21.73 | 1 |

All decision traces, family/sister counts and bank hashes match. Cache tests also
match 4,096 raw noise samples and 512 full-seed-biome-dependent heights bitwise.
These are single development runs, not final paired estimates.

The cache + early-rejection candidate also matched every decision and bank;
its development times were 15.28 / 32.66 / 22.54 seconds for temple / shipwreck /
village. The largest benefit is in rejecting non-wooded shipwreck sample patches.

The early-rejection candidate preserves the same conditions: a wooded patch
cannot reach seven of nine samples after three misses, and a pool cannot recover
after exceeding a monotonic min/max/spread/attempt-height bound. Independent
reference tests cover 256 wooded searches (including selected coordinates) and
2,560 pool decisions, with both accepting and rejecting cases.

An additional structural cost was found outside the stage timers: clearing the
entire approximately 10 KB Family record before every geometry attempt. Most
geometry attempts fail without consuming the arrays. The next candidate resets
only loot/count metadata after geometry passes, leaving arrays to be overwritten
up to their explicit counts. It must pass the same full decision/bank comparisons.

The first final paired temple/shipwreck runs matched all decisions and bytes,
with 1.10x / 2.51x speedups. Baseline timing varies, so these are provisional;
alternating-order repeats and independent stream offsets are still running.
The standalone build and 15 tests passed, including fail-closed protocols and
surface-reference comparisons. Gradle now fingerprints the native executables
used by its tests so C changes invalidate the test task's up-to-date state.

Artifacts: `run/filter-bench/20260913-042832/` (private banks, seed-free reports,
and frozen baseline/intermediate executables). Nothing is pushed or distributed.

## Final Paired Results

Development: two pairs per profile with reversed baseline/candidate ordering on
the second repeat. Each time limit was a safety cap; every measured run completed
the same fixed family budget. No search default or acceptance threshold changed.

| Profile | Baseline mean | Optimized mean | Interpretation |
| --- | ---: | ---: | --- |
| Temple | 16.74 s | 13.94 s | 16.7% less time; 1.20x throughput on this work set |
| Shipwreck | 86.18 s | 34.59 s | 59.9% less time; 2.49x throughput on this work set |
| Village | 24.21 s | 23.39 s | Mixed pair results, no established meaningful gain |

Separate holdout offsets were not used to select the optimizations:

| Profile | Baseline | Optimized | Accepted on each side |
| --- | ---: | ---: | ---: |
| Temple | 20.47 s | 16.69 s | 2 |
| Shipwreck | 36.93 s | 16.31 s | 3 |
| Village | 20.89 s | 21.21 s | 0 |

All nine completed pairs match the full decision trace, family/sister counts,
accepted counts, and SHA-256 of their private bank files. The holdout village
case validates rejection equivalence, not accepted village quality; accepted
village output is covered by development and smoke tests. These are small,
single-machine samples, not confidence intervals or universal seed-wait times.
The largest confirmed benefit is rejecting impossible wooded patches early.
The isolated benefit of candidate-array reset was not measured under a separate
paired design, so no standalone percentage is assigned to it.

## Retained Changes And Deferred Work

- Reuse only Cubiomes' lower48 surface-noise state. Full-seed biome seeding and
  every height calculation remain on the original mathematical path.
- Stop wooded-patch and pool checks only after their existing conditions become
  impossible. Search order and the first selected acceptable anchor are unchanged.
- Reset only candidate counts/loot after geometry passes. Never consume array
  entries beyond their populated counts.
- Add opt-in all-decision tracing and bounded paired benchmark tooling; production
  tracing is off by default. Native test executables are now Gradle task inputs.
- Do not alter village layout/omitted-loot policy, Nether checks, distances,
  sister budgets, or resource requirements. Do not introduce a new model/library.

The biome stage is now a larger fraction of temple cost. It genuinely depends
on the full seed; moving it to lower48 would be incorrect. Cubiomes already
provides established layer filtering, so a larger rewrite or broad spatial
cache needs separate evidence. Village's mathematical layout/terrain work and
omitted-loot behavior remain separate investigations; weakening that policy
would change accepted seeds and was deliberately excluded.

Lava-pool generation reliability is unchanged. The finder still accepts a
model-based pool opportunity, not guaranteed final Minecraft lava blocks.
No gameplay repair, Minecraft validation, push, release or deployment occurred.

The installed native executable matches the tested final candidate's SHA-256:
`4FE3C96C3C3B846D88C8B1D6C621A7DC1CBF63A3C34FDD743996199E49F593B3`.
This pass did not rebuild or modify the Minecraft mod's runtime code.

## V5 Shipwreck Biome Batching (2026-09-13)

This later pass uses the current v5 water/exposure criteria, not the older
executable above. Production remains model-only. No acceptance thresholds,
Nether requirements, stream traversal, sister budgets or profile version changed.

### Bottleneck And Approach

Recent larger searches put most shipwreck cost in `surface_proxy`: 158 of
189 seconds in the ten-accepted playtest. Temple cost was mainly sister biome
and terrain checks; village cost was mainly the full lower48 Nether model.
The water and temple exposure gates were too small to prioritize.

The shipwreck wooded-land scan repeatedly called `getBiomeAt` on overlapping
patches. Each call allocates a Cubiomes cache and regenerates the relevant biome
layers. The information is still required, but repeated generation is not.

- Incremental alternative: memoize individual samples. This saves overlapping
  points but still regenerates shared upstream layers for each distinct point.
- Selected replacement: generate one 45x45 scale-four biome map using the pinned
  Cubiomes `genBiomes` API, then read the original nine samples per candidate.
  Preserve the seven-of-nine rule, circular radius, height check and first-match
  x/z order, including signed and non-grid-aligned anchors.
- Remove unnecessary work: find the first qualifying deep-ocean ravine before
  searching for wooded land. These checks are independent; the chosen ravine
  and wooded anchor remain identical. No trees are searched when no ravine passes.
- A broader pipeline/library replacement is unnecessary for this bottleneck.
  Full-seed biomes cannot simply become lower48 checks. Cubiomes already performs
  early layer rejection for temple structure viability; that separate bottleneck
  needs more evidence before replacing its algorithm.

The upper bound from eliminating all shipwreck surface cost on that larger
sample would be about 6.1x overall. That is a sample-specific ceiling, not a
prediction: geometry, Nether, biome and spawn checks still have to run.

### Equivalence And Measurements

Artifacts: `run/filter-bench/v5-wood-batch-20260913/`, including frozen baseline,
two development pairs per profile (second pair reverses order), and one holdout
pair per profile. Every run completed its fixed family budget, not a time limit.

| Shipwreck sample | Before | After | Throughput ratio |
| --- | ---: | ---: | ---: |
| Development, first pair | 32.28 s | 9.58 s | 3.37x |
| Development, reverse-order pair | 31.89 s | 8.92 s | 3.57x |
| Separate holdout offset | 21.32 s | 11.27 s | 1.89x |

Each shipwreck side found the same three seeds per pair. Development surface
time averaged 23.62 s before and 0.43 s after. The two changes were measured
together; no separate speedup is attributed to batching versus check ordering.

All nine pairs across temple, shipwreck and village match family/sister counts,
all-decision digests, accepted counts and private bank SHA-256. Development
temple/village pairs include accepted seeds; their holdouts contain only
rejections. Unchanged temple/village timings varied, including one slow temple
candidate repeat; no performance benefit is claimed for those profiles.
These are small single-machine measurements, not universal waiting times.

The native oracle tests compare 256 wooded searches against the original point
queries and 256 reordered ship-surface searches against the original selection
order (including successful selections and empty ravine lists). Existing pool,
temple exposure, water and lower48 cache tests remain in place. The standalone
Gradle build and native tests passed. The three-profile model-only smoke suite
also passed, including repeated outputs and accepted temple/village water checks;
it created no Minecraft worlds.

Baseline SHA-256: `9F2BEB37AC519EE78FC646759E63BCF22AF5544AD2E391E06368CB1A46BFAD64`.
Optimized SHA-256: `D32840F2DB26A0DA4533BE3913EF40842AC27A7DE0A3AE263A0E1F8F579E1FED`.

Retain this measured shipwreck improvement. Next priorities are temple
biome/terrain work and village Nether-model cost, investigated independently
with the same fixed-work equivalence tests. Do not weaken criteria to claim a
speedup. No Minecraft runtime change, push, release or deployment was made.

## Sister Budgets And Family Caps (2026-09-14)

The success cap is now configurable from one to four. Native callers omitting
it retain cap one. The operator script uses per-profile starting values below;
explicit `-Sisters` and `-FamilyCap` always override them. All v5 acceptance
checks are unchanged. Caps limit successful sisters, while sister budgets
limit attempts; neither forces an unsuccessful family to produce seeds.
The global target is also enforced inside the sister loop.

### Measurement Design

Two independent 120-second prefix sweeps per type compared six attempt budgets
(64, 256, 1024, 4096, 16384, 65536) and three success caps (1, 2, 4).
They sampled the full budget with cap four and recorded aggregate times/counts
at earlier stopping points. Shared rejected-family/setup work is included.
Partially observed final families are excluded from every estimated policy.
No exact seeds or family IDs occur in the public measurements.

Completed Nether-qualified family samples: temple 43 development / 41 holdout,
shipwreck 31 / 30, village 19 / 16. The village sweeps contained only one
productive family in development and none in holdout, so their rankings alone
are not enough to choose a reliable village optimum. Prefix estimates also
cannot recreate each alternative's JVM warmup/cache history or exact I/O cost.

Promising policies were then tested directly, without profiling or tracing,
for 60 seconds each from a third offset. The table shows actual seed/family
counts; elapsed times were approximately one minute, not simulated timings.

| Type | Attempts / success cap | Accepted seeds | Productive families |
| --- | --- | ---: | ---: |
| Temple, previous | 65536 / 1 | 2 | 2 |
| Temple | 4096 / 2 | 16 | 10 |
| Temple | 4096 / 4 | 17 | 8 |
| Shipwreck, previous | 65536 / 1 | 19 | 19 |
| Shipwreck | 16384 / 4 | 45 | 15 |
| Shipwreck | 16384 / 2 | 29 | 16 |
| Village, previous | 256 / 1 | 0 | 0 |
| Village | 1024 / 2 | 1 | 1 |
| Village | 4096 / 4 | 1 | 1 |

A further village comparison used a fourth offset and reversed order, again
60 seconds per policy. All three settings found zero accepted seeds. No village
speedup ratio or statistically established optimum is claimed. Likewise the
temple/shipwreck results are promising starting points from bounded trials,
not guaranteed rates for every search range or confidence intervals.

### Initial Operator Defaults

- Temple: 4096 attempts, cap two. Almost the same direct yield as cap four,
  with more distinct families in that sample.
- Shipwreck: 16384 attempts, cap four. The two prefix sweeps also favored this
  budget over scanning every sister. Cap two remains available for a stricter
  related-seed limit; its direct sample found 29 seeds from 16 families.
- Village: 1024 attempts, cap two, explicitly provisional. This is a modest
  exploration increase over 256, not a proven optimum. The full-sister sweeps
  spent too much time in very few families to establish good rare-event rates.

Private multi-sister records now carry their lower48 `family` string. This is
metadata for future deduplication/family-aware serving, not a newly implemented
room seed distributor. Bank files from overlapping trials must not be merged
without deduplication. Profile v5, gameplay rules and Minecraft runtime code
remain unchanged. All measurements used one search worker; parallel scaling
has not yet been implemented or benchmarked.

Artifacts under `run/filter-bench/`:
`policy-v5-development-20260914`, `policy-v5-holdout-20260914`,
`policy-v5-direct-20260914`, and `policy-v5-village-confirmation-20260914`.
The pre-policy executable is preserved as
`v5-wood-batch-20260913/pre-policy.exe`.

Verification includes synthetic accounting tests for all 18 policies, failed
families and interrupted observations; invalid native cap/tuning arguments;
and live accepted families for all three profiles. Live tests compare caps
one/two/four, cap exhaustion before a larger global target, exact target stopping
inside a family, deterministic prefixes/metadata, repeated banks and cap-one
byte equivalence with the previous executable. The standalone Gradle build
passed. No push, release, relay deployment or Minecraft generation was performed.
