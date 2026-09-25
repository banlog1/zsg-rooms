# Buried Treasure Filter

## Status

Integrated September 24, 2026. The standalone finder accepts
`buried_treasure` through `Search-FilterCandidates.ps1`; it does not create a
Minecraft world. The hosted service and Rooms picker support this type as
`rooms-buried-treasure-v5` (ZSG Rooms Buried Treasure). It uses the existing
treasure HUD icon and optional picker/loading art. ZSG Rooms Mode uses this
bank for its full 20% BT share, replacing the Mapless/Mapless OP draws.
Existing Mapless choices and temple/village/shipwreck predicates are unchanged.
The overnight scheduler supports BT, including the `bt-rp` preset documented
in [Overnight Seed Bank](OVERNIGHT_SEED_BANK.md). The standalone and new overnight
BT searches use a measured 4,096-sister budget, cap two.

## Acceptance Contract

Baseline: regular ZSG mapless at
`073e1d1f3150213c7c3787eda7ef8106cb789817`, with full existing ZSG Nether models
and our shared stables predicate. This is not the OP filter.

### Lower 48

- Select the existing ZSG-compatible bastion within 96 blocks per axis and
  fortress within 256 blocks per axis of Nether origin; require biome viability.
- Scan treasure chunks X=-10..10, then Z=-10..10. Keep the first structure
  opportunity whose loot satisfies the regular mapless resource allocation.
  A later treasure does not rescue a failed ravine or sister check.
- Generate loot using decoration salt 30001 at the treasure chunk origin and
  its first chest seed. Preserve sequential resource allocation: one iron for
  ignition; three diamonds or three iron for a pickaxe; three iron for a bucket;
  TNT plus a two-iron/two-gold plate, or a three-iron/three-gold axe, for wood;
  and one remaining gold, diamond, iron or TNT for gravel. Regular mode does not
  require two TNT. These are resource predicates, not compulsory player actions.
- Scan a 15x15 chunk square centered on the treasure chunk for the first eligible
  modeled ravine. Require center vertical radius >=18, modeled lower Y<=8 and
  upper Y>=40. Its floating-point midpoint must be within 80 blocks per axis of
  the treasure, but not less than 25 blocks away on both axes. Export X/Z using
  truncation toward zero, matching the published ZSG filter.
- Run the existing persistent Nether model once per surviving family. Require
  its route checks and obsidian score >=20, including ZSG's assumed barter
  allowance. For stables require both `walls/side_wall_1` (good gap) and
  `ramparts/ramparts_1` (triple rampart), using `BastionLayoutChecks` unchanged.

### Full Seed

- Require Cubiomes treasure biome viability at the selected position.
- Search the full 3x3 grid at offsets -10, 0, +10 for a forest biome, at scale 1,
  Y255. Retain the first forest sample in X-then-Z order.
- Require deep ocean at the selected ravine midpoint and two-thirds of the way
  from treasure to ravine, at scale 1, Y64. Interpolation uses floor division,
  including negative coordinates.
- Require predicted spawn within 32 blocks per axis of the treasure. This is a
  square bound, not a 32-block Euclidean radius or exact in-game spawn guarantee.
- Require >=400 forest cells in the 41x41 scale-1 map around the forest sample.
- No surface lava pool, temple exposure, village loot, water-patch or artificial
  chest repair requirement is added. The entry reference is the modeled ravine.

## Source And Accuracy

DuncanRuns separately published the missing ravine operations as CC0 C code:
https://gist.github.com/DuncanRuns/c458b56b50220bbaf9ac45d5deee6634/81a4f3fbf77ffc97845b2326e927f706cd7c4f21

`mapless_model.h` adapts that revision, makes RNG draw evaluation order explicit,
and removes unused initial guesses and example programs. It does not replace
the existing GoATS-derived shipwreck model. Attribution is in
`tools/filter-worker/THIRD_PARTY.md`.

