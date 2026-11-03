# ZSG Replay Viewer Companion

Optional, separately built playback UI for Minecraft 1.16.1 and **ReplayMod
1.16.1-2.6.27**. Install both JARs to use it. It does not bundle ReplayMod,
record worlds, connect to the room relay, or change live race logic. The original
ZSG recording mod remains independently usable without this companion.

## Overhaul Preview

The ReplayMod menu entry now opens the ZSG **Replay Library**. It scans metadata
in the default recording folder, the selected output folder, and shared imports.
Search, folder browsing and playback do not move recordings. The trash icon deletes
the selected archive only after a permanent-deletion confirmation showing its name
and path. Cancel leaves it untouched. Changed/replaced files are rejected until a
refresh; deleting is disabled during gameplay or replay playback. Neighboring files
and cache folders are not removed. The pencil icon renames the selected `.mcpr`
file in the same folder after confirmation, without overwriting a destination or
rewriting archive contents. Custom filenames become the library title; generated
UUID filenames retain their metadata title. Rename shares the playback and changed-file
guards used for deletion and leaves sibling cache folders alone. In Rooms
settings, **Recording Folder** sets the destination of new recordings; it is
separate from the supporting writer-library folder. ReplayMod is still required
as the playback engine, but its file chooser is no longer the normal entry point.

**Rooms Settings > Recording > Recording detail** selects **Standard**, **Detailed**
or **Super Detailed** for the next recording. Existing Detailed Capture preferences
migrate to Detailed; Standard remains the default. Camera sampling and Performance
mode remain independent, and changing detail never changes an active session.
**Faithful Follow**
uses that track for recorded render distance, F5 perspective, F1 visibility,
attack cooldown/indicator, HUD, and automatic inventory, crafting-table and chest
screens. The inventory key opens a separate read-only inspection view without
pausing playback. Closing it returns to the current recorded presentation. Releasing
the cursor during automatic recorded screens can reveal and scrub the normal replay
toolbar; those screens fit above the visible toolbar and survival HUD. Auto-hide
and explicit hide/reveal work as elsewhere. Manual inspection remains a separate
modal view. **Settings > Display > Run timers** toggles RTA/IGT in all camera modes;
**Analysis > Piglin cluster counter** also applies to Faithful Follow. These replay
overlays are independent of recorded F1, but explicitly hiding replay UI hides them.
**Display > Render distance** adds a top-left chunk-grid indicator for the recorded
player's distance, updated on playback and seeks. It defaults off, works in every
camera mode, and shows `RD --` when Detailed Capture coverage is unavailable. It
never substitutes the viewer's own setting or changes it.
Cursor stacks are shown in a fixed inset; mouse movement is not recorded. Unsupported
screens and missing coverage are marked unavailable, never guessed. Recorded pause
is shown with a gray overlay and centered label, distinct from playback pause.
The existing Follow: Details mode and old replays remain supported.
**Rooms Settings > Recording > Camera sampling** independently selects Standard
(the default), 60 Hz, 120 Hz or 240 Hz (advanced) for the next recording, with any preset.
The optional camera track preserves full-precision first-person yaw/pitch at up to
the selected rate, limited by rendered frames. Follow Player, Follow: Details and
Faithful Follow use it automatically in first person, including Quick Mode. Old
replays, unsupported writer libraries and unavailable samples retain ordinary
packet-based camera behavior. Freecam, drone, F5, riding/sleeping and offline video
export are unchanged. Position, hand sway, targeting/block outlines, inventory and
world updates retain their existing rates with camera sampling alone; this is not high-rate game simulation.
The camera track uses a bounded 4,096-sample queue and a temporary disk file,
approximately 5.2/10.4/20.7 MB/hour at 60/120/240 Hz before compression.
Higher rates require more recorder work and storage; 240 Hz is intended for high-FPS capture.
A separate 2 GiB camera budget or overload stops/gaps only this optional track.
Playback never interpolates across explicit gaps, world/entity boundaries or
sample gaps longer than 100 ms, and deletes its temporary camera index on close.
Short live capture and Full/Quick playback checks cover pauses, seeks, resets and
fallback. Sustained 240 FPS, multi-hour AA recordings and slow-disk shutdown still
need stress testing. The recorder's existing two-second application-exit grace is
unchanged; closing the application during finalization can still outlast it.
Recorded render distance does not recreate chunks absent from the replay. Screen
state is sampled, not a video: intermediate changes within a 50 ms inventory sample may not
be retained. Standard/Detailed recordings do not include equip-animation progress.

