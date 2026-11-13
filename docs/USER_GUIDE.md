# Player Guide

## Before Starting

Every racer needs a Fabric 1.16.1 instance with a compatible set of:

- ZSG Rooms
- Fabric API
- Atum
- FSG Mod
- SpeedrunAPI

SpeedRunIGT is optional. When present, ZSG Rooms reads its completed in-game
time through reflection and includes the value under the victory title.

Use the same versions on every machine. Mismatched world-generation or FSG
mods can make otherwise identical seeds behave differently.

## Opening ZSG Rooms

The mod adds a `ZSG Rooms` button to the Minecraft title screen. The first menu
contains:

- `Create Room`: host and own the authoritative room state.
- `Join Room`: connect to an existing room.
- Gear button: choose HUD position, ruined-portal repair, update checks, and seed debug logging.

## Guided Tutorial

On the first launch with tutorial support, choose **No**, **A quick one**, or
**Yes, full**. The choice is remembered for this instance. The quick tour covers
personal settings, creating/joining rooms, sharing a code, and room settings.
The full tour adds filters, rules, race formats, tournaments, recording and replays.

Try the highlighted category, rule preset, recording detail or camera menu to see
the available choices and an explanation of the selected option. Recording and
seed-retention checkboxes can also be tried. These practice selections stay within
the tour; use **Next** when you are ready to continue. Other highlighted controls
advance to their next step. **Back** revisits a step; the close button or Escape
leaves the tour (Escape closes an open choice menu first). This practice view never
connects to a room, starts a race, copies a real code, or changes your settings.
Reopen either tour through **ZSG Rooms > Settings > General > Guided tutorial**
while outside a world. Its choice is stored in `config/zsg-rooms-tutorial.properties`.

## Importing Settings

Use the small import-arrow button beside **ZSG Rooms** on the Minecraft title
screen (hover for **Import or save settings**), or go to
**ZSG Rooms > Settings > General > Import settings**. Importing is available
outside a world. Choose a nearby MultiMC/Prism instance or browse to an instance
folder (its `.minecraft` or `minecraft` folder also works).

The instance list shows saved MultiMC/Prism display names and Minecraft versions
when available. The launcher label is shown when its local config identifies it;
missing or ambiguous metadata is marked unknown. Hover a row to see the full
name, version and folder path, including when two instances share a name. The
selected instance stays identified during category selection and review.

The **Saved Settings** tab stores named snapshots of the same supported Minecraft
and StandardSettings options. **Save Current** first saves live Minecraft options;
the snapshot also captures saved StandardSettings overrides, including disabled
and world-join values. It is not a full backup of every mod's configuration.
Snapshots are individual JSON files under
`config/zsg-rooms-import/snapshots`, with names and saved dates in the list.
Open a snapshot to rename, export, delete, or **Review Restore**. **Import File**
adds an exported snapshot to the list without applying it. Restore uses the same
category preview, restart requirement, shared-settings confirmation, and undo
backup as an instance import. Saving or deleting snapshots does not replace the
automatic undo backup. Existing snapshots are never overwritten just because
their names match.

Select the categories to copy, then review the individual changes and skipped
options. The importer supports compatible Minecraft controls, mouse, video,
audio, chat/accessibility, language and appearance settings. Installed mods'
registered keybinds are included. Known compatible settings can be imported
from other Minecraft versions; unsupported values, old numeric key codes and
unknown settings are skipped, not guessed.

When StandardSettings is installed here, its supported JSON settings are
included in the corresponding categories. Enabled/disabled states and separate
world-generation/world-join values are preserved. Existing enabled reset
overrides are also updated when necessary so they do not undo an imported
Minecraft setting. Legacy `standardoptions.txt`, other mod-specific configuration
files, accounts, worlds, mod JARs, resource packs, launcher/JVM settings and
replay paths are not copied.

**Import on Next Launch** stages the changes; it does not alter live settings.
**Close the Minecraft instance completely and relaunch it from your launcher.**
Returning to the title screen, rejoining a world or reloading resources is not
enough. The confirmation screen offers **Close Minecraft**, **Later**, and
**Cancel Pending Changes**. Close the source instance before previewing if you
want its latest saved settings.

Imports read shared StandardSettings files without modifying them. If this
instance uses a shared file, importing StandardSettings requires confirmation
to switch this instance to a local copy. Other instances are unaffected.
Relative shared-file pointers must first be changed to absolute paths.

