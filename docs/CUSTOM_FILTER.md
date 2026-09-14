# Custom ZSG Filter Work

For current search, use the [model-only finder](MODEL_SEED_FINDER.md).
The native-world pipeline described below is now an offline calibration harness.
`Search-FilterCandidates.ps1` has been replaced by the standalone model entry point.

## Status

The development pipeline now also has a reusable-server offline bank runner,
village/temple/shipwreck profile validation, natural-lake provenance, stables
layout checks and an independent fresh-server verifier. See
[Offline Filter Bank](OFFLINE_FILTER_BANK.md) for the current pipeline and commands.
The older probe and PowerShell runner described below remain partial investigation
tools; do not use their `PARTIAL_PASS` results as approved seeds.
The legacy temple probe below still uses actual trees. The current bank profile
uses tree-biome checks for village/temple; its criteria take precedence. See
[Filter source research](FILTER_RESEARCH.md) for the proposed replacement search
and the confirmed savanna correction in profile v3.
**This is not yet a new
seed provider or a selectable room filter.** Existing FSG filters, host prefetch,
seed metadata and portal placement are unchanged by this stage.

The probe is a separate development source set, excluded from release JARs. The
terrain checker classes have no lifecycle or tick hooks and are not called during
normal play. No new external library is bundled.

## Temple Validation And Retries

Temple-mode validation now checks the exact four basement chest positions in the
generated temple. They are below the piece's declared bounding box. Each chest is
copied into a temporary block entity, then its vanilla loot table and saved loot
seed are resolved with default luck. The saved chest is not opened or rewritten;
the probe asserts its NBT remains unchanged. Missing/wrong-table chests and a zero
(unseeded) loot seed fail conservatively. A pre-opened chest in a manually edited
probe save is not accepted as evidence of the original generated loot.

The resource check reserves one iron for ignition, three diamonds or three iron
for a pickaxe, and three iron for a bucket. Thus it needs seven iron, or four iron
plus at least three diamonds. This preserves the public temple allocation rule,
not a guarantee of nearby flint, water, food, or every possible tool recipe.

All qualifying pools within the agreed 96-block circle are considered, nearest
first with deterministic ties. The selected pool must also be within 224 blocks
of Overworld 0,0 per axis, and have an actual accessible tree within 20 horizontal
blocks. This replaces the old portal-adjacent tree-biome check with actual wood.
The generated **world spawn reference**, before player spawn-radius randomization
or room spawn relocation, must be within 32 blocks per axis of the temple's start
reference or the selected pool. These tests do not promise the exact eventual
player spawn. The pool envelope and spawn-distance conventions preserve the public
temple filter's portal reference limits; pool distance and wood distance are circles.

The temple snapshot uses radius 116 plus the usual capture margin, so a pool at
the outer edge can still have trees checked 20 blocks beyond it. A failing nearest
pool does not hide a farther qualifying pool. Lake-generation provenance, stables
layout and remaining profile checks are performed separately by the newer bank
runner, not by this legacy partial probe.

```powershell
# Up to three fresh candidate worlds; stop at the first partial pass
.\scripts\Search-FilterCandidates.ps1 -Type temple -MaxCandidates 3
```

If local PowerShell script execution is disabled, after reviewing the script use
`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Search-FilterCandidates.ps1 -Type temple -MaxCandidates 3`.
This override applies only to that process; no machine policy needs changing.

The PowerShell runner uses the normal Java/Gradle environment and offline cache.
It defaults to temple, three candidates (maximum 20), search starting value 1,
100000 cheap attempts and 15000 ms per preliminary search. It increments the search
starting value between candidates. `-Overpowered`, `-SearchSeed`, `-Attempts`,
`-SearchTimeoutMillis` and `-MaxMinutes` are available; the default 15-minute batch
budget is soft and checked between candidates, not an interruption of Minecraft
world generation. Duplicate exact seeds within a batch skip repeated validation.
Village and shipwreck mode currently run only their earlier partial checks, not
the temple loot/spawn rules. Run only one search/probe/batch at a time.

