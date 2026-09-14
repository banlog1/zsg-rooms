# Model-Only Seed Finder

For desktop-safe parallel batches, measured worker scaling and the rental shortlist,
see [Parallel Seed Bank Search](FILTER_PARALLEL_SEARCH.md).
For resumable overnight collection across all three types, see
[Overnight Seed Bank](OVERNIGHT_SEED_BANK.md).
For publishing completed snapshots to the locally tested hosted-delivery path,
see [Seed Service](../seed-service/README.md).

## Production Boundary

`scripts/Search-FilterCandidates.ps1` now searches and accepts seeds without
Minecraft, Fabric, Loom, a game installation, a server, or a save directory.
All profiles run a standalone Cubiomes C executable through a persistent Java
coordinator. A separate persistent Java worker uses ZSG's Nether helpers and
pinned SeedFinding/BastionGenerator versions. Village searches additionally use
profotoce59's VillageGenerator in the coordinator, on its original dependency
versions. The classpaths are isolated so Nether integration cannot silently
change village loot. Mathematical terrain noise/height arrays are not Minecraft
chunks or worlds.

There is **no Minecraft fallback**, including near distance boundaries. The
legacy `runFilterBank`, `runFilterProbe`, and `runFilterVerify` tasks are offline
calibration tools only and require `-PfilterCalibration=true`. They do not
participate in model acceptance. The normal mod and its Java 8 requirement are
unchanged; these are operator tools, not code installed on runners' machines.

The profile is `zsg-model-only-v5`, not the old block-verified v3 profile. It
accepts deterministic model predictions, including explicit terrain proxies.
It is suitable for generating a calibration pool, but its terrain-proxy gameplay
pass rate has not yet been measured. Searching does not automatically publish
or upload the bank. The separate seed-service publisher and mod integration
provide opt-in delivery after an operator publishes a completed snapshot.

## What It Checks