A backup is saved in `config/zsg-rooms-import` before changes are applied on
launch. **Undo Last Import** stages restoration for the next launch, including
any previous shared-file link. Undo restores the backed-up files in full, so
settings changed after the import are also reverted. Only the most recent
completed import can be undone. The importer reports its last result when
opened again and recovers interrupted imports from the backup.

## Creating a Room

`Create Room` connects immediately and opens your lobby; it does not start a
race. The connection can be cancelled. New rooms use **ZSG Rooms Mode**,
**Standard ZSG Rooms** rules with cheats off, **1 seed**, **1 finisher**,
**10 player places**, and tournaments off. Share the code with friends, then
change settings from the lobby before selecting `Start Race`.

The host can change these in lobby `Options` and `Game Rules`:

| Field | Meaning |
| --- | --- |
| Players | Maximum room size (2-64), never smaller than the current roster. Locked during races and active tournaments. The relay enforces the synchronized value. |
| Race | Opens Race Format. Seeds per race (1-20) and Finishers are independent; both default to 1. Hover either control for its rules. |
| Filter | Seed source used when the host starts the next race. |
| Manual | Shown only when `Manual Seed` is selected; accepts a Minecraft numeric or text seed. |
| Game Rules | Host-only gameplay modifications copied into the room snapshot. |

`Spawn Near Filter Structure` can move an unusually distant filtered route to
a deterministic safe spawn 24-48 blocks from its target whenever the original
distance exceeds 48 blocks. Shipwreck filters keep their original 140-block
threshold and 70-128-block target range. The loading screen prepares the final
destination. If no safe dry spot is found, the original spawn is retained.
It applies to filters with a known route structure, including ZSG Rooms bank
filters; manual, random, and room-code seeds are unchanged.

Room codes are hidden by default on both the join screen and in the lobby.
`Show` reveals the code locally, while `Copy` always copies the real code even
while it is concealed in the lobby. The join screen has show/hide and paste
buttons beside the code field.

The default relay requires no setup. To use your own relay, open `Connection
settings` from the join/error screen or `Settings > General > Room connection`
before creating a room. A hostname or HTTP(S)/WS(S) URL is accepted; bare
hostnames use HTTPS/WSS. `Use default` restores the built-in address, and
`Setup guide` opens the deployment instructions after confirmation.
The address is saved to `config/zsg-rooms-relay.txt` when applied or after a
successful connection. Everyone joining must use the same relay.

## Seed Sources

| UI label | Behavior |
| --- | --- |
| ZSG Mapless | Requests FSG filter ID `zsg`. |
| ZSG Mapless (OP) | Requests `zsgop`. |
| ZSG Village / Village (OP) | Requests `zsgvillage` or `zsgvillageop`. |
| ZSG Shipwreck / Shipwreck (OP) | Requests `zsgshipwreck` or `zsgshipwreckop`. |
| ZSG Desert Temple / Desert Temple (OP) | Requests `zsgtemple` or `zsgtempleop`. |
| ZSG Jungle Temple / Jungle Temple (OP) | Requests `zsgjungletemple` or `zsgjungletempleop`. |
| Ruined Portal Seedbank | Requests `rpseedbank` and enables the optional corruption repair path. |
| Random Seed | Host creates one local random `long` and shares it. |
| Room Code | Uses the room code as the Minecraft seed string. |
| Manual Seed | Uses the exact value entered in the Manual field. |

For an FSG filter, room creation stores a pending marker and the host immediately
starts privately preparing the next exact seed. The lobby can report
`Preparing next seed...`, `Next seed ready`, or a retry status, but the value is
not placed in room state or sent to the relay before launch. FSG requests are
retried up to three times and have an overall 75-second timeout.

## Joining a Room

Guests paste or enter the host's room code and select `Join`. There are no guest
filter or rule controls. After the WebSocket welcome, the host
sends a complete room snapshot containing players, seed/filter, rules, chat,
progress, and current loading state. The lobby opens only after that snapshot
includes the guest. A missing/full room is reported separately from a connection
problem. Retry, go back, or open connection settings as appropriate.

The UUID sent in the profile action is used to render the player's skin. It is
not repeatedly fetched as gameplay state.

## Lobby Controls

