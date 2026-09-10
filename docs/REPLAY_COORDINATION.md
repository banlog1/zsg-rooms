# Coordinating Racer Replays

Status: recording metadata, local import/grouping, and the companion's Players
selector are implemented. Sharing is manual; no relay or upload service is used.

## One File Per Recorder, One Shared Race Timeline

Keep each player's MCPR independent. The viewer opens one at a time and switches
files when the user selects another racer. Do not combine entity IDs, chunks, or
dimension packets from different players' worlds in the same playback connection.
Different players can be in different dimensions at the same race time.

The game already synchronizes `InGame.raceId` through `RoomSnapshot`. Use that
opaque identifier for grouping, never a room code, numeric seed, player name,
or local date. Repeated games on the same seed must not be grouped together.

The planned complete manifest contains:

- Recording ID and schema version.
- Race ID and recorder UUID; a display name is presentation only.
- Race-start offset in the recording's monotonic timeline.
- Actual recorded intervals, connection/reset boundaries and local-player IDs.
- Split timestamps, finish/result marker, final IGT and precise elapsed race time.
- Minecraft/protocol and capture-format versions.

Prefer a small namespaced JSON entry inside the MCPR, so manual sharing needs
only one file. Retain ordinary ReplayMod compatibility. Finalize this entry on
the writer thread; capture immutable timing markers without blocking gameplay.
Do not store room access codes or prefetched seeds. Treat metadata, including
claimed identities, as untrusted input, not proof of a result.

## Implemented Capture Schema

New ZSG recordings contain `zsg-rooms/races.json` inside the MCPR. ReplayMod's
packet stream and standard metadata are unchanged. Schema version 1 contains:

- `recordingId` (also the filename), `recorderUuid`, and `displayName`.
- `minecraftVersion`, `protocolVersion`, and `captureFormat`.
- `durationMillis`, `complete`, and `truncated`.
- `races`: `raceId`, `startOffsetNanos`, `endOffsetNanos`, and optional
  `finishElapsedNanos` / `finishIgtMillis` for the recorder's own portal finish.
  New captures also include `firstWorldIndex`; solo test captures may include
  `testGroupId`. Both fields are optional when reading older schema-1 files.
- `intervals`: `startReplayMillis`, `endReplayMillis`, `worldIndex`,
  `localEntityId`, and `dimension`.

Race starts come directly from `LocalRaceClock.onResumedTick`, once per race.
The marker uses that exact clock instant, not a later client callback. Local
resets do not emit a new start; a new shared round does. The nanosecond offsets
preserve the live clock's precision; ReplayMod seeks still have millisecond
resolution. Winner selection is untouched.

An interval begins when the local player's synthetic spawn is recorded. A
dimension/world transition or disconnect ends it; the next player spawn opens
another. `worldIndex` changes on JoinGame, not on dimension changes. Intervals
describe recorded player coverage, not whether the player was allowed to move.
Pauses remain on the elapsed recording/race timeline. Loading/reset gaps are
not continuous player coverage. Final coverage is clamped to the last packet
actually written.

Capture is bounded to 1,024 races and 4,096 intervals per recording. Hitting a
limit sets `truncated`; packets continue recording. The writer serializes the
manifest only at finalization, with a 2 MiB archive-entry limit. No file I/O or
JSON serialization is performed by the server start hook.

Singleplayer recordings and recordings that missed the race-start hook have no
fabricated race-zero marker. They remain playable individually, but are not
eligible for automatic race alignment. Split markers are still derived from
advancement packets by the companion; manifest split/result labels are not yet
implemented.

## Align At The Existing Race Start

Capture the start instant from the same resumed integrated-server tick used by
`EndExitTimeCapture`/the race clock. Do not start a new independent race clock in
the viewer or use arrival time of a relay message. Convert that process-local
instant to an offset from `ReplayPrototype`'s recording origin; never compare
absolute `System.nanoTime()` values across machines.

With the current elapsed-monotonic recorder clock, mapping is:

```text
raceElapsedNanos = currentReplayMs * 1_000_000 - currentRace.startOffsetNanos
targetReplayMs = (raceElapsedNanos + targetRace.startOffsetNanos) / 1_000_000
```

Example: racer A's world loads for 8 seconds and racer B's for 15 seconds.
Race time 2:00 maps to 2:08 in A's file and 2:15 in B's file. Synchronizing both
files at their local 2:00 would instead compare different moments in the race.
Use elapsed race time, not IGT: IGT can pause differently on the two machines.
These viewing offsets never participate in winner adjudication.

Local world resets remain inside the same MCPR, as requested. Record each
connection interval and its identity so player following survives resets.
Preserve the current race's time origin across a local retry where the live race
clock does so. A newly launched shared seed has a new race ID/start marker.
The clock and manifest tests cover these boundaries without changing race timing.

## Manual Sharing First

1. Each racer records locally and shares their completed MCPR manually.
2. Import copies into the viewer's local library; validate bounded metadata,
   supported schema/protocol, duplicate IDs, and matching race ID.
3. Show matching recordings by name and recording ID. The same recorder can have
   multiple test takes; player UUID alone is never used to discard a take.
4. On selection, remember race time, play/pause, speed, and follow/freecam mode;
   close the current playback cleanly, open the target, and seek to its mapped time.
5. Select that file's recorded player or place freecam near them. Do not retain
   coordinates from another player's dimension. Restore speed and paused state.

A switch may briefly load/seek. Instant switching would require more world state
and memory than this first implementation; do not promise it.

If the destination file starts late, ends early, is incomplete, or has a gap at
that race time, show an unavailable interval. Do not silently clamp the timestamp
and pretend the players are synchronized. A failed import/switch must not delete
the current recording or overwrite the original shared file.

Legacy files, including the current prototypes, lack trustworthy race-zero
markers. They remain viewable individually. Any manual alignment must be explicit
and marked approximate; do not infer synchronization merely from matching seeds.

## Using Players

Open a finalized ZSG MCPR in ReplayMod, advance past the race start, and press
**Players** on the compact bar. Playback pauses while the selector is open.
It scans `replay_recordings`, `replay_recordings/zsg-shared`, and the open file's
own directory (not recursively). **Import Replay...** copies a manually shared
file into `replay_recordings/zsg-shared/<recordingId>.mcpr`, leaving its source
untouched. There is no automatic download.
Up to 16 source directories are remembered during that playback session, so
switching to an import still allows returning to a take opened from elsewhere.

The list shows only recordings with the exact shared race ID, or the explicitly
selected solo test group. `[Current]` identifies the open take; `[Unavailable]`
means the mapped time is before its start, after its end, in a loading gap, or
belongs to another round's world. Choose a different available take to switch.
Back restores the prior playback speed. Switching retains pause/speed, camera
mode and speed, far-follow distance, chat and auto-hide settings. Freecam is
placed near the destination player rather than retaining another world's position.

The companion reads bounded metadata on a background thread using an independent
ZIP handle. It checks protocol/schema, duration, interval ordering, canonical
UUIDs, duplicate ZIP entries, and complete/non-truncated capture. Limits are
8 GiB per archive, 2 MiB per JSON entry, 4,096 ZIP entries, 16 JSON nesting levels,
and 2,048 files per library scan. Import validates again before publishing the
copy and never overwrites an existing file. ReplayMod still owns packet playback;
this is not a sandbox or an authenticity check for external recordings.

## Testing Alone

1. Enable ZSG recording normally. In **Room Settings > Replays > Recording >
   Solo Replay Testing**, select **New Group**, then **Apply**. No Java argument
   is needed. The group persists until **Disable Testing** or another Apply.
2. Start a room with only yourself and choose a manual seed. Play an attempt,
   then return to the room so the file finalizes. Start another attempt with the
   same seed and same test group; return to the room again to save it.
3. Open either recording in ReplayMod. Seek to a point where both attempts have
   recorded player coverage, open **Players**, and select the other test take.
   Separate recordings from the same Minecraft account remain separate rows.
