# Viewer Performance Review

## 2026-10-19 Recorder And Viewer Pass

Baseline: core 1.1.0 / viewer 0.6.0, commit `79d3ad8`. Retained changes are
deliberately limited to scratch-buffer reuse, disk-index page locality, and
duplicate trail-request allocation. Sampling, timestamps, packet ordering,
payloads, file formats, detail limits, trail bounds and settings are unchanged.
There is no new performance mode and no detail is dropped.

### Paired Measurements

Tests ran sequentially on Java 17.0.18 with four active processors. Original and
candidate paths alternate in the same JVM after warmup. These are component
measurements, not whole-game FPS, whole-recorder cost, or seek-completion claims.
Local XML/JFR evidence is under ignored `run/replay-performance-2026-10-19/`.

Detailed-frame lookup uses a synthetic three-hour, 20 Hz track (216,000 frames,
256-byte payloads), 8,192 queries per batch, four warmups and nine measured rounds.
Fixture construction/indexing is excluded. Both paths check each returned frame.

| Lookup | Original median | Retained change median | Allocation per lookup |
| --- | --- | --- | --- |
| Sequential | 55.48 us | 7.39 us | 336 -> 304 B |
| Reverse sequential | 54.45 us | 7.05 us | 336 -> 304 B |
| Random | 74.15 us | 68.79 us | 336 -> 304 B |
| Same frame | 80 ns | 84 ns | 0 -> 0 B |

The main gain is avoiding a whole-index disk search when the requested time is
already covered by the loaded index page. A synchronized 16-byte header buffer
also replaces per-frame allocation. Random-query ranges overlap substantially;
do not claim a reliable random-seek improvement. An initial candidate retaining
`ByteBuffer` wrappers had worse random-query timing and was simplified back to
local wrappers before the final measurement.

Trail requests compare an already pending selection before allocating and copying
a new request. The benchmark uses the actual request method with a dormant worker
and a frozen original method, not a stripped-down collection operation. Six
alternating rounds follow warmup and adaptive CPU-duration calibration.

| Duplicate pending request | Original CPU/request | Current CPU/request | Allocation/request |
| --- | --- | --- | --- |
| 1 UUID | 195 ns | 111 ns | 312 -> 64 B |
| 32 UUIDs | 1,660 ns | 553 ns | 1,544 -> 64 B |

This applies only to duplicate pending requests, not all frames or all trail work.
The proposed trail page-buffer rewrite was **rejected**: longer batches showed
warm-window CPU rising from 157 to 182 us with unchanged allocation, and eviction
workloads did not establish a gain. The disk-store implementation remains unchanged.

Detailed Capture now uses its own nested-safe, thread-local scratch buffer through
the existing encoding-buffer helper. Buffers above 8 KiB are released; output
arrays remain independently owned. A paired envelope/item-cache benchmark compares
every output byte outside timing across unchanged, one-changing and all-changing
slot scenarios. Representative unchanged-frame allocation:

| Fixture | Original | Reused scratch |
| --- | --- | --- |
| 46 slots, mostly empty | 2,048 B | 944 B |
| 90 slots, mostly empty | 4,576 B | 1,408 B |
| 54 slots, 16-byte NBT fixture payload | 20,632 B | 7,208 B |
| 90 slots, 128-byte NBT fixture payload (over retention cap) | 83,156 B | 83,156 B |

CPU timings are mixed, so this is an allocation reduction, not a demonstrated
general recorder speedup. Client/HUD observation, sampling, packet queues,
compression and disk writing are outside that microbenchmark. Normal recording
with Detailed Capture disabled is unaffected.

### Verification And Reproduction

Added tests cover detail page boundaries, gaps, world/entity mismatches, forward,
backward and random lookups; pending-request identity and defensive selection
copies; byte-identical detailed payloads with nested NBT mutations; buffer
ownership, nesting, thread isolation, oversize release and overflow recovery.

An isolated Java 17 recorder smoke passed inventory/crafting/chest interactions,
actual barter outcomes, pauses, dimension changes and one same-seed reset. Its
76.7-second replay contains 8,069 packets and 480 detailed frames, no reported
detail gaps, and passes the independent packet-order/content inspector. The JFR
includes startup and world generation; its GC totals are not recorder-specific.
Two approximately 147-149 ms connection-lock waits occurred together during
startup. This is a follow-up profiling lead, not enough evidence to change the
packet synchronization design.

Both builds pass. The full Java 17 suites passed 601 core, 242 viewer and 15
writer tests (12 additional tests skipped in the normal runs). Opt-in benchmarks
were run separately. Independent reviews found no boundary, ownership or
concurrency regression in the retained production changes.

The Faithful Follow runtime check decoded the fresh recording's populated detail
track, then used isolated synthetic presentation fixtures for deterministic UI
checks. It passed automatic inventory/crafting/chest playback with timeline
seeking, pause/hide recovery, manual read-only inspection, timer/counter/render
distance toggles, F1/F5, legacy fallback and browser playback at 1280x720 and
640x480. Screenshot pixel checks passed; crafting screenshots were also inspected.
The input recording's SHA-256 stayed unchanged. This is not an end-to-end proof
of arbitrary live-client capture equivalence; payload equivalence is covered by
the separate encoder fixtures above. The native folder dialog was not retested.

The historical-trail runtime regression also passed using its fixed recording:
paused historical positions, stable samples, backward/forward seeking, duration
bounds, reset-world isolation, recorded mob paths, independent styles, synthetic
fallback, shortcut deduplication and settings snapshot/restore.

