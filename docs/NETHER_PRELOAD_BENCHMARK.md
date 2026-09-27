# Nether Preload Performance

## Purpose

The September 27, 2026 gameplay log consistently reported `terrainReady=false`
through portal transfer. Correcting the ticket radius had bounded the requested
area, but the FEATURES ticket did not itself schedule generation. The old
headless lifecycle test explicitly finished FEATURES synchronously, masking this
second problem. A new opt-in benchmark lets normal server ticks and worker tasks
perform the preload without forcing completion.

## Method

- Fresh world/save and JVM per variant, seed `7338020334776386520`.
- Four destination references: Nether (4000,4000), (6000,4000), (8000,4000),
  (10000,4000). These are controlled test locations, not the filter's intended entry.
- An Overworld source portal is prepared before measurement, followed by 320
  settling ticks. The measured charge is 80 actual server ticks, about four seconds.
- Charges shorter than 3.8 seconds fail the run, preventing server catch-up from
  artificially reducing the time available for generation.
- Measure START_SERVER_TICK to END_SERVER_TICK during charge, synchronous vanilla
  dimension-transfer duration, process CPU and GC time, readiness, arrival and axis.
- Java 25.0.2, 512 MiB initial / 1536 MiB maximum heap, ActiveProcessorCount=4,
  view distance 8, debug logging OFF. ActiveProcessorCount is not a CPU quota.
- Dedicated/headless Minecraft 1.16.1 with Fabric and ZSG Rooms. No client rendering,
  packets to a real client, ReplayMod, Lithium, Starlight, World Preview or Toolscreen.
- Serial runs, no seed-search workers. First target includes colder JIT/worldgen
  code; raw results retain it and the summary can optionally exclude it.

The initial `current-a` run had shortened catch-up charges and is excluded.
Valid exploratory data is in `run/nether-preload-perf/{off,current,request,ring}-b`.
The `current-b` variant refers to code *before* the scheduling fix.

## Exploratory Results

Four locations per variant. These are pilot measurements, not confidence intervals
or a prediction of the user's complete loading screen duration.

| Variant | Median transfer ms | Max transfer ms | Charge tick p95 ms | Max charge tick ms | Ready before entry |
| --- | ---: | ---: | ---: | ---: | ---: |
| OFF | 846.43 | 1181.68 | 3.11 | 8.31 | 0/4 |
| Radius-corrected, polling only | 826.43 | 1594.82 | 2.84 | 9.27 | 0/4 |
| Explicit request, center only | 479.53 | 695.02 | 3.60 | 8.89 | 4/4 |
| Explicit request, 3x3 FEATURES | 484.65 | 582.17 | 9.85 | 93.53 | 4/4 |

Readiness means both FEATURES and FULL finished during charge. Only the center
was promoted to FULL; the experimental ring's surrounding tickets remained at
level 34. All arrival positions and portal axes matched across the variants.

The center-only experiment reduced the median transfer stall by about 43% versus
OFF. Median process CPU in the charge window increased from 289 to 1555 ms;
median charge-plus-transfer CPU was 1953 vs 2031 ms. This shifts work earlier,
not a demonstrated reduction in total computation. CPU sums include worker threads,
but exclude background work completing after transfer and server shutdown.

The ring used about 2516 ms median charge CPU and 3539 ms charge-plus-transfer CPU,
without improving median transfer latency, and produced one charge tick over 50 ms.
It is not enabled in production. Reported heap values are endpoint samples, not
peak memory. Chunk-holder counts include incomplete holders, not generated chunks.

## Production Confirmation

`run/nether-preload-perf/fixed-c` repeats the four locations using the actual
production fix, without the diagnostic request intervention:

- Median transfer 550.53 ms, maximum 930.61 ms: about 35% lower median than OFF
  in this pilot, compared with 43% in the earlier center-only experiment.
- FEATURES ready in 4/4 trials; FULL ready in 3/4. In the remaining trial,
  FEATURES finished at charge tick 62, beyond the existing FULL-promotion window.
  The healthy-tick and remaining-charge guards were intentionally preserved.
- Charge tick p95 4.05 ms, maximum 10.77 ms, no measured charge ticks over 50 ms.
  These are server tick callback intervals, not client frame times or all work
  performed by the server's between-tick task pump.
- Median charge CPU 1515.62 ms; charge-plus-transfer CPU 2304.69 ms.
- Arrival positions and portal axes matched every earlier variant.

The variability between runs matters: this establishes that the missing scheduling
request is fixed and provides evidence of a useful latency reduction, not a stable
35-43% speedup guarantee. The fresh-world readiness assertion, portal/lifecycle
integration test and build passed. Unit tests: 480 total, 479 passed, one skipped.

## Repeating

Use a new directory for each run; the task refuses an existing world. The existing
test-server EULA file under `run/perch-benchmark` must already be present.

```powershell
.\gradlew.bat runNetherPreloadBenchmark --offline --no-daemon --max-workers=1 `
    '-Dorg.gradle.jvmargs=-Xmx768m' -PpreloadMode=current -PpreloadExpectReady=true `
    -PpreloadDirectory=run/nether-preload-perf/new-fixed-run
```

`current` tests the actual current production preloader. `off` disables warmup;
`ring` adds the experimental 3x3 terrain requests. `request` is the original
diagnostic scheduling intervention and is redundant after the production fix.
Other parameters: `preloadSeed`, `preloadTrials` (1 to 16). Do not set
`preloadExpectReady=true` for OFF. The readiness assertion is a functional
regression check under these controlled conditions, not a guarantee on every PC.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -Command "& './scripts/Summarize-NetherPreloadBenchmark.ps1' -Directory @('run/nether-preload-perf/off-b','run/nether-preload-perf/request-b','run/nether-preload-perf/ring-b')"
```

The summary checks charge lengths and rejects arrival/axis mismatches for matching
seed/reference pairs. `-SkipFirstTrial` excludes each JVM's first location.

## Next Experiments

Keep the corrected single-chunk request and existing healthy-tick/remaining-charge
guards. Do not widen the production area based on this pilot. Before further
changes, repeat paired runs on additional seeds, reverse variant order, and
measure the integrated client with the actual optimization mods. Separately
time portal POI lookup, placement search, client chunk delivery and rendering
to identify the remaining stall. Earlier race-start warmup should only be tried
with total load time and memory measurements, since it can move cost to startup.
