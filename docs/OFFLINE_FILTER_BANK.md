# Offline Custom Filter Bank

**Legacy calibration harness only.** Production search is now the standalone
[model-only finder](MODEL_SEED_FINDER.md), with no per-candidate Minecraft
verification or fallback. The commands below require `-PfilterCalibration=true`
when they launch Minecraft and are retained for offline comparisons only.

Pipeline revision 8 replaces the spawn hint with a calibrated Cubiomes model
and lazy native boundary checks. See [model-first architecture](FILTER_ARCHITECTURE.md)
for the redesign, sample results, remaining native checks and exact-mode override.

Pipeline revision 7 adds check-level Stage 4 timings and a bounded queue of
persistent, isolated Minecraft verifier processes. See [validation budget and
library replacement audit](FILTER_VALIDATION_BUDGET.md) for operation, measured
check reach counts, worker settings and which checks still require real terrain.

## Scope

The development-only bank runner searches and checks seeds on the operator's
machine. It is not called by room creation, host prefetch, world loading, or a
runner's server tick. It does not replace the installed FSG filters or deploy a
seed service. Regular variants are the initial target; the bank runner does not
claim to produce OP variants.

Profile `zsg-lava-wooded-stables-v3` is our explicit custom profile, informed by
the public [ZigSeedGlitchless source](https://github.com/DuncanRuns/ZigSeedGlitchless).
The public repository omits parts of the deployed generator. This is not a claim
of identical acceptance to its private service. Optional upstream obsidian and
terrain-route checks are not enabled by this profile.

## Acceptance Checks

All variants require actual Overworld structure, bastion and fortress starts at
the preliminary candidate coordinates. The regular Nether bounds remain 96
blocks per axis from Nether 0,0 for the bastion and 256 for the fortress. Biome
eligibility and vanilla placement salts are checked during preliminary search.

For stables, the generated jigsaw layout must contain both
`hoglin_stable/walls/side_wall_1` (good gap) and
`hoglin_stable/ramparts/ramparts_1` (triple chest rampart). Final validation also
generates the bastion and checks that at least one qualifying wall retains its
gold blocks and one qualifying rampart retains all three chest block entities.
The template contract is checked against Minecraft resources in every rotation.
Other bastion types retain their vanilla layouts. This is not a pathfinding proof.

### Temple

- Four real basement chests with intact, seeded vanilla temple loot tables.
- Seven iron, or four iron plus three diamonds, for bucket, ignition and pickaxe.
- A usable surface lava pool within a 96-block circle of the temple reference.
- Pool within 224 blocks per axis of Overworld 0,0.
- Nearby forest, jungle, savanna, swamp or taiga biome, using the original 20-block sampling distance around the pool.
- Native world spawn within 32 blocks per axis of the temple or selected pool.

The optional fast rejection predicts the four loot seeds using the native
population/decorator RNG and resolves Minecraft's loot table. Candidates which
pass this gate still have their actual generated chests checked. Every generated
temple's iron/diamond totals are compared with the prediction; disagreement stops
the run. Disable prediction for regression batches with `-PfilterPredictLoot=false`.

### Village

- Plains, savanna or desert village, not an abandoned village.
- An actually generated iron golem, plus enough chest iron for **at least seven
  total ingots using the minimum three-ingot golem drop**. At least four ingots
  of chest iron are therefore required; nuggets and blocks count equivalently.
- Nearby forest, jungle, savanna, swamp or taiga biome, using the original 30-block sampling distance around the village reference.
- A usable natural surface lava pool within 96 blocks of the village reference.
- Native world spawn within 32 blocks per axis of the village or selected pool.

The golem's best-case drop is never assumed. Village ignition can use transported
wood; a chest ignition item is not separately required. The template fast gate
only rejects villages with no known iron-bearing chest potential. Actual loot,
not template presence, decides acceptance.

Village/temple biome checks sample Y=255 on the intended 3x3 grid at offsets
`-distance, 0, +distance`. The public Zig implementation fails to reset its inner
loop coordinate, unintentionally checking only one column of that grid. We retain
the categories and distances, but check all nine positions. These are biome
proxies, not actual-tree guarantees. Profile v1's stricter actual-tree checks were
replaced following the user's selection. Profile v2 mistakenly excluded savanna
for temples, including the optional tree-biome search hint. Profile v3 corrects
both paths to match the public source's categories. Do not pool timing or
acceptance results across these profiles, or relabel old reports as v3.

See [Filter source research](FILTER_RESEARCH.md) for the source comparison.
The first persistent lower-48/sister-seed implementation is now available in
the offline runner; its backend is still Minecraft Java. A separate native
structure-screening prototype is under differential testing, not serving seeds.

### Shipwreck

- Non-beached, counterclockwise-90-degree full upright or mast wreck, including
  its degraded equivalent, matching the public regular variant's layout choices.
- Treasure and supply chest present with their seeded vanilla loot tables.
- Treasure provides a pickaxe, bucket, flint-and-steel iron, axe and shovel under
  the public resource allocation order. Supply score is `wheat + carrots * 6 >= 30`.
- Native world spawn within 48 blocks per axis of the wreck reference.
- An actual tree on dry land within 96 blocks. Both islands and coastlines qualify.
- A generated ravine midpoint within 80 blocks per axis, outside the inner
  25-by-25-per-axis region, with deep-ocean biome at its midpoint and two-thirds
  of the way from wreck to midpoint. Its observed vertical radius is at least 18,
  its bottom reaches Y=8 and its top reaches Y=40.
- An actual lava-bottomed open underwater column near that midpoint. Geometry
  alone is not accepted as a usable ravine.

The ravine instrumentation observes native carving and always forwards the
original RNG call unchanged. It is excluded from release JARs. The radius check
uses the observed randomized radius, which is conservative compared with the
upstream raw maximum-radius test. Spawn uses actual Minecraft spawn calculation,
not a separate cubiomes estimate.

### Surface Pool Definition

At least ten cardinally connected, same-height, exposed lava source blocks at
Y>=60, containing a 2x2 square and adjacent dry two-block standing space. At least
ten final source positions must have been placed by the native lake feature.
Flowing lava, unrelated lava springs and merely attempted lakes do not suffice.

These are conservative terrain/resource tests, not a guarantee of nearby flint,
water, a particular player's preferred portal technique, or an unobstructed route.
Only the shipwreck addition requires actual trees; village and temple retain
the tree-biome approximation.

## Run A Batch

Use the normal project Java setup and the already accepted development-server
EULA. The server binds only to localhost on an ephemeral port, without players.
Run only one filter process at a time.

### Search Strategy

`filterSearchMode=staged` is the bank default. A search session tests structure
geometry, shipwreck layout and coarse Nether structure biomes once per lower-48
family. With temple loot prediction enabled, it also rejects insufficient
temple resources before checking sisters. Pipeline revision 5 additionally runs
vanilla bastion piece generation once per surviving family, without requesting
chunks or sampling terrain. Unsuitable stables are rejected before any sisters
are evaluated. Only the current family's prediction is retained.

`filterPredictBastion` defaults to true in staged mode. Disable it with
`-PfilterPredictBastion=false` for comparison; legacy mode does not use it. The
predictor uses vanilla 1.16.1 fixed-height rigid pools and the same lower-48 carver
seed. Each generated candidate's full structure-start NBT (except the reference
count) must match the cached prediction, or the batch stops. Final intact gold
and chest checks are still required and are never cached across sisters.

