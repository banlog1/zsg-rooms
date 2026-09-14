# Stage 4 Validation Budget

Production Stage 4 Minecraft validation has been removed. This is historical
calibration data; see the [model-only replacement](MODEL_SEED_FINDER.md).

For the subsequent model-first architectural decision, see
[Filter Architecture](FILTER_ARCHITECTURE.md). This document measures the strict
revision-7 reference pipeline; its proof requirements are not the target design.

## Scope

Pipeline revision 7 instruments and schedules the existing
`zsg-lava-wooded-stables-v3` profile. No resource thresholds, distances, layouts,
biome categories, pool definitions, generation order within a candidate, or
acceptance checks were relaxed. This is offline operator tooling, not race code.

## Scheduling

The coordinator keeps the lower-48/sister search and its template caches on its
own server thread. A bounded FIFO feeds persistent verifier JVMs. Each verifier
boots Minecraft once, then creates and closes fresh Overworld/Nether worlds for
each job on its own server thread. Never run parallel `ProbeWorlds` in one JVM:
it temporarily swaps the server's world map and generation-evidence state.

- Default: one worker, two queued jobs, at most three outstanding results.
- `-PfilterWorkers=2` allows two workers; each worker has a 3 GiB heap ceiling,
  in addition to the coordinator's 3 GiB ceiling. These are not memory-use measurements.
- `-PfilterQueue=1` through `16` controls buffering; completed but uncommitted
  results also count against the coordinator's outstanding-work bound.
- `-PfilterWorkers=0` retains serial validation for controlled comparisons.
- Atomic private JSON handoffs carry the exact seed. Public reports and progress
  logs contain job UUIDs and metrics, not seed values. Workers reconstruct and
  recheck the exact candidate; they never read the producer's mutable family cache.
- Results are committed in submission order. Only one passing sister is staged
  per family. A late result cannot retire a different family now being searched.
- Search stops at the soft time/trial/target/stop-file limit, then drains the
  bounded outstanding work. In-progress checks are not truncated into rejections.
  Worker startup counts toward the soft time budget; backlog draining can exceed it.
- Worker startup has a three-minute timeout; dispatched jobs have a ten-minute
  safety timeout. A worker error, exit or timeout fails the batch, never accepts
  a seed or substitutes a weaker check. Normal shutdown joins/stops child JVMs;
  a shutdown hook also handles normal JVM termination.
- The FIFO is process-local, not an automatically resumed distributed queue.
  Private job files are retained for explicit diagnostic replay, not published
  or automatically retried after a crash.

## Reading The Measurements

Every completed validation has a `checks` object. Each named operation records
calls, false boolean results, exceptions, inclusive milliseconds and exclusive
milliseconds. `checkTotals` aggregates these and counts candidates reaching each
check separately from its invocation count. A candidate testing three pools
counts as one candidate but three provenance calls.

Use **exclusive** time to rank budget consumption. For example, `temple.loot`
includes `temple.generate` and `loot.resolve`; summing all inclusive times would
count chunk generation twice. The exclusive `stage4` time is unclassified glue
and instrumentation overhead. `world.create`, `world.close`, `worker.*` preparation,
queue waiting and process startup are outside Stage 4 and reported separately.
These are elapsed service timings, not CPU samples; they include blocking, GC,
scheduling and any generation tasks awaited by the server thread.

`falseResults` is not the number of rejected seeds: alternative pools or stables
pieces can fail while a candidate still passes. Missing check rows mean **not
reached**, not zero-cost. `producerMs` describes producer work (including native
validation in serial mode). For completed queued candidates, `totalMs` measures
proposal start through ordered result collection, including queueing and any
wait for earlier results. `workerMs`, `queueWaitMs` and `completedAtMs` provide
the worker side. The initial three reports below predate the `totalMs` correction;
use their check traces, not their per-attempt `totalMs`, for validation costs.

From the repository root, with the existing development EULA accepted:

```powershell
.\gradlew.bat runFilterBank -PfilterProbe -PfilterType=temple -PfilterTrials=300 -PfilterTarget=3 -PfilterMaxMinutes=1 -PfilterSisters=1000
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Summarize-FilterBank.ps1 -Report run/filter-bank/bank-report.json
```

Reports are `run/filter-bank/bank-report.json` and `reports/<runId>.json`.
Private replay inputs are `private-jobs/<runId>/<jobId>.json`. Worker logs live
under `workers/<runId>/<workerNumber>/`. All are local ignored development output.
Do not share private jobs or private staging files as performance reports.

## Initial Measurements

September 13, 2026, same laptop/Java 25 development setup, regular variants,
1,000 sisters per family, unchanged enabled early checks and hints. Each batch
had a one-minute soft limit and one persistent worker. These tiny cold-start
samples identify costs; they do not estimate accepted seeds/hour or establish a
parallel speedup. All candidates ultimately rejected.