4. Disable testing afterward. Use **Copy ID** and Apply to reuse a test group in
   another local instance when needed.

This is a replay-only grouping override, not a reused live race ID. Each race
keeps its real `InGame.raceId`, synchronized clock, and winner logic. The group
is captured only when the room has one player at start; multiplayer rooms ignore
it. Ordinary singleplayer worlds without a room still have no race start marker.
Test mode does not enforce the same seed: choose that explicitly. Local resets
stay in one continuous MCPR and keep the original race origin, as before.

## Playback Status Metadata

The optional `timingSamples` manifest array stores compact rows of
`[replayMillis, worldIndex, state, rtaMillis, igtMillis]`. State is 0 for active,
1 for a paused Minecraft client, or 2 for unavailable/loading. A missing timer
value is -1. The optional SpeedRunIGT integration resolves its methods once;
reads happen at most once per second except for immediate state/world changes.
The collection is synchronized and bounded to 12,000 rows. Finalization drops
samples beyond the written packet prefix. Hitting just this limit does not
invalidate race grouping or stop packet recording.

The viewer uses replay timestamps to select/interpolate the recorded clocks,
including after backward seeking or switching files. It never interpolates
across reset boundaries or state changes. Coverage more than 1.5 seconds past
the last sample is unknown. Older manifests without this field remain supported.
These sampled timers are for viewing, not winner arbitration or finish timing.

The live REC badge has an independent persisted `showRecordingHud` preference,
defaulting to true. Changing it during recording does not affect capture.

## Later Network Sharing

No relay changes are needed for manual sharing. Later automatic sharing would
need opt-in upload, storage, retention/deletion, access control, size/rate limits,
and a manifest/download service. Keep large replay payloads off the live room
WebSocket. Sharing recordings exposes recorded world/player data and chat, so
uploads must not be silently enabled.

## Verification

The timer/badge update passes 266 core tests and 26 companion tests. A fresh
SpeedRunIGT capture spanning two resets saved 45 timing rows, including six
paused samples across three worlds. Inspection confirmed IGT stayed fixed while
RTA advanced during pauses. The derived multi-recording playback fixture also
renders the timer/pause overlay in Far Follow and first-person Follow, including
at 640x480. These tests use a local development instance, not a full modpack.

Capture-stage checks passed: 262 main-mod tests, nine isolated writer tests,
and a development Minecraft recording spanning two resets/three worlds. Its
manifest had two synthetic race markers and nine dimension intervals, with
reset gaps and distinct player entity IDs. ReplayMod 1.16.1-2.6.27 opened that
file, played across both resets and rewound successfully with the companion.
Synthetic markers test the recorder pipeline; the exact first-resumed-tick
hook and unequal-load-time alignment are separately unit tested. The solo UI
test generated/applied a group, recorded two resets, and finalized a file with
the group only on the solo-marked race. Real race IDs remained unchanged.

The companion's automated Minecraft test imports a separate take by the same
recorder, switches both ways between different file offsets/dimensions at the
same elapsed race time, preserves paused/2x playback and Far follow, and renders
the selector at 1280x720 and 640x480 (GUI scale 2). It also starts from a custom
directory and returns there from the imported copy. This uses derived local
fixtures, not two independently played machines; that broader test remains.

ReplayMod 2.6.27 wraps archives in `ManagedReplayFile`/`DelegatingReplayFile`.
The companion unwraps that object to read the archive independently. Switching
starts the destination sender in synchronous mode, seeks once, clears its
initial async-only `startFromBeginning` flag, then restores playback mode.
Without clearing that flag, enabling async playback can rewind the destination
after a successful seek. These private integration points require retesting when
upgrading ReplayMod. Ordinary playback and vanilla game code are not patched.

- Two clients with deliberately different world-load times align at race zero.
- Backward/forward switching at a known split preserves elapsed race time.
- Pause gaps, local resets, dimension changes and multiple rounds stay distinct.
- Missing, truncated, duplicated or mismatched manifests fail without data loss.
- Old MCPRs still open normally; watching never arms capture or submits results.