| Check | Implementation and acceptance policy |
| --- | --- |
| Structure positions | Cubiomes 1.16.1 region placement. Main structure in positive region 0,0; main X/Z limits: temple 320, village 224, shipwreck 208. |
| Nether structures | Bastion within 96 and fortress within 256 blocks of origin on each axis, with modeled biome viability. |
| Bastion layout | ZSG's pinned BastionGenerator for 1.16.1. Stables must contain `hoglin_stable/walls/side_wall_1` (good gap) and `hoglin_stable/ramparts/ramparts_1` (triple-chest rampart). Other bastion types remain eligible without extra layout restrictions. |
| Bastion obsidian | ZSG's selected route chests plus its assumed barter allowance, score at least 20. Bridge adds 7; other types add 4. This is NOT a guarantee of 20 chest obsidian or of those barter results. Treasure counts Y82, stables Y35/72, bridge all chests, housing Y73 and its rotated Y36 double chest. |
| Nether terrain | ZSGJavaBits/SeedFinding route samples roughly every 10 blocks, at least 80% air at Y60 or Y95, for origin to bastion and bastion to fortress. If only the high fortress route works, require at least six air samples down the fortress column from Y95 to Y50 in steps of 5. Same reference positions and interpolation as ZSG, not a block-perfect traversability guarantee. |
| Temple loot | ZSG-derived four-chest decorator/loot model. At least seven iron, or four iron plus three diamonds. No ruined portal required. |
| Temple exposure (v4) | Cubiomes approximate surface heights at a 4x4 grid, offsets 4,8,12,16 inside the 21x21 footprint. Require one adjacent 2x2 group (four corners of a 4x4-block roof patch) with predicted terrain at least three blocks below the stepped roof: Y72 at the inner four samples, Y68 at the others. Two blocks of desired exposure plus a provisional one-block model allowance. |
| Exposure limitations | Rejects modeled heavy burial, not every difficult-to-spot temple. Does not guarantee entrance access, line of sight from spawn, an unobstructed route, or exact final blocks. The allowance is not a measured error bound. Needs gameplay sample calibration; never falls back to Minecraft generation. |
| Village iron | Non-abandoned plains/desert/savanna village, actual modeled jigsaw layout, and at least four ingot-equivalents from its own smith chests. No temple substitution. Natural golem presence is not an acceptance requirement in v2. |
| Village loot confidence | VillageGenerator omits loot it cannot model confidently after certain feature placements. Omitted loot contributes zero; errors abort search rather than silently passing or calling Minecraft. Layout uses its full height model, not its optional height-map shortcut. |
| Village/temple wood | Nearby tree-bearing biome sampling, preserving the original biome-check approach rather than guaranteeing a specific tree. Village anchor radius 30; pool radius 20. |
| Surface lava pool | Lower48 decorator chance, position, height and surface roll; correct desert/default salt selected by full-seed biome. Pool-center radius 96 from main structure, 224-axis origin envelope. Model requires a dry, non-river/non-ocean, roughly level surface (five samples, height 64-80, spread at most 6) below the attempted lake Y. |
| Lava-pool limitations | This is a surface-lake opportunity proxy, not proof of a completed ten-source lake. It does not simulate the lake ellipsoid, final block replacement, caves, water-feature interference, or structure overwrites. Sample gameplay tests must measure false positives. There is no per-candidate native verification. |
| Nearby water (v5, temple/village only) | A modeled 4x4-block water patch centered within 48 horizontal blocks of the selected lava pool. All four corner samples must be unfrozen river/ocean biomes with predicted ground height at most Y61, below sea level Y63. Excludes isolated springs and frozen river/ocean biomes; does not depend on a single water block. |
| Water limitations | A multi-source surface-water proxy, not exact source-block counting or guaranteed shore/path access. Does not count village fountains, decorative ponds or springs, so otherwise usable seeds can be rejected. Decoration, biome-boundary and height errors remain possible. No Minecraft verification fallback in search. Another already-eligible lava pool is tried if the first one lacks nearby modeled water; original spawn/wood/lava requirements still apply. The mod's bank profiles separately support [runtime pool/water preparation](SEED_BANK_TERRAIN.md); this does not change filter acceptance. |
| Shipwreck layout/loot | Ocean-biome, counterclockwise-90 intact layouts 0,7,10,17. ZSG-style resource allocation for pickaxe, bucket, ignition, axe, shovel, and food score at least 30. |
| Shipwreck entry | GoATS-style initial ravine-carver proxy near the wreck, with deep-ocean checks at the candidate and along the route. Not ZSG's unpublished ravine midpoint implementation; a usable exposed ravine is not guaranteed. |
| Wooded island/coast | A sampled forest/taiga/jungle patch within radius 80, at least seven of nine biome samples wooded, center modeled height at least 65. Accepts island or coastline. Does not count actual generated tree blocks. |
| Spawn | Cubiomes `getSpawn`, within 48 blocks per axis of village/temple or its selected pool; shipwreck 64. These are the original 32/48 distances plus a 16-block model allowance. No exact fallback. |

Version 4 adds temple exposure only; all village, shipwreck, loot and Nether
criteria remain unchanged. The 1.16.1 `DesertTempleGenerator` starts at Y64 and
does not call the terrain-height adjustment used by some other structures. Its
central roof is symmetric under rotation, so the exposure proxy does not need
to reconstruct orientation or inspect the towers. A partly buried temple can
pass with its central roof exposed; a single isolated exposed sample cannot.
The check runs after the existing surface and spawn gates and has a separate
`checks.temple_exposure` reached/rejected/time entry in seed-free reports.
Old banks are not rewritten or retroactively marked as exposure-checked.

Version 5 adds the water gate after exposure/spawn checks and before village
smith loot. Biome candidates are generated in one bounded grid per pool;
expanding grid rings prefer close candidates, with terrain sampled only for
unfrozen-water patches. This is deterministic but not an exact nearest-shore
search. Private accepted records now include `water: [x,z]` for temples/villages
and `water: null` for shipwrecks. Seed-free reports include `checks.nearby_water`.
Shipwreck acceptance and all Nether/stables requirements are unchanged. Old v4
banks remain v4; they do not acquire a water requirement retroactively.