| Type | Proposals | Native validations | Dominant Stage 4 check | Candidates reaching it | Exclusive total |
| --- | ---: | ---: | --- | ---: | ---: |
| Temple | 68 | 5 | `terrain.prepareChunks` | 2 | 16.118 s |
| Temple | 68 | 5 | `start.main` | 5 | 11.002 s |
| Temple | 68 | 5 | `temple.generate` | 5 | 6.494 s |
| Village | 144 | 3 | `village.generate` | 3 | 27.708 s |
| Village | 144 | 3 | `start.main` | 3 | 9.757 s |
| Shipwreck | 33 | 7 | `spawn.actual` | 7 | 17.455 s |
| Shipwreck | 33 | 7 | `start.main` | 7 | 17.380 s |
| Shipwreck | 33 | 7 | `shipwreck.generate` | 7 | 8.947 s |

Temple Stage 4 totaled 35.237 s. Chunk preparation for pool inspection used
45.7%, main-start construction 31.2%, and temple generation 18.4%. Pool-shape
inspection itself took only 40.49 ms across two candidates; snapshot construction
took 12.84 ms. Three of five candidates failed the exact spawn/pool envelope
before terrain capture; of the remaining two, one lacked a surface pool and one
failed spawn distance. Only one reached provenance and entry-distance checks;
none reached the pool wood-biome check.

Village Stage 4 totaled 37.993 s. Village generation used 72.9% and main-start
construction 25.7%. Of 62 producer layout predictions, 59 rejected before any
world was created. Three reached real loot/golem checks, two failed iron, and
the remaining one failed the spawn/pool envelope. None reached pool capture.

Shipwreck Stage 4 totaled 44.037 s. Actual spawn used 39.6%, main-start construction
39.5%, and wreck generation 20.3%. One spawn search alone took 15.218 s, so the
mean is sensitive to an outlier. All seven passed resources but failed spawn
distance; none reached actual-tree, ravine or final stables-integrity checks.
Those later checks remain instrumented but unmeasured in these batches.

Worker startup took 31.2/32.0/33.2 s respectively. Total measured batch time,
including startup and drain but excluding coordinator bootstrap, was
76.0/90.9/84.1 s. World cleanup alone added 3.21/3.35/4.71 s. The queue reached its
three-outstanding-job bound in all three runs.

Report IDs, in temple/village/shipwreck order:

- `ff9d4eb3-23e0-4ee8-ab4d-37ccd3f7a216`
- `5bf1223b-f1b5-4430-ac76-4e56696e770e`
- `4cacf9f8-073b-493e-9055-0cac135ff26a`

The first two runs predate the final `checkTotals`/bastion verification-count
reporting adjustment; their per-candidate traces contain the measurements above.

## Which Checks Can Move To Libraries?

There is no new external-library acceptance shortcut enabled by this change.
"Eligible" below means the predicate is exactly reproducible without a decorated
world, **after** matching the specific MC 1.16.1 implementation and differential
tests. It does not mean an untested library return value is already trusted.

### Exact, World-Free Predicates

| Existing check | Replacement scope | Required safeguards |
| --- | --- | --- |
| Structure placement/biome eligibility feeding `start.*` and `present.*` | Cubiomes placement and biome functions can supply the predicates. Placement geometry is already outside Stage 4. | Correct 1.16.1 salts, shared Nether placement selection, biome scale/Y and all start conditions. A placement prediction alone is not a native `StructureStart` or a proof that final blocks exist. |
| `village.treeBiome`, temple `pool.wood`, `ravine.deepOcean` | Exact biome queries via Cubiomes or SeedFinding biome sources; retain existing sample positions/categories. | Village/temple use block-biome sampling at Y=255, not the structure's coarse noise-biome sample. Match Voronoi access and negative-coordinate rounding. Temple pool coordinates still come from real terrain. |
| `shipwreck.layout` | SeedFinding's shipwreck generator exposes beached status, rotation and template. | Match 1.16.1 carver-seed call order, biome sample and the four allowed templates. Does not replace actual chests or their integrity. |
| `temple.resources`, `shipwreck.tools`, `shipwreck.food`, chest portion of `village.iron` | Arithmetic already requires no world. SeedFinding loot machinery could provide predicted counts. | Exact table version, loot seeds, call order, luck and stack/count semantics. Keep final chest presence checks; village golem presence is separate. Our temple prediction already uses vanilla loot code without generating terrain. |
| `pool.entryDistance`, `pool.spawnDistance`, `pool.spawnEnvelope`, `shipwreck.spawnDistance`, `ravine.distance` | Pure arithmetic; no library needed. | Inputs must be exact. Replacing the native spawn or observed ravine/pool with an approximation is not the same predicate. |

