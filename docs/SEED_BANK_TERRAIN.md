# Seed Bank Terrain Preparation

The **ZSG Rooms Desert Temple** and **ZSG Rooms Village** bank profiles prepare
two usable surface-lava routes when their new race world loads. This is a gameplay
repair, not an additional seed-filter rejection. Existing bank files, search
criteria and the seed service do not change. Shipwreck, FSG, manual and random
profiles are unaffected.

This behavior is part of these two bank profiles, independent of the RNG and
spawn-proximity toggles. They are modified practice/racing worlds, **not vanilla
verifiable runs**, including when selected with the Regular Verifiable preset.
Use the original FSG choices for an unmodified filter world.

## Placement Rules

- Use the same located temple or village as the existing structure-proximity
  feature. The service does not yet send the model's structure coordinates.
- Keep qualifying natural pools. Count connected surface pools with at least
  10 exposed source blocks, a 2x2 portion and a dry two-block standing space.
- Search for two routes within 96 horizontal blocks of the structure anchor first,
  expanding to a hard maximum of 128 only if needed. Routes must be at least
  48 blocks apart and point in directions at least 60 degrees apart. Among the
  available partners for a site, prefer opposite sides (at least 90 degrees).
- Each route needs an exposed, unfrozen 4x4 source-water patch within 48 blocks
  of its pool. A small rounding margin keeps patch centers inside that limit.
  Existing water may be at most eight blocks above/below the pool. Only naturally
  present water is used: the repair never creates water ponds or sources. A dry or
  unsafe candidate triggers a search for another location, not abandonment of a route.
- Added lava lakes use Minecraft 1.16.1's `LakeFeature` geometry: 4-7 overlapping
  ellipsoids in a 16x16x8 volume, variable-depth lava below the waterline, cave air
  above it, and the vanilla randomized stone-border pass. They are not fixed
  shallow basins. The liquid surface is one block below the selected ground level.
- A staged lake must still pass the exposed-source and shore-access checks before
  it counts. Water distance and route separation use an actual exposed lava source,
  not the center of the generation volume. Existing water can serve both pools.
- Placement starts on ground at Y63-248 with build-height clearance. Lower cavities
  and boundaries must be solid before excavation; the upper cavity follows the
  vanilla shape instead of flattening a rectangular area. Sites avoid caves, existing
  liquids, trees, unusual blocks, block entities, the initial spawn area, and
  generated structure pieces with a safety margin. They do not excavate buildings
  or supply portal frames, obsidian, iron or ignition items.

The water scan extends beyond the pool-placement radius by the full water distance
plus a patch-edge margin: 146 blocks initially, 178 only for the expanded search.
Those are inspection limits, **not** permission to place pools beyond 128 blocks.
The area is completed with a decoration halo before inspection. Water patches are
spatially indexed, so dry candidate sites are rejected before modeling a lake.
Likewise, existing pools on the same side can remain alongside an added opposite
route. Two is a minimum target, not a limit on naturally generated pools.

The search tries a coarse four-block grid, then every remaining block column,
before expanding from 96 to 128. Viable sites are matched against earlier choices;
there is no fixed quota of first-choice retries. A complete pair is preferred.
If the full search cannot find two safe sites, it keeps one qualifying natural pool
or creates one safe planned pool instead. That fallback is committed only after
exhausting the pair search, and is reported as one pool, not two. If no valid site
exists, terrain remains untouched. Both incomplete outcomes log a warning and the
game still loads that seed; there is no automatic seed replacement. The repair
never adds water, exceeds 128 blocks, or excavates protected terrain to force success.

## Lifecycle And Determinism

`StructureSpawnProximity.prepare` runs the repair on the integrated server thread
during initial server startup, before the normal race simulation. It also runs
when Spawn Near Filter Structure and nearby-animal guarantees are off. A cached
spawn cannot bypass preparation in a fresh same-seed world.

The area is generated in a fixed order before inspection. Candidate order uses
an independent `surface_pool_repair` event seed, derived from the world seed,
profile and structure X/Z. It does not advance mob, barter, eye or vanilla world
RNG streams for placement choices. The same seed, anchor and generated terrain
produce matching patches across fresh resets; differing worldgen mods can still
produce differing terrain.

Each lava shape has its own `surface_lava_lake` event seed, derived from the world
seed, profile and candidate X/Y/Z. Rejecting a candidate does not advance a shared
shape RNG or change later candidates. The geometry and stone lining match vanilla;
route placement, source/access checks and protection are custom. Vanilla's blanket
village-reference rejection is not used: actual structure pieces remain protected.
Existing soil outside the carved cavity/stone border is preserved rather than
running vanilla's separate biome-dependent soil restoration pass.