- `Room Chat`: type a message and press Enter. Hover the chat panel and use the
  mouse wheel to view older messages.
- `Mute` / `Unmute`: locally hides or restores another runner's typed chat and
  advancement notifications in the lobby and active run. Seed votes, forfeits,
  results, and other race-critical messages remain visible.
- `Options`: host-only filter, manual seed, race format, tournament, and player capacity settings.
- `Start Race`: host-only. Consumes the prepared seed, or waits for the
  remainder of the matching preparation, and launches it once.
- `Share Seed`: writes the current internal seed value to room chat.
- `Leave Room`: sends a clean leave action and closes the local room state.
- `Show` / `Hide`: controls whether the room code is visible locally.
- `Copy`: copies the real room code even while its display is hidden.

Advancements received during a race are also copied into room chat. Recipe
unlocks and advancements without display metadata are intentionally ignored.

## Synchronized Start

The start sequence is:

1. The host consumes its private prepared seed, reusing a matching request when
   one is still running.
2. The host starts its local Atum launch.
3. Immediately before sending `launch`, the host commits the exact seed to the
   synchronized room state and broadcasts it to every guest.
4. The host begins privately preparing the following seed.
5. Atum creates or resets each local singleplayer world with the launched seed.
6. Each client waits until its new world and player are usable.
7. The client opens the non-skippable `World Ready` screen and sends
   `world_ready`.
8. When every current room member is ready, the host sends `race_start`.
9. All waiting screens close and the HUD displays `GO!`.

Changing the selected filter invalidates the old private result. A late result
from the previous filter is ignored and cannot launch or replace the newly
selected seed.

If a player leaves while everyone is loading, the host re-evaluates the current
player list so the remaining players are not held forever.

## Personal Settings

The rooms menu gear opens five categories: General, HUD, Loading, Recording,
and Advanced. These are local preferences, separate from the host's room rules.
Wide screens have a category sidebar; narrow screens have a category menu.
Scroll the content or use Page Up/Page Down to reach additional settings.

Advanced contains the seed-bank service URL, replay-library setup, solo replay
testing, portal-repair testing, and debug logging. A gold `Testing active` button
remains visible in every category when repair testing, debug logging, or a saved
solo replay test group is enabled. It opens Advanced without disabling anything.

Switches take effect through their existing settings APIs. Service URLs, library
folder overrides and solo group IDs require their Save/Apply action. Unsaved
fields and category scroll positions survive navigation and window resizing
while Settings remains open. Hover a setting, including a disabled control, for
its effect and any availability restriction.

## In-Game HUD

The match HUD shows player skin heads, names, and latest tracked stages. Open
`Settings > HUD` from the rooms menu gear, or use the `HUD` button in
the in-game pause menu. These preferences only affect your own display.

- Appearance: show/hide the HUD, header, heads, and progress numbers; choose
  background opacity (0% removes the background) and size (75%-150%).
- Players: choose the corner, number of visible players, and rotation interval
  (2-10 seconds). Your row stays visible by default, with one opponent beneath
  it rotating every five seconds. Rooms that fit do not rotate. You can show up
  to four rows, or turn off the pinned row to rotate everyone together.
- Preview: `HUD preview > Preview...` opens the existing live editor. Changes
  appear immediately in its sample HUD. On narrow screens, use its Preview tab;
  short screens provide arrows and mouse-wheel paging.

The default corner remains top-right. Heads and stage updates for players not
currently visible are not removed from the room; they appear when their row
rotates into view. HUD settings do not mute chat or change race rules.

Tracked milestone labels include:

1. Starting Minecraft progression
2. Stone
3. Iron
4. Entered Nether
5. Found Fortress or Entered Bastion
6. Found Stronghold
7. Entered End
8. Dragon Defeated

These labels are race context, not the victory condition.

## Nether Entry Warmup

When the host enables `Preload Nether Entry Chunk`, entering an Overworld Nether
portal prepares the projected destination chunk through Minecraft's generation
pipeline while the normal portal charge is already running. Terrain is prepared
first; the chunk is promoted to fully loaded only when enough charge time and
server headroom remain. Temporary tickets are released after leaving the portal
or completing the transition.

Each player can disable `Nether Entry Warmup` in Room Settings for their own
Minecraft instance. The optimization runs only when the host's room rule and
the player's local preference are both enabled.