Opt-in benchmarks (excluded from normal test runs):

- `DetailedTrackBenchmark`: JVM property `zsgrooms.viewerDetailBenchmark=true`.
- `TrailPerformanceBenchmark`: environment `ZSG_TRAIL_BENCHMARK=1`.
- `ReplayDetailedCaptureBenchmark`: environment `ZSGROOMS_DETAILED_CAPTURE_BENCHMARK=true`.

Run the corresponding Gradle `test --tests '*ClassName'` task (viewer tests use
`-p replay-viewer`), ensuring the test JVM is Java 17, not just the Gradle JVM.
Use `--info` for benchmark output and `--rerun-tasks` when changing only opt-in
environment flags. The local `java17.gradle` init script and saved XML reports
record this pass's exact setup. These benchmarks do not assert timing thresholds.

## Earlier Investigation

Date: 2026-09-28. Baseline investigation followed by completed-index caching and
live chest-lookup allocation reduction.

## Quick Mode Held Items

Quick Mode now reads main-hand and offhand equipment from the existing optional
`zsg-rooms/player-hud.bin` snapshots. This is viewer-only: no recorder changes,
packet rescans, cache-format changes or new recording data. Snapshot sampling is
roughly 200 ms, so very brief switches and exact use/swing animations are not
reconstructed by this feature.

The viewer performs the existing binary-search timestamp lookup before rendering,
decoding only when the frame or recorded player changes. Equipment getters use
that cached result; the underlying replay inventory is never rewritten. Empty
recorded hands override older equipment, whereas unavailable, invalid or stale
snapshots leave ReplayMod's own result alone. The overlay is limited to the
recorded player in the current world and timestamp, and is bypassed while changing
playback modes or outside Quick Mode. Armor and other mobs are unaffected.

The Quick Mode runtime checks exercise main/offhand equality against HUD samples,
backward and forward seeking across six dimension/reset intervals, camera/armor
exclusion, missing snapshot fallback, stale timestamps, unchanged inventory,
cached decoding and return to normal mode. The timing probe alternates 24 seeks
with and without the HUD track in one initialized Quick Mode session, including
the following viewer update; it excludes initial Quick Mode indexing and rendered
item draw cost. This is a small regression check, not a broad performance claim.

Two paired runs measured 1,665/1,706 ms and 1,828/1,806 ms respectively
(without/with overlay, 24 seeks per side). The small differences changed sign;
there was no consistent seeking slowdown in this fixture. The full latest
detailed-follow, Quick Mode and audio runtime suite passed, as did all 93 viewer
unit tests. Added explicit changed-slot, empty-hand and malformed-payload checks.
The old audio shutdown assertion was corrected to test whether reconstruction
is active, rather than expecting the default-on preference to become false.

Reproduce using a current six-interval recorder smoke fixture, not the old
`details-smoke.mcpr` which predates recorded inventory/crafting screen intervals:

```powershell
.\gradlew.bat runReplayPlaybackTest --offline '-PreplayViewer=true' `
  '-PreplayDetailsSmoke=true' `
  '-PreplayPlaybackFile=<absolute path to a copy of the current recorder smoke replay>' `
  '-PviewerProfileJava=C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot/bin/java.exe'