Spawn calibration from the earlier harness included 34 exploratory and 27
holdout cases. Most errors were zero, but outliers exceeded the allowance
(exploratory maximum 272, holdout maximum 100 blocks). The allowance is a
gameplay tolerance, **not** a mathematical error bound or a universal guarantee.

Version 3 always enables ZSG's otherwise-optional bastion-obsidian and Nether
terrain checks. Both run once per lower48 family, after cheap Overworld
loot/feature-attempt rejection and before any sister checks. OP variants and
mapless are not implemented by this finder. Its modified Overworld criteria
and added stables requirements still differ from stock ZSG.

Version 1/2 banks, including the earlier 25-village batch, did not pass the
new Nether checks. They must not be relabeled as version 3 without rechecking.

Version 2 removes the natural-golem gate. It assumes a missing golem can be
provided during gameplay, but **this tool change does not implement that gameplay
repair**. Without a natural or supplied golem, the filter alone promises only
four modeled chest ingots, not seven total. No other acceptance criterion was
relaxed. Village golem presence remains an observational diagnostic so reports
can show how many additional acceptances removing the gate actually permits.

## Pipeline And Architectural Decision

The former bottleneck was self-inflicted: each surviving candidate triggered
real-world generation for properties often predictable by cheaper models.
Measured revision-7 batches spent 35.24 seconds on five temples, 37.99 on three
villages, and 44.04 on seven wrecks, plus approximately 31-35 seconds of worker
startup. Those rejected batches are not accepted-seed throughput measurements.

| Stage | Why it exists / information | Replacement decision | Where it runs |
| --- | --- | --- | --- |
| Structure discovery | Position and biome viability | Remove Minecraft starts; Cubiomes already models these. | Region/template checks lower48, Overworld biome checks sisters. |
| Loot generation | Enough route resources | Replace chest/block generation with ZSG loot arithmetic or VillageGenerator layout plus SeedFinding tables. | Temple/wreck lower48; village layout/loot needs sister terrain. |
| Spawn creation | Starting distance | Remove server bootstrap and vanilla spawn search; use calibrated Cubiomes prediction with explicit tolerance. | Surviving sisters, after terrain proxies. |
| Surface chunks | Usable pool / wooded land | Remove final block verification; model decorator opportunity and terrain/biome proxies. | Lake/carver attempts lower48; height/biome sisters. |
| Bastion expansion | Required stables layout and route obsidian | Reuse ZSG's mathematical BastionGenerator, applying the existing good-gap/triple-rampart predicate to its piece names. | Once per surviving lower48. |
| Nether terrain | Open routes to bastion and fortress | Adapt published ZSGJavaBits directly; retain its SeedFinding versions and decisions. | Once per surviving lower48, after layout/obsidian pass. |
| Cleanup / verifier queue | Dispose worlds and amortize startup | Remove Minecraft from production entirely. Persistent standalone model processes handle mathematical work only. | No Minecraft workers. |

Options compared before replacing the search entry point:

- Keep/optimize native validation: pooling amortizes startup, but every accepted
  candidate still pays final chunk generation. Local caching cannot remove that
  floor. Expected impact is smaller than eliminating those worlds.
- Replace individual checks with models but retain native boundary fallback:
  potentially useful, but throughput still depends on expensive fallback rates;
  explicitly rejected for this production architecture.
- Move equivalent deterministic checks earlier: cheap template/resource/lake
  checks run once per lower48 family. Full-seed biome/terrain cannot generally
  be moved lower48; claiming otherwise would reject valid sisters incorrectly.
- Redesign around ZSG's lower48/sister split: selected. All acceptance is model
  based. The initial temple/shipwreck defaults explored up to all 65,536 sisters
  of each passing Nether family, as ZSG does. Village initially used 256 because its custom
  smith-layout check can repeatedly fail within the same family. `-Sisters`
  overrides either default. This changes search order, not acceptance criteria.