**Super Detailed** adds an optional render-only presentation track at up to 60 Hz,
limited by rendered FPS: effective first-person FOV, compatible FOV-based zoom,
view bobbing, held items, equip/swing progress and item-use timing. Faithful Follow
uses these inputs without installing them into game entities or changing saved
FOV/bobbing options. Missing coverage, Freecam, F5, riding/sleeping and offline
video export retain ordinary rendering. This is not a capture of arbitrary modded
post-processing or a video of the original screen.

Super Detailed also records supported original local-player sound events: footsteps,
mining, block placement, bucket interactions, eating/drinking, damage and falls.
Incoming world sounds remain in the normal packet stream. The viewer's player-sounds
toggle controls this playback; covered sound categories replace their inferred
equivalents, and old or unavailable coverage keeps the existing reconstruction.
Seeking is silent and does not replay the sound history up to the destination.
Vanilla crafting remains silent.

The extra presentation queue is bounded to 4 MiB / 128 frames and its disk track
to 2 GiB. Local sounds use a 256 KiB / 1,024-record queue and a 512 MiB disk budget;
an incomplete sound sidecar is discarded so the viewer can safely use reconstruction.
These limits affect optional fidelity, not packet recording. Neither mode disables
itself merely because a run is long, but these limits and existing metadata limits
still apply. Super Detailed costs more CPU, disk work and storage during recording;
multi-hour and comparative performance profiling remain necessary.
**Capture health** is a separate local HUD toggle for every mode, independent of the
REC badge. It shows observed camera Hz, actual dropped camera samples, packet/camera
queue pressure and Super Detailed track status. A rate below the target may simply
reflect lower game FPS.

The optional detail track streams through a bounded 4 MiB / 128-frame queue to
disk, rather than retaining a whole session in memory. It records changed states
at up to 20 Hz with a one-second heartbeat. Overload inserts explicit gaps; the
2 GiB detail budget stops only extra capture, not the ordinary packet recording.
The viewer builds a temporary disk index and retains only a small index page and
current frame, deleting its temporary files on normal close. This is not a claim
of zero recording overhead: comparison profiling and real multi-hour AA testing
are still required. Existing race/timer metadata limits are unchanged.

The transparent control bar includes hand-pixelled icons, a camera mode selector,
a go-to-time dialog and explicit hide/reveal. Explicit hiding also works while paused. The
clock accepts seconds, `m:ss`, or `h:mm:ss`, with optional fractional seconds.
Go-to-time and hide/show have rebindable, initially unassigned Replay Mod controls.
Split icons replace permanent milestone labels, with hover details and a paged
chooser for overlapping events. The progress arrow and stem share the native
timeline's center coordinate. Replay settings have Playback, Analysis and Display
tabs. The Splits table compares the recorded Nether, Bastion, Fortress, Stronghold,
End and Dragon milestones across matching race recordings. Times are elapsed from
each recording's race start, including loading; tooltips also show the replay time
and available recorded IGT. Click a time to seek and follow that runner. Existing
follow modes are retained; freecam switches to Follow Player. The Players action
still imports recordings and switches at the same elapsed race time.
Reset worlds are separate per-column selections, not merged or assumed to share
a seed. Missing milestones remain unavailable, never estimated. Recorded splits
inside coverage/loading gaps remain visible but cannot be jumped to. Only visible
columns are indexed on a cancellable background worker, with a bounded completed
index cache. The current recording reuses its existing milestone index.
Historical player/mob trails and **Piglin Barters** are available under Analysis.
The guided tutorial and longer-session comparison profiling remain subsequent
overhaul work.

## Controls

