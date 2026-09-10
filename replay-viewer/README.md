# ZSG Replay Viewer Companion

Optional, separately built playback UI for Minecraft 1.16.1 and **ReplayMod
1.16.1-2.6.27**. Install both JARs to use it. It does not bundle ReplayMod,
record worlds, connect to the room relay, or change live race logic. The original
ZSG recording mod remains independently usable without this companion.

## Controls

- Compact bottom playback bar, with an extra control row at smaller GUI sizes.
- ReplayMod's play/pause, speed slider, and marker timeline.
- Five-second backward/forward buttons, clamped to the recording's bounds.
- Number-row 3 skips back five seconds; 4 skips forward. Rebind these under
  Minecraft Controls > Replay Mod as "ZSG: Back 5 seconds" and "ZSG: Forward
  5 seconds". They work in compact playback, even with the bar hidden or playback
  paused, but not in menus, text entry, open dropdowns, or the camera-path editor.
  Modified key combinations are ignored. Normal hotbar keys remain unchanged
  outside playback. No new bindings use 1, 2, or the arrow keys.
- Elapsed and total recording time (not IGT); after grouping, elapsed race time too.
- A top-right overlay shows the recorded player's RTA and IGT. An amber pause
  icon appears above the recorded player's name while they were paused, facing
  the camera and hidden by intervening terrain. First-person Follow instead
  places the icon beside the timers because the player's head is out of view.
  These remain visible when the bottom bar auto-hides. Playback pause is separate
  from recorded player pause.
  F1 and the camera-path editor hide this overlay along with the compact HUD.
- **Players** pauses playback and lists locally available matching race recordings.
  **Import Replay...** adds manually shared MCPRs without moving their originals.
- Direct freecam, accelerated Classic freecam, first-person Follow Player, and
  Follow: Far (behind and above the recorded player).
- Direct and Classic camera speeds are remembered separately across dimension
  changes, recorded world resets, and switching compact camera modes.
- Far follow starts eight blocks behind the player. Scroll adjusts the distance
  from two to 32 blocks; solid terrain brings the camera closer. The chosen
  distance survives camera replacement too.
- Chat is hidden by default during playback. The Chat checkbox reveals it
  without changing live-game chat settings or removing recorded messages.
  ReplayMod's own Show Chat filter must also be enabled to see those messages.
- Colored Nether, Bastion, Fortress, Stronghold, and End milestone notches above
  the timeline. Hover a notch for its event, recording time, and world attempt;
  click it to seek while retaining the current playback speed or paused state.
- Auto-hide after three seconds idle. Hovering/dragging controls, an open camera
  menu, and paused playback keep controls visible. Press ReplayMod's cursor key
  (normally T) to release the mouse; cursor movement reveals hidden controls.
- Editor restores ReplayMod's original camera-path UI; Compact returns to the
  bottom bar. Playback settings do not silently rewrite ReplayMod's config.

For grouped recordings, Follow Player uses the recorder UUID from the manifest.
Otherwise it selects the sole non-camera player and does not guess between
multiple recorded players. The Players selector preserves playback and camera
preferences between files. Opening an unrelated replay normally resets controls.
Camera-path editing remains controlled by ReplayMod, not the compact camera modes.

Milestones come from completed vanilla advancement packets, not chat messages.
Opening a recording starts one read-only background scan; it does not seek the
active replay or rewrite its archive. Each advancement appears once per recorded
world attempt. Ordinary dimension swaps do not duplicate it, but a new-world
reset allows it again. Advancements already complete when recording begins are
not given invented completion times. Old files work when they contain the
relevant advancement packets. These are recording timestamps, not race IGT.
Closely spaced markers retain their notches and hover details even when their
labels cannot fit. The index is limited to 2,048 milestones per file.

World Preview interfered with replay viewing in the user's test instance. If
that happens, disable its preview behavior for playback. No terrain-screen
workaround is applied by this companion.

## Recorded Timers

Pause and timer data require a new recording made with the updated ZSG recorder.
Install SpeedRunIGT when recording to capture its RTA/IGT values. Without it,
the pause indicator still works but timers show `--:--`; legacy recordings also
show unavailable values. The viewer never substitutes its own live timer.

The recorder samples once per second and immediately on the next client tick
after a pause/resume or world-state change. Playback interpolates neighboring
samples only within the same world and state, so these are convenient viewing
timers, not a replacement for the exact finish result. Reset/load gaps are not
interpolated. Capture is bounded to 12,000 samples per file; missing or stale
coverage becomes unavailable rather than showing an indefinitely paused player.
Metadata is read asynchronously from the archive without seeking playback.

To hide only the live recording badge, use ZSG **Settings > Replays > Recording >
Show Recording Indicator**. This does not stop recording or hide playback timers.
The recording-status badge is never shown during replay playback, even if the
previous live recording's status still says it was saved.