The public C midpoint model is not proof of identity with ZSG's unavailable Zig
dependency. It uses ordinary trigonometry and modeled geometric bounds; it does
not simulate block carving, terrain intersections or guarantee an open route.
The user approved the gameplay samples below for initial publication. This is
not exhaustive calibration and introduces no per-candidate Minecraft fallback.

Intentional forest corrections: reset the inner loop for each X coordinate,
require an actual forest sample, and center the size check on that sample.
The pinned ZSG code does not reset its inner loop or test `forest_found` before
using the loop coordinates for the size check. We preserve the intended grid
and anchor, not these control-flow artifacts. Thus this is not a bit-for-bit
stock-ZSG accept/reject clone.

## Operator Commands

Build with the existing native builder, then run a bounded sample:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Build-FilterWorker.ps1 -FinderOnly
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Search-FilterCandidates.ps1 -Type buried_treasure -Target 10 -Seconds 180
```

Choose a new `-OutputFile` and reserve fresh ranges with the existing global
cursor for bank collection. Default stream zero is for reproducible experiments,
not repeated production collection. Supply `-Java` if needed.

Search defaults: 4,096 sisters, family cap 2, based on the bounded policy trial
below. Explicit `-Sisters` and `-FamilyCap` override these defaults. Other seed
types retain their own search policies.

Build and run model tests:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Build-FilterWorker.ps1 -TestsOnly
.\gradlew.bat -p tools/model-finder test netherTest --offline
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Test-MaplessFinder.ps1
```

`Test-MaplessFinder.ps1 -BaselineFinder <previous-executable>` additionally
compares fixed-work decision traces and private bank bytes for all three
existing types. Samples never print numeric seeds. The test checks repeated
mapless outputs, stage gates, distances, metadata and resource-model invocation.

Tests include 4,096 loot vectors against SeedFinding, ordered Java Random
ravine-reference vectors, signed coordinates, upper-bit invariance, resource
allocation boundaries, ravine thresholds, and fail-closed model protocol checks.
The Java reference validates the published mathematical model, not Minecraft
terrain. Existing Nether parity and stables tests remain in the test suite.

## Initial Results

On September 24, a fresh globally reserved range produced ten accepted seeds
from six lower48 families in 78.337 seconds of native search (78.489 seconds
including coordinator startup). This was a target-limited single-worker sample,
not a sustained or representative seeds/hour benchmark. The sample contains
three bridge, five housing and two treasure bastions; no final stables acceptance.
All ten saved Overworld records passed the rechecker, which also rejected an
altered ravine coordinate. No Minecraft worlds were generated or inspected.

Of 509,544 lower48 families, 1,209 reached the full Nether model and 33 passed;
three of those were stables families, not necessarily final accepted seeds.
There were 1,871,375 sister checks. Biome/nearby-forest screening used 71.877
seconds; Nether-model requests used 4.877 seconds. This points to sister-budget
tuning as the next measurement, not weakening loot or stables requirements.

Private samples and reports:
`run/mapless-samples/607c706f271a4784a9190ae4c0887ae5/`.
Repeated smoke searches produced identical banks. Fixed-work old/new comparisons
matched decision traces and output bytes for each existing type across 20,000
families. Because that small shipwreck range had no sister checks, a separate
old/new comparison also generated identical nonempty shipwreck banks. Existing
loot and surface regression tests passed. These are regression samples, not
exhaustive equivalence over every seed.

## Initial Integration

A subsequent fresh two-minute run accepted 32 seeds from 20 lower48 families:
7 stables seeds (4 layouts), 7 bridge, 15 housing and 3 treasure. All 32 saved
Overworld records passed the rechecker. After the user tested the supplied
examples and approved integration, this batch became the initial publication
input. Private source: `run/mapless-samples/long-160d8e69b1864e6c9215ff6f92536a1c/batch-001.jsonl`.

The hosted API type is `buried_treasure`. Imports require
`buriedTreasureRule: mapless-regular-v1` and allow at most two seeds per family.
Legacy three-type publications remain readable with zero treasure records;
extension keeps the bank revision and all existing per-type slots unchanged.
See [production state](../seed-service/PRODUCTION.md) for live counts.

