# Technical Overview

## System Boundary

ZSG Rooms has two cooperating systems:

1. A Fabric client/integrated-server mod on every runner's machine.
2. A Cloudflare Worker and one Durable Object instance per room code.

The mod owns world creation and all gameplay. The relay owns connection routing
and temporary room persistence. It does not run Minecraft logic.

## Host Authority

The host's mod instance is authoritative for:

- The room snapshot and player list.
- Filter and game rules.
- Exact seed requests.
- Seed-change vote completion.
- Synchronized start release.
- Progress aggregation and match result.

Guests send a restricted action set to the Worker. The Worker forwards those
actions only to the host. The host validates and applies them, then broadcasts
an action or a new snapshot.

## Wire Format

Every relay message is a JSON object with four string fields:

```json
{
  "type": "chat",
  "room": "ZSG-ABCD2345",
  "player": "RunnerName",
  "value": "hello"
}
```

The Worker rejects messages larger than 256 KiB. Player identities are trimmed
and limited to 32 characters. Room paths accept 4-64 letters, digits,
underscores, or hyphens.

Important message types are:

| Type | Direction | Purpose |
| --- | --- | --- |
| `welcome` | Worker to client | Confirms the WebSocket role and room. |
| `join_room` | Worker to host | Requests admission of a connected guest. |
| `profile` | Guest to host | Supplies UUID for skin rendering. |
| `snapshot` | Host to Worker/guests | Replaces synchronized room state. |
| `chat` | Either player through host | Adds one room chat line. |
| `filter` | Host | Changes the next seed specification. |
| `start` | Host | Begins exact seed acquisition. |
| `launch` | Host to guests | Launches the exact shared seed. |
| `world_ready` | Client to host | Reports that a local world is playable. |
| `race_start` | Host to guests | Releases synchronized start screens. |
| `advancement` | Client to host | Shares a displayed, non-recipe advancement. |
| `progress` | Client to host | Supplies an explicit numeric progress stage. |
| `reset_run` | Client to host | Resets one player's progress on the same seed. |
| `seed_change` | Client to host | Records a new-seed vote. |
| `seed_change_vote` | Host to guests | Announces an individual vote. |
| `seed_change_ready` | Host to guests | Announces unanimous agreement. |
| `complete_run` | Winner to host | Reports End exit portal completion. The Worker binds the message to the guest's socket identity. |
| `forfeit` | Client to host | Concedes the current match. |
| `match_result` | Host/Worker to guests | Carries winner and reason separated by a tab. |
| `leave_room` | Client to host | Removes a room member. |
| `relay_status` | Worker to guests | Reports host interruption or recovery. |

The guest whitelist in `relay/src/index.js` prevents guests from directly
sending host-only controls such as `start`, `filter`, `snapshot`, or
`match_result`.

## Room Snapshot

`RoomSnapshot` protocol version 1 serializes:

- Room name, host, maximum players, and player UUID/profile state.
- Seed, normalized filter, game rules, and `inGame` state.
- Chat history.
- Progress values and labels.
- Ready-player set and synchronized-start release state.
- Seed-change request flags.

The Worker stores the latest snapshot in Durable Object storage. A guest gets
the host's current snapshot after admission. A reconnecting host receives the
stored snapshot and then publishes a fresh one from local state.

## Seed Lifecycle

### FSG filter

1. Room creation stores `pending-<filter>` plus internal metadata.
2. On start, the host configures `FSGModConfig.selectedOnlineFilters`, filter
   display name, and practice mode.
3. The host initializes Atum's running/reset flags and invokes
   `SeedManager.runFilter()` on a dedicated seed-request executor.
4. Empty or failed results are retried three times with 750 ms and 1,500 ms
   waits. The complete request times out after 75 seconds.
5. The exact result is wrapped as
   `<minecraft-seed>|structure:<filter>|iron:4` for local metadata.
6. The host snapshots and broadcasts that value, then sends `launch`.
7. Every client extracts only the Minecraft seed portion and installs a
   one-shot Atum `SeedProvider`.