## Build And Test

From the repository root, prepare the pinned reference and build:

```powershell
.\gradlew.bat -p replay-prototype fetchReplayModReference
.\gradlew.bat -p replay-viewer build --offline
```

Artifact: `build/libs/zsg-replay-viewer-0.1.0.jar` inside this directory.
The version comes from `viewer_version` in this directory's `gradle.properties`;
both the filename and `fabric.mod.json` use it. The core's playback test resolves
the same version automatically. See [release packaging](../RELEASING.md).
The independent module uses Java 8 bytecode and the main project's Loom version.
ReplayMod is a compile/runtime dependency, never an included JAR.

The root test-only driver can load this artifact. Always use a disposable copy
of the input MCPR because ReplayMod may create caches/metadata beside it:

```powershell
.\gradlew.bat runReplayPlaybackTest '-PreplayPlaybackFile=<absolute copy path>' -PreplayViewer=true -PreplayViewerSmoke=true --offline
```

The extended smoke driver uses the local `7bbdc52d-751b-4136-8a03-ff65922ffe6f`
fixture: a dimension transfer before 22 seconds, a world reset around 66 seconds,
and two recorded End entries. It seeks across those boundaries to check camera
speed retention. It also exercises chat defaults/toggling, far follow,
compact/editor restoration, auto-hide/reveal, seeking, and wide/small rendering.
For a different recording, omit `-PreplayViewerSmoke=true` for plain playback
testing or adjust the test driver's fixture timestamps. Screenshots and logs are under
`run/replay-playback-test`. Require the `[ReplayViewerSmoke] PASS` marker, not just
a successful process exit. Unit tests cover time bounds, responsive row selection,
visibility transitions, per-mode speed restoration, bounded follow distance,
advancement IDs, initial snapshots, deduplication, reset handling, and stable
milestone ordering.

The companion unit tests and extended in-game smoke cover camera speeds
across actual dimension/world changes, marker hit testing, paused marker
seeking, and restoring normal chat visibility after playback.
Captured and inspected 1280x720 and 640x480 windows at GUI scale 2, including the open
camera menu, hidden state, and editor restoration. Both 2x-speed forward seeking
and paused backward seeking preserve their playback state. This is not a claim
of full modpack or two-machine capture coverage.

The additional `-PreplayRaceSmoke=true` driver derives two test-group fixtures
from the local `6397faa4-a5a5-4b54-b257-cac6bdf56a59` development capture. It
imports one, switches in both directions at matched elapsed time across different
dimensions, checks pause/speed and Far follow, and screenshots the Players UI at
wide/small sizes. It requires `[ReplayRaceSmoke] PASS`. Metadata tests cover
separate same-account takes, real multiplayer race IDs, custom-folder return,
unequal starts, loading gaps, reset/round boundaries, invalid archives, and safe
duplicate imports. Do not enable both smoke modes together.

## Race Recordings And Solo Tests

See [the coordination contract and testing steps](../docs/REPLAY_COORDINATION.md).
New core recordings contain `zsg-rooms/races.json` with shared race IDs, precise
start offsets and world coverage. The Players selector groups by race ID and
maps the current elapsed race time into the selected file. It does not align by
seed, wall-clock date, file timestamp, or IGT. Missing coverage is marked
unavailable rather than clamped to a different race time. A switch reloads the
selected world and can take time; this is not an instant multi-world viewer.

Matching recordings are discovered in `replay_recordings`, its `zsg-shared`
import folder, and the current file's directory. Imports are validated and copied
under their recording UUID, with no overwrite. Pause/speed, camera mode/speed,
far-follow distance, chat and auto-hide preferences survive switching. Freecam
starts near the destination recorder; the previous world's coordinates are not
reused. A failed switch attempts to reopen the previous file.

To test with one Minecraft account, enable **Solo Replay Testing** from the core
mod's Replay Recording settings. **New Group > Apply** gives separate one-player
room attempts a shared replay test group without changing live race IDs/results.
Record two attempts using the same manual seed and return to the room to save
each. Open one, press **Players**, and choose the other test take. No Java
argument or second player is needed. The toggle is local and ignored for rooms
with multiple players. Disable testing when done.

Older files without race metadata remain individually playable. Automatic
upload/download, recording authenticity, and approximate manual alignment of
legacy files are not provided. Manual sharing needs no relay deployment.

## Maintenance And License

This new optional module is GPL-3.0-or-later; see LICENSE. It uses ReplayMod's
existing widget and camera APIs without copying its viewer implementation.
The rest of the repository's licensing is unchanged. Publish this module's
corresponding source and license with any distribution of its binary.

The integration is deliberately version-pinned: ReplayMod does not promise a
stable external API. Updating ReplayMod requires rebuilding and re-running the
viewer checks. Original editor widgets/layout are retained and restored instead
of replacing ReplayMod's playback engine.