No justified numeric theoretical seeds/second ceiling is available: success
probabilities are correlated and depend on the profile. The new work floor is
noise/biome/layout computation, not game-world construction. Initial timing
identified spawn prediction as wasted work before pool rejection; moving it
later reduced village spawn-check work from 23,115 to 4,013 calls in separate
60-second runs, without weakening acceptance. These runs explored different
numbers of candidates and are not a controlled whole-pipeline speedup ratio.

Further priorities: sample pool/ravine proxy quality; measure smith-layout and
loot rejection separately; then consider a safe earlier smith-layout screen.
Do not optimize loot arithmetic just because it is easy. Native generation may
calibrate a representative sample, never decide production candidates.

## Build

Requires Git, GCC (tested with MSYS2 UCRT64), and JDK 16+ for every profile;
JDK 25 was used here. The standalone Gradle project uses only the Java
plugin, independently of the root mod build.

```powershell
git clone https://github.com/Cubitect/cubiomes.git run/filter-reference/cubiomes
git -C run/filter-reference/cubiomes checkout --detach e61f90580cbdd883214a8054670dacae655e59c0
git clone https://github.com/profotoce59/VillageGenerator.git run/filter-reference/VillageGenerator
git -C run/filter-reference/VillageGenerator checkout --detach ee9e0c6c82aeac3fef9b1ffa3f452a2a1eae6339
git clone https://github.com/DuncanRuns/BastionGenerator.git run/filter-reference/BastionGenerator
git -C run/filter-reference/BastionGenerator checkout --detach 9cccd19863e941d2dc8d2820037c48d49fe7d526
git clone https://github.com/DuncanRuns/ZigSeedGlitchless.git run/filter-reference/ZigSeedGlitchless
git -C run/filter-reference/ZigSeedGlitchless checkout --detach 073e1d1f3150213c7c3787eda7ef8106cb789817
.\scripts\Build-FilterWorker.ps1 -Compiler C:/msys64/ucrt64/bin/gcc.exe -FinderOnly
.\gradlew.bat -p tools/model-finder installModelFinder
```

Skip cloning when the pinned clean checkouts already exist. The builders reject
different revisions or dirty dependency sources. Dependencies are downloaded
only at build time; subsequent builds can use `--offline`. The village library
is locally compiled, not vendored or shipped in mod releases. See
[third-party notes](../tools/filter-worker/THIRD_PARTY.md) before redistributing
tool binaries.

## Search And Repeat

```powershell
.\scripts\Search-FilterCandidates.ps1 -Type temple -Target 10 -Seconds 60
.\scripts\Search-FilterCandidates.ps1 -Type village -Target 10 -Seconds 120
.\scripts\Search-FilterCandidates.ps1 -Type shipwreck -Target 10 -Seconds 60
```

Add `-Java 'C:/path/to/jdk/bin/java.exe'` if the PATH Java is too old.
Each invocation creates a new private JSONL file under ignored `run/model-bank`.
`-OutputFile` selects an alternative **new** file; an existing file is never
overwritten. A time-limited or failed run may leave a partial bank. Successful
rows are immediately flushed with `MODEL_ACCEPTED`; they are not queued for a
Minecraft verifier. Keep these files private because they contain numeric seeds.

Public output contains counts, elapsed times and per-check reach/reject/timing
data, never numeric seeds. A seed-free `.report.json` companion stores those
counts plus search budgets and process wall time (including Java startup).
Village reports separate layout rejection, no smith chests, no modeled smith
loot, partially modeled loot with insufficient known iron, fully modeled loot
with insufficient iron, and acceptance. Missing/omitted predictions do not prove
that an actual chest lacks iron. Layout and loot times are reported separately.
Both models receive candidates only over private parent-child pipes. A direct
native search is refused without the coordinator protocol. Model errors or
30-second per-request timeouts abort, never become acceptance. Reports separate
Nether layout, obsidian, and terrain reach/rejection counts and time, plus
accepted lower48 families and accepted stables families. Those are not counts
of final accepted full seeds. Private bank rows include `netherObsidianScore`
and `bastionType`; no numeric seed is included in public reports.

