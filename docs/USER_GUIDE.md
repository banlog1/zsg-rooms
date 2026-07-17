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
- Gear button: choose HUD position, ruined-portal repair, and update checks.

## Create Room Fields

| Field | Meaning |
| --- | --- |
| Room | Generated room code. Share this exact code with guests. |
| Relay | Cloudflare Worker hostname or URL. A bare `workers.dev` hostname is accepted; the mod adds HTTPS/WSS automatically. |
| Players | Maximum room size. The relay enforces the synchronized value. |
| Series Goal | Stored and synchronized first-to-N target. The current implementation still ends each match after one completed run. |
| Filter | Seed source used when the host starts the next race. |
| Manual | Enabled only for `Manual Seed`; accepts a Minecraft numeric or text seed. |
| Game Rules | Host-only gameplay modifications copied into the room snapshot. |

The `Copy` button copies the room code. The relay hostname is saved to
`config/zsg-rooms-relay.txt` after a successful connection.

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

For an FSG filter, room creation initially stores a pending marker. The host
does not request the exact seed until `Start Race` or until every player agrees
to a seed change. FSG requests are retried up to three times and have an overall
75-second timeout.

## Joining a Room

Guests enter the same relay hostname and room code. The guest's filter and game
rule controls do not override the host. After the WebSocket welcome, the host
sends a complete room snapshot containing players, seed/filter, rules, chat,
progress, and current loading state.

The UUID sent in the profile action is used to render the player's skin. It is
not repeatedly fetched as gameplay state.

## Lobby Controls

- `Room Chat`: type a message and press Enter. Hover the chat panel and use the
  mouse wheel to view older messages.
- `Options`: host-only selection for the filter used by the next seed.
- `Start Race`: host-only. Requests and launches one shared seed.
- `Share Seed`: writes the current internal seed value to room chat.
- `Leave Room`: sends a clean leave action and closes the local room state.
- `Copy`: copies the room code shown in the upper-right area.

Advancements received during a race are also copied into room chat. Recipe
unlocks and advancements without display metadata are intentionally ignored.

## Synchronized Start

The start sequence is:

1. The host requests one exact seed.
2. The host stores it in the room snapshot and sends `launch` to every guest.
3. Atum creates or resets each local singleplayer world with that seed.
4. Each client waits until its new world and player are usable.
5. The client opens the non-skippable `World Ready` screen and sends
   `world_ready`.
6. When every current room member is ready, the host sends `race_start`.
7. All waiting screens close and the HUD displays `GO!`.

If a player leaves while everyone is loading, the host re-evaluates the current
player list so the remaining players are not held forever.

## In-Game HUD

The match HUD shows each player's skin head, name, and latest tracked stage. Its
position can be set to any screen corner from the gear button on the main rooms
menu. The default is top-right.

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

## Winning and Returning

Killing the dragon updates progress, but it does not finish the match. Victory
is detected from Minecraft's `GAME_WON` event when the player enters the End
exit portal. The local winner's result waits until the player has returned to
the Overworld and left the credits/loading screen.

The result shows:

- `Victory!` for the local winner, or `<name> Wins!` for other players.
- `Final IGT: <time>` when SpeedRunIGT returned a time.
- `Seed completed` when SpeedRunIGT is unavailable.

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
| `config/zsg-rooms-relay.txt` | Last successful relay hostname. |
| `config/zsg-rooms-ui.txt` | Match HUD corner. |
| `config/zsg-rooms-rp-repair.txt` | Ruined-portal repair preference. |
| `config/zsg-rooms-update-url.txt` | Optional replacement GitHub latest-release API URL. |
| `config/zsg-rooms/update/` | Pending verified update files. |
| `zsg-rooms-seed-detection.log` | Seed bridge and FSG diagnostics for the current launch. |

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
- Open `zsg-rooms-seed-detection.log` for the request attempts and exception.
- FSG empty results are retried three times; wait for the final lobby status.

### Players remain on World Ready

- Check the ready count and room chat to identify the client still loading.
- Confirm everyone is using the same seed-generation mod versions.
- Wait for relay reconnection if the lobby/HUD reports an interruption.

### Update downloads but does not install

- Updates can only replace a packaged mod JAR, not an IDE classes directory.
- Close the entire Minecraft instance using the update screen button.
- Confirm the launcher allows the instance's `mods` directory to be modified.