```

## Full-Detail Seeking Investigation

**Result: rejected whole-seek batching; boundary-aware batching passes the current
destination comparison, but has no demonstrated speedup.**
The viewer now offers **Replay Analysis > Seek batching (experimental)**, off
for each newly opened replay. Quick Mode remains separate; the recorder is
unchanged. The rejected whole-seek experiment and all measurement hooks remain
confined to `src/replayPlaybackTest`, outside both release JARs.

### Default-Off Setting And Ordering Fix

The production experiment defers only the extra chunk lighting callbacks inside
interactive, synchronous `ReplayHandler.doJump` packet batches. It flushes pending
work before light-array updates, block changes, explosions, chunk unloads,
view-distance/center updates and world changes, and again at the batch boundary.
A render-thread-local scope is restored in `finally`, including exceptional
exits. Normal playback and direct exporter packet sends are not wrapped.

The setting cannot be changed during Quick Mode or its initialization. Closing
the replay clears it; another replay starts with it off. Disabling does not
rebuild existing state immediately: rewind to reconstruct without batching.
The tooltip retains a lighting warning because the sample is not a universal
correctness proof.

In the `seek-barriers` run, all **18/18** captured destinations matched the
settled baseline for blocks, sky/block lighting, entities and indexed viewer data,
with no pending lighting. Audio suppression also passed. This fixes the observed
17/18 lighting mismatches from the aggressive prototype below. However, most
deferred callbacks encounter a barrier almost immediately, so little work is
coalesced. Representative seek-plus-settlement measurements:

| Transition | Baseline | Boundary-aware |
| --- | --- | --- |
| 4:20 / 90s to 180s | 3,496 ms | 3,770 ms |
| 7:41 / 95s to 90s | 3,974 ms | 4,326 ms |
| 3:25 / 95s to 90s | 3,519 ms | 3,638 ms |
| 3:25 / 90s to 180s, End | 1,358 ms | 1,469 ms |

These are individual runs, not statistically established regressions or gains.
Do not enable this by default or advertise a speedup. Further optimization must
reduce total reconstruction work without losing the ordering established here.
Unit tests cover empty flushes, coalescing, barriers and failure cleanup in the
batch helper. Real replay runs also assert default-off state on every open.

Final verification: 93 viewer unit tests passed. `seek-setting-off` matched the
original baseline at all 18 destinations; `seek-setting-on-final` matched that
off run at all 18 destinations too. Both runs exercised checkbox toggling and
control bounds at logical GUI sizes 640x360 and 427x240. Screenshots were inspected
and small-screen tooltip wrapping corrected. Timings again varied in both
directions, providing no evidence of a reliable speed gain.

### Original Bottleneck

The installed ReplayMod 1.16.1-2.6.27 bytecode schedules a complete lighting drain
after each processed chunk-data packet in `FullReplaySender.channelRead`.
This also appears in the upstream [FullReplaySender source](https://github.com/ReplayMod/ReplayMod/blob/stable/src/main/java/com/replaymod/replay/FullReplaySender.java).
Full-detail backward seeking restarts packet playback, so these drains can run
thousands of times before reaching the destination. Our test-only hook measures
the drain callback directly, rather than inferring its duration from CPU samples.

The first baseline's 95s-to-90s rewind in the 7:41 replay took 3,786 ms, including
3,286 ms across 1,572 lighting callbacks. This is primarily a ReplayMod/Minecraft
reconstruction cost, not viewer HUD or chest-index overhead. Packet replay is
needed to restore state; draining all intermediate lighting is the architectural
candidate, but its ordering also affects the result. A hypothetical zero-cost
replacement of those callbacks would save at most their measured 3.29 seconds
in this one seek. It is not a prediction of achievable performance.

### Experiment And Correctness

The opt-in `viewerSeekBatchLights` experiment suppresses only ReplayMod's extra
per-chunk drain while a synchronous packet batch is active, then drains the
current world's lighting before `sendPacketsTill` returns. It does not skip
packets, replace Minecraft's light algorithm or use Quick Mode. This is a
rejected development experiment, not an available user setting.

Ran four isolated Java 17 sessions: baseline/candidate, then candidate/baseline
with explicit post-seek lighting settlement before destination snapshots.
Each uses all three private real recording copies and the fixed target sequence
90s, 95s, 90s, 180s, 30s, 90s, covering short forward skips, rewinds, dimension
changes and an End destination. The End-containing fixture also crosses recorded
world resets on the way to 180s. Settings remain 1280x720, render distance 8,
120 FPS cap and 512 MiB initial/2 GiB maximum heap, without the user's full mod
stack. Both variants use the same instrumentation and separate JVMs.

The first pair found different destination light arrays, but both paths still
had pending lighting. The second pair explicitly drains that remaining work and
includes its time in the totals below. Snapshot hashing is outside the timed
window. These are seek-plus-lighting times, not all-chunk-mesh-ready times.

| Recording / target transition | Baseline settled | Batched settled | Changed chunk light-array hashes |
| --- | --- | --- | --- |
| 4:20 / 90s to 180s | 3,496 ms | 2,054 ms | 305 |
| 7:41 / 95s to 90s | 3,974 ms | 1,693 ms | 442 |
| 3:25 / 95s to 90s | 3,519 ms | 1,828 ms | 485 |
| 3:25 / 90s to 180s, End | 1,358 ms | 725 ms | 0 |

All 18 destinations had identical loaded chunk block-state hashes, entity
IDs/types/positions/rotations/health, and recorded/predicted chest histories and
HUD tracks. Audio was muted immediately after every seek in both variants.
However, **17 of 18 destinations had different light-array hashes even with no
pending light updates**. Individual differences ranged from 6 to 485 loaded
chunks. The snapshot records both sky/block arrays and their presence; this is
not a visual severity measurement or proof of which result is more correct.
It is enough to reject the claim of unchanged lighting state. Short skips also
did not consistently improve. Driver `PASS` means the workload completed, not
that the candidate is equivalent; the separate comparison correctly returns
failure for these results.

Local Minecraft source explains why deferral needs more care: queued section
light arrays can replace earlier queued arrays, section unloads remove data,
and edge propagation depends on update ordering. Simply waiting for all pending
work at the end does not establish equivalent results. No speculative production
patch from that aggressive variant was retained. The subsequent boundary-aware
setting is documented above.

### Next Decision

- **Keep current reconstruction:** known behavior, but retains the measured wait.
- **Narrow, boundary-aware batching:** best incremental investigation. Preserve
  light-packet replacements, section unloads and dimension transitions before
  coalescing any drains, then use the destination comparison. Remaining savings
  are unknown and may be much smaller than whole-batch deferral.
- **Existing lighting-engine replacement:** potentially substantial, but a
  broader compatibility change. Requires an explicit 1.16.1/mod-stack evaluation;
  not demonstrated to preserve behavior by this experiment.
- **Full-detail seek checkpoints:** potentially avoids replaying history, but
  requires complete restorable client state, bounded memory and invalidation.
  Our chest-index cache is not such a checkpoint. This is a separate large design,
  not a small cache addition; no performance estimate is justified yet.

Recommendation: keep the boundary-aware setting experimental and off; do not
ship the aggressive candidate. Use the harness to test any further boundaries
before considering a larger replacement.
Real progress here must improve total seek-plus-settlement time and pass state
comparison, not just move the cost into frames after the seek.

### Reproduction

Use three private replay copies as for `ViewerProfile` below; this is a fixed
workload, not a general benchmark for arbitrary files. Use fresh output names.

```powershell
.\gradlew.bat runReplayPlaybackTest --offline '-PreplayViewer=true' `
  '-PreplayPlaybackFile=<absolute path to one of the three replay copies>' `
  '-PviewerSeekProfile=true' '-PviewerSeekBatchLights=false' `
  '-PviewerProfileJava=C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot/bin/java.exe' `
  '-PviewerProfileJfr=run/viewer-performance/seek-before-settled.jfr' `
  '-PviewerProfileOutput=run/viewer-performance/seek-before-settled.jsonl'

# For the current setting, use viewerSeekFast=true (leave viewerSeekBatchLights=false).
# For the rejected prototype only, use viewerSeekBatchLights=true instead.
# Use distinct JFR/JSONL paths for every run, then compare the desired pair:
node scripts/Compare-ViewerSeekProfiles.mjs `
  run/viewer-performance/seek-before-settled.jsonl `
  run/viewer-performance/seek-batch-settled.jsonl `
  run/viewer-performance/seek-comparison.json
