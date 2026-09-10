# ZSG Rooms Documentation

This directory describes the behavior that is currently implemented in the
mod. It deliberately distinguishes finished features from stored fields and
future-facing UI that do not yet have complete gameplay logic.

## For Players

- [Player guide](USER_GUIDE.md): installation, creating and joining rooms,
  controls, updates, configuration files, and troubleshooting.
- [Game rules](GAME_RULES.md): exact behavior of each rule, progress tracking,
  victory detection, ruined-portal repair, and world cleanup.

## For Contributors and Relay Operators

- [Technical overview](TECHNICAL_OVERVIEW.md): state ownership, seed lifecycle,
  relay messages, reconnect behavior, privacy boundaries, and key classes.
- [Development](DEVELOPMENT.md): local dependencies, Gradle commands, relay
  commands, tests, and source layout.
- [Release process](../RELEASING.md): version tags and GitHub Releases.
- [Relay deployment](../relay/README.md): deploying the Cloudflare Worker and
  Durable Object.

## Design Proposals

These documents cover planned work and opt-in development prototypes, not
finished player-facing features.

- [Replay foundation decision](REPLAY_FOUNDATION.md): selected playback and file
  infrastructure, inspected versions, integration boundaries, and remaining
  compatibility checks.
- [Replay development prototype](../replay-prototype/README.md): isolated library
  tests, opt-in live recording, automated capture checks, and remaining limits.
- [Replay viewer companion](../replay-viewer/README.md): optional bottom playback
  controls, camera modes, setup, and in-game UI checks.
- [Multi-player replay coordination](REPLAY_COORDINATION.md): planned race-time
  manifests, manual sharing, and switching between racers' recordings.

## Supported Baseline

- Minecraft 1.16.1
- Fabric Loader 0.19.3+
- Fabric API
- Java 8 bytecode
- Atum, FSG Mod, and SpeedrunAPI for the full filtered-seed flow

The root [README](../README.md) is the short project overview. These pages are
the detailed source of truth.