Biome-dependent lava attempts and Overworld checks remain full-seed work.
Generated loot/terrain/spawn still decide
acceptance; neither the profile nor the gameplay mod's behavior has changed.

The cursor uses a non-repeating odd-stride lower-48 traversal and an odd-stride
upper-16 permutation per surviving family. It resumes after each proposal,
native rejection and bounded search slice. A staged pass retires the family to
avoid near-duplicate sisters in the pool; independent verification is still
required. Hints and validation rejections do not retire a family.

`-PfilterSisters=65536` is the initial exhaustive-family budget, not a measured
optimal choice. Compare with 100 and 1000. `-PfilterSearchMode=legacy` retains
independent full-64-bit sampling for comparison. The legacy candidate/probe
commands in `CUSTOM_FILTER.md` also remain unchanged.

Reports use `pipelineRevision=6` and record mode, backend, sister limit, total
lower-48 checks, completed sister checks and family-loot rejections. In staged
mode `cheapCandidates` is work units (family checks plus sister attempts), not
full seeds; compare separate counters instead. `searchMs` at report root includes
search slices which returned no candidate. The trial budget bounds search calls,
including those empty slices. A slice has a one-million-work/30-second budget;
exhaustion resumes the session rather than supplying a fallback seed or error.

The cursor is in memory, not a crash-resume checkpoint. Restarting with the same
search inputs starts the same traversal again. Accepted seed handoffs remain
private, and staging files still cannot be served directly to racers.

### Early Full-Seed Checks