```

The comparison rejects incomplete workloads, incorrect destination timestamps,
pending lighting or state mismatches, and exits nonzero on failure. Snapshots
do not cover all client RNG, particles, block-entity data or rendered pixels, so
passing is necessary but insufficient for a production lighting change. Original
recordings and installed game mods were not edited. Private artifacts use
`seek-before`, `seek-batch`, `seek-before-settled` and `seek-batch-settled` prefixes
under `run/viewer-performance`; they are not intended for the repository.

## Fresh Chest Index Allocation

Implemented world/dimension-scoped primitive position maps for builder-only
live chest state. Block-update misses no longer construct string keys or box
positions. This uses Minecraft's existing fastutil dependency, adding no shipped
library. Chunk invalidation visits only the matching world/dimension; double
chests still invalidate both halves. Permanent history keys, snapshots, ordering
and queries are unchanged. Builder maps are cleared when indexing finishes.
The recorder, replay format, sounds, seeking and playback detail are unchanged.

Compared the completed-cache build above with this change in four separate
Java 17 development sessions, ordered baseline/candidate/candidate/baseline.
Each session scanned the same three real recording copies five times with the
completed-index cache explicitly cleared before each measured scan. Filesystem
and ReplayMod caches were warm; these are fresh index scans, not cold-disk tests
or end-to-end first-open timings. The paused game continued rendering during
scans. All sessions used the same 512 MiB initial/2 GiB maximum heap and baseline
graphics settings. Analysis tools ran after the measured processes exited.

Five-scan medians per recording and session:

| Recording | Baseline 1 | Candidate 1 | Baseline 2 | Candidate 2 |
| --- | --- | --- | --- | --- |
| 7:41 | 730 ms | 619 ms | 772 ms | 587 ms |
| 4:20 | 398 ms | 342 ms | 390 ms | 361 ms |
| 3:25 | 420 ms | 351 ms | 400 ms | 437 ms |

JFR sampled allocation weight on the indexing worker, summed only over the
15 measured scan windows, fell from **1,016 to 765 MiB** in the first pair and
**1,005 to 729 MiB** in the reverse-order pair: approximately **25-27% less**.
Allocation attributed to `ChestOpening.key` fell from approximately 212 MiB
per baseline session to 0.5/3.0 MiB. These are sampled weights, not exact byte
counts or whole-process memory savings. Estimated retained cache memory remains
2,623,072 bytes for the same three recordings.

Timing improved on two recordings in both pairs, but the third was faster in
one pair and slower in the other. Do not claim a universal percentage speedup,
an FPS improvement or faster seeking. The repeatable result is reduced transient
allocation without changing indexed results.

Verification: viewer build and all **90 tests** passed. A frozen copy of the old
string-key implementation is exercised against 25,000 deterministic randomized
events, with full history comparisons and separate double-chest boundary,
equal-timestamp, world/dimension and primitive-key edge cases. All four runtime
profiles completed successfully. All **72 history digest checks** agreed across
the old and new implementations for every recorded/predicted chest frame and
milestone in the three real recordings. Runtime cache guards also passed.

Private artifacts: `run/viewer-performance/chest-{before,after}.{jfr,jsonl}`
and `chest-{before,after}-2.{jfr,jsonl}`, with matching `-summary.json` files.
Baseline JAR: `run/viewer-performance/chest-before.jar`. Optimized viewer JAR
SHA-256: `B59941CE07E57CDED2D3E68A04D51969D948DF224C80FE572910B207B53C2EA1`.
Use the reproduction command below with `-PviewerProfileColdIndexes=true`;
`-PreplayViewerJar=<path>` selects a saved baseline JAR without replacing the
normal build output. Cold-index mode ends after the index/open checks and does
not run playback windows. Its repeat-open timings are not cache-hit benchmarks.

Recommendation: keep this narrowly scoped allocation reduction. The largest
remaining measured wait is seeking/world reconstruction, which needs a separate
investigation before changing behavior. Timer formatting remains a small,
independently equivalence-tested candidate, not a likely frame-rate fix.

## Completed Index Cache

Implemented a session-only LRU for completed milestone, recorded-chest and
predicted-loot histories. It retains at most four recordings with a 64 MiB
estimated-memory budget. This is an accounting estimate, not a hard JVM heap
limit; active viewers can also retain histories after cache eviction. Oversized
results still work but are not cached. No open archive handles, packet buffers,
workers or recording objects are retained by the cache.

Keys use the canonical file path and ZIP entry sizes/CRCs for `recording.tmcpr`,
`zsg-rooms/races.json` and `metaData.json`. File attributes must be stable while
reading the identity, and the open ReplayMod archive must match the archive on
disk. Changes to unrelated camera paths do not require rescanning packets.
These are change-detection checksums, not cryptographic authenticity checks.
Pending packet writes, replacements or removals bypass caching. Only successful
EOF scans with unchanged inputs are inserted, with cancellation checked under
the same lock as closing. Failed metadata/chest scans are never cached.

Finished histories drop builder-only state. Chest inspection copies item stacks
when opening its UI so item renderers/tooltips cannot mutate a shared history.
The recorder, packet scan logic, seeking, Quick Mode and playback fidelity are
unchanged. First opens still scan normally; metadata/timer loading is not cached.

### Before And After

Same Java 17 configuration and three recording copies as the baseline below.
The final build ran the same six opens, nine extra index loads and nine playback
windows. All extra index loads reused the original completed history objects.

| Recording | Baseline repeated index median | Cached median | Baseline repeat open | Cached repeat open |
| --- | --- | --- | --- | --- |
| 7:41 | 655 ms | 1.69 ms | 998 ms | 512 ms |
| 4:20 | 518 ms | 2.09 ms | 521 ms | 406 ms |
| 3:25 | 710 ms | 2.21 ms | 751 ms | 359 ms |

Cached index trials ranged from 1.47 to 4.40 ms. The open measurements are one
repeat open per recording per run, not statistically established averages;
readiness still includes parallel Minecraft and ReplayMod work. A preceding
cache trial measured 592/398/450 ms repeat opens, corroborating the direction
but also showing run-to-run variation. First-open startup was 5.02 seconds in
the final run versus 4.03 seconds in the baseline; caching offers no cold-open
benefit, and these runs do not isolate startup/JIT variation.

The three retained entries totaled 2,623,072 estimated bytes (2.50 MiB), versus
the 64 MiB estimated budget. Across the nine cache-hit windows JFR recorded no
milestone-worker CPU or allocation samples, compared with 158 CPU samples and
527 MiB sampled allocation weight in the baseline scan windows. These short
1-4 ms windows are below meaningful sampling resolution: this demonstrates
removal of the repeated full scans, not literally zero lookup allocation.

Do not interpret this as an FPS or seeking improvement. The first cache trial's
initial follow p95 was worse (31.81 ms versus 14.05 ms baseline), while later
normal/detail/analysis p95 values were roughly 12.7-13.4 ms. Removing nine long
scans also shortens warmup before playback, so those frame comparisons are not
controlled steady-state measurements. A five-second JFR summary process ran
during the final run's freecam portion; use that run for indexing/opening and
functional checks, not a clean playback-throughput comparison. World lighting
and chunk reconstruction remain the separate seeking bottleneck.

Verification: viewer build and 87 tests passed, including LRU count/weight
eviction, oversized entries, canonical paths, packet/metadata/header changes,
preserved timestamps, finished history queries, reset/dimension isolation,
double-chest aliases and equal-timestamp ordering. Development playback checks
also verify shared result identity and bypass on pending packet writes,
replacements and removals. Both cache profiling runs completed successfully.
Original recording files and installed mods
were not changed; the three replay copies still match their original hashes.

Private artifacts: `run/viewer-performance/cache17-final.{jfr,jsonl}` for the
final build; `cache17.{jfr,jsonl}` is the preceding cache trial before adding
the standard-header CRC to identity. Final viewer JAR SHA-256:
`62D895336C31BB631CE0CF6ADEBE242C514A142A13A2D7555031FE56CD0C5F46`.

Recommendation: keep this bounded cache. The follow-up first-scan chest-key
allocation change is implemented and measured in the section above.

## Measured In-Game Baseline

Completed September 28 using the released viewer 0.4.0, ReplayMod 2.6.27,
Minecraft 1.16.1 and Java 17.0.18 (the user's configured Java major version).
The isolated development instance used 2 GiB maximum heap, 1280x720, render
distance 8, VSync off and a 120 FPS cap. It did not include the user's full
performance-mod stack or shaders. Original recordings and installed mods were
not modified; hashes of all three replay copies still match their originals.

Three real recordings were 3:25, 4:20 and 7:41 long, containing 81.7-184.2 MiB
of uncompressed packet data. This is not a long AA or worst-case chest sample.
The driver opened each recording twice and ran three additional completed index
scans per recording. Playback comparisons used the same Nether segment twice
per mode, in reversed order, with three seconds settling and twelve seconds
measurement each, followed by an End fight/death segment. All nine playback
windows completed. The actual inventory modal was not profiled.

### Indexing And Opening

| Recording length | Packet data | Median repeated index scan | Repeat open to world/index readiness |
| --- | --- | --- | --- |
| 7:41 | 184.2 MiB | 655 ms | 998 ms |
| 4:20 | 81.7 MiB | 518 ms | 521 ms |
| 3:25 | 117.1 MiB | 710 ms | 751 ms |

Individual repeated scans ranged from 457 to 818 ms. These run in a worker
while the paused game still renders, not an isolated CPU benchmark. Opening
readiness includes both index workers and a client world/camera; it is not a
measurement of all visible chunks being ready. The first opening in the Java
17 session took 4.03 seconds, including initialization. Filesystem and ReplayMod
caches were already warm, so these are not cold-disk measurements. The driver
reopens recordings; it does not exercise the linked-player switch UI itself.

Across nine repeated-scan windows, the milestone worker had 158 CPU samples;
66 were directly in ZIP inflation. Its sampled allocation weight was 527 MiB,
of which **154 MiB (29%)** came from `ChestOpening.key`. Packet-buffer filling,
Netty allocation and ReplayStudio's packet queue accounted for much of the
rest. Chunk-wide chest-list iteration did not register CPU samples in these
windows. There were only 1-6 opened-chest keys and 36-39 loot keys per recording.
Zero samples is not zero cost, and these recordings do not justify a large
chunk-index redesign yet.

### Seeking And Playback

- Rewinding to the same 90-second Nether position took **3.17-3.53 seconds**
  after the first 4.51-second seek. The forward End seek took 1.38 seconds.
- Lighting appeared in **1,836 / 3,637 CPU samples (50.5%)** across seek
  windows; another 229 were attributed to chunk-mesh work. Most sampled work
  was Minecraft/ReplayMod reconstruction, not viewer UI. This is the largest
  measured wait, but index caching will not remove it.
- Normal-follow p95 frame intervals were **14.05 / 11.35 ms**; detailed-follow
  **13.71 / 11.99 ms**; details plus analysis **12.48 / 12.39 ms**. These overlap
  and show warmup/scene noise, not evidence that analysis makes playback faster.
  All medians were close to the 8.33 ms frame cap. This is not an uncapped FPS
  or GPU benchmark.
- The Nether sample loaded 79-81 piglins, but most were not in the nearby
  trading cluster (the inspected screenshot showed cluster size 1). This does
  not measure the worst case of 128 nearby trails or a packed trading hole.
- Within sampled viewer playback stacks, vanilla item rendering invoked by
  `DetailedFollowHud.item` was the largest allocation source. Decoding the HUD
  snapshots was small, consistent with the existing frame-change cache. Do not
  cache rendered item images without preserving animated/glint behavior.
- Idle chest raycasting and timer formatting were minor sampled CPU sources.
  Samples do not establish zero overhead, and stack-depth limits can undercount
  deep viewer calls. No GPU timestamps or actual disk-throughput benchmark were
  collected; native graphics calls cannot distinguish driver work from waits.

### Revised Priority

1. **Bounded reuse of completed indexes:** best first viewer-only change.
   Avoids a measured 0.5-0.7 second median scan and its transient allocations
   on repeat opens. Actual open-time savings must be measured with a cache;
   parallel world reconstruction may hide part of the gain. Preserve file
   invalidation, chest history semantics and bounded retained memory.
2. **Chest lookup allocation:** better supported than chunk-list redesign.
   First test empty-history fast paths and world/dimension-scoped numeric
   position lookups. The 29% allocation share is an opportunity, not a promised
   CPU or total-allocation reduction. Preserve cross-world and double-chest
   invalidation, including equal timestamps.
3. **Timer formatter:** independently measured small, low-risk cleanup. Keep
   identical strings and full refresh precision; do not sell this as an FPS fix.
4. **Idle raycasts, chunk buckets and piglin clustering:** lower priority until
   more representative stress recordings show a meaningful cost.

The separate high-impact investigation is **full-detail seeking and lighting
reconstruction**. Its cost is real, but this profile does not establish which
work is redundant or safely removable. Quick Mode trades detail for speed and
is not a behavior-preserving solution to substitute silently.

### Reproduction And Artifacts

The opt-in `ViewerProfile` driver lives only in `src/replayPlaybackTest`.
`scripts/ViewerJfrSummary.java` reads JFR and phase logs using JDK 17+ and Gson.
Neither is bundled into the core or viewer JAR. Use a private directory with
exactly three replay copies, one containing the End segment at 180 seconds.
The supplied driver is deliberately a fixed workload, not a general benchmark
for arbitrary replay timings. Use a fresh output path for every invocation.

```powershell
.\gradlew.bat runReplayPlaybackTest --offline '-PreplayViewer=true' `
  '-PreplayPlaybackFile=<absolute path to one of the three replay copies>' `
  '-PviewerProfile=true' `
  '-PviewerProfileJava=C:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot/bin/java.exe' `
  '-PviewerProfileJfr=run/viewer-performance/baseline17.jfr' `
  '-PviewerProfileOutput=run/viewer-performance/baseline17.jsonl'
```