### Random, room-code, or manual seed

These sources resolve immediately on the host. They still follow the same
snapshot, launch, local world creation, and synchronized-ready flow.

### Reset and new seed

`Reset Run` launches the current exact seed only on the requesting client. A
unanimous `New Seed` vote makes the host repeat exact seed acquisition and
automatically launches the result for everyone without returning to the lobby.

## Atum Control

`ZsgSeedBridge` accesses Atum and FSG through reflection because their APIs are
not stable compile-time interfaces for this project. `OneShotSeedProvider`
returns the room seed once and restores Atum's previous provider immediately.

`AtumResetGuardMixin` cancels arbitrary `scheduleReset()` calls while a room is
managed. `RoomResetAuthorization` grants one atomic permission immediately
before an intentional room reset. The mod also disables the debug overlay
before generation.

## Relay Connection Lifecycle

`SimpleWebSocketClient` implements the client-side RFC 6455 upgrade and frame
handling without a runtime WebSocket dependency.

- TCP keepalive is enabled.
- A protocol ping is sent every 10 seconds.
- The last pong must be newer than 30 seconds.
- Old socket callbacks include their source instance and are ignored after a
  replacement socket is installed.

`RoomWebSocketTransport` reconnects for 60 seconds with delays of 1, 2, 4, 8,
and then 15 seconds. It queues at most 128 actions while disconnected. The host
keeps the same random 32-byte bearer token for each reconnect attempt.

The Worker uses Cloudflare's WebSocket hibernation API and automatic
`ping`/`pong` responses. Abnormal closes create a 60-second Durable Object alarm
deadline. Clean close code 1000 removes the client immediately.

If the host returns during grace:

1. The Worker verifies the original bearer token.
2. Guests remain connected.
3. The host receives the stored snapshot.
4. Guests receive `Host reconnected`.
5. The mod sends its authoritative snapshot and flushes queued actions.

If the host grace expires, guests are closed and Durable Object room storage is
deleted. In a running two-player match, the remaining guest can receive a match
result before closure.

## Security and Privacy

- Connections use WSS when the configured endpoint is HTTPS or a bare
  hostname.
- A random host bearer token prevents a second host from claiming a live or
  reconnectable room code.
- Guest admission uses room code plus player name; room codes should be shared
  only with intended participants.
- Messages are not end-to-end encrypted beyond TLS.
- UUIDs are synchronized for skin rendering.
- No world chunks, coordinates, inventory contents, keystrokes, or movement are
  sent by the relay protocol.

## Local Fallback Transports

`RoomSocketTransport` contains a legacy plain TCP JSON transport on port 27181,
and `ZsgRoomNetworking` can broadcast room actions through Fabric packets when
clients share a Minecraft server. The current Create/Join UI uses
`RoomWebSocketTransport`; the Cloudflare relay is the supported Internet path.

## Main Source Ownership

| Area | Main classes/files |
| --- | --- |
| Domain state | `Room`, `Player`, `InGame`, `ZsgRooms` |
| Client lifecycle | `ZsgRoomsClient` |
| Seed/FSG/Atum bridge | `ZsgSeedBridge`, `RoomResetAuthorization` |
| Relay client | `RoomWebSocketTransport`, `SimpleWebSocketClient`, `RoomProtocol` |
| Persistent wire state | `RoomSnapshot` |
| Relay service | `relay/src/index.js` |
| Lobby/setup UI | `RoomMenuScreen`, `RoomSetupScreen`, `RoomLobbyScreen` |
| Race UI | `MatchHud`, `SynchronizedStartScreen`, `ZsgInGameActions` |
| Game rules | `RngStandardization`, `BastionIronGuarantee`, `BastionZombifiedPiglinControl` |
| RP repair | `RuinedPortalChestRepair` and ruined-portal mixins |
| Completion/progress | `GameCompletionMixin`, `ClientAdvancementManagerMixin` |
| Updates | `UpdateManager`, `UpdateScreen`, `UpdaterHelper` |

Mixin registrations are listed in `src/main/resources/zsg-rooms.mixins.json`.
