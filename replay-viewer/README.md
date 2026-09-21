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
  Fresh recordings also show a cyan pixel-ring loading indicator above the player
  and beside the timers, including reset gaps when no player is visible. It follows
  explicit recorded loading intervals, not missing data or the viewer's own loading
  screen. Its animation follows replay time and stops when playback is paused.
  F1 and the camera-path editor hide this overlay along with the compact HUD.
- **Players** pauses playback and lists locally available matching race recordings.
  **Import Replay...** adds manually shared MCPRs without moving their originals.
- Direct freecam, accelerated Classic freecam, first-person Follow Player, and
  Follow: Drone (an independently controlled orbit around the recorded player).
- **Follow: Details** adds recorded health, hunger, armor, XP, air, status effects,
  hotbar and offhand to first-person follow using Minecraft's normal HUD layout.
  The hotbar stays bottom-center with health/hunger above it. The playback bar
  sits above the HUD when visible. Press your Minecraft inventory key (normally E)
  for the centered vanilla inventory layout; playback controls hide until E or
  Escape closes it. Small GUI sizes fit the inventory above the survival HUD.
  **Analysis > Always show inventory** enables the optional always-visible mode.
  E/Escape can dismiss it without changing that preference. Inventory display is
  read-only and does not open a live inventory screen. Crafting slots are marked
  unavailable because their contents are not part of the recorded inventory track.
- Direct and Classic camera speeds are remembered separately across dimension
  changes, recorded world resets, and switching compact camera modes.
- Drone follow starts eight blocks away, above and behind the player. Hold the
  left mouse button and move the captured mouse to orbit; release it to keep
  the viewing angle. Player turns do not rotate the orbit. Scroll adjusts the
  distance from two to 32 blocks; solid terrain brings the camera closer.
  Orbit angles and distance survive camera replacement and recording switches.
  ReplayMod's click-to-spectate is suppressed only in Drone mode. Use T to
  release the cursor for the playback controls.
- **Analysis** has three optional overlays, all off initially:
  **Piglin cluster counter**, **Dragon trail**, and **Piglin trails**. Settings survive
  switches between grouped recordings; opening an unrelated replay resets them.
  The counter follows [Llama's Bastion Practice 3.15.0](https://github.com/LlamaPag/bastion/releases/tag/3.15.0):
  count piglins within one block of each piglin and display the largest count.
  The viewer considers living recorded piglins within 64 blocks of the recorded
  player, without the practice map's `bastion_mob` tag. It includes babies and
  does not detect walls, identify a particular hole, or infer unrecorded mobs.
  `--` means the recorded player is unavailable; zero means no eligible piglins.
  **Customize** beside either trail opens independent color swatches, length
  (2-30 seconds), thickness (1-4 pixels), opacity (10-100%), and optional fading
  of older segments. Reset restores that trail's defaults: ten seconds, one pixel,
  85% opacity, no fading, cyan for dragons and gold for piglins. Increasing length
  accumulates more history going forward; it cannot recover discarded samples.
  Line thickness is subject to the graphics driver's supported OpenGL line widths.
  Dragon trails show observed body positions; piglin trails sit just above the
  feet. Piglin trails cover up to 128 living recorded piglins within 64 blocks
  of the recorded player and work independently of the cluster counter.
  Trails are sampled at most ten times per replay second. They build during
  playback, stay still when paused, and clear on seeking, world changes,
  disappearance, leaving the piglin tracking radius, or large teleports. They do
  not reconstruct history preceding a seek and are depth-tested against
  terrain. F1 and the editor hide all analysis overlays; no replay files are modified.
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

The companion orders ReplayMod 2.6.27's terrain-screen dismissal with Minecraft's
packet tasks. ReplayMod otherwise queues that dismissal separately, which can
run before the queued JoinGame handler opens the screen and leave it stuck.
This preserves the original position-packet trigger and terrain-only screen
check; it does not add a timeout, fabricate packets, or alter recordings.
It also applies when seeking or crossing recorded worlds/dimensions. World
Preview can separately interfere with playback; disable its preview behavior
if necessary.

## Recorded Timers

### Player Details

Detailed follow requires a fresh replay from the updated ZSG recorder. ReplayMod
filters out several ordinary inventory/status packets, so ZSG stores a separate
compressed `zsg-rooms/player-hud.bin` archive entry. Capture reads only the local
player, with at most five samples per second and one-second heartbeats for
unchanged state. It works in both normal and Performance recording modes.
The track is capped at 16 MiB uncompressed and 72,000 samples; reaching its limit
does not stop the replay. The viewer loads the bounded track asynchronously and
selects only past samples from the current world interval and recorded entity.
Missing, invalid, or stale coverage displays "Player details unavailable", never
the replay camera's inventory or invented health. Seeking and recording switches
select the corresponding recorded state. This is viewing data, not tick-perfect
verification. Inventory visibility preferences survive grouped recording switches.

### Timers And Pause

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

Loading coverage is stored separately as optional `loadingIntervals` in the race
manifest, capped at 4,096 intervals. It survives long gaps without client ticks and
is clamped to the written recording duration. Older recordings without this field
do not show a loading indicator; older viewers can ignore the field.

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

Use `-PreplayStartupSmoke=true` instead of `-PreplayViewerSmoke=true` for the
controlled terrain-screen queue-order regression. It exercises the real
ReplayMod position-packet handler with both task-queue orders, an unrelated
screen, no position packet, and a synchronous position packet. The unpatched
2.6.27 handler fails when ReplayMod's queue runs first. For cold-open checks,
copy only the MCPR to a new directory, without any adjacent playback caches.
The regression establishes the ordering defect, not that every reported
first-open hang necessarily has this cause.

Use `-PreplayDetailsSmoke=true` for detailed-follow checks against a fresh
`runReplayPrototype -PreplaySmoke=true -PreplayResets=1` capture. It checks captured
inventory/status, actual inventory-key dispatch, both display modes, small/wide
windows, backward seeking, dimension changes and a same-file world reset.

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