Private artifacts: `run/viewer-performance/baseline17.{jfr,jsonl}` and
`baseline17-summary.json`; screenshots in the development instance. The earlier
`baseline.*` run was a Java 25 pilot with different scene ordering. Its last
scene was mislabeled as End and is excluded from the conclusions above. Both
drivers completed, but only the corrected Java 17 run establishes this baseline.

## Measured Candidate

`ReplayTimings.clock` uses `String.format` twice per visible timer HUD frame.
A local isolated candidate builds the identical padded string directly.

Java 8u482, 256 MiB fixed heap, two separate JVM forks. Each fork warmed both
paths for 30,000 pairs, then alternated five measurements of 60,000 pairs.
Results below are per pair (RTA and IGT), medians within each fork:

| Fork | Current wall time | Candidate wall time | Current allocation | Candidate allocation |
| --- | --- | --- | --- | --- |
| 1 | 4,458 ns | 119 ns | 3,975 bytes | 256 bytes |
| 2 | 3,087 ns | 132 ns | 4,119 bytes | 304 bytes |

Both forks passed 5,018 string equivalence cases including negative/unknown,
padding boundaries, minutes over 99, integer max and long max values.
The candidate is only in ignored `run/viewer-performance/ViewerPerfProbe.java`.
It compiles alongside the actual production `ReplayTimings.java`, using Gson
2.8.0, and does not replace production code.