- **Settings > Analysis > Mob Highlights** selects mob types for colored glowing outlines,
  visible through terrain. Search by mob name or registry ID, select multiple
  types, and choose a color swatch. The master switch retains the selection;
  Clear removes it. No types are selected when a replay is first opened.
  Selections survive seeking, dimension changes and switching player recordings.
  F1 and the camera-path editor hide them. Only mobs already loaded from recorded
  chunks can be highlighted; no extra chunks are loaded. This is viewer-only,
  works with existing recordings, and never changes recorded glow flags or teams.
- Compact bottom playback bar, with an extra control row at smaller GUI sizes.
- ReplayMod's play/pause, speed slider, and marker timeline.
- **Settings > Playback > Quick Mode (experimental)** switches to ReplayMod's indexed
  playback for faster seeking. Off when opening a replay, including another
  player's recording. The first enable prepares an index; progress appears in
  Analysis. Switching retains playback speed and pause state. Turn it off for
  normal playback fidelity; returning to normal can require a longer rebuild.
  Quick Mode omits some visual details, including particles and second skin layers.
  ZSG's detailed HUD and timers still read their separate timestamped tracks.
  This setting does not change the recorder or recording Performance Mode.
  The companion corrects ReplayMod 2.6.27's chunk-section fluid-count version
  check during indexed playback and uses separate `zsg-quick-v1` cache entries
  so previously generated Quick Mode caches cannot supply malformed terrain.
- Five-second backward/forward buttons, clamped to the recording's bounds.
- Number-row 3 skips back five seconds; 4 skips forward. Rebind these under
  Minecraft Controls > Replay Mod as "ZSG: Back 5 seconds" and "ZSG: Forward
  5 seconds". They work in compact playback, even with the bar hidden or playback
  paused, but not in menus, text entry, open dropdowns, or the camera-path editor.
  Modified key combinations are ignored. Normal hotbar keys remain unchanged
  outside playback. No new bindings use 1, 2, or the arrow keys.
- Elapsed and total recording time (not IGT); hover it for the grouped race clock.
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
  New recordings also distinguish an open player inventory (pixel-art drawstring
  pouch) from an open crafting table (Minecraft's textured crafting-table item,
  including resource-pack changes). These icons appear above the player and
  beside the timers, including first-person follow. They describe the recorded
  player's screen, not the viewer's inventory overlay; pause remains a separate icon.
  Release the cursor and hover an overhead or HUD indicator for its explanation.
  F1 and the camera-path editor hide this overlay along with the compact HUD.
- **Players** pauses playback and lists locally available matching race recordings.
  **Import Replay...** adds manually shared MCPRs without moving their originals.
- Direct freecam, accelerated Classic freecam, first-person Follow Player, and
  Follow: Drone (an independently controlled orbit around the recorded player).
- In Direct or Classic freecam, use Minecraft's Use Item key (normally right-click)
  while looking at a nearby chest to inspect its last recorded contents. The window
  is read-only and leaves playback running (or paused if already paused). Close it
  with Done, Escape or your inventory key. It supports normal, trapped and double chests.
  Fresh recordings are required for the chest-position markers, in either recorder
  mode. Contents come from existing inventory packets, indexed alongside milestones,
  and work with normal or Quick playback. Seeking never reveals future loot.
  Chests without recorded contents or usable prediction metadata show unavailable contents. Closed chests
  show the last observed contents, not guaranteed current contents; breaking,
  changing or reloading a chest's chunk invalidates them until another opening.
  The recorder never scans containers, loads extra chunks or generates unopened loot.
