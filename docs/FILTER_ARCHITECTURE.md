# Model-First Filter Architecture

**Superseded production design:** [Model-only finder](MODEL_SEED_FINDER.md)
removes Minecraft entirely from production acceptance. Everything below records
the earlier revision-8 investigation; its native fallback is calibration-only.

## Decision

The objective is a playable, fair pool, not a proof of every generated block.
This supersedes the assumption in the revision-7 research/budget documents that
every accepted seed must receive complete native-world confirmation. Existing
v3 tooling retains an exact reference mode while individual checks migrate.
No uncalibrated model result is silently relabeled as an exact verification.

Use established models first. Calibrate uncertain properties on a sample,
measure gameplay-relevant errors, then accept clear passes, reject clear failures,
and reserve native generation for ambiguous boundaries or unsupported properties.
Rare errors alone do not justify native verification of every production seed.
Model version, acceptance policy, and sample results must travel with a pool build.

## Current Bottleneck

Revision-7 check traces on the development laptop, not accepted-seed throughput:

| Batch | Candidates reaching native | Stage 4 total | Dominant measured work |
| --- | ---: | ---: | --- |
| Temple | 5 | 35.24 s | Pool-area chunk preparation 16.12 s; main starts 11.00 s; temple generation 6.49 s |
| Village | 3 | 37.99 s | Village generation 27.71 s; main starts 9.76 s |
| Shipwreck | 7 | 44.04 s | Native spawn 17.45 s; main starts 17.38 s; wreck generation 8.95 s |

All seven shipwrecks failed spawn distance. One spawn search took 15.22 s.
Village loot arithmetic was only 8 ms over 11 calls; temple pool scanning was
40 ms over two candidates. Optimizing that arithmetic cannot solve this budget.
Worker startup was 31-33 s and cleanup another 3-5 s per batch. Startup amortizes
in long runs; generation repeats per seed. These short rejection-heavy batches
do not establish accepted seeds/hour or a reliable steady-state speedup.

The per-seed proof requirement is self-inflicted. Terrain generation is
fundamental only for questions that really require final terrain. Persistent
workers remove repeated JVM startup but not generation. A queue hides producer
work only while the verifier has spare capacity; it cannot increase its service
rate. At observed averages, a single worker's Stage-4-only ceilings were about
511 temple, 284 village, or 572 shipwreck candidates/hour, before cleanup and
other work. These are illustrative service ceilings, NOT accepted seeds/hour.

Native `GeneratedStructureProbe.start -> getChunk(STRUCTURE_STARTS) ->
ChunkGenerator.setStructureStarts` is not a target-structure-only calculation.
It tests the stronghold feature as well. Its eligibility path initializes the
generator's stronghold-position list lazily. Avoiding native generation avoids
this unrelated work; the traces do not isolate its fraction of `start.main`.

## Information Audit

