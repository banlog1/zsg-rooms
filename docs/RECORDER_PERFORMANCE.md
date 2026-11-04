# Recorder Performance

This guide consolidates the recorder's profiling decisions, correctness
constraints and repeatable checks. Measurements below are historical, focused
experiments, not end-to-end FPS promises or fresh verification of every release.
Keep recordings, JFR files and per-session results under ignored `run/`.

See [viewer performance](VIEWER_PERFORMANCE_REVIEW.md) for playback, seeking and
supplemental-track indexing, and [development](DEVELOPMENT.md) for the build setup.

## Invariants

A behavior-preserving optimization must retain packet bytes, submission order,
timestamps, reset/dimension identity, capture sampling, saved duration and failure
semantics. Keep queue payloads independently owned, including during nested
capture. Test cancellation and repeated/concurrent finalization, not only the
successful writer path.

Do not infer a performance win from allocation counts alone. Measure client and
writer CPU, lock waits, backlog, disk/compression work and retained memory
separately. Startup library verification and registry loading are not recurring
per-packet costs. JFR sampling and thresholded events cannot prove zero cost.

## Retained Changes

### Packet Encoding

Both Normal and Performance recording reuse bounded per-thread packet scratch:
256 KiB retained capacity, with the existing 4 MiB packet limit. Oversized buffers
are released. Each admitted queue record still owns its payload; scratch reuse
does not pool queued bytes or change reservation accounting.

Paired small/medium packet experiments reduced allocation from 456 to 120 bytes
per 96-byte packet, and from 196,984 to 65,560 bytes per 64 KiB packet. The 1 MiB
case exceeded the retention cap and had no allocation reduction. These isolate
encoding and the owned copy, not the complete capture/writer pipeline.

Tests cover mixed sizes, nested calls, thread isolation, retention, oversized
writes and queued-byte ownership. Performance-only inventory caching and metadata
suppression are separate decisions, not consequences of packet scratch reuse.

### Finalization

`ReplayRaceManifest.finish` seals capture and closes intervals under the capture
monitor, then trims and serializes after releasing it. A separate finish lock
orders repeated/concurrent calls and preserves duration-clamping and completion
flags. This avoids holding the capture monitor through JSON serialization.

`ReplayHudTrack.finish` follows the same locking approach and encodes the saved
prefix directly into an exactly sized byte array. Big-endian bytes and duration
bounds are unchanged; repeated successful finishes still return an empty track.
Frames beyond the saved packet prefix do not need a full temporary encoding.

| Synthetic HUD fixture | Output bytes | Allocated bytes before / after |
| --- | ---: | ---: |
| 12,000 frames, 64-byte payload | 960,008 | 1,920,152 / 960,112 |
| 4,000 frames, 4,096-byte payload | 16,448,008 | 32,896,152 / 16,448,080 |
| Same large track, half saved | 8,224,008 | 24,672,152 / 8,224,144 |

Paired measurements compared output bytes every round. Separate fixed-operation
CPU batches observed roughly 42-53% less HUD-finalization CPU on the tested
fixtures, excluding ZIP compression, disk I/O and GC worker time. The absolute
saving for a short recording was only about five microseconds at save time.
This does not halve retained recording memory or establish an FPS improvement.

A historical 254 ms client monitor wait motivated the lock change. It was one
save-boundary observation, not 254 ms of CPU work or a typical stall duration.
Regression coverage includes late capture during serialization, concurrent finish,
duration boundaries, empty frames, world/entity identity and byte limits.

### Detailed Capture Encoding

Detailed Capture uses nested-safe, bounded 8 KiB encoding scratch while returning
independently owned payloads. Paired fixtures reduced allocation from 2,048 to 944
bytes for 46 empty slots, and from 20,632 to 7,208 bytes for a 54-slot NBT fixture.
Oversized payload allocation was unchanged and timing results were mixed.
Preserve slots, cursor-held items, timing and file format when revisiting this.

## Buffer Headroom

The buffer allows 128 MiB of accounted data and 65,536 records. Payloads allocate
on demand; the full budget is not reserved upfront. Both limits remain necessary
when a client or writer stalls. The 64-byte per-record allowance is an estimate,
not an exact heap bound: callbacks, packet objects, collections and scratch add
memory beyond it.

Exceeding a limit still stops recording and produces an incomplete replay.
There is no deliberate packet dropping or blocking backpressure. Raising limits
changes failure behavior and memory exposure; it is not a free optimization.

Primitive diagnostics distinguish retained, pending-client and queued-writer
bytes/packets, record the largest payload and freeze the first rejection's cause.
Lifetime peaks are independent maxima, not simultaneous measurements.
High pending-client peaks locate delayed submission; high queued-writer peaks
locate writer backlog. Neither alone identifies disk, compression, GC or renderer
work as the cause.

Synthetic tests hold 20,000 small records on the client side and 768 64 KiB
records on the writer side. They verify accepted order, timestamps, payloads and
zero accounting after drain, plus rejection snapshots, reset cancellation,
oversized records, close and discard. They do not prove all live transition
bursts fit. Measure repeated Overworld/Nether/End transitions at representative
render distances and low heap before changing either limit.

## Rejected Or Unproven Candidates

- HUD compare-before-copy reduced small allocations but increased execution time,
  including for unchanged frames. Do not promote it solely for allocation savings.
- Inventory caching benefits stable NBT and a few changing slots, but deep-copy
  and re-encoding costs regressed when every slot changed. Retain its
  Performance-only scope unless representative tests justify a broader change.
- Writer batching, queue rewrites and pooled payload ownership are not established
  wins. Profile queue latency, cancellation, capacity and compression first.
- Long-lived HUD frames and high-rate supplemental tracks need separate retained
  memory and finalization measurements. Faster finalization does not reduce
  capture-time retention.

## Reproduce

Run from the repository root with the existing Gradle/JDK setup:

```powershell
.\gradlew.bat replayRecorderBenchmark --offline --no-daemon --max-workers=1
.\gradlew.bat test --tests '*ReplayBufferTest' --tests '*ReplayHudTrackTest' --tests '*ReplayRaceManifestTest' --tests '*ReplayFinalizationTest' --offline
```

The dedicated benchmark task opts in to `ReplayRecorderBenchmark`; ordinary
tests skip it. It checks byte equivalence as well as timing/allocation. For
before/after finalization CPU batches, use
[`Benchmark-RecorderCpu.ps1`](../scripts/Benchmark-RecorderCpu.ps1) with a local
JDK and recording path. The harness builds the historical and current HUD
implementations separately and alternates fresh JVMs.

[`ReplayJfrSummary.java`](../scripts/ReplayJfrSummary.java) analyzes saved JFR
files using a JDK 17+ source launcher. For live capture, see the
[replay prototype harness](../replay-prototype/README.md). Use separate fresh JFR
paths and do not run microbenchmarks concurrently with Minecraft profiling.
Keep audio muted during unattended tests.

For high-rate camera, presentation and local-sound measurements, opt in to
`ReplaySupplementalCaptureBenchmark` with
`ZSG_SUPPLEMENTAL_CAPTURE_BENCHMARK=1`. Detailed encoding comparisons use
`ReplayDetailedCaptureBenchmark` with
`ZSGROOMS_DETAILED_CAPTURE_BENCHMARK=true`. Pass `--rerun-tasks` when changing
benchmark environment variables; they are not Gradle task inputs.