Warmup does not add a second loading barrier and never delays dimension travel
when preparation is incomplete. Existing destination portals can be farther
from the projected coordinate, so previously linked portals may receive less
benefit than a first Nether entry.

## Pause Menu Controls

While a room race is active, ZSG Rooms replaces `Save and Quit`/`Disconnect`
with room-aware controls:

- `Forfeit`: ends a multiplayer match and awards the first remaining player.
- `Reset Run`: resets only your local world on the same seed and reports the
  reset to room chat. Other players continue uninterrupted.
- `New Seed`: records your vote. A new seed is requested and launched
  automatically only after every current player has voted.
- `Room`: solo-room replacement for Forfeit; immediately returns to the lobby.

Atum reset requests that do not come from one of these authorized room actions
are blocked while the room controls the run. The F3 debug overlay is closed
before world generation to avoid the known reset-time crash path.

## Tournaments

Open **Options > Tournament** in the room. Check **Tournament** (default off).
Select **Bracket** for single elimination or **Points** for cumulative scores.
Hover the controls for the rules.

Bracket matches are best of 1, 3, 5 or 7 races. Each race can still contain
multiple seeds; finishing one seed is not a match win. Bracket races use one
qualifying finisher. A draw awards neither player a win and repeats the race
with the selected seed source. Manual seeds stay fixed.

Under **Players and Byes**, arrange the opening order and select exactly the
required number of byes. Agree the recipients with the players before starting.
There are no random bye assignments. **Standings / Bracket** previews the opening
matches. Bye recipients advance one round without receiving race wins.

The bracket shows connected rounds through to the final, with scores and winner
highlights. Drag to pan, use **+ / -** or Ctrl-scroll to zoom, and **Fit** to see
the entire bracket. The scrollbars and arrow keys also pan; Shift-scroll moves
horizontally. Hover a match for full player names and its status. Points standings
use a ranked table with scores and withdrawal status.

For points, set the round count and a comma-separated list of placement points.
The room's **Finishers** setting controls how many players may finish each round.
Unlisted places, unplaced players and DNF score zero. Equal race times share
their place's points, with subsequent places skipped. Equal tournament totals
share a final place; there is no hidden tiebreaker.

The host starts each race or next match from the lobby. Only the current bracket
pair loads the world. The host can run the room while waiting for their own match.
Open **Tournament** (**Event** in compact layouts) for standings, the bracket,
setup and the last race's results. Return to Room stays in the event. Forfeit
or Leave Race affects the current race only; Leave Room withdraws from all
remaining matches or rounds. Points already earned are retained.

Rules, filter and starting roster lock when the host starts the tournament.
New arrivals wait until the next event. Between races the host can use
**End / Reset** and confirm to clear progress and unlock setup. Keep the host's
game open; tournaments cannot resume after restarting Minecraft.

## Winning and Returning

Killing the dragon updates progress, but it does not finish the match. Victory
is detected from Minecraft's `GAME_WON` event when the player enters the End
exit portal. The local winner's result waits until the player has returned to
the Overworld and left the credits/loading screen.

For sequence-capable rooms, the result shows **Finished!** and your total race
time with your current placement. Other runners keep playing. **Standings** in
the lobby remains provisional until the finisher limit is settled or everyone
finishes or withdraws. The default limit is one; increase **Finishers** in
**Race Format** to allow more placements. Unfinished runners are unplaced when
the cutoff is reached. A sequence
skip adds 30 minutes to the total and advances one stage; leaving is a DNF.
The first seed starts together; subsequent loading counts toward total time.
Single-seed forfeits lose the race with no time penalty. With the default
one-finisher limit, the last remaining runner wins if all opponents forfeit.
For multi-seed penalties, a finisher does not close the race while another
runner can still beat their adjusted time.

Legacy rooms using the previous finish protocol show:

- `Victory!` for the local winner, or `<name> Wins!` for other players.
- `Final IGT: <time>` when SpeedRunIGT returned a time.
- `Seed completed` when SpeedRunIGT is unavailable.

Relay and direct-socket rooms compare **local elapsed race duration**, not
absolute portal-entry times, IGT, or packet arrival order. ZSG Rooms starts its
own monotonic clock on the first simulation tick after the local waiting gate
releases and stops it at the integrated-server End exit event. A guest whose
start message arrives later does not have that initial network wait added to
their race duration. Pauses, lag, dimension loading, and individual world
resets after starting remain part of elapsed time. They do not restart the clock.

