# Viewer Performance

This guide separates retained improvements from experiments and remaining
profiling leads. Historical component measurements are not whole-game FPS
claims or guarantees for every recording. Recorder encoding, buffer and
finalization findings are in [Recorder Performance](RECORDER_PERFORMANCE.md).

Keep raw recordings, JFR files, screenshots and session results under ignored
`run/`. The cleanup of these notes changes no runtime behavior.

## Correctness Boundaries

Preserve timestamps, world/reset identity, packet order, seek destination,
recorded detail, trail bounds and resource cleanup. Test forward/backward seeks,
gaps, corrupted input, cancellation and replacing a replay on disk. Quick Mode
deliberately trades detail for speed; it must not silently replace full-detail
playback as an optimization.

CPU samples do not measure GPU time. Allocation improvements do not establish
lower latency. Separate first open, repeated open, indexing, world reconstruction
and steady playback rather than combining them into one timing.

## Retained Improvements

### Completed Index Reuse

A session-only LRU retains immutable completed indexes for up to four recordings
and an estimated 64 MiB. Oversized results are uncached. The cache retains no
open archive handles, packet buffers or workers and publishes only after successful
EOF, with cancellation protected by the same close lock.

Identity includes canonical path and relevant ZIP entry sizes/CRCs for
`recording.tmcpr`, `zsg-rooms/races.json` and `metaData.json`, with stability
checks between the open archive and disk. This is invalidation, not authenticity
verification. Pending writes, replacement, removal and failed scans must not
reuse stale results. Builders release mutable state; UI consumers copy ItemStacks.

Historical repeated-index measurements fell from roughly 518-710 ms to
1.69-2.21 ms on the tested recordings. First-open scanning and ReplayMod world
reconstruction remain separate costs; this is not a playback-FPS improvement.

### Chest Index Allocation

World/dimension/position lookups use primitive maps instead of allocating string
keys. Chunk invalidation visits relevant entries and still invalidates both
halves of double chests, preserving equal-timestamp event order.

The measured fresh-scan allocation reduction was about 25-27%; one scan was
slower in elapsed time. Treat this as reduced bookkeeping allocation, not a
universal first-open speedup.

### Detailed Frame Locality

Detailed-frame queries reuse a loaded index page when it covers the requested
time, plus a synchronized 16-byte header buffer. Returned frames and decoded-state
identity remain unchanged.

A paired Java 17 test used a synthetic three-hour, 20 Hz track with 216,000
256-byte frames, 8,192 queries per batch, four warmups and nine measured rounds:

| Lookup | Before | After | Allocation/query |
| --- | ---: | ---: | ---: |
| Sequential | 55.48 us | 7.39 us | 336 -> 304 B |
| Reverse sequential | 54.45 us | 7.05 us | 336 -> 304 B |
| Random | 74.15 us | 68.79 us | 336 -> 304 B |
| Same frame | 80 ns | 84 ns | 0 -> 0 B |

Fixture construction and indexing are excluded. Random-query ranges overlapped;
no reliable random-seek benefit was established. Tests cover page boundaries,
gaps, forward/backward/random queries and world/entity mismatches.

### Duplicate Trail Requests

An already pending selection is compared before allocating and copying a new
request. Paired request-method benchmarks measured allocation falling from
312 to 64 B for one UUID, and from 1,544 to 64 B for 32 UUIDs. This applies only
to duplicate pending requests, not all trail rendering or historical queries.
Tests retain defensive selection copies and request identity checks.

A separate trail disk-page buffer rewrite was rejected: warm-window CPU rose
from 157 to 182 us with unchanged allocation, and eviction tests showed no
established gain. Lower allocation alone is not a reason to adopt a rewrite.

### Quick Mode Held Items

Quick Mode reads main/offhand equipment from optional player-HUD snapshots and
applies copied hand items only when world, player and time identity match.
It does not restore the full inventory or change recording. Seek/reset handling
must not leak equipment between worlds or players.

## Experimental Full-Detail Seeking

Lighting reconstruction dominated the inspected full-detail seek workload
(roughly half of sampled CPU). That establishes a target for investigation, not
which work can safely be skipped.

Whole-seek light batching was rejected because destination light arrays differed.
The boundary-aware replacement remains experimental and default-off. It flushes
before relevant light arrays, block changes, explosions, chunk unloading,
view-center/distance changes, world changes and batch completion. Thread-local
scope is cleared in `finally`; normal playback/exporter sends are not wrapped.

The setting is separate from Quick Mode and cannot be changed during Quick Mode
or its initialization. Closing the replay clears it; each replay starts with it
off. After disabling it, rewind/reconstruct before judging the resulting world
state. Tested destinations matched, but there is **no demonstrated speedup**.
Do not promote it on correctness checks alone.

The seek harness and [profile comparison tool](../scripts/Compare-ViewerSeekProfiles.mjs)
check completion, destination and pending lighting/state. They do not establish
equivalence of all client RNG, particles, block entities or pixels.

## Supplemental Track Priorities

