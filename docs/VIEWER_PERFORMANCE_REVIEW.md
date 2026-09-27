# Viewer Performance Review

Date: 2026-09-28. Investigation only; no production viewer changes in this pass.

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

## Priorities

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

## Next Verification

Profile a representative long recording during normal playback, detailed
follow, dense piglin trails/highlights, first open, repeated open and player
switching. Separate render-thread CPU/allocation, indexing CPU, disk work and
GPU/frame time. The small development replay is not representative of a long
AA recording. ReplayMod seeking/world reconstruction remains a separate cost;
neither formatter cleanup nor our metadata cache removes it.

Recommendation: measure index reuse's end-to-end opportunity next; implement
the small formatter change as an independent equivalence-tested cleanup.
Do not promise an overall speedup from this microbenchmark.