| Stage / reason | Information actually needed | Established alternative and earlier placement | Can native work disappear? |
| --- | --- | --- | --- |
| Lower-48 geometry | Main structure, bastion and fortress candidate coordinates | Cubiomes/ZSG placement mathematics; existing Java/native comparison matched 600,000 cases | Yes, already chunk-free. Native C is faster locally, but this is not the present bottleneck |
| Nether viability | Relevant biome permits bastion/fortress | Cubiomes or SeedFinding 1.16 Nether biome sampling; lower-48 stage with correct coordinates/version | Yes for viability. Final terrain interference is a separate, normally sampled risk |
| Full-seed biome checks | Main structure viability, village variant, tree-biome proximity | Cubiomes/ZSG/SeedFinding; upper-16 sister stage | Yes. Preserve sampling coordinates and distinguish coarse noise biomes from block biomes |
| Stables layout | A good gap and triple-chest rampart | Existing chunk-free `BastionLayoutPrediction`, upstream BastionGenerator template model; lower-48 family gate | Yes for layout. Rechecking its NBT and every final gold/chest block per seed is excess proof unless samples demonstrate gameplay damage |
| Temple resources | Vanilla loot-table iron/diamond totals | ZSG/SeedFinding loot RNG; existing family-level `TempleLootPrediction` | Yes. Final four-chest integrity can become a sampled audit rather than mandatory generation |
| Village resources | Non-abandoned village, chest iron + minimum golem drop >= 7 | Cubiomes variant and existing chunk-free village layout can reject absent iron templates; reviewed SeedFinding village generator is incomplete | Not yet for the complete resource requirement. Build/test loot+template entity prediction; do not substitute an expected golem drop or mere chest potential for the budget |
| Shipwreck layout and loot | Full upright wreck, rotation, food and iron | ZSG and SeedFinding ShipwreckGenerator/loot models, lower-48 layout then full-seed beach/biome decisions | Yes for predicted layout/resources. Rare damaged-chest cases warrant sampling, not automatically full verification |
| Spawn | Gameplay distance from common start to intended resource | ZSG uses Cubiomes `getSpawn`; includes approximate terrain search beyond biome-only hint | Yes if sampled error is acceptable; sister stage after cheap gates. This is approximate, not lower-48-only |
| Surface lava | A usable nearby pool, not isolated lava | GoATS predicts decorator attempts and applies biome/height heuristics; SeedFinding terrain can improve exposure screening | Potentially, after pool-success calibration. An attempt is not proof of successful formation, source count or access. Current native area scan is an expensive oracle, not necessarily the eventual production implementation |
| Trees | Village/temple tree-biome proxy; shipwreck accessible real wood | ZSG/Cubiomes directly supply biome proxy. Reviewed terrain libraries do not supply complete decorated trees | Proxy needs no native world. For shipwrecks, calibrate wooded-land/tree-density model against actual accessible wood; native fallback only if prediction is insufficient |
| Ocean ravine entry | Suitable opening to deep lava within range | ZSG/GoATS carver predictions; terrain libraries for substrate/depth | Replace geometric/carver RNG checks analytically. Calibrate accessibility, since nearby features/carving can alter the final opening |
| Nether route | Traversable route | ZSG Java helper samples SeedFinding terrain columns at coarse intervals | A model already supports approximate playability; do not strengthen it to exhaustive native walkability accidentally |
| Final fresh-server recheck | Detect implementation/model mistakes | Differential sample and periodic pool audit | Remove mandatory per-entry recheck after validating a model-based profile. Retain the command as an audit oracle and for genuinely ambiguous jobs |

The reviewed GoATS pool algorithm produces potential positions, not a completed
lake volume. The reviewed SeedFinding terrain implementation supplies noise and
surface columns, not all decorated world features. Those are specific gaps, not
a general argument against trusting these libraries. A playable-pool model can
still be adequate without reproducing all final blocks; measure its success rate.

## Options And Impact

1. Keep/local-optimize: defer spawn ahead of loot generation; use fewer native
   chunks. In the seven-wreck batch, moving the same exact rejection first could
   avoid much of 8.95 s of wreck generation, but spawn itself still generates
   chunks and pays startup costs. Pool scanning micro-optimizations have <1%
   measured Stage-4 upside in that temple batch.
2. Replace with models: spawn filtering before world creation can avoid the
   entire downstream service time of clearly unsuitable seeds. If it had
   correctly rejected all seven wrecks, up to 44.04 s of Stage 4 would disappear,
   minus model time. This is an upper bound, not an observed result. Reliable
   loot/layout predictions also eliminate generation on passes, not just failures.
3. Remove by earlier equivalent check: existing family-level temple loot and
   bastion layout results should become authoritative for those properties.
   Keep sampled parity tests rather than recomputing them per sister/native world.
   Village iron potential is NOT equivalent to actual resource sufficiency.