The planner retains viable site coordinates rather than large patches for every
candidate, then stages and rechecks both selected routes before applying edits.
Discarded plans never touch the world. It validates each lava patch against existing water. Solid
containment is established before fluids, and failed writes restore original
block states. Nearby chunk-generation mobs are moved to dry ground after editing
rather than used to choose a different pool location.

The world stores `zsg_rooms_surface_pools` persistent data, including incomplete
attempts. Reopening a save never refills lava that players have collected. A world
whose game time has already advanced is not retrofitted. A fresh same-seed reset
has fresh persistent data and repeats the deterministic preparation.

This adds one bounded loading-time pass, not recurring per-tick work. Generating
previously unprepared chunks may dominate its cost. Seed Debug Logging includes
the chosen route count, added lava count and total preparation time, but
not the exact seed.

## Testing

Unit tests cover repeatable edits, profile scope, existing-pool preservation,
source counts/containment, nearby water, opposite routes, unsafe terrain, failed
writes, the 128-block limit, water beyond the old scan boundary, exhaustive column
coverage, deterministic single-pool fallback and persistent state. Fallback tests
also check failed writes and continuing past an inner pool to find an outer partner.
A direct comparison checks cavity, liquid depth and
stone edging against the actual Minecraft `LakeFeature` on 128 flat-stone fixtures.
An optional offline test uses actual bank seeds in
disposable Minecraft worlds, repeats each seed, runs real fluid ticks and checks
that used lava is not refilled:

```powershell
.\gradlew.bat runSurfacePoolCalibration -PfilterProbe -PfilterCalibration=true -PpoolCalibrationSamples=2 --offline --max-workers=1
```

This uses the existing `run/perch-benchmark` test-server setup and reads the main
bank by default. Override its input with `-PpoolCalibrationBank=PATH`. Its sample
limit is 1-5 seeds per type. It never edits the bank or runs in production search.
Use `-PpoolCalibrationOnlySample=N` to repeat just one sample from that selection.
The harness also reports safe-site rejection counts for diagnosing partial placement.
By default, a sample without two separated, watered pools fails the calibration.
For measuring preparation failures across a mixed sample, pass
`-PpoolCalibrationAllowPartial=true`: partial results are explicitly reported and
counted separately, while reset consistency, containment, block-entity preservation
and no-refill assertions still run. This mode does not certify two pools on every seed.
Logs are in `run/perch-benchmark/logs/latest.log`, under `[SurfacePoolTest]`.
The test temporarily disables that test server's watchdog while generating its
disposable worlds, and a Gradle finalizer restores the original server properties.
Do not run another benchmark using that same test directory concurrently.

### Initial Fixed-Basin Calibration (Superseded Geometry)

On 2026-09-14, two temple and two village bank seeds were each generated twice.
Three seeds produced two qualifying, separated pools; one hilly village produced
only one on both attempts under the safety checks. All eight worlds passed reset
consistency, fluid containment, block-entity preservation and no-refill checks
with partial-result reporting enabled. The strict two-pool check fails on that
village, so this remains a safety-first target, not an unconditional guarantee.
This small sample is not an estimate of the whole bank's success rate.

### Vanilla-Shape Calibration (Before Natural-Water-Only Change)

The same four seeds were tested twice again after replacing the fixed lava basins
with vanilla geometry. All eight worlds produced two qualifying, separated pools
with nearby water, including the previously partial hilly village. Reset comparisons
included submerged cavities and banks. Fluid containment, block-entity preservation
and no-refill checks passed; there were no partial results in this sample.
This does not remove the safety-first fallback for other seeds. Existing saved
worlds keep their original terrain; test the new shapes in freshly created worlds.
Ponds added by older versions are not removed from existing saves. The calibration
above predates removal of artificial water and is not a success-rate measurement
for the natural-water-only implementation.

### Natural-Water-Only Pair Search (2026-09-15)

After adding full water-range coverage, coarse/fine site searches and the 128-block
fallback limit, two temple and two village bank seeds were each generated twice.
All eight worlds passed the strict two-route test, including the hilly village.
Natural water was unchanged, routes satisfied the separation/angle checks, reset
terrain matched, and containment, block-entity and no-refill checks passed.
There were no partial results. This is a four-seed regression sample, not proof
that every bank seed can provide a safe pair within the hard limit.

The full Gradle build also passed: 378 main tests and 14 filter-probe tests,
with one existing optional native-parity test skipped. The headless harness prepares
the complete inspection area before timing the repair, so its reported preparation
milliseconds must not be interpreted as total added world-loading time.
