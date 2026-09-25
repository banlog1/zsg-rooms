# Replay Buffer Burst Headroom

## Evidence

The reported recording stopped with `buffer capacity reached` during a second
Nether entry, before the End fight. The old log did not identify which limit
was reached or whether the backlog was pending client tasks or queued writes.
The historical cause below that limit failure is therefore still unproven.

## Change

- Raise the accounting budget from 32 to 128 MiB and the packet limit from
  8,192 to 65,536. Allocate payloads on demand, without reserving the whole budget.
- Retain both limits so a stalled writer or client cannot grow the recorder
  indefinitely. The 64-byte per-record allowance is not an exact JVM heap bound;
  queued callbacks, packet objects, collection capacity and encoding scratch also
  consume memory. This change does not promise zero impact on GC under a burst.
- Track peak retained bytes/packets, peak pending-client bytes/packets, peak
  queued-writer bytes/packets and largest payload using primitive counters inside
  existing buffer synchronization. No added per-packet timers, logging, maps or
  diagnostic objects on successful capture.
- At a rejected reservation, freeze the first failure's exact limit, requested
  payload size, protocol/packet ID and pending/queued/active counts and bytes.
  Log its packet class and recording ID separately from the short UI status.
- Log a snapshot on world application and in the writer's final cleanup path,
  including successful saves and discarded/failed recordings. These diagnostics
  do not depend on Seed Debug Logging. Peak fields are independent lifetime
  maxima, not a claim that all those peaks happened simultaneously.

Packet content, submission order, timestamps, capture modes, compression, file
format and writer thread behavior are unchanged. There is no deliberate packet
dropping or blocking backpressure. Exceeding the new limits still stops recording
and produces an incomplete replay.

## Synthetic Checks

`ReplayBufferTest` compares the old and new budgets with two deliberately held
backlogs, twice each, checking that every accepted packet drains in submission
order with unchanged payload and timestamp, and accounting returns to zero:

| Workload | Old budget accepts | New budget accepts | New peak accounted bytes |
| --- | ---: | ---: | ---: |
| 20,000 packets, 128-byte payloads, held on client side | 8,192 | 20,000 | 3,840,000 |
| 768 packets, 64-KiB payloads, held on writer side | 511 | 768 | 50,380,800 |

These are synthetic buffer tests, not actual Minecraft chunk sizes, timings,
throughput measurements or proof that the reported transition is fixed.
Additional tests check rejection snapshots after draining, both limits,
oversized packets, immutable snapshots, reset cancellation, close and discard.

## Next Gameplay Measurement

Repeat Overworld/Nether/Overworld/Nether/End travel using the affected mod set,
render distance and recording mode. Keep the new session's log through saving.
Compare `peakAccountedBytes` and `peakPackets` with their respective budgets.
High `peakPendingClient*` points to delayed client submission; high
`peakQueuedWriter*` points to queued writing. These indicate where backlog builds,
not whether disk, compression, GC or renderer work is ultimately responsible.
For that distinction, use the existing recorder JFR procedure.

Choose further headroom from repeated high-water marks across representative
machines and render distances, including low-heap instances. Do not infer a
universal safe bound from this one failure, or keep raising it if the backlog
grows steadily rather than draining after a transition.