The high-rate camera, Super Detailed presentation and local-sound audit used
synthetic 10- and 30-minute archives on Java 17 with four active processors.
It was headless and silent, with warm filesystem caches, 240 Hz camera samples,
60 Hz presentation samples and four sound events per second.

| Optional track open/index | 10 minutes | 30 minutes |
| --- | ---: | ---: |
| Camera | 131 ms | 211 ms |
| Presentation | 113 ms | 200 ms |
| Local sounds | 843 ms | 2,532 ms |

Each cell is one observation, not a distribution or cold-disk guarantee. A
30-minute repeat measured 181/176/2,151 ms respectively. These findings prioritize
experiments; they are not measurements of shipped improvements:

1. Evaluate buffered sound-index construction. Unbuffered primitive writes in
   `LocalSoundTrack.read` dominated these optional-track load tests. Sounds load
   before presentation, delaying its availability. Preserve validation and cleanup.
2. Avoid unchanged paused sound-cursor disk queries. Paused calls measured about
   126-184 us versus 3-4.4 us while advancing. Preserve exact seek/pause/resume,
   blocked state, dimension/identity and no-catch-up-audio behavior.
3. Evaluate bounded presentation reads. Sequential lookup/decode measured roughly
   23-27 us with about 519 B/query; random queries were about 157-223 us.
   This excludes packet/world reconstruction. Camera queries allocated zero
   bytes in sequential, stationary and random cases.

Recorder-side fixtures found about 4.95/9.89/19.78 MiB per hour of raw camera data
at 60/120/240 Hz. Presentation extrapolated to 28.63 MiB/hour with ordinary tools
and 242.87 MiB/hour with 1 KiB item NBT. These are uncompressed sidecar sizes,
not final MCPR sizes or heap retention. Queues are bounded and spools disk-backed.
The producer benchmark excludes sustained pressure and live render observation.

## Further Validation

- Profile long AA recordings, dense piglin trails/highlights, Faithful Follow,
  player switching and high render distances. Small development recordings do
  not represent these workloads.
- Keep native item/glint rendering and exact trail detail. Piglin clustering is
  quadratic but sampled at 10 Hz of replay time; measure crowded scenes before
  introducing spatial indexing or caches.
- Timer string formatting has an equivalence-tested local candidate with small
  absolute savings, not a demonstrated FPS benefit. Avoid lowering refresh rates
  or rounding displayed times to claim an equivalent optimization.
- Measure 60/120/240 Hz live capture under chunk-loading bursts, transformed
  mining-hook allocations when capture is disabled, capture-health HUD cost and
  concurrent compression. Headless API tests do not cover injected hook overhead.
- Measure multi-hour stop-to-save latency and temporary disk peaks. Track joins
  can each wait five seconds before archive copying, while client shutdown waits
  two seconds for the recorder worker. This is a code-inspected lifecycle risk,
  not a reproduced failure in the audit.
- Validate payload/event equivalence, gaps, corrupt input and resource cleanup
  for each candidate before changing defaults.

## Reproduce

Use a Java 17 test JVM, not merely a Java 17 Gradle launcher. Run benchmarks
serially and use fresh private output paths. Normal test runs skip these
opt-in benchmarks:

| Test class | Opt-in |
| --- | --- |
| `DetailedTrackBenchmark` | Test JVM property `zsgrooms.viewerDetailBenchmark=true` |
| `TrailPerformanceBenchmark` | `ZSG_TRAIL_BENCHMARK=1` |
| `SupplementalTrackBenchmark` | `ZSG_SUPPLEMENTAL_TRACK_BENCHMARK=1` |

Run the viewer's `test --tests '*ClassName' --rerun-tasks --info` task with
`-p replay-viewer`. Configure system properties on the **test JVM** explicitly;
the build does not automatically forward arbitrary Gradle system properties.
`ZSG_SUPPLEMENTAL_TRACK_MINUTES` accepts 1-30, default 10. Environment flags
are not task inputs, so changing them requires `--rerun-tasks`.
These benchmarks assert correctness, not timing thresholds, and process sound
events without opening an audio device.

The opt-in `ViewerProfile` driver is in `src/replayPlaybackTest`, outside release
JARs. It expects exactly three private replay copies, including the specified End
segment at 180 seconds; it is a fixed workload, not an arbitrary-replay benchmark.
From the repository root, replace the two path placeholders:

```powershell
.\gradlew.bat runReplayPlaybackTest --offline -PreplayViewer=true `
    '-PreplayPlaybackFile=<absolute path to a fixture replay>' `
    -PviewerProfile=true '-PviewerProfileJava=<Java 17 executable>' `
    -PviewerProfileJfr=run/viewer-performance/baseline.jfr `
    -PviewerProfileOutput=run/viewer-performance/baseline.jsonl
```

For the seek driver, use `-PviewerSeekProfile=true` and keep
`-PviewerSeekBatchLights=false`. Compare the baseline with
`-PviewerSeekFast=true` using distinct output paths and the comparison tool.
Keep live test audio muted; do not run these profiles alongside microbenchmarks.
