# Recorder finalization optimization, 2026-09-28

## Changes

The September 23 JFR files were re-analyzed using `scripts/ReplayJfrSummary.java`.
The normal-mode recording contains a 254.01 ms client-thread wait entering
`ReplayRaceManifest.closeInterval` during finalization. This is one observation,
not a typical stall duration or an FPS measurement.

`ReplayRaceManifest.finish` now closes active intervals and seals capture under
the capture monitor, then trims and serializes the data after releasing that
monitor. All capture methods already reject sealed state. A separate finish lock
keeps repeated/concurrent finalizations ordered and preserves their existing
duration-clamping and completion-flag behavior. No manifest snapshot is copied.

`ReplayHudTrack.finish` uses the same locking approach. It counts the frames in
the written duration and encodes directly into one exactly sized byte array via
`ByteBuffer`, preserving the existing big-endian binary format. This removes the
full-sized temporary stream buffer and final copy, including allocation for
frames beyond the saved packet prefix. Repeated finishes still return an empty
HUD track after the first successful finish.

The changes apply to both Normal and Performance recording. Packet capture,
timestamps, order, HUD sampling/heartbeats, limits, seed-release policy and file
formats are unchanged.

## Measurement

The opt-in `ReplayRecorderBenchmark.compareHudFinalizationCostsAndBytes` compares
the previous stream encoding with production finalization. Capture/fixture setup
is outside the timed region. Four warmup and nine measured paired rounds alternate
execution order and compare output bytes every round. These are synthetic writer
costs on this machine using JDK 25.0.2, not game frame-time improvements.

| HUD fixture | Output bytes | Allocated bytes before / after | Median wall time before / after |
| --- | ---: | ---: | ---: |
| 12,000 frames, 64-byte payload | 960,008 | 1,920,152 / 960,112 | 1.896 / 0.571 ms |
| 4,000 frames, 4,096-byte payload | 16,448,008 | 32,896,152 / 16,448,080 | 5.381 / 3.014 ms |
| Same large track, half the frames saved | 8,224,008 | 24,672,152 / 8,224,144 | 3.371 / 1.658 ms |
| Same large track, no frames in saved prefix | 8 | 16,448,152 / 80 | 0.781 / 0.010 ms |

Full large-track times ranged from 4.776 to 12.342 ms before and 2.652 to
6.729 ms after. Thread CPU time is too coarse for these single-finish operations
on this Windows runtime, so the benchmark's CPU columns are not used here.
The byte-allocation reduction is the strongest quantitative result. It does not
imply that the recorder's steady-state memory footprint has halved.

## Direct CPU follow-up

Measured with `scripts/Benchmark-RecorderCpu.ps1` and
`scripts/ReplayHudCpuBenchmark.java` on the local Intel Core i7-8750H, JDK 25.0.2,
with a fixed 512 MiB JVM heap. The harness compiles the actual original HUD source
(Git blob `1eb8ea77e89555a834252571c963f150879f54e9`) and the current source into
separate directories, then runs fresh JVMs in before/after/after/before order.
There are three warmup and seven measured batches per fixture per JVM.

The table reports mean **benchmark-thread CPU time per HUD finalization**, averaged
across both JVM runs of each version. This isolates the writer-side encoding work;
it does not include ZIP compression, disk I/O, metadata JSON or GC worker CPU.

| Saved HUD track | CPU before | CPU after | Reduction |
| --- | ---: | ---: | ---: |
| Actual smoke recording: 141 frames, 27,111 bytes | 9.78 microseconds | 4.60 microseconds | 53% |
| 12,000 small frames, 960,008 bytes | 0.490 ms | 0.283 ms | 42% |
| 4,000 large frames, 16,448,008 bytes | 7.07 ms | 3.52 ms | 50% |

Each batch uses identical operation counts in both versions: 65,536, 1,024 and
256 respectively. All 84 measured batches accumulated at least 250 ms of thread
CPU time, well above this Windows counter's observed 15.625 ms increments. The
first exploratory run used adaptive batch sizes; those results are excluded
because changing fixture memory between JVMs complicated the comparison.

Fixture creation, reflection, output comparison and logging are outside the timed
interval. Prebuilt tracks share immutable frame/payload objects to bound fixture
memory; each owns its frame list and executes the unmodified production `finish`
method once. This is a warmed batch benchmark, not the timing of one cold game
save. Fixture hashes matched across all four JVMs and each implementation's output
matched the expected stream bytes. JVM process CPU is logged diagnostically, but
includes background GC/JIT work, potentially carried over from fixture setup, and
is not used for the improvement percentages.

Run-to-run mean CPU ranges were 8.86-10.69 versus 4.56-4.63 microseconds for the
actual track, 0.447-0.534 versus 0.275-0.292 ms for the small-frame fixture, and
5.98-8.16 versus 3.32-3.71 ms for the large-frame fixture. These ranges describe
two local JVM runs, not confidence intervals or predictions for every machine.