This is a microbenchmark, not an FPS benchmark. At 240 frames per second, the
formatter alone currently allocates approximately 0.95-0.99 MB/s; the candidate
allocates approximately 0.06-0.07 MB/s. CPU savings are only a few microseconds
per frame. Windows thread CPU timing is too coarse to rank the small candidate
reliably; use wall time and allocation here. JVM/JIT, frame context and machine
load affect these numbers.

## Original Pre-Profile Priorities

1. Reuse completed milestone/chest indexes when reopening the same unchanged
   recording or switching back to a player. `ViewerControls` creates a new
   `MilestoneIndex` on each attachment, which decompresses/scans all packets.
   This is repeated architectural work, not inherently necessary. A bounded
   session cache is the first design to evaluate; persistent caching needs
   versioned serialization, robust file identity and corruption handling.
   Cache immutable completed results only, preserve invalidation semantics,
   and avoid retaining open replay handles. Expected benefit is largest for
   repeated opens, not normal playback FPS or ReplayMod's world reconstruction.
   End-to-end impact has not yet been measured.
2. Reduce first-scan chest bookkeeping. `ChestHistory.chunk` copies the live
   values into a list for each relevant chunk packet; `ChestLootHistory.chunk`
   scans all live loot entries. Indexing these by world/dimension/chunk could
   avoid unrelated work. Empty-history block updates also build string keys.
   Preserve double-chest invalidation and equal-timestamp event order exactly.
   Measure a long chest-heavy replay before investing in this redesign.
