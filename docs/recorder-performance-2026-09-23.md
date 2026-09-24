# Recorder profiling and candidate experiments

## Follow-up: packet scratch reuse (2026-09-24)

Promoted only bounded packet encoding-buffer reuse to normal recording. Both modes
now use the existing per-thread scratch buffer with a 256 KiB retention cap and
the same 4 MiB packet limit. Larger buffers are released. Queue payloads still own
their bytes; reservation, ordering, timestamps and reset handling are unchanged.
This retains some scratch memory between packets in exchange for less allocation.

Inventory caching, HUD/metadata scratch reuse and unchanged-metadata suppression
remain Performance-mode-only. No writer batching or HUD compare-before-copy change
was made. The measurements and original decision below describe the pre-change
baseline; they are not a claim of an end-to-end FPS improvement.

Follow-up verification: core build and 451 unit tests passed (the opt-in benchmark
is skipped by the ordinary test task). The separately run paired benchmark passed
its packet/HUD/inventory output-equivalence checks. Packet scratch results repeated:
96-byte packets used 456 -> 120 allocated bytes/op and 192 -> 106 ns/op; 64 KiB
packets used 196,984 -> 65,560 bytes/op and 30,595 -> 12,161 ns/op. The 1 MiB case
still allocated equally because it exceeds the retention cap. New ordinary unit
tests cover mixed packet sizes, queued-byte ownership and recovery after an
oversized write; existing tests cover nesting, thread isolation and retention.

The normal-mode live recording with one same-seed reset finalized successfully
(8,763 packets, 12,054,584 payload bytes, complete=true) on return to the room.
The smoke harness nevertheless failed its four-minute deadline after long Minecraft
world saves, before its final success assertion. Do not count that harness as passed.
Independent `inspectRecording` verification of the saved file passed timestamp
ordering, dimension/reset sequence, local-player spawn/movement and required packet
content checks. Visual playback was not rerun for this change. Fixture:
`run/replay-prototype/replay_recordings/e885cc62-1bb8-447d-9f54-406707ff6d9b.mcpr`.

## Decision

No production recorder behavior or defaults were changed. The new code is an opt-in
test benchmark and a JFR analysis tool. Do not promote an optimization based only on
lower allocation counts.

1. **Recommend bounded encoding-buffer reuse for normal mode as the next candidate.**
   It is already used in Performance mode. It consistently reduced small/medium
   packet encoding/copy time and allocation in these experiments. Keep the existing
   retention limits, independent queued payload ownership, and nested-capture safety.
2. **Keep item caching in Performance mode for now.** Stable inventories and a single
   changing slot benefit, but every-slot-changing NBT is substantially worse. Moving
   it into normal mode unconditionally is not an across-the-board improvement.
3. **Reject the tested HUD compare-before-copy implementations.** Both preserve the
   track but exchange small allocations for more execution time. The bulk version
   also allocates an extra wrapper on changed frames. At roughly five samples/sec,
   the absolute potential saving is small.
4. **Do not rewrite the writer queue or pool queued payloads yet.** The live profiles
   do not establish a dominant per-packet writer bottleneck. Batching and ownership
   changes need separate backlog, latency, capacity and cancellation measurements.
5. **Investigate finalization lock scope next.** One profile captured a 254 ms main
   thread wait on the manifest while returning to the room. This is a save-boundary
   issue, not evidence of a continuous per-tick slowdown.

## Environment and method

- Windows, Intel Core i7-8750H, Microsoft OpenJDK 25.0.2, Minecraft 1.16.1.
- Two unchanged-recorder JFR runs: normal and current Performance mode. Same smoke
  scenario, view distance 4, 60 FPS cap, one same-seed reset, dimension transitions,
  inventory changes and return-to-room finalization. Heap: 512 MiB initial / 1536 MiB max.
- Neither live run contains synthetic microbenchmarks. No competing benchmark JVM
  was run during either live profile.
