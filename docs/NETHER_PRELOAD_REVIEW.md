# Nether Preload Review

Reviewed against the cached, mapped Minecraft 1.16.1 sources on 2026-09-27.
The original review found the issues below. The ticket-radius and lifecycle
fixes have since been implemented and tested separately from the AA filter.
The follow-up real-tick performance experiment is documented in
[NETHER_PRELOAD_BENCHMARK.md](NETHER_PRELOAD_BENCHMARK.md). It found another
functional issue that the original synchronous integration fixture did not catch.

## Priority Findings

### Fixed, High: Ticket Levels Passed As Radii

`NetherPortalPreloadMixin.tickNetherPortal` calls `NetherPortalPreloader.tick`
once per server-player tick. During an enabled Overworld portal charge,
`advanceWarmup` adds a terrain ticket once and may add a full ticket once.
The old call sites passed `TERRAIN_TICKET_LEVEL = 34` and `FULL_TICKET_LEVEL = 33`
to `ServerChunkManager.addTicket`, whose integer argument is a radius.

The vanilla implementation delegates to `ChunkTicketManager.addTicket`, which
constructs a ticket at level `33 - radius`. The actual requested levels are
therefore -1 and 0, not 34 and 33. Level propagation clamps negative levels to
zero. This requests a large FULL/ticking region rather than one terrain-only
destination chunk; the theoretical FULL footprint at level zero is roughly
67 by 67 chunks before generation dependencies. That is a requested footprint,
not a claim that every chunk finishes before portal transfer or ticket removal.
The supposed FULL promotion is not a meaningful promotion from terrain-only
under these calls. This is confirmed structural overhead, not a small allocation
or a hypothetical micro-optimization.

Implemented: radius `-1` for level 34 / FEATURES and radius `0` for level 33 /
FULL, with matching removal arguments. Regression tests use Minecraft's actual
ticket manager to verify both stages, removal, and the old failure. Timing-guard
tests cover the slow-server cutoff. The target and promotion timing are unchanged.

### Fixed, Medium: Warmup State Had No Server-Stop Cleanup

The static `WARMUPS` map retains a `ServerWorld` per warmup. Normal portal exit
and completed Nether transfer cleared it, but there was no disconnect/server-stop
cleanup hook. A shutdown/reset during portal charge could leave the old world
reachable until that player's next warmup clears it. Ticket expiry does not
remove Java map entries. Frequency is once per interrupted warmup, not every
tick; actual retained memory needs a heap measurement.

Implemented: SERVER_STOPPING releases warmups belonging to that server; a
PlayerManager.remove injection clears the disconnecting player's warmup. Tickets
use player UUID ownership, so cancelling one warmup cannot remove another
player's ticket at the same destination.

## Verification

The focused preloader/shared-entry unit suite passed all 10 tests. The real
Minecraft headless `runNetherEntryTest` also passed, checking:

- FEATURES level 34 and guarded FULL level 33 promotion.
- Independent ticket ownership at the same destination.
- Cancellation, player removal, server-scoped cleanup, restart and disable guards.
- Ticket/map cleanup after portal transfer.
- Identical portal frames across entry coordinates/orientation and same-seed resets.
- Vanilla return/later entries, non-portal travel and disabled shared-entry fallback.

The headless lifecycle test completes the terrain stage synchronously to make
its assertions deterministic; production polling remains nonblocking. This test
does not measure frame time, tick percentiles or portal-transfer stalls. Those
still need the controlled gameplay comparison below.

## Remaining Investigation

### Fixed: Terrain Tickets Did Not Schedule Terrain Generation

At level 34, `ChunkHolder.tick` does not start the FEATURES future merely because
the ticket exists. The preloader only polled existing futures. On a fresh
destination, the terrain was never requested and could never trigger FULL
promotion. The real-tick benchmark reproduced this in all four baseline trials.

The preloader now invokes the nonblocking private `ServerChunkManager.getChunkFuture`
with FEATURES and `create=false`, once its own ticket has propagated. An unloaded
holder is retried on a later player tick. Once accepted, the request is not repeated.
This avoids `getChunkFutureSyncOnMainThread`, which pumps tasks and waits when
called on the server thread, and avoids vanilla's extra ticket from `create=true`.
Cancellation still releases only this warmup's tickets; shared generation futures
are not explicitly cancelled. Duplicate status polling when no promotion occurs
has also been removed.

### Medium, Needs Measurement: Cover The Placement Area Deliberately

For eligible first entries, `SharedNetherEntry.preloadCenter` already returns
the shared reference used by `beginTransfer`. It is not mistakenly using the
runner's individual Overworld portal. However, it is a reference/search center,
not a precomputed final portal block.

Vanilla `PortalForcer.createPortal` searches up to 16 blocks in X/Z and checks
additional placement-clearance blocks. `getPortal` also searches existing
portal POIs around the target: its POI preloading requests EMPTY chunks, which
is distinct from fully generating all chunks in that search area. Consequently,
one properly bounded center ticket cannot eliminate all transfer work.

The benchmark compared center-only against a small, bounded 3x3
FEATURES warmup. That pilot showed essentially equal median transfer time but
more CPU work and a worse charge-tick tail for 3x3, so production remains
center-only. For further experiments, compare center-only against a bounded
FEATURES warmup covering the creation/clearance area. Start only with terrain,
retain the healthy-tick and remaining-charge guards for FULL work, and never
create portals early or change the vanilla search/order/RNG. Starting at race
ready is another experiment because the shared reference is already known, but
it shifts work into initial loading and should be tested separately.

## Lower Priority

`collectStatus` now runs once per active warmup tick, with a second poll only
after FULL promotion. It polls completed futures using `getNow`; it does not
synchronously generate chunks. This is low likely impact compared with generation.
Per-tick `ChunkPos` allocation is likewise not the primary problem. Debug output
is event-based here; no expensive per-tick formatting or structure searches were
found in the preloader itself.

## Benchmark Plan

Use fresh copies of the same seeds/world states, fixed render distance, and the
same portal charge/entry sequence. Include first shared entry, vanilla later
entry, cancelled charge, and reset during charge. Compare warmup OFF, corrected
center-only tickets, and a bounded terrain ring. Record total preparation time,
portal-transfer stall, server tick p95/max, generated chunks by status, heap peak,
and ticket/map cleanup. Verify identical portal location/orientation and preserved
RNG advancement. An earlier loading spike is not a win merely because transfer
time decreases. Do not run these measurements alongside seed-search workers.