- Fresh recordings finalized after a room match result, returning to the room, or quitting ordinary
  singleplayer, can also show **Predicted vanilla loot** for generated block chests
  across all vanilla structures, including villages, bastions, fortresses, shipwrecks,
  buried treasure and ruined portals. Normal, trapped and double chests are supported.
  The integrated-server recorder attaches the existing loot-table ID and loot seed
  when Minecraft already sends a chest's chunk. This adds no tick scan, inventory
  reads, extra chunk generation or loot rolls, and leaves live packet NBT unchanged.
  Metadata is capped at 2,048 chest observations per recording. Only recorded chunks
  are covered; custom loot tables, zero loot seeds, chest minecarts and ender chests
  are not predicted. Player-placed items still require recorded inventory contents.
  The viewer generates items on inspection using Minecraft 1.16.1's built-in tables
  and inventory shuffling, including enchantments and stew effects. Predictions
  use the recorded vanilla effect order for shipwreck stews, since that order varies
  between JVM launches. This six-entry list is captured once per recording. Treasure maps
  have an explicitly unresolved destination; no structure lookup or map generation
  is performed. No seed-finding library or Minecraft world is started by the viewer.
  Predictions are original vanilla loot, not observed/current contents; custom
  world generation, data packs and other loot-changing mods are not modeled.
  The metadata is bound to original packet order, world/reset, dimension and block
  position. Chunk replacement, unloading or a changed chest invalidates that identity
  until a fresh observation. Older temple-only recordings retain their geometry-checked
  seed-model preview. Predictions do not include the bastion first-chest iron top-up.
  Recorded contents take priority, and invalidated recorded contents stay unknown.
  Loot metadata and the seed are not exported on
  a manual recording stop, same-seed reset, individual finish or an unannounced room
  disconnect. Returning to the room releases predictions for the saved replay even
  if the shared room race is still active. Starting another run revokes that local
  viewing permission. This does not change other players' runs or relay results.
  This is a normal-client safeguard, not anti-cheat protection against modified files
  or clients. Old files without the released seed remain usable without predictions.