Each candidate is validated in a separate headless server process and fresh save.
Terrain/resource rejection advances to the next search. A failed process, malformed
handoff or stale/missing report stops with an error instead of silently retrying.
No fallback seed is supplied when the candidate budget is exhausted. The saves are
retained under `run/filter-probe`, so larger batches use disk space.

`validation.json` contains the matching candidate ID, filter, rejection reasons,
counts and timings, never the numeric seed. It is reset before launch and atomically
replaced on completion. `batch-report.json` aggregates the attempts and wall-clock
times, including Gradle/server startup and shutdown. `PARTIAL_PASS` means only the
implemented checks passed, **not an approved seed**. `REJECTED` is a normal candidate
failure; `ERROR` is a broken validation run. Fixtures still run after measurements.

Initial end-to-end retry smoke test: two fresh temple candidate worlds were
rejected, first for spawn distance, then for resources and missing surface lava.
The runner advanced automatically and stopped at its configured limit. These took
about 63 and 58 seconds including Gradle/JVM startup, spawn generation, validation,
fixtures and saving; the measured validation portions were about 11.5 and 4.4
seconds. This small **offline harness** measurement is neither average valid-seed
search time nor added race loading time. No full filter pass has been established.

## Preliminary Candidate Search

`FilterCandidateSearch` samples full 64-bit seeds from a reproducible, search-local
`SplittableRandom`. It does not touch a running world's RNG, create a server,
generate chunks, request online FSG seeds or require a ruined portal. Searches
have attempt/time limits and cancellation checks; no result is returned on failure.
This is not yet the upstream lower-48/sister-seed optimization.

The cheap checks currently cover:

- Vanilla structure placement using Minecraft's default 1.16.1 spacing and salts.
- Bastion/fortress selection using the complementary vanilla `nextInt(5)` roll.
- Bastion within 96 and fortress within 256 blocks **of Nether 0,0**, per axis.
  Preliminary OP searches use 32 and 112 respectively. This retains the public
  source's coordinate convention, not an assumption that fortress distance is
  measured from the bastion or the runner's eventual portal.
- Village and shipwreck start positions in region 0,0, within 224 and 208 blocks
  respectively. Temple's preliminary envelope is 320, allowing a 224-block entry
  reference plus the new 96-block pool window; final pool/spawn placement still
  needs validation. These are chunk-corner references.
- Native Overworld/Nether biome eligibility at the same quarter coordinates used
  for structure starts. Village biomes are limited to plains/savanna/desert and
  shipwrecks to ocean biomes.
- For village/temple, at least one possible lava-lake attempt whose 16x16 footprint
  could intersect the 96-block search circle and reach the minimum pool height.

