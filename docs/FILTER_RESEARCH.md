# Offline Filter Source Research

Follow-up: [model-only production finder](MODEL_SEED_FINDER.md). The standalone
profotoce59 VillageGenerator was subsequently found and built; unlike the partial
SeedFinding village implementation discussed below, it models layout and loot.
All native-world checks below are now calibration-only, with no search fallback.

Reviewed 2026-09-13. This is an implementation decision record, not a claim that
the full replacement search or a production seed pool is finished. Public sources
were inspected, including source files and ZSG history; external programs were
not built or benchmarked during this review. Revision pins are listed below.

Follow-up implementation: the offline bank now has a persistent Java lower-48
and sister search, with early temple resource rejection. A separate
[native structure comparison](../tools/filter-worker/README.md) tests Cubiomes
against Minecraft geometry screening. Native production IPC and a full-seed
native biome backend remain future work. See [bank commands](OFFLINE_FILTER_BANK.md).

## Decision

Replace the current independent full-64-bit proposal search with a staged
lower-48/sister-seed search. Keep our existing native Minecraft checks as the
reference validator and independent final acceptance step. Do not discard that
work or relax the agreed criteria to make another generator appear faster.

The preferred first implementation is a small, offline Cubiomes-based candidate
worker. Reuse public ZSG criteria where appropriate and use persistent Java
analysis for properties that need it. No new generator library should be loaded
into runners' Minecraft instances. Benchmark the search architecture before
building a service or committing to additional native dependencies.

Keep these goals unchanged:

- Village/temple: usable natural surface lava within 96 horizontal blocks.
- Village: minimum seven iron including the minimum three-ingot golem drop,
  requiring an actual golem and at least four ingots of chest iron equivalents.
- Village/temple: tree-biome screening, not the stricter actual-tree requirement.
- Shipwreck: actual trees on an island or coastline; the current provisional
  tree radius is 96 blocks. Retain the other documented shipwreck requirements.
- Stables: at least one good gap and one triple-chest rampart.
- Keep explicit custom profile/version identifiers and seed-private handoffs.

Replacing a ruined portal requirement can broaden one part of the search, but
does not establish that the complete new profile is more common. Pool shape,
spawn proximity, village iron and stables requirements all affect final yield.

## What ZSG Actually Publishes