- **Follow: Details** adds recorded health, hunger, armor, XP, air, status effects,
  hotbar and offhand to first-person follow using Minecraft's normal HUD layout.
  The hotbar stays bottom-center with health/hunger above it. The playback bar
  sits above the HUD when visible. Press your Minecraft inventory key (normally E)
  for the centered vanilla inventory layout; playback controls hide until E or
  Escape closes it. Small GUI sizes fit the inventory above the survival HUD.
  **Settings > Display > Always show inventory** enables the optional always-visible mode.
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
- **Analysis** has optional overlays, all off initially:
  **Piglin cluster counter**, **Dragon trail**, **Piglin trails**, **Player trails**,
  and a searchable **Mob Trails** type picker. Settings survive
  switches between grouped recordings; opening an unrelated replay resets them.
  The counter follows [Llama's Bastion Practice 3.15.0](https://github.com/LlamaPag/bastion/releases/tag/3.15.0):
  count piglins within one block of each piglin and display the largest count.
  The viewer considers living recorded piglins within 64 blocks of the recorded
  player, without the practice map's `bastion_mob` tag. It includes babies and
  does not detect walls, identify a particular hole, or infer unrecorded mobs.
  `--` means the recorded player is unavailable; zero means no eligible piglins.
  The compact counter uses a hand-pixelled piglin face and a number; hover it for
  the grouping rule. Its counting behavior is unchanged.
  **Customize** beside each trail opens independent color swatches, length
  (2-30 seconds), thickness (1-4 pixels), opacity (10-100%), and optional fading
  of older segments. Reset restores that trail's defaults: ten seconds, one pixel,
  85% opacity, no fading, cyan for dragons, gold for piglins, green for players and
  violet for other mobs. Mob Trails selections and styling are independent from
  Mob Highlights. Enabled dragon/piglin shortcuts take precedence over the generic
  picker for the same entity, so it is never drawn twice.
  Line thickness is subject to the graphics driver's supported OpenGL line widths.
  Dragon trails show observed body positions; piglin trails sit just above the
  feet. Piglin trails cover up to 128 living recorded piglins within 64 blocks
  of the recorded player and work independently of the cluster counter.
  Player trails cover up to 32 loaded recorded players, excluding the viewer camera.
  Generic mob trails cover the nearest 128 loaded matching mobs to the camera.
  On first use, an independent background scan indexes packet positions into a
  temporary disk file. Once prepared, enabling trails, increasing their length or
  seeking restores the preceding configured seconds, even while paused. Settings
  show indexing/preparation status; live sampling remains available while preparing
  or when historical data is unsupported. Historical display refreshes up to ten
  times per replay second and never includes future positions. World/respawn
  boundaries, entity removal/reuse and known teleports break paths. Unmarked short
  local-player teleports cannot always be distinguished from ordinary movement.
  History uses a bounded 1 GiB scratch file, sparse anchors and a bounded window/page
  cache, deleted on normal close or failure. If a limit is reached, historical
  indexing stops and live trails remain available; the replay is unaffected.
  Trails are depth-tested against terrain. F1 and the editor hide all analysis
  overlays; no replay files or recorder behavior are modified.
- **Settings > Analysis > Barters** opens a paused snapshot of completed trades in
  the current recorded world, through the current replay time. History can be
  searched by item name/ID, sorted newest/oldest first, and paged. Click a trade
  to jump to it. **Items** totals received quantities, preserving item/NBT variants;
  click an item to filter history. Gold counts completed trades, not dropped
  ingots or interrupted inspections. Piglins counts distinct contributors, not
  the current cluster. World resets and future trades are excluded. Done returns
  to settings and restores the prior playback speed/pause state.
  **Bartering labels** marks living adult piglins visibly inspecting a gold ingot,
  not babies or other admired gold items. The amber labels are depth-tested,
  limited to 64 blocks, and hidden with the UI/F1/editor. Labels can work with old
  recordings; Quick Mode can omit equipment/state changes. An inspection label
  does not promise that a trade will complete.
  Accurate outcomes require a new Rooms recording and updated supporting writer.
  The recorder observes the existing completed-loot call, including standardized
  and boosted barters, without rerolling, modifying stacks or sending packets.
  Only piglins tracked in the matching local world/session are retained. Capture
  is event-driven in every recorder mode, independent of Detailed Capture. The
  optional `zsg-rooms/barters.json` track is capped at 4 MiB / 8,192 events / 32,768
  output stacks, with at most 64 pending callbacks. Limits mark history partial;
  optional-track failures do not stop normal packet recording. Old/unsupported
  recordings report history unavailable rather than inferred or known-empty
  totals. This is bounded extra work per barter, not a zero-overhead claim.
- Chat is hidden by default during playback. **Settings > Display > Show chat** reveals it
  without changing live-game chat settings or removing recorded messages.
  ReplayMod's own Show Chat filter must also be enabled to see those messages.
- Pixel-art Nether, Bastion, Fortress, Stronghold, and End icons above
  the timeline. Hover an icon for its event, recording time, and world attempt;
  click it to seek while retaining the current playback speed or paused state.
- Auto-hide after three seconds idle. Hovering/dragging controls, an open camera
  menu, and paused playback keep controls visible. Press ReplayMod's cursor key
  (normally T) to release the mouse; cursor movement reveals hidden controls.
- **Settings > Display > Path editor** restores ReplayMod's original camera-path UI; Compact returns to the
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
Closely spaced markers form one icon with grouping dots and bounded hover details.
Click it to choose any event from the paged list. The index is limited to 2,048
milestones per file.

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

Screen coverage uses optional `screenIntervals` rows `[startMs, endMs, kind]` in
the same race manifest, with kind 1 for inventory (including creative inventory)
and 2 for a crafting table. The existing client tick captures only transitions,
with no extra inventory serialization or packets in either recording mode.
Intervals close on loading, world changes and recording end, and are bounded to
4,096 entries. Seeking uses the recorded timestamp. Older recordings without this
field show neither icon; other screens such as chests do not imply either state.

Loading coverage is stored separately as optional `loadingIntervals` in the race
manifest, capped at 4,096 intervals. It survives long gaps without client ticks and
is clamped to the written recording duration. Older recordings without this field
do not show a loading indicator; older viewers can ignore the field.

To hide only the live recording badge, use ZSG **Settings > Recording >
Show Recording Indicator**. This does not stop recording or hide playback timers.
The recording-status badge is never shown during replay playback, even if the
previous live recording's status still says it was saved.

## Build And Test

From the repository root, prepare the pinned reference and build:

```powershell
.\gradlew.bat -p replay-prototype fetchReplayModReference
.\gradlew.bat -p replay-viewer build --offline
```

Artifact: `build/libs/zsg-replay-viewer-<viewer_version>.jar` inside this directory.
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
windows, backward seeking, dimension changes and a same-file world reset. It also
checks inventory/crafting indicators in drone and first-person follow, verifies
that the viewer's own inventory controls do not change them, and captures screenshots.

For Super Detailed, create a fresh development capture with
`runReplayPrototype -PreplaySmoke=true -PreplaySuperDetailed=true -PreplayResets=1`.
Camera sampling is optional; add `-PreplayCameraHz=120` to cover it too. Then use
`runReplayPlaybackTest -PreplayViewer=true -PreplaySuperDetailedSmoke=true`
with `-PreplayPlaybackFile=<absolute capture path>`. This driver validates the
real presentation/sound tracks, makes isolated test copies, and checks transformed
Minecraft projection/hand/bob rendering, pause/seeks, ordinary-camera and
missing-track fallbacks, immediate exit cleanup, and unchanged source SHA-256.
Require `[SuperDetailedSmoke] PASS`. Short live checks passed with both Standard
and 120 Hz camera capture; this does not establish long-session performance cost.

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

For the focused mob-highlighting test, use `-PreplayHighlightSmoke=true` instead
of the other smoke flags, with `-PreplayViewer=true` and a local replay copy.
It checks the type picker at 640x480, selection/color controls, independent
player-switch snapshots, F1, seek cleanup, and unchanged entity flags/teams.
Synthetic playback-only mobs and a stone wall provide a screenshot pixel check
for through-wall outlines and clearing them. The task requires
`[MobHighlightSmoke] PASS:`; it does not modify the recording.

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

## Experimental Player Sounds

**Settings > Playback > Player sounds (experimental)** reconstructs the recorded player's
footsteps, mining hits, nearby held-block placements, water/lava bucket filling
and emptying, eating/drinking, hurt/death, shield/thorns and damaging fall impacts.
It is on for each newly opened replay and can be disabled in Analysis. Reconstruction
is entirely viewer-side: existing recordings work, with no change to capture
performance or file size. Super Detailed recordings additionally supply the original
supported local sound events described above. Sounds remain
positional and use Minecraft's sound-category volume controls.

Movement, swings and block updates are approximations of the original inputs.
Mining requires repeated swings at the same block, not just one interaction's
animation; menus, buckets and recent placements suppress mining. This deliberately
misses some very short mining actions rather than making ordinary interactions noisy.
Placements accept a matching currently or very recently held block to tolerate
packet ordering. Bucket sounds require a matching held/recent bucket, a source-fluid
change, and the recorded player's aim at that position. Ordinary fluid flow is
silent. Evaporation and interactions without an observable fluid change are not
reconstructed. Hurt sounds use vanilla's recorded damage-status events, including
burning, drowning and berry bushes, rather than guesses from health changes.
Fall impacts require a continuous recorded descent from the ground and a generic
damage event within 250 ms of landing. They include the small/big player impact
and landing-block sound. This is approximate: the protocol does not identify fall
damage separately from other generic damage. Missing movement, unusual landing
blocks and overlapping combat can prevent accurate classification. Water, flying,
climbing and slime landings are excluded; seeks and toggles clear fall evidence.
Crafting items remains silent, matching vanilla 1.16.1. Quick
Mode may omit action information, so it cannot reproduce every sound. This option
is intended for ZSG recordings; unrelated capture formats that already include
local-player audio may produce duplicate action sounds and should leave it off.

Forward/backward seeking is always silent, regardless of this option. Existing
and queued sounds are stopped, incoming seek sounds are discarded, and audio
resumes after the replay sender settles. This also covers Quick Mode changes.
Live gameplay and the recorder are unaffected.

## Maintenance And License

This new optional module is GPL-3.0-or-later; see LICENSE. It uses ReplayMod's
existing widget and camera APIs without copying its viewer implementation.
The rest of the repository's licensing is unchanged. Publish this module's
corresponding source and license with any distribution of its binary.

The integration is deliberately version-pinned: ReplayMod does not promise a
stable external API. Updating ReplayMod requires rebuilding and re-running the
viewer checks. Original editor widgets/layout are retained and restored instead
of replacing ReplayMod's playback engine.
