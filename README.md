# ZSG Rooms

ZSG Rooms is a Fabric mod for Minecraft Java Edition 1.16.1 that runs private,
synchronized filtered-seed races. Every runner plays in a local singleplayer
world. A small Cloudflare Worker relay synchronizes the room, shared seed,
chat, loading state, progress, votes, and match result.

This is not a Minecraft multiplayer server. World chunks, inventories, entity
state, and player movement never pass through the room relay.

## What It Does

- Creates or joins Internet-accessible rooms with a short room code.
- Privately prepares the host's next filtered seed through FSG Mod, then
  launches that exact seed through Atum for every player.
- Supports the ZSG Mapless, Village, Shipwreck, Desert Temple, Jungle Temple,
  and Ruined Portal Seedbank filters, including OP variants where available.
- Also supports random, room-code-derived, and manually entered seeds.
- Holds loaded players behind a synchronized start screen until every runner is
  ready.
- Can warm the projected Nether destination chunk during the normal portal
  charge to reduce first-entry generation and lighting stalls.
- Shares room chat, selected advancements, race progress, player names, UUIDs,
  and skins.
- Adds in-game controls for forfeiting, resetting the current run, requesting a
  new seed, or returning to a solo room.
- Ends a run when the winner enters the End exit portal and displays SpeedRunIGT
  time when that mod is installed.
- Offers optional race rules for deterministic RNG, boosted barters, minimum
  bastion iron, cheats, structure-proximity spawning, and removing zombified
  piglins inside bastions, plus Nether entry warmup.
- Repairs known ruined-portal chest and obsidian corruption for RP seedbank
  runs when the local repair preference is enabled.
- Adds a confirmed `Clear Speedrun Worlds` action for worlds whose name starts
  with `Set Speedrun #`.
- Checks GitHub Releases for optional, SHA-256-verified updates.

## Requirements

| Component | Version or role |
| --- | --- |
| Minecraft Java Edition | 1.16.1 |
| Java | 8 or newer |
| Fabric Loader | 0.19.3 or newer |
| Fabric API | Required |
| Atum | Required to create and reset race worlds |
| FSG Mod | Required for filtered seeds |
| SpeedrunAPI | Used by the FSG provider stack |
| SpeedRunIGT | Optional; adds final IGT to the victory result |

All racers should use the same Minecraft, loader, ZSG Rooms, Atum, FSG Mod,
and SpeedrunAPI versions. The versions used by this project are recorded in
[`gradle.properties`](gradle.properties) and the tracked JAR names under
[`libs/`](libs/).

## Install

1. Create or select a Fabric 1.16.1 instance.
2. Install Fabric API, Atum, FSG Mod, and SpeedrunAPI in that instance.
3. Download the latest `zsg-rooms-<version>.jar` from
   [GitHub Releases](https://github.com/banlog1/zsg-rooms/releases).
4. Place the JAR in the instance's `mods` folder.
5. Give every racer the same mod set.

SpeedRunIGT can be installed separately if final in-game time should appear on
the victory screen.

## Quick Start

### Host

1. Open Minecraft and select `ZSG Rooms` on the title screen.
2. Select `Create Room`.
3. Leave the relay at its default or enter your own deployed Worker hostname.
4. Pick a filter and open `Game Rules...` if the defaults should change.
5. Create the room and share the displayed room code.
6. Wait for the other runners, then select `Start Race`.

### Guest

1. Open `ZSG Rooms` and select `Join Room`.
2. Enter the same relay hostname and room code as the host.
3. Join the lobby. No port forwarding, VPN, or tunnel application is required.

The host requests the seed. Each client loads that same seed locally, reports
`world_ready`, and remains paused until the room releases everyone together.

## Documentation

- [Documentation index](docs/README.md)
- [Player guide](docs/USER_GUIDE.md)
- [Game rules and world modifications](docs/GAME_RULES.md)
- [Technical overview and relay protocol](docs/TECHNICAL_OVERVIEW.md)
- [Development setup](docs/DEVELOPMENT.md)
- [Release process](RELEASING.md)
- [Relay deployment](relay/README.md)

## Build From Source

On Windows:

```powershell
.\gradlew.bat clean test build
.\gradlew.bat runClient
```

The packaged mod is written to `build/libs/`. `runClient` copies the tracked
development mod JARs from `libs/` into `run/mods/`.

See [Development](docs/DEVELOPMENT.md) before changing dependencies, mixins, or
the relay.

## Current Limitations

- `Series Goal` is stored and synchronized, but the current match flow still
  ends after one completed run. A first-to-N scoreboard is not implemented yet.
- The host is authoritative for room state and seed selection. If the host
  cannot reconnect within 60 seconds, the relay closes the room.
- RNG standardization makes equivalent event sequences deterministic. Different
  player actions or a different kill/barter order can still produce different
  later outcomes.
- Room chat uses TLS in transit through the relay, but it is not end-to-end
  encrypted and should not be treated as private messaging.

## License

ZSG Rooms is available under the [CC0 1.0 license](LICENSE).