3. Replace timer formatting with the measured equivalent helper. Low risk,
   small absolute gain. Cache labels/widths only when the underlying displayed
   value is unchanged; do not round timer values or lower their refresh rate.
4. Investigate the extra chest raycast in `ViewerControls.inspectChest`, called
   from ReplayMod's camera input handler before checking `keyUse.wasPressed`.
   A non-consuming pending-input check could avoid idle raycasts. Checking only
   whether the key is held would not preserve buffered taps; moving the
   consuming call early could steal non-chest inputs. Test those semantics.

## Already Efficient Or Lower Priority

- HUD track, timer and interval queries use binary search.
- Detailed inventory snapshots decode only when the selected frame changes.
- Trails sample at 100 ms intervals with bounded histories and entity counts;
  renderer layers are already cached. Do not reduce sampling, trail length,
  opacity, or vertex detail to claim a behavior-preserving optimization.
- Piglin counting is quadratic in nearby piglins, but only sampled at 10 Hz of
  replay time. Spatial buckets with the same exact distance predicate are an
  option if crowded-scene profiling justifies them, not an assumed bottleneck.
- Highlight selection is a set lookup and uses native outlines. Enabled glow
  has rendering cost; measure dense scenes before adding caches to this path.
- Quick Mode is an existing fidelity tradeoff, not a behavior-preserving fix.

## Remaining Verification

Profile a representative long recording during normal playback, detailed
follow, dense piglin trails/highlights, first open, repeated open and player
switching. Separate render-thread CPU/allocation, indexing CPU, disk work and
GPU/frame time. The small development replay is not representative of a long
AA recording. ReplayMod seeking/world reconstruction remains a separate cost;
neither formatter cleanup nor our metadata cache removes it.

Index reuse and first-scan chest-key allocation reduction are now implemented
and measured above. The small formatter change remains an independent
equivalence-tested candidate. None is an established overall FPS improvement.

## Supplemental Capture Audit (2026-10-22)

Scope: high-rate camera, Super Detailed presentation, original local sound
capture, and their viewer tracks. This pass adds opt-in benchmarks only; it
does not change production behavior, recording defaults, or audio settings.
All tests are headless and silent: no Minecraft client, sound engine or OpenAL
device is started. Sound events are processed as data with a silent consumer.

### Measured Viewer Priorities

Java 17.0.18, Windows, four JVM-visible processors, separate serial test runs.
Synthetic 10- and 30-minute archives contain 240 Hz camera samples, 60 Hz
presentation samples with tagged held items, and four local sound events per
second. Peak fixture/index disk bounds are about 20 and 58 MiB respectively.
These are warm-filesystem data-path tests, not live gameplay/FPS tests.

| Optional-track open/index | 10-minute fixture | 30-minute fixture |
| --- | ---: | ---: |
| Camera | 131 ms | 211 ms |
| Presentation | 113 ms | 200 ms |
| Local sounds | 843 ms | 2,532 ms |

Each open figure is one observation, including first-use effects, not a
distribution or a cold-disk guarantee. The sound track contains only 2,399
or 7,199 events. Its unbuffered primitive writes in
`LocalSoundTrack.read` dominate these supplemental load observations.
`RecordingIndex.readSupplemental` loads sounds before presentation, so this
also delays presentation availability. Evaluate buffered index construction
and bounded page/record reads first, preserving all validation and cleanup.