The lower duration wins, compared at nanosecond precision. Only exactly equal
recorded durations produce `Draw!`; there is no 50 ms margin. Nanosecond storage
does not mean start/event capture is physically accurate to a nanosecond. IGT
remains display/history-only, and is never used as a fallback race clock.

In legacy rooms only, if another current player has already reported `Free the End`, the host waits
two seconds after the first completion report for close finishes. Otherwise
the result is finalized on the next host client tick without that wait. A late
dragon-defeat report can bypass this window, and finish reports arriving after
the window cannot change the result. Long stalls/disconnects can still matter.

All players must use the elapsed-time version; older completion formats are
rejected rather than compared using incompatible clocks. A missing local
start/finish capture produces an explicit warning and no completion submission.
This requires a local integrated-server world. Existing relay Workers already
forward the new completion payload without a deployment. The legacy Minecraft
packet-only fallback does not perform multi-player finish arbitration.

After about 90 client ticks, every player returns to the room lobby.

## Relay Disconnects

The mod sends a WebSocket protocol ping every 10 seconds and treats 30 seconds
without a pong as a dead connection. It retries after 1, 2, 4, 8, and then
15-second delays, within a total 60-second reconnect window.

During that window:

- The Worker retains the room and snapshot.
- Up to 128 local actions are queued by the mod.
- A returning host reuses its bearer token and reclaims the room.
- Guests see relay status text instead of being removed immediately.

A deliberate `Leave Room` remains immediate. If the host does not return within
60 seconds, the Worker closes the room.

## Updates

On the title screen, the mod checks the repository's latest GitHub Release when
update checks are enabled. An update can be downloaded, skipped, or postponed.
The JAR must have a matching SHA-256 digest. After download, select
`Close This Instance`; the helper replaces the old mod JAR after Minecraft
exits.

Update checks are optional and can be disabled in Room Settings.

## Local Configuration Files

| Path | Purpose |
| --- | --- |
| `config/zsg-rooms-relay.txt` | Applied or last successful relay address. |
| `config/zsg-rooms-ui.txt` | Match HUD corner. |
| `config/zsg-rooms-hud.properties` | Local HUD appearance and player rotation preferences. |
| `config/zsg-rooms-rp-repair.txt` | Ruined-portal repair preference. |
| `config/zsg-rooms-seed-debug.txt` | Verbose seed diagnostics preference. |
| `config/zsg-rooms-update-url.txt` | Optional replacement GitHub latest-release API URL. |
| `config/zsg-rooms/update/` | Pending verified update files. |
| `zsg-rooms-seed-detection.log` | Seed bridge and FSG diagnostics when Seed Debug Logging is enabled. |

The relay can also be overridden with the `zsgrooms.relay` system property or
`ZSG_ROOMS_RELAY` environment variable.

## Troubleshooting

### Relay rejects the connection

- Confirm both players entered the same hostname and room code.
- A bare hostname such as `example.workers.dev` is valid; do not add a path.
- A room code already owned by another live host returns a conflict.
- The host must connect before a new guest.

### FSG returns no seed

- Confirm Atum, FSG Mod, and SpeedrunAPI are loaded.
- Enable Seed Debug Logging in Room Settings, reproduce the request, then open
  `zsg-rooms-seed-detection.log` for the request attempts and exception.
- FSG empty results are retried three times; wait for the final lobby status.

### Players remain on World Ready

- Check the ready count and room chat to identify the client still loading.
- Confirm everyone is using the same seed-generation mod versions.
- Wait for relay reconnection if the lobby/HUD reports an interruption.

### Nether portal does not transfer

- Enable Seed Debug Logging in Room Settings before entering the portal.
- Reproduce the first-entry failure, then provide the MultiMC console output or
  `logs/latest.log` lines containing `[ZSG-Rooms/NetherWarmup]`.
- The diagnostics report warmup and vanilla-transfer state without logging the
  numeric seed or destination coordinates.

### Update downloads but does not install

- Updates can only replace a packaged mod JAR, not an IDE classes directory.
- Close the entire Minecraft instance using the update screen button.
- Confirm the launcher allows the instance's `mods` directory to be modified.