`-Families`, `-Sisters`, `-FamilyCap`, `-Target`, `-Seconds`, and `-Stream` control the search.
Current operator-script defaults from the bounded policy trials are:

| Type | Sister attempts per family | Accepted sisters per family |
| --- | ---: | ---: |
| Temple | 4096 | 2 |
| Shipwreck | 16384 | 4 |
| Village (provisional; sparse evidence) | 1024 | 2 |

Explicit flags override these choices. The native CLI still requires an
explicit attempt budget and accepts an optional final cap argument (omitted
cap means one). To reproduce the old operator strategy, pass
`-FamilyCap 1 -Sisters 65536` for temple/shipwreck or `-FamilyCap 1 -Sisters 256`
for village. Search-policy changes do not change the v5 acceptance criteria.

`Stream` is a starting family offset, not a world seed and not an independent
parallel shard: nearby offsets overlap. Same offset and budgets reproduce the
same sequence until a time limit intervenes. Use the reported family count to
advance the offset; deduplicate when combining runs. `-FamilyCap` permits 1-4
accepted sisters from each visited lower48 family per run. It is a maximum,
not a quota: move on when either the success cap or sister-attempt budget is
reached. Every accepted sister still passes all v5 checks, and `-Target` stops
at the exact requested total even in the middle of a family. Runtime limits are soft at
one in-flight model check (each Java model request has a 30-second timeout).