- Separate 512 MiB test JVMs measured paired baseline/candidate workloads, alternating
  order each round, with four warmup and nine measured rounds. Repeated in fresh JVMs.
- Time is median wall-clock nanoseconds per operation. Allocations use HotSpot's
  per-thread allocated-byte counter. Thread CPU measurements are also printed, but
  Windows CPU-accounting granularity makes short batches noisy, sometimes zero.
- These are focused experiments, not JMH confidence intervals or an FPS benchmark.
  Fixed-input equivalence is separate from live recordings, whose timestamps and
  packet counts naturally differ. No byte-identical ZIP claim is made.

## Final paired measurements

These are medians from `recorder-benchmark-final.xml`. Earlier runs agreed on the
important tradeoffs, although absolute timings varied with JVM warmup and scheduling.

| Workload | Baseline ns/op | Candidate ns/op | Baseline allocated B/op | Candidate allocated B/op |
| --- | ---: | ---: | ---: | ---: |
| 96-byte packet, reusable scratch | 221 | 153 | 456 | 120 |
| 64 KiB packet, reusable scratch | 20,638 | 7,443 | 196,984 | 65,560 |
| 1 MiB packet, reusable scratch | 456,903 | 412,643 | 3,146,104 | 3,146,104 |
| 256-byte unchanged HUD, byte comparison | 311 | 769 | 281 | 9 |
| 4 KiB unchanged HUD, byte comparison | 500 | 7,614 | 4,121 | 11 |
| 256-byte unchanged HUD, bulk comparison | 249 | 464 | 281 | 9 |
| 4 KiB unchanged HUD, bulk comparison | 417 | 2,488 | 4,121 | 11 |
| 4 KiB changing HUD, bulk comparison | 374 | 394 | 4,160 | 4,208 |
| Typical stable inventory, item cache | 2,804 | 1,455 | 384 | 1 |
| 42 NBT-heavy stable items, item cache | 69,880 | 19,482 | 9,665 | 40 |
| Same inventory, one changing slot | 59,715 | 18,973 | 9,672 | 1,919 |
| Same inventory, every slot changing | 75,554 | 172,922 | 9,993 | 79,209 |

The packet experiment isolates scratch encoding and the owned payload copy, not the
entire capture/queue/writer pipeline. The 1 MiB scratch buffer exceeds the existing
256 KiB retention limit, so both paths allocate equally; its timing difference is
not evidence of a pooling win. Smaller-packet allocation reductions repeated exactly.

The HUD experiment starts with already encoded data and fresh tracks each round,
keeps measured workloads below saturation, and excludes final serialization from the
timed section. Both existing rate limits and one-second heartbeats are retained.
For unchanged 4 KiB data, approximately 20 KiB/sec of temporary allocation is at
stake at five samples/sec, not megabytes per frame. Rejecting the slower comparison
does not leave a large measured bottleneck unaddressed.

The every-slot-changing inventory is an artificial stress case, not a claim about
ordinary speedrunning. It exposes the deep-copy and re-encoding cost of cache misses.

## Memory and correctness costs

- Scratch reuse retains capacity rather than eliminating it: at most 256 KiB for
  packet scratch per participating thread, 8 KiB for HUD scratch, and 256 KiB for
  metadata scratch. These are separate caches. Oversized buffers are released.
  Queued payloads still allocate owned byte arrays; the queue's 32 MiB accounting
  limit is not a whole-process memory limit.
- Item caching retains up to 42 copied ItemStacks/NBT graphs plus up to 64 KiB of
  encoded bytes. The encoded-byte bound is not a bound on all object-graph memory.
- Both HUD prototypes keep the same stored-frame output and retention behavior as
  the baseline. The bulk prototype adds a wrapper for the last payload; it releases
  that wrapper on replacement/finalization. Neither prototype is used in production.