The absolute saving for the actual short recording is about **5 microseconds at
save time**; for the near-limit synthetic track it is about **3.55 ms**. This pass
does not change per-packet capture during gameplay, and no sustained Minecraft CPU
or FPS improvement has been established. The manifest lock refactor reduces a
source of client waiting, while retaining the same JSON serialization algorithm;
the historical 254 ms monitor wait must not be counted as 254 ms of CPU work saved.

Raw results: `run/recorder-cpu-fixed/{1-before,2-after,3-after,4-before}.log`.
Aggregated local data: `run/recorder-cpu-fixed/summary.json` and `comparison.json`.

## Verification

- Full root test suite and build passed: 489 passed, two opt-in benchmark methods
  skipped. The new HUD finalization benchmark passed when explicitly enabled.
- Four new regression tests cover nonblocking late capture during paused
  serialization, concurrent/repeated finishes, duration boundaries, empty frames,
  world/entity identities and the HUD byte limit.
- A separate local parity harness compiled the original manifest from Git HEAD
  alongside the modified implementation. All 24 JSON strings matched exactly,
  including complete/incomplete output, prediction withheld/released, capped data
  and repeated finishes with changing durations.
- Normal-mode live smoke passed with one same-seed reset and return-to-room save:
  9,218 packets, 13,133,491 payload bytes, `complete=true`. The replay inspector
  passed packet ordering, world/reset and required player-state checks. A separate
  sidecar check passed JSON/HUD format and duration bounds: six coverage intervals,
  141 HUD frames, 27,111 HUD bytes, and a 63,678 ms recording duration.
- The fresh JFR recorded no monitor waits on `ReplayRaceManifest` or
  `ReplayHudTrack`. Monitor events are thresholded; this is not evidence that all
  contention is zero or a controlled FPS comparison. A 65.46 ms main-thread wait
  on the Mixin transformer occurred during first player capture, which is a
  separate class-loading investigation.
- The buffer drained to zero. Its peaks were 2,260,300 accounted bytes / 241
  packets, 2,259,522 pending-client bytes and 2,038,316 queued-writer bytes. These
  are independent lifetime maxima from a low-render-distance smoke test, not
  justification to shrink the existing limits. Visual playback was not rerun.

Local logs: `run/recorder-finalization-check.log`, `run/recorder-full-check.log`,
`run/recorder-parity/result.log`, and `run/recorder-jfr-review-2026-09-28.log`.
The parity harness and generated classes stay under ignored `run/recorder-parity`.
Live logs: `run/recorder-live-finalization-check.log`,
`run/recorder-inspection-check.log`, `run/recorder-sidecar-check.log` and
`run/recorder-finalization-jfr-after.log`. The recording and JFR are under
`run/replay-prototype/`: `replay_recordings/265bf5f4-4ed8-443c-b96e-e7b159032c86.mcpr`
and `recorder-finalization-after-2026-09-28.jfr`.

## Further work

1. Profile repeated Overworld/Nether/End transitions with the usual mod pack and
   render distance. Existing buffer high-water diagnostics distinguish pending
   client callbacks from queued writer work. Use those alongside JFR to decide
   whether the next improvement belongs in connection-lock scope, packet
   allocation, compression or disk I/O. The historical 12–21 ms buffer/connection
   waits warrant investigation; they do not identify a proven universal fix.
2. Measure retained HUD data during long recordings. Equal payloads already share
   arrays at heartbeat frames, and the track is bounded at 16 MiB of serialized
   data. Any further packing/compression must preserve frame timestamps, payloads,
   failure behavior and the existing admission limits. Finalization improvements
   alone do not reduce the stored frames during capture.
3. Revisit inventory caching only with representative changing inventories. The
   previous measurements show wins for stable NBT but regressions when every slot
   changes. It remains Performance-only. Compare-before-copy HUD experiments were
   also slower despite saving allocations; do not promote them solely for lower
   allocation counts.

Packet scratch reuse is already enabled in both modes. Library verification is
already asynchronous and cached per loaded selection; its startup samples should
not be mistaken for a recurring capture cost. Buffer budget increases and packet
dropping are not behavior-preserving optimizations.

## Reproduce

```powershell
.\gradlew.bat replayRecorderBenchmark --tests 'zsgrooms.modid.replay.ReplayRecorderBenchmark.compareHudFinalizationCostsAndBytes' --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx768m'
.\gradlew.bat test build --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx768m'
.\gradlew.bat runReplayPrototype -PreplaySmoke=true -PreplayResets=1 -PreplayPerformance=false -PreplayEnd=room '-PreplayRecorderJfr=run/replay-prototype/recorder-finalization-after-2026-09-28.jfr' --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx768m'
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Benchmark-RecorderCpu.ps1 -JdkDirectory 'C:/Program Files/Microsoft/jdk-25.0.2.10-hotspot' -Recording 'run/replay-prototype/replay_recordings/265bf5f4-4ed8-443c-b96e-e7b159032c86.mcpr' -OutputDirectory run/recorder-cpu-fixed
```

Use a fresh JFR filename on subsequent runs. Do not run the microbenchmark and live
game profile simultaneously.