Pipeline revision 6 enables `filterEarlyChecks=true` in the offline bank runner.
Use `-PfilterEarlyChecks=false` for the previous validation path. This does not
change the profile, room settings, installed FSG filters or host prefetch.

- Village proposals which survive the existing hints first use vanilla village
  piece generation with the full seed's biome and terrain generator, without
  creating a candidate world or chunk manager. Abandoned layouts and layouts without an iron-bearing
  chest template are rejected early. Unknown predictions use full validation.
  Predictions are **not shared across sister seeds**. Each surviving prediction
  must match the generated start's NBT before actual loot and golem checks proceed.
- After vanilla has determined the real spawn, village and temple validation can
  reject `SPAWN_POOL_ENVELOPE` if no point in the structure's 96-block pool circle
  could be within 32 blocks per axis of spawn. This is a necessary geometric
  condition, not the approximate biome-spawn hint. It cannot reject a pool that
  satisfies the existing spawn rule. Final distances remain checked as before.
- Pool terrain reads prepare exactly the same chunks and heightmap as before,
  but classify blocks on demand during the immediate server-thread pool check.
  They retain the same halo, depth limit, standing-space and pool selection
  behavior. They avoid a 36-block-deep copy of every column; they do **not** skip
  decoration or predict that an attempted lake actually generated. Shipwreck
  tree checks retain the dense snapshot.

The report records `earlyChecks`, `villageLayoutsChecked`,
`villageLayoutsRejected`, `villageLayoutMs`, per-candidate
`villagePredictionVerified` and `poolCaptureMs`. Lazy `poolCaptureMs` excludes
on-demand cell reads during the following check, so compare whole candidate
times, not capture time alone.

Run the native differential suite:

```powershell
.\gradlew.bat runFilterBank -PfilterProbe -PfilterEarlyTest=true --offline
```

It compares village layouts and rejection decisions, positive pool/shore/canopy
fixtures and full candidate acceptance with early checks on/off. Results go to
`run/filter-bank/early-test.json`, without numeric seeds. Failures fail the task.

```powershell
.\gradlew.bat runFilterBank -PfilterProbe -PfilterType=temple -PfilterTrials=1000 -PfilterTarget=3 --offline
.\gradlew.bat runFilterBank -PfilterProbe -PfilterType=village -PfilterTrials=100 -PfilterTarget=3 --offline
.\gradlew.bat runFilterBank -PfilterProbe -PfilterType=shipwreck -PfilterTrials=100 -PfilterTarget=3 --offline

# Compare a bounded sister policy or the previous independent-seed search
.\gradlew.bat runFilterBank -PfilterProbe -PfilterType=temple -PfilterSisters=100 --offline
.\gradlew.bat runFilterBank -PfilterProbe -PfilterType=temple -PfilterSearchMode=legacy --offline
```

The runner stops at the requested passing count, trial limit, or the soft
`filterMaxMinutes` budget (default 30, checked between candidates). Creating
`run/filter-bank/stop-requested` requests a graceful stop after the current
candidate; remove that flag before another batch. `filterSearchSeed`
changes the reproducible search sequence; it is not a Minecraft seed. Duplicate
seeds are skipped within a batch. A broken probe, mismatched loot prediction or
failed write stops the batch instead of supplying a fallback seed.

`filterSpawnHint` defaults to true. It prioritizes candidates whose native initial
spawn-biome selection is near the structure or predicted lake, with room for
local terrain adjustment. This is a **search heuristic which can skip valid
seeds**, not a claim to reproduce Minecraft's full spawn algorithm. Skips are
`SEARCH_SKIPPED`, not proven invalid seeds. Every generated candidate still needs
the exact native spawn-distance check. Use `-PfilterSpawnHint=false` for an
unrestricted comparison batch. No-spawn-biome cases are not skipped by this hint.

`filterWoodHint` also defaults to true for temple searches. It samples possible
tree biomes around the predicted lake footprints before generating chunks. This
coarse search screen can omit thin biome edges or unusually joined pools; such
skips are `SEARCH_SKIPPED` with `POOL_TREE_SEARCH_HINT`, not proven invalid seeds.
Use `-PfilterWoodHint=false` for comparison. The final biome check is unchanged.

One headless server owns one candidate at a time. Each candidate gets fresh world
properties, generators, chunk managers and a UUID scratch save. Native spawn
finding is deferred until cheaper checks pass. The harness does not prepare a
441-chunk spawn region for every rejected seed. On close it restores the bootstrap
world and deletes only the owned, path-checked UUID scratch directory.

## Reports And Verification