The preserved distance conventions are taken from the public
[village filter](https://github.com/DuncanRuns/ZigSeedGlitchless/blob/main/src/filter_1_16_village.zig)
and [temple filter](https://github.com/DuncanRuns/ZigSeedGlitchless/blob/main/src/filter_1_16_desert_temple.zig),
with the lava replacement's provisional temple envelope called out above.

`LavaLakeHints` reads the actual vanilla biome's LAKES feature list to obtain the
lava feature index and decorator chance, then uses native population/decorator
seeding. Its small placement-roll helper is regression-tested against Minecraft's
`LavaLakeDecorator`, including the remaining RNG state. The hint search includes
neighboring chunks whose positive-X/Z lake mask can reach into the search area.
An attempt is **not** a successful pool: terrain, village exclusion, lake geometry
and later decoration can still make it fail. Final validation must associate the
accepted natural pool with a lake attempt rather than accepting unrelated ruined
portal lava. This logic targets vanilla default generation, not custom worldgen
datapacks or additional mods' decorated feature graphs.

The candidate result intentionally says `PRELIMINARY`. It does not yet verify
loot, actual spawn distance, shipwreck variant/rotation/ravine, abandoned villages,
the village's replacement resource policy, stables layout, or the optional upstream
Nether obsidian/terrain checks. Neither regular nor OP candidates are approved seeds.

### Search And Handoff Commands

```powershell
# Search without creating a Minecraft world
.\gradlew.bat generateFilterCandidate -PfilterProbe -PfilterType=temple --offline

# Ten independent, reproducible searches, reporting preliminary timings only
.\gradlew.bat generateFilterCandidate -PfilterProbe -PfilterType=temple -PfilterSamples=10 --offline

# Validate the last preliminary result in its own candidate world
.\gradlew.bat runFilterProbe -PfilterProbe -PfilterCandidate --offline
```

Also supported: `-PfilterType=village|shipwreck`, `-PfilterOp=true`,
`-PfilterSearchSeed=<search starting value>`, `-PfilterAttempts=<limit>` and
`-PfilterTimeoutMillis=<1..300000>`. Defaults are search starting value 1,
100000 attempts and 15000 ms per sample. Samples default to 1 and are limited to
100. `filterSearchSeed` is the input to the search sampler, **not a Minecraft seed
to validate**. Repeating these inputs deliberately repeats the search.

`run/filter-probe/candidate.json` is a local, ignored developer handoff. It contains
the exact seed as a string, format version, filter, coordinates, lake attempts and
a unique ID. It is replaced atomically and marked non-ready before a search starts;
failed searches cannot leave the old seed marked ready. Console output contains
only status, counts and timings. This is not the host's live prefetch storage.
Run search and probe commands sequentially, not concurrently.

The probe validates the handoff format/status and loaded seed, checks the exact
predicted start chunk rather than locating some other structure, and uses a
`candidate-<id>` save under `run/filter-probe`. Generating another candidate does
not overwrite an old save, so these disposable worlds consume disk space until
removed. The normal `world` probe save is preserved. Returning to that older save
requires restoring its matching `server.properties`; the task refuses ambiguous
reuse. A successful terrain probe still does not approve the complete filter.
Candidate-mode probing also verifies that the predicted bastion and fortress
produce valid structure starts, without generating their full terrain. It does
not yet validate their loot, stables layout or accessible routes.

Initial measurement on this development machine: 10/10 temple searches produced
preliminary candidates, averaging 96 ms of search time. This excludes JVM/registry
startup and all final validation. It is **not time-to-valid-seed** or an estimate of
acceptance rate after the remaining requirements.

## Agreed Requirements

- Village and desert temple: replace the ruined-portal requirement with a usable
  surface lava pool within **96 horizontal blocks** of the structure reference.
- Shipwreck: nearby actual trees on either an **island or coastline** are acceptable.
- Stables: at least one good gap and one triple-chest rampart.
- Preserve the other ZSG requirements, particularly the Nether portion. Do not
  change existing public filter choices while developing the custom variants.

## Implemented Terrain Checks

`SurfaceTerrainChecks` takes an immutable terrain snapshot, does not read or
advance any RNG, and returns qualifying pools closest first with fixed Z/X/Y tie
breaking. Its original single-pool API still returns the closest result. Terrain
distance is circular X/Z distance, not a square or a 3D distance.

The initial conservative lava definition is:

- At least 10 cardinally connected, exposed source blocks at the same Y level.
- At least one 2x2 square of sources, excluding thin lava channels.
- Surface at Y >= 60, excluding deep open ravine lava.
- Adjacent solid, non-hazardous shore within one block of the surface height,
  with two air blocks in which a player can stand.
- Only exposed source cells inside the search radius count. Separate puddles,
  diagonal connections, flowing lava and different-height shelves do not combine.

Ten sources correspond to the lava needed for a minimal portal frame. This is a
conservative screening test, **not a proof that a particular portal-building
technique fits**, that the player has a bucket or water, or that a route is clear.
Trees covering the pool, small but deep pools, and some usable irregular pools
can be rejected. Y=60 and the shape limits are initial implementation choices to
evaluate with seed samples, not inherited ZSG rules. It checks the final block
configuration, not whether the lake generator originally placed those blocks.

The wooded-land test requires:

- At least four consecutive vertical Overworld log blocks rooted on dirt, grass,
  coarse dirt or podzol.
- At least eight non-persistent leaf blocks near the top of that trunk.
- Dry standing space beside the trunk. Water and hazardous shore blocks fail.
- No island-only test, so wooded coastlines pass as well.

The provisional shipwreck tree search radius is **96 blocks**; only the lava
radius has been explicitly agreed. Tall or unusual trees may fail the bounded
trunk/canopy check. This does not prove a swimming/walking route from the wreck,
attribute every leaf to that trunk, or replace the existing shipwreck loot checks.

## Snapshot And Cost

`MinecraftSurfaceTerrain.capture` must run on the owning Overworld server thread.
It finishes chunks in a bounded, consistently ordered area, including a one-chunk
decoration margin, before reading the terrain. It retains only each column's
surface height and top 36 block classifications, with a four-block horizontal
margin. Reads beyond captured bounds fail conservatively rather than guessing air.

The resulting snapshot contains no world/chunk references and can be checked off
thread. Capturing a new area can be expensive because it generates real decorated
chunks. This is why the probe is offline, and why it is not attached to room
creation, world loading or a server tick. It must eventually be the expensive final
validation after cheap candidate rejection, not how every random seed is tested.
The snapshot describes terrain at capture time; it is not a guarantee against all
possible later neighbor-generation ordering effects or changes made by other mods.

## Running The Probe

Use the project's normal Java/Gradle setup:

```powershell
.\gradlew.bat runFilterProbe -PfilterProbe -PfilterType=village --offline
.\gradlew.bat runFilterProbe -PfilterProbe -PfilterType=temple --offline
.\gradlew.bat runFilterProbe -PfilterProbe -PfilterType=shipwreck --offline
```

The probe uses its own save under `run/filter-probe/world`. Its first seed defaults
to 1; use `-PfilterSeed=<numeric seed>` for a different initial seed. Seed 0 is
rejected because Minecraft 1.16.1's server properties treat it as random. Once a world
exists, conflicting seed settings are rejected instead of silently checking the
old terrain. Move that disposable world aside to test another seed. No race or
MultiMC save is opened.

The existing accepted development-server EULA is reused if available. Otherwise,
read and accept Minecraft's server EULA in `run/filter-probe/eula.txt` first. The
probe server binds to localhost on an ephemeral port and shuts down on completion.

`-PfilterX=<x> -PfilterZ=<z>` changes the structure-search origin, which defaults
to 0,0. Minecraft locates the requested structure with a bounded locate radius of
8 structure-search regions. The returned locate position is the current probe
reference, not a verified entrance or chest position. The probe checks both lava
and trees within 96 blocks of that reference and reports each separately.

Read `[FilterProbe]` in `run/filter-probe/logs/latest.log` for structure presence,
pool/tree results, exposed source count, and separate locate/capture/check timings.
`COMPLETE` only means the probe ran successfully, not that the seed passed the
complete proposed filter. The probe does not print numeric seeds or anchor positions.
Its `server.properties` and world save naturally contain the explicitly supplied
test seed; they are local development files, not host prefetch or relay state.

After the natural-terrain measurements, the probe temporarily builds a small
high-altitude fixture in that disposable world and restores its original blocks.
It verifies source versus flowing lava, natural versus persistent leaves, chunk
boundary handling and snapshot independence. No fixtures are created during play.

## Still Required Before Room Integration

1. Complete sufficiently large successful timing batches and independent
   fresh-server verification. A candidate passing the reusable harness alone
   must not be served to players.
2. Keep the profile's differences from the public upstream source explicit.
   Village resources now require seven minimum total iron including the golem;
   temple and shipwreck retain their documented resource allocations.
3. Validate many generated candidates, including false rejections and actual
   portal-building/wood accessibility, and measure throughput on fresh worlds.
4. Add separately named, versioned custom filter specifications. Carry accepted
   anchors privately alongside the exact prefetched seed, reject stale generations,
   and publish them only at launch. Do not silently fall back to unverified seeds.

The public [ZigSeedGlitchless source](https://github.com/DuncanRuns/ZigSeedGlitchless)
is useful for preserving criteria, but does not supply all of the deployed
generator/service implementation. This stage does not claim byte-for-byte parity
with that service or modify its installed FSG integration.