4. ZSG-style redesign: lower48 geometry/layout/loot -> sister biomes/spawn/terrain
   predictions -> accepted model pool; bounded native queue handles samples and
   ambiguous cases. Largest expected end-to-end gain because most worlds vanish.
   ZSG does not implement all of our new gameplay requirements unchanged.

Recommendation: option 4 incrementally, replacing whole checks, not polishing
the proof pipeline. First calibrate the established spawn model and replace the
ad hoc hint. Next promote existing loot/layout models to acceptance, then
calibrate GoATS-style pool success and wooded land. Only then consider an
in-memory, status-limited Minecraft backend for remaining unsupported checks.
SeedChecker demonstrates that architecture, but its reviewed Java version and
unclear source licensing make blind vendoring inappropriate. `FEATURES` rather
than `FULL` generation may avoid lighting, entities and save work, but adjacent
decoration/order and template entities need parity tests first.

The native-free theoretical ceiling is producer/model throughput, currently
unmeasured for the complete profile. With fallback fraction `q`, validation work
is roughly `q * nativeServiceTime + modelTime` per candidate; worker parallelism
does not remove that term. Do not promise a speedup until `q` and acceptance rate
are measured on the redesigned funnel.

## Spawn Calibration

### Implemented In Pipeline 8

The offline bank now defaults to the pinned Cubiomes spawn model. The old
biome-only `SpawnSearchHint` is removed from production (its historical fixture
selector remains local to an exact-verifier test). The existing
`-PfilterSpawnHint=false` switch now selects fully native spawn checks for audits.

For the 32-block temple/village and 48-block wreck distance checks, model
distance <= radius minus 16 passes; distance > radius plus 16 rejects; the
intermediate band lazily resolves native spawn once per world. A model position
never overwrites Minecraft's spawn. Reports and staged entries identify
`CUBIOMES_MARGIN_16_V1` versus `NATIVE`, plus model pass/reject/native-request counts.
Private worker jobs carry the pinned model revision and prediction alongside
their existing seed identity. Old jobs without model fields retain native mode.

Before creating a world, wrecks use that distance rule directly. Temple/village
early rejection uses a broader 128-block envelope around the structure (pool
radius 96 plus spawn distance 32), with the same 16-block tolerance. It does NOT
treat a predicted lake-attempt position as a verified pool. Once an actual pool
is found, the tighter spawn check uses its anchor or the main structure.
The exact-mode circular pool-envelope rejection is preserved.

This replaces the spawn check only. Other v3 native checks and non-exportable
development staging remain. Skipping spawn generation can change decoration
request order; model mode does not promise block-perfect equivalence. Before
pool export, finish the remaining model assessments rather than silently
claiming all native verification has gone away.

The bounded worker smoke batch `e4e5fcbb-ba51-4c16-ac19-27884a28d913`
processed 281 proposals: 272 were screened out without a candidate world, nine
were queued and all nine completed with the model policy preserved. Those nine
failed wreck resources before reaching their final spawn-distance check. There
were zero full-profile passes; this is IPC/early-screen coverage, not proof of
full-profile acceptance or a measurement of the production fallback fraction.
The batch took 65.62 s including 34.64 s worker startup and backlog draining.

Unit tests cover clear-decision native bypass, boundary fallback/memoization,
alternative anchors, exact-mode circular envelope, and worker revision/coordinate
validation. The native headless samples independently compare model predictions
against real spawn, including pass, boundary, and reject cases.

### Initial Measurements

On 2026-09-13, Java 25, this development laptop:

| Sample | Size | Distinct lower48 families | Exact X/Z matches | Median / p95 / max error | Total model / cold native spawn time |
| --- | ---: | ---: | ---: | --- | --- |
| Initial: 24 unfiltered + 7 saved wreck + 3 saved village | 34 | 27 | 26 | 0 / 32 / 272 blocks | 0.267 s / 106.81 s |
| Fresh boundary-stratified holdout | 27 | 27 | 21 | 0 / 13 / 100 blocks | 0.110 s / 78.54 s |