Spawn-close and preload use the existing buried-treasure structure path.
Shared first Nether entry retains the existing common-spawn reference behavior;
the API does not send the model's predicted ravine coordinates to clients.
Artificial surface lava pools remain temple/village only.

## Twenty-Minute Trial

On September 25, 2026 (local time), a single BelowNormal-priority worker ran
for 1,200 seconds on a reserved fresh range, using 65,536 sisters and a two-seed
family cap. It accepted **309 seeds from 172 families**, approximately **927
seeds/hour** for this window. Bastions: 148 housing, 92 bridge, 44 treasure and
25 stables seeds (14 stables families). All 309 Overworld records passed the
model recheck; none were uploaded.

The worker searched 8,850,352 lower48 families and 27,692,071 sisters. Biome
checks consumed about 1,116 seconds (93% of search time); full Nether model
requests consumed 54 seconds. This points toward sister/biome policy as the
main tuning opportunity, not rewriting the Java terrain model. A few builds
and code checks ran alongside it, so this is not an idle-machine maximum.
Frozen runtime and results:
`run/mapless-samples/benchmark-20min-c3dc871fcc964b73bff0196ea5f842da/`.

## Sister-Budget Tuning

On September 25, a three-minute diagnostic measured all 18 existing budget/cap
policies over the same completed families. At cap two it favored 4,096 sisters.
There were only nine productive completed families (57 completed model families,
one interrupted), so its estimated rates were not treated as measured throughput.
This diagnostic overlapped the RP throughput check; Chrome remained open.

The subsequent one-worker comparison ran sequentially after RP finished, using
a different reserved range shared by all four policies, 120 seconds per case,
cap two, and diagnostics OFF:

| Sister budget | Accepted seeds | Productive families | Seeds/min |
| ---: | ---: | ---: | ---: |
| 65536 (old default) | 30 | 17 | 15.0 |
| 16384 | 56 | 35 | 28.0 |
| 4096 (new default) | 91 | 66 | 45.5 |
| 1024 | 58 | 43 | 29.0 |

The new default produced 3.03x as many seeds in this short comparison. This is
not a guaranteed overnight rate or proof of a globally optimal budget. It
spends less time exhausting unsuccessful families and visits more families;
it deliberately skips some valid later sisters. Loot, forest, ravine, spawn,
Nether and stables predicates are unchanged, as is the published two-seed cap.
No production/search Minecraft generation or new acceptance proxy was added.

The baseline spent 110.3 of 120 seconds in biome checks (92%). This justified
budget tuning ahead of Java or biome-model rewrites. All 235 accepted records
across the four cases passed the saved-record Overworld model recheck. The 91
new-default records include nine stables, 29 bridge, 40 housing and 13 treasure.
Samples remain private and were not uploaded; the policies intentionally
overlap, so their outputs must not be blindly concatenated for publication.

Private measurements:
`run/mapless-samples/policy-44913ec6d29143f3b91e174aa43d900e/` and
`run/mapless-samples/policy-benchmark-6cd1c730da194e86888c7594c7175f4c/`.

The reusable scripts now accept `-Type buried_treasure` and an explicit reserved
`-Stream`: `Measure-ModelSearchPolicies.ps1` estimates policies on common
families; `Benchmark-ModelSearchPolicies.ps1` runs the four actual budgets.
Both require a fresh `-OutputDirectory`; only benchmark samples are suitable
for the live two-seed-cap importer, after normal deduplication and review.

## Next Steps

1. Expand gameplay samples, including qualifying stables, and calibrate ravine,
   treasure and spawn predictions on a representative offline sample.
2. Confirm the 4,096-sister policy with longer runs on additional reserved ranges
   before treating its measured rate as an overnight forecast.
3. Monitor the new BT/RP overnight preset under sustained multi-worker load;
   existing private snapshots and assigned ranges remain unchanged.
4. Grow the initial bank; keep existing Mapless choices and random-mode weights
   unchanged unless separately requested.