- `run/filter-bank/bank-report.json`: current batch results, rejection reasons and
  timings. Numeric seeds are excluded.
- `run/filter-bank/reports/<runId>.json`: per-batch report archive.
- `run/filter-bank/bank-staging.json` and `private-staging/<runId>.json`: private
  candidate handoffs with exact seeds. **Do not publish these or send them to clients.**
- `run/filter-bank/logs/latest.log`: progress without exact seed values.

`VALIDATION_PASS` means the candidate passed the reusable-server harness, not that
it is ready to serve. Independently recheck each staged entry:

```powershell
.\gradlew.bat runFilterVerify -PfilterProbe -PfilterEntry=0 --offline
# Use -PfilterStaging=run/filter-bank/private-staging/<runId>.json for an archived batch.
```

This creates a fresh ordinary server world, including native initial spawn
preparation, then runs the same complete profile. Both the criteria and selected
anchors must agree. A mismatch is `REVALIDATION_REJECTED`, never silently approved.
The verifier rechecks the exact staged seed's preliminary predicates directly,
independently of the legacy or staged search order. It uses the unoptimized
native validation path, without the new early full-seed checks.
Successful private artifacts go to `run/filter-verify/approved/<candidateId>.json`
with status `VERIFIED_PROFILE`. Verification worlds are retained for inspection;
they are not gameplay saves and can consume disk space.

The private approved artifacts are input for a future centrally hosted seed bank.
They are not embedded in the mod, exposed as a downloadable full pool, or sent to
the relay by this development pipeline. A separately named room filter and private
single-seed delivery still need wiring before players can select this profile.

## Interpreting Timings

`searchMs` covers preliminary search, `validationMs` covers checks inside a
candidate world, and `totalMs` includes candidate-world creation/cleanup. Batch
`elapsedMs` includes all its searches and validations, but excludes the single
bootstrap server startup. Passing entries record `foundAtMs` from batch start.
Independent fresh-server verification is an additional cost.

`mainStartMs`, `bastionStartMs` and `fortressStartMs` separate the cost of obtaining
native structure starts, and `spawnMs` records native spawn finding when reached.
`familyLayoutsChecked`, `familyLayoutsRejected` and `familyLayoutMs` measure early
layout screening; `layoutPredictionsVerified` counts generated-start comparisons.
Layout screening is included in `searchMs`, not an additional independent cost.
Use `scripts/Summarize-FilterBank.ps1 -Report <report.json>` for a seed-free summary.

Do not divide by zero passes or call rejection throughput valid-seed throughput.
For a successful fixed-budget batch, report total time / passing count alongside
sample size, acceptance rate, verification cost and machine details. Small or
target-stopped batches provide planning observations, not a reliable tail latency.

### First Staged Smoke Batch

Profile v3, pipeline revision 4, temple, 65,536-sister budget and default hints:
the one-minute batch `bd89f5cf-1b38-4a12-aa19-628fbed9e280` checked 361 lower-48
families and 5,527 sisters. Eight families failed the early temple loot gate.
It returned 441 proposals: 374 skipped by the spawn hint, 54 by the wood hint,
and 13 generated worlds, all rejected for `STABLES_LAYOUT`. No seeds were staged.
The batch stopped normally after 60.04 seconds, excluding bootstrap/shutdown.
This run preceded the stop-file polling throttle; it is a smoke result, not a
controlled throughput comparison or a valid-seed rate.

This motivated the early layout gate in pipeline revision 5. Do not discard
families based on arbitrary terrain/spawn rejections: those can differ between
sisters.

### Bastion Layout Differential Test

```powershell
.\gradlew.bat runFilterBank -PfilterProbe -PfilterLayoutTest=true --offline
```

This runs the test instead of a seed-bank batch. It uses the public fixed fixture
sequence to compare full piece NBT across upper-bit values 1, 32768 and 65535,
then compares representatives against starts in fresh native Nether worlds.
Report: `run/filter-bank/layout-test.json`, without numeric seeds. A failure makes
the Gradle task fail. No external bastion-generation library is required.

The first run covered 64 families, 192 sister comparisons and 32 native starts.
All matched. All four bastion types and initial rotations were represented,
including three accepted and seven rejected stables families. Test work took
10.66 seconds, excluding bootstrap/shutdown. This measures parity testing, not
seed-bank throughput or proof that every possible seed has been sampled.

### Early Layout Gate Smoke Batches

Profile v3, pipeline revision 5, regular variants, search sequence 1000, default
loot/spawn/wood hints, one process on the i7-8750H with Java 25 and a 3 GiB heap:

| Variant | Sister budget | Seconds | Lower-48 checked | Sisters checked | Layouts checked/rejected | Generated worlds | Staged passes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Temple | 65,536 | 120.02 | 420 | 36,518 | 2 / 1 | 1 | 0 |
| Temple | 1,000 | 120.05 | 3,057 | 5,253 | 8 / 2 | 7 | 0 |
| Village | 1,000 | 62.86 | 286 | 1,476 | 2 / 0 | 22 | 0 |
| Shipwreck | 1,000 | 60.37 | 1,913 | 119 | 1 / 0 | 7 | 0 |

Public report IDs, in table order:
`f8e7d1f5-c15f-4377-80af-8e06d2c0f710`,
`81852dde-c9ef-4deb-817e-e6913e8772db`,
`6b1ffa8d-27bf-4ff7-bda4-32985b6dcfbe`,
`062926d3-a9a9-493f-8e6b-4ae5e7c27d9b`.

The 65,536-sister temple batch skipped 816 proposals for the spawn hint and 116
for the wood hint; its only generated world had no qualifying surface pool.
The 1,000-sister batch skipped 82 and 10 respectively, generated four worlds
without qualifying pools and three with failing final spawn distance. The
village batch skipped 37 proposals and rejected 22 generated layouts without an
iron-bearing chest template. Shipwreck skipped 21 proposals and rejected seven
generated worlds for final spawn distance. All 37 generated bastion starts
matched predictions. No generated candidate failed the stables layout gate.

The bounded sister policy explored more families during the temple sample, but
zero passes means neither policy has a measured accepted-seed rate. These are
small sequential smoke runs, not repeated controlled benchmarks. The default
remains 65,536 pending yield measurements. No entries were available for fresh
server verification. The next useful work is reducing per-sister native terrain
cost and improving spawn screening, while retaining exact final checks, before
using larger batches to estimate pool production time.

### Early Full-Seed Check Measurements

The revision 6 differential suite passed on the same machine:

- 12 full-seed village layouts across plains, desert and savanna matched native
  structure-start NBT and the existing rejection decision (eight rejected, four
  allowed through the layout gate). Prediction took 2.68 seconds total; creating
  disposable worlds and obtaining native starts took 32.93 seconds total.
- A constructed positive pool, shore and canopy fixture matched in pool order,
  anchors, source lists and 57,575 cell comparisons, including halo/depth edges.
  Dense capture plus the pool check took 10.44 ms; lazy capture plus the check took
  0.76 ms in that small already-generated fixture. This is not full-world speedup.
- Three identical temple candidates were rejected on both paths. Their combined
  validation/world-lifecycle time was 44.14 seconds baseline and 24.14 seconds
  optimized. The suite took 114.36 seconds excluding bootstrap/shutdown. These
  ordered single-pass comparisons are diagnostic, not a warmed repeated benchmark
  or proof of final acceptance for an entire seed pool.

The bounded revision 6 village batch
`4b82518e-6038-41f5-9253-cdda811b74e7` used the same search sequence 1000,
1,000-sister budget and hints as the previous village smoke batch. It checked
301 lower-48 families, 2,206 sisters and 144 proposals in 66.97 seconds. Of 62
village layout predictions, 59 were rejected without world creation; the three
survivors matched native starts. Two failed actual iron and one failed the exact
spawn/pool envelope. Another 82 proposals were skipped by the existing spawn
hint. No seed passed.

The shared first 59 proposal outcomes matched the earlier village report after
normalizing the `_PREDICTED` rejection suffix. Summed per-proposal time fell from
62.70 to 13.00 seconds for that prefix. This measures quicker rejection of that
specific sequence, not accepted seeds per hour. The soft time limit permits the
current native validation to finish before stopping.

The corresponding revision 6 temple batch
`c7afb665-98ad-43c0-b975-5006a13a0b4c` checked 7,058 lower-48 families, 10,194
sisters and 192 proposals in 122.99 seconds with the same 1,000-sister budget.
Of 11 native validations, seven failed the new necessary spawn/pool envelope,
three failed final spawn distance and one had no qualifying surface pool. All
11 bastion starts matched predictions. The previous revision 5 two-minute run
reached 99 proposals and seven native validations. These batches both found
zero passing seeds, so no accepted-seed production rate can yet be calculated.

Build and all 347 unit tests passed alongside the native differential suite.
The exact-seed verifier fix has unit coverage for staged proposals of all three
types; fresh-server verification still awaits a fully passing staged seed.