- Equivalence checks passed for packet bytes and scratch ownership; HUD snapshots,
  heartbeats, world/entity changes, nonzero reader offsets, size/count budgets and
  duration clamping; and inventory encoding in all four measured workloads.
- Core unit tests and build passed. The opt-in benchmark is skipped by normal tests.

## Live JFR observations

| Observation | Normal | Existing Performance |
| --- | ---: | ---: |
| Completed packet count | 8,685 | 8,063 |
| Written packet payload bytes | 13,529,438 | 13,526,875 |
| Whole-JVM CPU samples | 17,681 | 19,793 |
| Packet-capture CPU samples | 19 | 17 |
| Packet-capture allocation samples | 112 | 39 |
| Sampled packet-capture allocation weight, MiB | 45.91 | 15.62 |
| HUD capture CPU samples | 0 | 1 |
| Writer CPU samples | 21 | 31 |
| Whole-JVM largest GC pause, ms | 37.3 | 68.5 |
| Whole-JVM peak observed after-GC heap, MiB | 806.1 | 770.6 |

JFR allocation weights are estimates, not exact per-packet allocation totals.
The reduced sampled allocation is consistent with scratch reuse, but normal vs
Performance is not an isolated candidate comparison: metadata suppression and
variable world generation also differ. GC and heap include game startup, worldgen,
rendering and all mods. They do not establish that Performance causes more GC latency
or that recorder memory fell by the difference between those heap values.

Initialization (library verification, hashing and ReplayStudio registry loading)
accounts for much of the remaining recorder-attributed startup sampling. Do not
confuse that one-time work with steady packet capture. Zero samples in a small hot
path do not prove zero cost. File I/O and monitor events also have thresholds.

Observed monitor waits:

- Normal: 21.45 ms Netty local client thread and 13.77 ms main thread on connection
  state during capture/handoff.
- Normal: 254.01 ms main thread entering `ReplayRaceManifest.closeInterval` at
  return-to-room finalization. `finish` currently serializes JSON while holding the
  same monitor. Investigate sealing under lock and serialization outside it, after
  confirming all post-seal operations and repeated finish calls remain safe.
- Performance: 12.08 ms main thread entering `ReplayBuffer.isOpen`.

These are individual observations, not lock-wait percentiles. No reliable FPS delta,
frame-time percentile, recorder-only peak resident memory, or queue high-water mark
has been established. Those remain required before promoting threading/payload-pool
changes. A single finalization stall should be reproduced before assigning a typical
cost to it.

## Reproduce

From the repository, with its existing Gradle/JDK setup:

```powershell
.\gradlew.bat replayRecorderBenchmark --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx768m'
.\gradlew.bat runReplayPrototype -PreplaySmoke=true -PreplayResets=1 -PreplayPerformance=false -PreplayEnd=room '-PreplayRecorderJfr=run/replay-prototype/recorder-normal-before.jfr' --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx768m'
.\gradlew.bat runReplayPrototype -PreplaySmoke=true -PreplayResets=1 -PreplayPerformance=true -PreplayEnd=room '-PreplayRecorderJfr=run/replay-prototype/recorder-performance-before.jfr' --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx768m'
java scripts/ReplayJfrSummary.java run/replay-prototype/recorder-normal-before.jfr run/replay-prototype/recorder-performance-before.jfr
```

Use distinct JFR filenames to preserve prior profiles. The analyzer uses the JDK 17+
source launcher/JFR consumer API; it is not part of the Java 8-compatible mod.
The opt-in benchmarks and all candidate implementations are test sources only.

Local artifacts live under `run/replay-prototype/`: `recorder-*-before.jfr`, matching
`.log` files, and `recorder-benchmark-{first,bulk,items,final}.xml`. They stay out of
Git and release JARs. Gradle also writes the latest HTML/XML benchmark reports under
`build/reports/tests/replayRecorderBenchmark` and `build/test-results/replayRecorderBenchmark`.