Most model calls were around 1-2 ms; the initial slowest was 192 ms. Native
measurement includes fresh-world creation and spawn search, but excludes
cleanup. Model measurement includes IPC, not process startup or the repeatability
call. These demonstrate check-level savings, NOT whole-pipeline throughput.

The holdout used a preselected 16-block margin, nine new families per type, with
three model PASS, three VERIFY and three REJECT cases each. All nine clear passes
and nine clear rejections agreed with the native anchor-distance predicate.
Nine cases required verification, including the 100-block error. This is a
deliberately stratified small sample, not a claim that production fallback is
33%, that error is bounded by 16, or that failure probability is zero. A rare
mislocated spawn can still affect travel distance. Larger periodic audits should
track that gameplay impact, particularly coastal cases, without forcing every
production seed through the oracle.

```powershell
.\gradlew.bat runFilterBank -PfilterProbe -PspawnCalibration=true -PspawnCalibrationHoldout=true --offline --console=plain
```

This writes `run/filter-bank/spawn-holdout.json`, separately from the initial
calibration. Selection targets three cases per decision/type within a 60-second
search budget per type; the report explicitly records achieved coverage. It uses
fresh search streams and skips the remaining family after selecting a case.

### Calibration Command

```powershell
.\gradlew.bat runFilterBank -PfilterProbe -PspawnCalibration=true --offline --console=plain
```

Builds the pinned Cubiomes worker and uses one persistent private stdin/stdout
process with request timeouts. No seeds in command arguments or public reports.
Default corpus: 24 fixed unfiltered seeds plus up to eight unique saved candidate
jobs per type, each compared with a fresh native world's server spawn. Repeated
model requests check repeatability. `-PspawnCalibrationSamples=64` enlarges the
unfiltered component. Saved jobs are selection-biased and may share lower48
families; the report records this and the distinct family count.

`run/filter-bank/spawn-calibration.json` records per-case model/native times,
Chebyshev X/Z error, biome categories, and pass/verify/reject decisions for
margins 0/8/16/32/48/64/96/128. Lake anchors here are predicted attempts, not
validated pools, so candidate decisions measure an early screening predicate,
not full playability. World spawn is distinct from a player's randomized spawn
offset. A sample maximum is NOT a guaranteed error bound. Reserve a new corpus
for holdout validation after selecting a margin; do not tune and claim accuracy
on the same sample.

## Reviewed Sources

- [ZSG temple filter](https://github.com/DuncanRuns/ZigSeedGlitchless/blob/073e1d1f3150213c7c3787eda7ef8106cb789817/src/filter_1_16_desert_temple.zig), village/shipwreck siblings and `ZSGJavaBits.java` in the same pinned local checkout.
- [Cubiomes spawn implementation](https://github.com/Cubitect/cubiomes/blob/e61f90580cbdd883214a8054670dacae655e59c0/finders.c) and [accuracy warning](https://github.com/Cubitect/cubiomes/blob/e61f90580cbdd883214a8054670dacae655e59c0/finders.h).
- [GoATS lava model](https://github.com/AeroAstroid/GoATS-filter/blob/b329d026a57cb3ceec9ab0c4c1c4651ab0b6c066/filters/lavapool.c).
- [SeedFinding feature generators](https://github.com/SeedFinding/mc_feature/tree/b612ebee5723188045750757c117dad9e385a7f2) and [terrain generator](https://github.com/SeedFinding/mc_terrain/tree/a03e440ec5b282e399382f2cc5ad0db91b438d2e).
- [SeedChecker 1.16.1 reference](https://github.com/jellejurre/seed-checker/tree/efe7c8fd742b694c54db0fd7336e186c0d698edd).

Source conclusions also use the pinned local checkouts and Minecraft 1.16.1
Yarn sources in the development cache. No source copied from unlicensed projects.