Cubiomes documents separate structure-position and biome-viability APIs and
versioned biome sampling. Neither a biome proxy nor a placement attempt certifies
actual trees, loot or usable terrain. See its [biome API](https://github.com/Cubitect/cubiomes/blob/master/generator.h)
and [structure API](https://github.com/Cubitect/cubiomes/blob/master/finders.h).
SeedFinding's [shipwreck generator](https://github.com/SeedFinding/mc_feature_java/blob/main/src/main/java/com/seedfinding/mcfeature/structure/generator/structure/ShipwreckGenerator.java)
directly computes the relevant layout rolls.

### Layout Prediction Is Not Final Integrity

`bastion.layout` and `village.nonAbandoned`/`village.ironPotential` do not inherently
need decorated chunks. We already run vanilla bastion and village layout predictors
before Stage 4 and compare their native NBT on survivors. Keep this working path
until another generator matches complete layouts across biomes, rotations and
sister seeds. In particular, a bastion **type** prediction does not prove that
the two required stables pieces exist.

The inspected SeedFinding [village generator](https://github.com/SeedFinding/mc_feature_java/blob/main/src/main/java/com/seedfinding/mcfeature/structure/generator/structure/VillageGenerator.java)
is not a ready substitute for all our variants: its `STARTS` map has null entries
for plains and savanna. Its [registered generators](https://github.com/SeedFinding/mc_feature_java/blob/main/src/main/java/com/seedfinding/mcfeature/structure/generator/Generators.java)
also do not provide a bastion generator. Cubiomes has a fortress-piece API, but
that does not supply a complete bastion jigsaw layout.

The [SeedFinding pyramid generator](https://github.com/SeedFinding/mc_feature_java/blob/main/src/main/java/com/seedfinding/mcfeature/structure/generator/structure/DesertPyramidGenerator.java)
contains placeholder chest positions. It can participate in loot prediction, but
those positions must not replace our four actual basement-chest checks.

### Keep Real Minecraft Validation

| Existing checks/work | Why a library prediction is insufficient |
| --- | --- |
| `spawn.actual` | The exact vanilla spawn search depends on real terrain and valid spawn blocks. Cubiomes explicitly warns its `getSpawn` may be inaccurate because of grass blocks. Our 32/48-block limits are too tight to substitute an estimate. |
| `temple.chestCount`, `shipwreck.chestCount`, generated village loot scan | Need actual surviving chest block entities, tables and nonzero loot seeds after placement/decoration. Predicted contents do not prove container existence. |
| `village.golem` | A template marker is not proof that the iron golem was actually generated inside the village bounds. |
| `pool.shape`, `pool.provenance`, safe shore in pool capture | Need final exposed source lava, connected area, 2x2 section, safe standing space and successful natural lake evidence, not just a lava-feature attempt. |
| `shipwreck.actualTrees` | Requires actual logs, soil, canopy, trunk height and dry standing space; a forest/coastline biome is only a proxy. |
| `ravine.evidence`, `ravine.openColumn` | Need native carving outcome and actual lava/open water column after generation. An RNG ellipse or base-height column is not proof of an unobstructed usable ravine. |
| `stables.gold`, `stables.chests` | Need final surviving gold and three chest block entities, not only predicted jigsaw pieces. |

"Requires Minecraft" means with the libraries and evidence currently available,
not a theoretical impossibility of reimplementing world generation. SeedFinding's
[surface generator](https://github.com/SeedFinding/mc_terrain_java/blob/main/src/main/java/com/seedfinding/mcterrain/terrain/SurfaceGenerator.java)
provides noise/surface/bedrock columns and optional jigsaw terrain contributions;
those are not a complete decorated-world, lake, tree, carving and entity pipeline.
The conclusion that these APIs cannot certify our final integrity checks follows
from comparing their outputs with the block/entity checks in this repository.

## Verification

The build includes 358 passing tests with `-PfilterProbe`: 353 main tests and
five offline result-queue tests. New cases cover nested timing accounting,
candidate-versus-invocation counts, thread-local isolation, exceptional results,
out-of-order completions, target bounds, duplicate sisters and late family
retirement. Four native comparisons of saved village jobs (including a repeated
candidate) matched all semantic report fields between two persistent worker JVMs
and serial validation. These are rejection-path comparisons, not proof of a
fully passing end-to-end bank entry.

```powershell
.\gradlew.bat build -PfilterProbe
.\gradlew.bat runFilterBank -PfilterProbe -PfilterVerifierTest=private-jobs/5bf1223b-f1b5-4430-ac76-4e56696e770e
.\gradlew.bat runFilterBank -PfilterProbe -PfilterEarlyTest=true
```

The middle command requires that local private job directory; it deliberately
does not embed numeric seeds in committed fixtures. Its result is
`run/filter-bank/verifier-test.json`. The early-check suite covers positive
pool/shore/tree terrain fixtures and native-versus-predicted village layouts.

## Next Decisions

Keep criteria frozen. The measurements favor investigating how much **chunk
generation can be avoided**, not optimizing loot arithmetic or pool predicates.
An exact early spawn rejection would be valuable, especially for shipwrecks,
but an approximate spawn hint is not enough. Likewise, predicting poor village
resources before generating the village could help only after exact loot/golem
parity is established. Do not delete final terrain or integrity checks to achieve
a faster benchmark.

Repeat representative fixed candidate sets with serial/one-worker/two-worker
runs, account separately for startup, service time, queue wait and memory, and
include positive fixtures that reach wood/ravine/stables checks before claiming
a complete validation-cost model. No seed-pool production rate is established yet.