`LocalSoundCursor.frame` calls the disk-backed `after(now)` on every paused
frame, even when time and identity are unchanged. Median batch means were
126 and 184 microseconds per paused call, versus 4.2 and 4.4 microseconds
during sequential playback. This path is reached by `ViewerControls.update`
while paused. At 240 rendered frames/s, those paused means correspond to
30-44 ms of wall work per second, not a claimed FPS gain or CPU percentage.
Avoid repeated unchanged lookups, while retaining exact seek/pause/resume,
blocked-state, dimension/identity, failure, and no-catch-up-audio semantics.

Presentation lookup plus cached decode/interpolation took about 25-27
microseconds per sequential query and allocated about 519 bytes/query in
these fixtures. Random presentation lookups were about 205-223 microseconds;
this excludes world/packet reconstruction and is not total seek latency.
`PresentationTrack.load` reads headers through primitive disk calls and
allocates payloads; evaluate bounded bulk reads before redesigning item caches.
Camera lookups allocated zero bytes in sequential, stationary and random tests.

### Recorder Findings

`ReplaySupplementalCaptureBenchmark` uses the actual camera sampler, private
presentation encoder, sound encoder and real buffered disk spools. Presentation
state is supplied through method handles: live client observation, transformed
Mixin hooks and rendering are not included. Measured producer work also
includes fixture state setters and, in the changing case, item mutation.

| Producer workload | Approximate allocation/input | Raw sidecar size/hour |
| --- | ---: | ---: |
| Camera, 60/120/240 Hz at synthetic 480 Hz render timestamps | 0 B | 4.95 / 9.89 / 19.78 MiB |
| Presentation, ordinary held tools | 180 B | 28.63 MiB |
| Presentation, stable 1 KiB item NBT | 1,220 B | 242.87 MiB |
| Presentation, changing 1 KiB item NBT | 4,116 B | 242.87 MiB |
| Local sound event | 344 B | 1.03 MiB at 4 events/s |

Sizes are extrapolations of measured uncompressed fixtures, not final MCPR
sizes or heap retention. These spools are disk-backed and their queues are
bounded. Full presentation states duplicate unchanged item bytes at up to
60 Hz; large NBT therefore matters more for temporary disk use than ordinary
tools. Payloads are encoded/copied before queue rejection is known. Consider
bounded owned-buffer reuse only after live allocation/pressure profiling.
Sound encoding creates temporary buffers/copies inside its queue monitor,
but its event frequency is much lower; it is below the measured viewer work
in optimization priority. All accepted benchmark streams validated complete
with no dropped camera/presentation frames or rejected sound events.

The benchmark submits batches of 32 and waits for dequeue outside producer
timing. This deliberately excludes sustained pressure and is not actual
480-FPS pacing. An empty queue may still have an in-flight write; pre-finish
writer counters omit that last work and flush. Short Windows CPU samples are
coarse. Sidecar-only ZIP timings are not complete recorder stop/save timings.
The initial sub-millisecond park-based harness timed out on Windows and is
excluded; the corrected harness uses untimed yields and passes.

### Remaining Checks

- Measure transformed mining-hook allocation with capture disabled: `ModifyArgs`
  packages arguments before the capture API null guard. Headless disabled-API
  results do not cover this injection, the camera entrypoint, or all sound hooks.
- Profile real render-state reads, 60/120/240 Hz capture under chunk-load bursts,
  capture-health HUD on/off, and concurrent packet compression. No queue/writer
  redesign or sampling reduction is justified by these microbenchmarks alone.
- Measure multi-hour stop-to-save latency and peak temporary disk usage. The
  new tracks can each join for five seconds, followed by archive copying,
  whereas client shutdown still waits two seconds for the recorder worker.
  This is a code-inspected lifecycle risk, not a reproduced failure in this pass.
- Validate candidate optimizations with exact payload/event equivalence and
  gaps, corrupt input, seeks, pauses, identity changes and resource cleanup.

### Reproduction

Core opt-in: `ZSG_SUPPLEMENTAL_CAPTURE_BENCHMARK=1`, test class
`zsgrooms.modid.replay.ReplaySupplementalCaptureBenchmark`.
Viewer opt-in: `ZSG_SUPPLEMENTAL_TRACK_BENCHMARK=1`, optional
`ZSG_SUPPLEMENTAL_TRACK_MINUTES=1..30` (default 10), test class
`zsgrooms.replayviewer.SupplementalTrackBenchmark`. Use `--rerun-tasks` for
repeat measurements; the environment flags are not Gradle task inputs.
Tests otherwise skip these benchmarks. Use the existing Java 17 test init
script and run recorder/viewer benchmarks serially.

Private XML evidence is in `run/replay-performance-2026-10-22/`. Viewer
sequential batches span about 17 seconds regardless of archive length;
longer fixtures test index size, not an uninterrupted long playback session.
The initial stationary presentation probe used fractional timestamps; it is
not used as exact paused-viewer latency. The finalized harness uses integer
paused timestamps and clears decoded states for random-seek queries.

The final 30-minute repeat, run with the full viewer test suite, measured
2.151 s for sound indexing, 181 ms for camera and 176 ms for presentation.
Its paused sound cursor averaged 157 microseconds/query (median batch mean),
versus 3.0 microseconds while advancing. Sequential presentation was 23
microseconds/query and random presentation lookup/decode was 157 microseconds.
These warmed-run differences reinforce the priorities, not an optimization
claim: no production code changed. A separate recorder repeat reproduced
the allocation and raw-size table above. Final validation: 642 core tests
and 282 viewer tests passed; 12 other opt-in tests were skipped, with no
failures. Writer tests were up-to-date rather than rerun in this audit.