With a cap greater than one, private records include a decimal-string `family`
identifier (the seed's low 48 bits). Native reports add `familyCap` and
`acceptedFamilies`, the number of families supplying final accepted seeds.
This differs from the nested Nether-model count of families merely passing
the Nether checks. Family identifiers, like exact seeds, stay out of public
reports. A future bank-serving layer can use them to space related worlds;
automatic family-aware serving is not implemented here.

The theoretical maximum across all lower48 families is `cap * 2^48`: about
563 trillion seeds at cap two or 1.126 quadrillion at cap four. The actual
eligible pool is much smaller and unknown; many families have no passing
sisters or fewer than the cap. Shared lower48 models make sisters correlated
gameplay variants, not equally distinct Nether routes.

### Measuring Search Policies

`scripts/Measure-ModelSearchPolicies.ps1 -OutputDirectory <new-directory>`
runs bounded, single-worker model searches for all three types. It samples
65,536 sisters with cap four, recording prefixes for budgets 64, 256, 1024,
4096, 16384 and 65536 crossed with caps 1, 2 and 4. `-SecondsPerType` defaults
to 120; `-Samples Holdout` uses separate family offsets. Supply `-Java` when
the desired standalone JDK is not on PATH.

Its opt-in `ZSG_MODEL_TUNE=1` protocol requires 65,536 attempts and cap four.
It records aggregate counts/times, never family or seed identities in public
reports. A family interrupted by a time limit or global target is excluded
from every prefix comparison. Shared failed-family/search costs are added to
each policy, so estimates include the cost of finding usable lower48 families.
The estimates do not reproduce different JVM warmup/cache histories or all I/O
overhead; they are screening measurements, not proven accepted-seed rates.
Normal searches do not record these policy observations.

`scripts/Benchmark-ModelSearchPolicies.ps1 -OutputDirectory <new-directory>`
directly benchmarks shortlisted settings with profiling/tracing off. `-Type`
can restrict the run to one profile. It uses
equal time budgets and a separate starting offset, reports both accepted
seeds/minute and productive families/minute, and reverses case order for odd
`-SampleIndex` values. Do not merge overlapping comparison banks without
deduplication. These scripts do not start parallel workers or Minecraft worlds.

`scripts/Test-ModelSearchCaps.ps1 -FixtureBank <private-bank>` checks caps one,
two and four against a family with four accepted sisters, exact target stopping,
metadata/prefix equivalence and repeatability. `-LegacyFinder` additionally
compares the cap-one private bank byte-for-byte with the previous executable.

The 65,536-sister shipwreck trial found two seeds in 22.41 seconds after
36,937,177 families. With only 256 sisters, the same starting offset exhausted
100,000,000 families in 38.25 seconds with no acceptance. The village full-sister
trial spent 60.05 seconds in its first passing Nether family without acceptance;
873 smith checks returned no usable modeled loot. These are exploratory samples,
not a general speedup ratio, and support keeping profile-specific search budgets.
Keeping 256 sisters for village found the first valid seed in 20.93 seconds
(891,776 families, 37,613 sisters): 6,210 Nether layouts, 5,444 obsidian checks,
and 1,002 terrain checks, taking 1.92, 1.61, and 8.96 seconds respectively inside
the model. Only 42 final village models were needed. These measurements include
no game generation and are not a representative bank-fill average.

## Version 3 Smoke Measurements

The subsequent bounded performance pass kept all version-3 criteria and models
unchanged. Paired fixed-work runs showed about 17-18% less temple search time and
56-60% less shipwreck time on the tested windows; village had no consistent
improvement. It reuses lower48 surface-noise initialization, stops mathematically
failed sample checks early, and avoids clearing unused candidate arrays.
See [the optimization report](FILTER_OPTIMIZATION_LOG.md) for baseline/holdout
data, scope, and limitations. The older smoke times below are historical, not
updated performance promises.

Default profile-specific sister budgets, stream zero, on the development laptop:

| Profile | Accepted | Search time | Lower48 families | Sisters checked |
| --- | ---: | ---: | ---: | ---: |
| Temple, repeated | 2 each | 12.92 / 12.37 s | 102,269 | 231,673 |
| Shipwreck, repeated | 2 each | 25.35 / 19.68 s | 36,937,177 | 2,723 |
| Village | 1 | 22.19 s | 891,776 | 37,613 |

Repeated banks were byte-identical. Resource/schema/secrecy and overwrite
checks passed for all three profiles. These are small functional smoke runs,
not steady-state averages or controlled CPU benchmarks; other development work
ran during parts of the tests. Reports under `run/model-finder-tests` include
separate process-wall time and per-check totals. In the village trial the added
Nether terrain model dominated; in the shipwreck trial the wooded-surface proxy
was larger than all Nether model checks combined.

The standalone build passed 14 tests, including direct parity against unmodified
ZSG, existing village/loot regression tests, and fail-closed protocol tests for
all profiles. Minecraft worlds generated: zero.

## Historical Measurements, Before Full Nether Checks

Small smoke runs on the development laptop, default 256 sisters per family,
single search process, offset zero. These are not representative long-run rates:

| Profile | Accepted | Search time | Lower48 families | Sisters checked |
| --- | ---: | ---: | ---: | ---: |
| Temple | 3 | 0.93 s | 90,023 | 17,717 |
| Shipwreck | 3 | 14.82 s | 4,625,450 | 2,576 |
| Village, smith + modeled golem | 2 | 90.03 s | 390,538 | 517,243 |

All used zero Minecraft worlds. Temple/wreck figures precede the explicit
ocean-template safeguard; repeat the scripted checks on the final binary before
using any throughput estimate. Village made 573 final smith checks, of which
571 failed. Its final loot/model work took 29.57 seconds, biome checks 37.55,
surface proxies 15.85, and spawn prediction 6.87. These are wall-clock stage
totals, not CPU samples or isolated per-library benchmarks. Do not extrapolate
bank-fill time from two or three acceptances.

Final-binary repeatability smoke tests: two temples in 0.415/0.414 seconds,
two shipwrecks in 8.71/9.09 seconds, and the first village in 9.61 seconds.
Both repeated private banks were byte-identical. All three profiles passed
resource/schema/secrecy checks; existing output files were not overwritten.
Timing reports are saved beside the private test banks under
`run/model-finder-tests`. These measurements include cold Java startup only in
the separate `processWallMs` field (9.77 seconds for that village invocation).

## Longer Village Run, Version 2

On 2026-09-13, a 600-second run with 256 sisters, offset zero, and the natural
golem gate removed accepted **25 distinct seeds**. It searched 2,641,037 lower48
families and 3,500,692 sisters. All accepted seeds had at least four modeled
smith-chest ingots. This one batch averaged 24 seconds per acceptance; that is
an observed batch rate, not a guaranteed waiting time.

The final village model was called 4,009 times:

| Outcome | Count |
| --- | ---: |
| Layout rejected by model | 0 |
| No smith chest in modeled layout | 3,357 |
| Smith chest present, no modeled smith loot returned | 504 |
| Partially modeled smith loot, insufficient known iron | 16 |
| Fully modeled smith loot, fewer than four ingots | 107 |
| Accepted | 25 |

728 smith chests were identified across those layouts, but loot was returned for
153. Missing predictions are not proof of empty or low-iron actual chests.
Every evaluated village happened to include a modeled golem, so removing the
golem requirement made no difference within this sample.

Measured stage costs: biome checks 248.35 seconds, surface proxies 107.10,
spawn prediction 47.60, and the final village-model call path 195.53. Inside the
village worker, layout/terrain construction and smith discovery took 194.38
seconds, while loot generation and counting took only **0.15 seconds**.
The useful next investigations are cheaper smith-layout rejection and the
upstream model's omitted-loot policy, not loot-arithmetic micro-optimization.

Private seed list and coordinate table are under
`run/model-bank/village-v2-10min-20260913-0553-seeds.txt` and `-accepted.md`.
The seed-free detailed report is
`run/model-bank/village-v2-10min-20260913-0553.jsonl.report.json`.

Rechecking these 25 seeds against version 3's added Nether rules retained 2;
16 failed ZSG's obsidian score and 7 failed its terrain check. Their old bank
files were not modified or relabeled.

## Verification Commands

```powershell
.\scripts\Build-FilterWorker.ps1 -Compiler C:/msys64/ucrt64/bin/gcc.exe -TestsOnly
.\gradlew.bat -p tools/model-finder check --offline
.\gradlew.bat filterProbeTest -PfilterProbe -PfilterModelParity=true --offline
.\scripts\Test-ModelFinder.ps1 -Village
```

The `filterProbeTest` command is offline calibration, not production. It compares 512
deterministic resource vectors with Minecraft's actual loot-table code while
constructing **no server or world**. Temple resources, shipwreck treasure and
food matched all 512 vectors. The separate standalone tests compare temple and
treasure against SeedFinding, check signed coordinates/lower48 invariance and
resource boundaries, and reproduce an upstream village loot fixture in 1.16.1.
SeedFinding 1.171.5 supply-loot enchantment behavior disagreed with the ZSG/vanilla
results; it is not used for production shipwreck supply acceptance.

`netherTest` compiles the pinned unmodified ZSGJavaBits source only as a test
oracle. It compares 128 obsidian vectors (all types and rotations) and 20
terrain vectors, including negative coordinates and coincident origin/bastion
references. It also checks upper-16 sister invariance, stables layout predicates
against actual modeled pieces, and preserves ZSG's barter/chest policy. The
original helper's interpolation/casts and terrain short-circuit order are retained.
The build scans both model dependency sets for forbidden Minecraft/Fabric classes.

Native surface regression tests additionally compare 4,096 noise samples and
512 full-seed-biome-dependent heights bitwise, plus 256 wooded searches (including
chosen coordinates) and 2,560 pool checks against the original query policies.
`Build-FilterWorker.ps1 -TestsOnly` builds these alongside the loot test executable;
the standalone Gradle test task tracks all native test/search binaries as inputs.

## Reproducible Performance Comparisons

`Search-FilterCandidates.ps1 -Finder <path>` selects an alternate frozen native
executable for operator benchmarks. Normal searches continue using the installed
finder. `ZSG_MODEL_TRACE=1` enables a seed-free digest of every visited lower48
family and full-seed acceptance decision; the default is off. Do not compare
different search inputs, time-limited prefixes, or only the final seed count.

`Benchmark-ModelFinder.ps1` runs fixed work for all three profiles, preserves
private banks and seed-free reports, and fails on any decision/file mismatch.
It requires an explicit deadline and a fresh output directory. `-Samples Holdout`
uses separate stream offsets; `-Repeats 2` reverses pair ordering on the second run.

```powershell
.\scripts\Benchmark-ModelFinder.ps1 `
    -BaselineFinder run/filter-bench/20260913-042832/baseline.exe `
    -CandidateFinder run/filter-worker/seed-finder.exe `
    -OutputDirectory run/filter-bench/my-comparison `
    -Samples Holdout -Repeats 2 `
    -DeadlineUtc ([datetime]::UtcNow.AddMinutes(20))
```

The example baseline is a locally preserved artifact, not a distributed binary.
It is a historical v3 example: the v5 coordinator intentionally rejects older
executables. Use two same-profile builds for equivalence benchmarks; comparing
different profiles requires their matching coordinators and allows changed decisions.
On another machine, preserve its current known-good finder before editing/building
the candidate. Use the same Java/model dependencies on both sides. Benchmark
reports include executable hashes; keep the matching private banks for auditing.

Full terrain, village-model and distance accuracy still need representative
gameplay calibration. Passing arithmetic tests does not prove those proxies.

The surface-query test also checks all 65,536 exposure masks, threshold and
invalid-height cases, and 256 batched-vs-point terrain grids. The three-profile
smoke test rechecks accepted temple coordinates with the exposure model and
ensures shipwrecks do not enter the exposure stage. For offline inspection,
`surface-query-test.exe --temple-exposure` reads private `seed x z` rows on stdin
and emits only row indices, exposure decisions and sampled heights. This is a
model diagnostic, not a real-world calibration result.

### Temple Exposure Trial (2026-09-13)

The final v4 policy retained 5 of the previous 10 temple-bank entries on model
reinspection. A new search at stream offset 102269 accepted 10 unique temples
in 156.873 seconds, visiting 2,557,578 families and 2,783,657 sisters. Exposure
ran on 112 otherwise passing candidates, rejected 102, and took 28.485 ms total
(about 0.25 ms per check). These are correlated sister candidates, not 112
independent structures. Stricter acceptance can increase total search time
despite the small check cost. Tests/build activity overlapped part of this run;
it is an indicative throughput sample, not a controlled speed comparison.

Private bank: `run/model-bank/24259b97-3f5b-4fc8-868d-32f9f0cdf306.jsonl`;
seed-free report has the same path plus `.report.json`. All 10 entries were
rechecked with the final exposure model. The standalone Gradle build (15 tests)
and temple/shipwreck/village search smoke tests passed. No Minecraft worlds were
created. Actual visibility and height-prediction error still need gameplay
calibration, especially on the user's buried-temple example.

### Nearby Water Trial (2026-09-13)

The agreed radius is **48 blocks from the selected lava pool**, not from the
temple/village anchor. Reinspecting the previously shared v4 playtest banks found
modeled water for 6/10 temple and 3/10 village **recorded pool anchors**. These
are not full-seed rejection rates: v5 can choose another already-eligible pool.
The suspected problem temple (row 2) still predicts water near X133,Z216; this
needs a gameplay check rather than claiming the reported problem was reproduced.

A v5 temple trial at stream offset 10000000 found three acceptances in 55.426
seconds. The water stage checked 81 otherwise passing candidates, rejected 78,
and spent 5.537 ms total. Correlated sisters and overlapping test activity mean
this is not a controlled throughput comparison. Private bank:
`run/model-bank/temple-v5-water-trial.jsonl`, with its seed-free `.report.json`.

Standalone build: all 15 tests passed. Native coverage includes all 16 wet/dry
corner masks, frozen biome and height boundaries, 64 repeatable signed-coordinate
searches, point-query agreement, radius checks, spawn eligibility, empty pool
lists and dry-first/wet-second pool selection. All three profile smoke tests
passed, including repeatability, exposure, recorded water coordinates and the
absence of water filtering on shipwrecks. `surface-query-test.exe --nearby-water`
reads private `seed poolX poolZ` rows and emits only row indices, found flags and
predicted water coordinates. No Minecraft worlds were generated.
