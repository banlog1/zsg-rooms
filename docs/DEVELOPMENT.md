# Development

## Toolchain

The project targets Minecraft 1.16.1 and Java 8 bytecode. The current Gradle
configuration uses Fabric Loom and Yarn mappings from `gradle.properties`.

Repository CI currently builds with a modern JDK while `JavaCompile.release`
remains 8. A JDK supported by the current Loom version is therefore recommended
for development; the release workflow uses JDK 21.

## Local Mod Dependencies

`build.gradle` uses every JAR under `libs/` as `modCompileOnly` and copies those
JARs into `run/mods/` before `runClient` and `build`.

The tracked development stack currently includes:

- `atum-2.7.2+1.16-1.16.1.jar`
- `FSG-Mod-5.3.0+MC1.16.1.jar`
- `speedrunapi-2.2+1.16-1.16.1.jar`

SpeedRunIGT is optional and accessed through reflection, so it is not required
to compile. Do not accidentally commit a local SpeedRunIGT JAR or other
instance-specific mods.

## Common Commands

Windows PowerShell:

```powershell
# Compile and run tests
.\gradlew.bat test

# Clean, test, and build the remapped mod JAR
.\gradlew.bat clean test build

# Launch the Fabric development client
.\gradlew.bat runClient
```

Build outputs are under `build/libs/`. Development instance files, logs, worlds,
and configuration are under `run/` and are ignored by Git.

## Test Layout

JUnit 5 tests are under `src/test/java`. Existing tests cover domain behavior,
seed metadata, timer formatting, game-rule helpers, room snapshots, protocol
encoding, and reconnect backoff.

Before committing:

```powershell
.\gradlew.bat clean test build
git diff --check
```

For mixin changes, also launch `runClient` because target method signatures and
runtime injection behavior cannot be proven by the unit tests alone.

## Relay Development

The relay is an ES module Cloudflare Worker with a SQLite-backed Durable Object.

```powershell
cd relay
npm.cmd install
npm.cmd run dev
```

Wrangler prints the local endpoint, normally `http://127.0.0.1:8787`. Enter
that endpoint in both local game clients, using a room code not already stored
by the local Durable Object state.

Syntax and dependency checks:

```powershell
node --check src/index.js
node --check scripts/smoke-reconnect.mjs
npm.cmd audit
```

The reconnect smoke test accepts a deployed HTTP/HTTPS Worker URL and converts
it to WS/WSS:

```powershell
npm.cmd run smoke -- https://your-relay.workers.dev
```

It creates a unique temporary room, connects host and guest, interrupts the
host with close code 1011, verifies the 60-second grace status, reconnects with
the original bearer token, and checks snapshot restoration.

## Deploying the Relay

```powershell
cd relay
npx.cmd wrangler login
npm.cmd run deploy
```

The Worker name, Durable Object binding, compatibility date, and migration are
defined in `relay/wrangler.jsonc`. See `relay/README.md` for the operator-facing
steps.

Deploying the Worker and releasing the mod are separate operations. Protocol
changes that require both sides should be deployed in a backward-compatible
order or coordinated as one release.

## Host Seed Prefetching

`HostSeedPrefetchManager` owns one host-only prepared or pending exact seed. It
keys that state by room name and the exact normalized seed specification, so
manual seed values and filter variants cannot share a result accidentally.

Every selection change advances a generation token and detaches the old pending
future. FSG work is allowed to finish when cancellation is unsafe, but its
completion callback checks the token and selection before changing manager
state. Relay and direct-socket launch paths also use a launch generation to
prevent duplicate or retired callbacks from starting a world.

The prepared value must remain outside `Room`, `InGame`, `RoomSnapshot`, relay
messages, UI strings, chat, and logs. The host starts its local Atum operation,
then commits the exact seed to synchronized state immediately before the
`launch` broadcast. After a successful launch, `onSeedConsumed` starts preparing
the following seed.

## Changing the Protocol

The current protocol is version 1 at the snapshot level. When adding a field:

1. Add it to `RoomSnapshot` with a backward-compatible default.
2. Capture it from host state.
3. Apply it in `ZsgRooms.applyRoomSnapshot`.
4. Include unit coverage for JSON round trips.
5. Decide whether guests may originate the related action and update the
   Worker's `GUEST_ACTIONS` whitelist if needed.
6. Verify reconnect restoration with `npm run smoke` when relay persistence is
   affected.

Do not trust guest-supplied `player` values in host-only state transitions. The
Worker replaces guest message identity with the player attached to that socket.

## Adding a Game Rule

A synchronized room rule normally touches:

1. `RoomGameRulesScreen` and `RoomSetupScreen` for host selection.
2. `ZsgRooms.createRoom` and `InGame` for state.
3. `RoomSnapshot` capture/apply for guests and reconnects.
4. `ZsgRooms.onServerStarted` for local integrated-server activation.
5. A focused helper/mixin for the actual Minecraft behavior.
6. `GameLogicTest` or a dedicated test class.

Keep local preferences such as HUD placement separate from host rules unless
they must be identical for competitive behavior.

## Adding a Seed Filter

Add the filter ID and label to `ZsgSeedBridge`, then add the ID to the selector
arrays in both `RoomSetupScreen` and `RoomOptionsScreen`. FSG's selected online
filter ID must exactly match the provider's ID.

## Updates and Releases

`UpdateManager` reads the latest public GitHub Release, selects the versioned
non-sources JAR, and verifies either GitHub's SHA-256 asset digest or the
uploaded `.sha256` file.

Follow [`RELEASING.md`](../RELEASING.md) for the tag-driven release workflow.