The public repository contains the five 1.16 filter implementations: mapless,
village, shipwreck, desert temple and jungle temple, including regular/OP
settings and the split between structure-seed and sister checks. It also
contains `filter_common.zig` loot/resource helpers, `rp_chest_offset.zig`,
`java_bits.zig`, `seed_features.zig`, filter dispatch and the Java worker.
These are substantive filtering implementations, not merely a launcher.
[Public source tree](https://github.com/DuncanRuns/ZigSeedGlitchless/tree/073e1d1f3150213c7c3787eda7ef8106cb789817/src).

Mapless supplies buried-treasure resource allocation, forest sampling and
ravine criteria. Village and desert temple supply their portal/resource/spawn
checks. Shipwreck supplies variant, rotation, loot and ravine tests. Jungle
temple combines temple loot with portal ignition/food and jungle eligibility.
Our lava replacement changes village/temple entry requirements; it does not
require inventing the remaining criteria from scratch.
[Mapless](https://github.com/DuncanRuns/ZigSeedGlitchless/blob/073e1d1f3150213c7c3787eda7ef8106cb789817/src/filter_1_16_mapless.zig),
[shipwreck](https://github.com/DuncanRuns/ZigSeedGlitchless/blob/073e1d1f3150213c7c3787eda7ef8106cb789817/src/filter_1_16_shipwreck.zig),
[jungle temple](https://github.com/DuncanRuns/ZigSeedGlitchless/blob/073e1d1f3150213c7c3787eda7ef8106cb789817/src/filter_1_16_jungle_temple.zig).

The published tree is not the complete deployed application: its root build and
application/configuration orchestration are absent, as are the imported
`ravines` implementation and Cubiomes Zig build/binding integration. Token
generation is explicitly withheld. We need our own offline orchestration, not
private token compatibility. The README grants MIT terms to published `src`
and `ZSGJavaBits` source, while distinguishing unpublished code and release
executables. This does not establish licenses for dependencies.
[Source availability statement](https://github.com/DuncanRuns/ZigSeedGlitchless/blob/073e1d1f3150213c7c3787eda7ef8106cb789817/README.md#source-code).

### Important Parity Details

- Both public temple and village tree checks accept forest, jungle, savanna,
  swamp and taiga categories. Their sampling distances are 20 and 30 blocks
  respectively. Our v2 temple check mistakenly excluded savanna; v3 corrects
  the final predicate and its early search hint.
- Those upstream nested loops do not reset their inner coordinate. We retain
  the intended full 3x3 grid rather than reproduce that apparent bug.
- The public source measures the fortress search from Nether origin, despite
  README wording describing distance from the bastion. Preserve the documented
  source-based convention in our profile; do not silently change it.
- Optional bastion-obsidian and terrain-route checks are explicit feature gates.
  Public flag initializers alone do not prove the deployed application's chosen
  settings. Our profile currently leaves those optional checks off.

[Temple source](https://github.com/DuncanRuns/ZigSeedGlitchless/blob/073e1d1f3150213c7c3787eda7ef8106cb789817/src/filter_1_16_desert_temple.zig),
[village source](https://github.com/DuncanRuns/ZigSeedGlitchless/blob/073e1d1f3150213c7c3787eda7ef8106cb789817/src/filter_1_16_village.zig),
[feature flags](https://github.com/DuncanRuns/ZigSeedGlitchless/blob/073e1d1f3150213c7c3787eda7ef8106cb789817/src/seed_features.zig).

### Sister Search: Correcting The Earlier Recommendation

ZSG changed its maximum sister search from 50 to all 65,536 upper-bit values.
The source comment explains that structure checks had become expensive enough
to justify keeping a successful lower-48 family longer. Current filter loops
stop at the first accepted sister; they do not always perform 65,536 checks.
The v3.2.1 release reports a large speed improvement, but that is an upstream
claim, not a measurement of our custom profile.
[Exact change](https://github.com/DuncanRuns/ZigSeedGlitchless/commit/7249b152afb3b8b5fd8511f72e0440e9133c8985),
[release history](https://github.com/DuncanRuns/ZigSeedGlitchless/releases/tag/v3.2.1).

Therefore, do not adopt a fixed 50/100-sister cutoff merely because another FSG
filter uses one. Compare bounded and exhaustive policies against the costs and
acceptance distribution of our own pipeline. A family that fails an actually
lower-48-invariant requirement can be discarded immediately. Failure of one
full-seed terrain or biome check cannot reject all of its sisters.

## Resource Assessment

### ZSGJavaBits And Bastion Layouts

The public Java worker generates bastion pieces and selected chest loot, adds
a type-dependent expected barter contribution to obsidian, and samples Nether
terrain columns for route viability. That obsidian total is not a guaranteed
barter outcome. The Zig bridge keeps a thread-local child process alive instead
of starting a JVM for every seed. This is a useful architecture to retain.
[Worker](https://github.com/DuncanRuns/ZigSeedGlitchless/blob/073e1d1f3150213c7c3787eda7ef8106cb789817/ZSGJavaBits/src/main/java/xyz/duncanruns/zsg/javabits/ZSGJavaBits.java),
[bridge](https://github.com/DuncanRuns/ZigSeedGlitchless/blob/073e1d1f3150213c7c3787eda7ef8106cb789817/src/java_bits.zig).

Its pinned BastionGenerator dependency already exposes `getStableInfo()`, counting
`hoglin_stable/ramparts/ramparts_1` and `hoglin_stable/walls/side_wall_1`.
These correspond directly to our triple-rampart and good-gap template tests.
Use this as a candidate-screening opportunity, while keeping our final chest
and block checks. Neither DuncanRuns' fork nor the inspected upstream tree
provided an explicit license file; clarify reuse terms before copying or
bundling it. Our native layout checker remains usable without that dependency.
[Layout helper](https://github.com/DuncanRuns/BastionGenerator/blob/9cccd19863e941d2dc8d2820037c48d49fe7d526/src/main/java/Xinyuiii/properties/BastionGenerator.java),
[upstream](https://github.com/Xinyuuu7/BastionGenerator).

### Cubiomes

Best fit for the low-level worker: structure attempts, variants, biome queries,
viability and lower-48 enumeration. It explicitly separates placement attempts
from biome viability. Use its `MC_1_16_1` configuration and differential-test
coordinates and negative-region division against native Minecraft.
[Search methodology](https://github.com/Cubitect/cubiomes/blob/e61f90580cbdd883214a8054670dacae655e59c0/README.md),
[structure implementation](https://github.com/Cubitect/cubiomes/blob/e61f90580cbdd883214a8054670dacae655e59c0/finders.c).

For 1.16.1, Nether structure-biome sampling uses the coarse biome layer, which
is suitable for lower-48 screening. Do not generalize that to every Nether
block-biome query: final Voronoi access uses the full seed hash. Overworld
biomes need full-seed checks. Cubiomes is not a decorated-block verifier for
actual trees, exposed lava source counts or intact chest placement.
[Nether noise and Voronoi](https://github.com/Cubitect/cubiomes/blob/e61f90580cbdd883214a8054670dacae655e59c0/biomenoise.c).

### GoATS Filter

Useful examples include separate lower-48 and biome passes, bastion type,
fortress pieces/double spawners, buried-treasure loot, stronghold/blind checks,
and End tower order/entry terrain checks. These are references, not additional
requirements to impose on our pool. Its main loop uses a bounded sister policy.
[Search ordering](https://github.com/AeroAstroid/GoATS-filter/blob/b329d026a57cb3ceec9ab0c4c1c4651ab0b6c066/logic/logic.c),
[main loop](https://github.com/AeroAstroid/GoATS-filter/blob/b329d026a57cb3ceec9ab0c4c1c4651ab0b6c066/main.c),
[individual filters](https://github.com/AeroAstroid/GoATS-filter/tree/b329d026a57cb3ceec9ab0c4c1c4651ab0b6c066/filters).

Its magma-ravine check uses initial carver height/width heuristics, not ZSG's
simulated midpoint or our final usable underwater column. Lava-roll prediction
also does not prove a usable pool. Its separate Java lava checker is a useful
lead, not a measured replacement for our verifier. It pins AeroAstroid's
Cubiomes fork (`e3a0f1ed5151f221bd0764e2cca51a26508786bd`); do not assume all
calls are interchangeable with current upstream. Root source is MIT, but audit
submodules and packaged helper provenance separately.
[Ravine](https://github.com/AeroAstroid/GoATS-filter/blob/b329d026a57cb3ceec9ab0c4c1c4651ab0b6c066/filters/magmaravine.c),
[lava](https://github.com/AeroAstroid/GoATS-filter/blob/b329d026a57cb3ceec9ab0c4c1c4651ab0b6c066/filters/lavapool.c),
[submodules](https://github.com/AeroAstroid/GoATS-filter/blob/b329d026a57cb3ceec9ab0c4c1c4651ab0b6c066/.gitmodules).

### EZ Seed Finder

Useful for declarative criteria, relative structure distances, variants and
caching query context. Not suitable as our authoritative loot checker:
`_eval_loot` can return success for unimplemented cases, chest coordinates can
be estimates, and `chest_loot_seed` uses world seed plus position hash rather
than the native population/decorator and chest-generation sequence. A familiar
loot table does not make the resulting loot match Minecraft.
[Criteria evaluator](https://github.com/codingsushi79/ezseedfinder/blob/fcd63bf1bff7049eb1c58909d6e8c715937a1e58/ezseedfinder/engine/checker.py),
[loot positions](https://github.com/codingsushi79/ezseedfinder/blob/fcd63bf1bff7049eb1c58909d6e8c715937a1e58/ezseedfinder/engine/loot_positions.py),
[RNG helper](https://github.com/codingsushi79/ezseedfinder/blob/fcd63bf1bff7049eb1c58909d6e8c715937a1e58/ezseedfinder/engine/java_random.py).

### SeedFinding Java And Older FeatureUtils

`mc_core_java` provides versioned placement/population/decorator RNG utilities,
positions and jigsaw support. `mc_biome_java` provides biome simulation;
`mc_terrain_java` provides sampled terrain columns. These are useful before
full chunk generation, but terrain columns are not a complete tree/lake
decoration simulation. Prefer the coherent dependency versions already pinned
by ZSGJavaBits over mixing old KaptainWutax and renamed SeedFinding artifacts.
[Core RNG](https://github.com/SeedFinding/mc_core_java/blob/eee662999e9f3fe037476b6940dbd6d5e23cdbb6/src/main/java/com/seedfinding/mccore/rand/ChunkRand.java),
[terrain implementation](https://github.com/SeedFinding/mc_terrain_java/blob/a03e440ec5b282e399382f2cc5ad0db91b438d2e/src/main/java/com/seedfinding/mcterrain/terrain/SurfaceGenerator.java),
[ZSG dependency pins](https://github.com/DuncanRuns/ZigSeedGlitchless/blob/073e1d1f3150213c7c3787eda7ef8106cb789817/ZSGJavaBits/build.gradle).

`mc_feature_java` includes temple and shipwreck generators and loot tests.
However, its village generator's `getChestsPos()` returns `null`. It cannot
currently replace the actual village chest-iron check. Older FeatureUtils is
useful lineage/reference material, not a reason to maintain two dependency
stacks. These projects identify their source as MIT and use Java 8-compatible
build targets in the inspected feature/core ecosystem.
[Village generator](https://github.com/SeedFinding/mc_feature_java/blob/b612ebee5723188045750757c117dad9e385a7f2/src/main/java/com/seedfinding/mcfeature/structure/generator/structure/VillageGenerator.java),
[FeatureUtils dependencies](https://github.com/KaptainWutax/FeatureUtils/blob/a271711f58e283547634ca31e466b9b8b0e5d825/build.gradle).

### Other References

- **Rust Nether generation:** provides Nether biome/noise queries with C/C++ and
  Python bindings, not decorated Nether terrain or bastion pieces. Cubiomes
  already covers the immediate biome need; adding Rust/FFI is not justified
  without a relevant benchmark. MIT source.
  [Implementation](https://github.com/SeedFinding/minecraft_nether_generation_rs/blob/a563af755a3c76a0cd38e2ba170de4d10e15eefc/src/lib.rs).
- **Historical shipwreck FSG:** useful separation of cheap structure/type/loot
  checks from biome/spawn checks and mode-dependent sister budgets. Several
  ravine/lava checks are disabled in the inspected source. Its README describes
  relaxed Overworld constraints, but that is not evidence that an equivalent
  strict profile became faster. Do not run bundled legacy binaries or copy
  undocumented proxies as guarantees; reuse terms need clarification.
  [Source](https://github.com/haydenthai/Minecraft_FSG_Shipwreck_Generator/blob/dcb194b9f6b7c1c2ae7618e3cf99794cbb583b1e/csprng.c).
- **AndyNovo filteredseed:** early lower-48/biome retry architecture and fixed
  Nether quadrants, useful historically. It is not a ready implementation of
  our lava, village iron, wooded shore or stables criteria. No explicit root
  license was found in the inspected checkout.
  [Source](https://github.com/AndyNovo/filteredseed/blob/17735b89e2dfa88ee1fd76d3eea641d68f9e9006/csprng.c).
- **Cubiomes Viewer:** use separately to explore relative criteria, biome area,
  and restrictive combinations visually. Its pre-1.18 spawn estimate is not
  exact because it cannot check grass blocks. Main code is GPLv3; there is no
  need to integrate its GUI into our worker.
  [Documentation](https://github.com/Cubitect/cubiomes-viewer/blob/3acc863245b30c655498d60323c50da0865e0199/README.md).
- **Additional lead, SeedChecker 1.16.1:** exposes native blocks, chest loot,
  spawn and selected chunk-generation stages without an ordinary world launch.
  Its branch matches our Yarn build, but targets Java 16, uses server mocks and
  serializes feature generation on a shared lock. No explicit license was found.
  Evaluate the design as a possible later verifier optimization, not an
  immediately safe replacement or a dependency for Java 8 runners.
  [API](https://github.com/jellejurre/seed-checker/blob/efe7c8fd742b694c54db0fd7336e186c0d698edd/src/main/java/nl/jellejurre/seedchecker/SeedChecker.java),
  [generation](https://github.com/jellejurre/seed-checker/blob/efe7c8fd742b694c54db0fd7336e186c0d698edd/src/main/java/nl/jellejurre/seedchecker/SeedChunkGenerator.java),
  [build](https://github.com/jellejurre/seed-checker/blob/efe7c8fd742b694c54db0fd7336e186c0d698edd/build.gradle).

## Proposed Pipeline

1. **Lower-48 proposal:** test structure attempt geometry and type. Cache only
   results proven independent of the upper bits. Add cheap layout/loot predicates
   after differential tests; handle biome-dependent variants explicitly.
2. **Sister evaluation:** test full-seed Overworld viability and tree categories.
   Validate the applicable biome's actual lava decorator configuration. Predict
   likely lake positions without treating attempted lakes as existing pools.
3. **Targeted native checks:** retain real village/temple/shipwreck resources,
   golem, pool provenance/shape, wooded shore, ravine and stables validation.
   Defer exact spawn and expensive generation until cheaper checks pass.
4. **Independent acceptance:** recheck staged successes in a fresh ordinary
   Minecraft world. Only matching criteria and anchors produce `VERIFIED_PROFILE`.
5. **Private pool:** store exact seed, profile, source/tool revisions and anchors.
   Deliver one selected entry at launch through future room integration, not a
   public downloadable pool. Worker failures must never produce fallback seeds.

Keep bounded queues and persistent worker processes. Use process isolation for
native Minecraft validators rather than assuming shared server globals are safe
across threads. IPC needs exact-length reads, request IDs, profile versions,
timeouts and explicit errors. Numeric seeds belong only in private handoffs.

Do not finalize on the first cheap-passing sister: if native validation rejects
it, continue that family's search until the chosen budget is exhausted. Avoid
filling the pool with many nearly identical sisters; initially target one final
verified seed per lower-48 family. Record that choice when measuring yield.

## Validation And Timing Plan

Implementation checkpoint: the offline staged Java runner now performs vanilla
bastion piece generation before sister evaluation. Minecraft 1.16.1's bastion
start uses a lower-48 `ChunkRandom.setCarverSeed`, fixed Y=33 and rigid jigsaw
pools, so this step needs templates but not terrain. The headless differential
test matched 192 sister layouts and 32 fresh native starts across all four types
and initial rotations. Full piece NBT is also checked for each generated bank
candidate, while intact-block/chest validation remains per full seed. This avoids
depending on the separately reviewed bastion library. See
[offline bank documentation](OFFLINE_FILTER_BANK.md#bastion-layout-differential-test)
for the test command and limitations.

Start with a small differential worker, not the entire replacement at once.
Check structure coordinates, variants, coarse Nether viability and biome queries
against the existing native implementation. Cover negative coordinates, biome
borders, multiple sisters, all relevant rotations and both accepted/rejected
cases. Terrain-sensitive predictions must remain hints until verified.

Then run these comparisons on the same machine and fixed candidate inputs:

| Comparison | Required measurements |
| --- | --- |
| Current Java proposals vs staged proposals | Lower-48 and full-seed checks/sec, CPU time, wall time, allocations/peak memory, matching predicates |
| 100, 1,000 and up to 65,536 sisters | Cost per family, sister pass rate, time to native/final acceptance, stalled-family tails |
| Cheap bastion layout/loot before vs after sisters | Cost saved per family, native rejection rate, disagreement cases |
| Existing native verifier vs targeted generation prototype | Per-stage chunk/time counts, memory, exact blocks/loot/spawn/anchors on the same corpus |
| Parallel worker counts | Verified seeds/hour, CPU/RAM saturation and duplicate-family rate, not merely proposal throughput |

Use randomized-but-recorded sister order without duplicates and fixed-budget
batches; keep heuristic flags and profile identical. Report warm-up/startup
separately. Generate and independently verify successful examples for each
variant before claiming it works fully. Include samples rejected by early hints
to detect unintended exclusion, not only survivors.

The existing v2 temple batch examined 10,000 preliminary candidates, generated
23 worlds and obtained zero passes in 547,887 ms (about 9m 8s). It excluded
savanna and used spawn/wood search hints, so it is not an unbiased measurement
of the intended v3 profile. Zero passes cannot provide average valid-seed time.
Do not alter the archived report or quote upstream throughput as our own.

For a measured rate of `R` independently verified, family-distinct seeds/hour,
an initial pool estimate is `pool size / R` hours on that configuration. Report
sample size, variation between batches and final verification cost alongside
it. We do not yet have a defensible value for `R` for the complete new profiles.

## Reviewed Revision Pins

These are source-review pins, not newly added build dependencies.

| Repository | Revision |
| --- | --- |
| DuncanRuns/ZigSeedGlitchless | `073e1d1f3150213c7c3787eda7ef8106cb789817` |
| DuncanRuns/BastionGenerator | `9cccd19863e941d2dc8d2820037c48d49fe7d526` |
| Cubitect/cubiomes | `e61f90580cbdd883214a8054670dacae655e59c0` |
| AeroAstroid/GoATS-filter | `b329d026a57cb3ceec9ab0c4c1c4651ab0b6c066` |
| codingsushi79/ezseedfinder | `fcd63bf1bff7049eb1c58909d6e8c715937a1e58` |
| SeedFinding/mc_biome_java | `17af8cb1110fdc983b7cb2b887d1fb2060e23ee3` |
| SeedFinding/mc_feature_java | `b612ebee5723188045750757c117dad9e385a7f2` |
| SeedFinding/mc_core_java | `eee662999e9f3fe037476b6940dbd6d5e23cdbb6` |
| SeedFinding/mc_terrain_java | `a03e440ec5b282e399382f2cc5ad0db91b438d2e` |
| KaptainWutax/FeatureUtils | `a271711f58e283547634ca31e466b9b8b0e5d825` |
| SeedFinding/minecraft_nether_generation_rs | `a563af755a3c76a0cd38e2ba170de4d10e15eefc` |
| haydenthai/Minecraft_FSG_Shipwreck_Generator | `dcb194b9f6b7c1c2ae7618e3cf99794cbb583b1e` |
| AndyNovo/filteredseed | `17735b89e2dfa88ee1fd76d3eea641d68f9e9006` |
| Cubitect/cubiomes-viewer (documentation) | `3acc863245b30c655498d60323c50da0865e0199` |
| jellejurre/seed-checker (1.16.1) | `efe7c8fd742b694c54db0fd7336e186c0d698edd` |
