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

# Run the full-period headless dragon-perch benchmark
.\gradlew.bat runPerchBenchmark
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

### Headless Dragon Perch Benchmark

`runPerchBenchmark` launches a headless development server and runs paired
trials from dragon age zero until the dragon actually perches. Each pair uses
the same initial dragon RNG, stationary fountain player, crystal count, End
terrain, and starting pose. It compares fully vanilla behavior with the current
1,300-tick vanilla grace period followed by deterministic perch decisions.

```powershell
# Default: 10 paired trials with all 10 crystals alive
.\gradlew.bat runPerchBenchmark

# Faster smoke run, or a different fixed crystal count
.\gradlew.bat runPerchBenchmark -PperchTrials=10 -PperchCrystals=0

# Reproduce a trial sample or raise the timeout
.\gradlew.bat runPerchBenchmark -PperchSampleSeed=123456789 -PperchMaxTicks=18000

# Testing comparison: current 1,300-tick grace versus no grace
.\gradlew.bat runPerchBenchmark -PperchCompareGrace=true
```

Each trial begins with a synthetic player entering the End. The benchmark lets
vanilla `EnderDragonFight` detect that player and create the dragon naturally,
then reports the natural spawn delay, the tick where `LANDING_APPROACH` begins,
and the tick where the dragon reaches a sitting phase. All timings are measured
from End entry and include mean, median, p90, p95, p99,
completed trials, timeouts, and a paired second-mode-minus-first-mode delta. This is a
CPU-heavy simulation of complete dragon ticks; progress is logged every 500
simulated ticks, and larger samples may take a long time. Its run directory is
`run/perch-benchmark`; set `eula=true` there before the first dedicated-server
run after reviewing Mojang's EULA. The no-grace override exists only inside the
benchmark process; normal `RngStandardization.configure(...)` calls restore the
production 1,300-tick grace.

### Nether Natural Spawn Standardization

When RNG standardization is enabled, every natural `MONSTER` spawn cycle in the
Nether receives deterministic, event-indexed random streams for initial
positions, attempt offsets, spawn-entry selection, pack size, and random
spawn-restriction checks. The section key contains the dimension, spawn group,
and chunk coordinates; each invocation advances only that section's cycle
index. Each of vanilla's three outer pack passes receives fresh attempt-count,
offset, selection, and spawn-check streams. A shortened or rejected earlier
pack therefore cannot shift the random sequence used by a later pack in the
same cycle. Spawn-restriction RNG is also isolated by attempt index within each
pack and initialized only when that attempt reaches a random restriction check.
Skipping a check, or consuming extra rolls in an earlier check, cannot shift
later attempts. Multiple rolls within one attempt still advance normally.
Standardizing surrounding Nether chunks also makes the natural mob
population feeding vanilla's mob cap more consistent between equivalent runs.

Minecraft still performs the exact-position fortress lookup and therefore still
chooses between its biome and fortress spawn tables normally. Biome and fortress
spawn tables, weights, environment checks, and entity RNG after creation remain
vanilla. The initial fortress protection described below is the sole new
global-cap exception; no extra spawn pass is scheduled.
Each replaced roll also consumes and discards the corresponding vanilla RNG
result so unrelated `world.random` advancement is preserved as closely as the
same branch path permits. Natural fortress spawning is independent from the
existing deterministic blaze block-spawner implementation.

With Seed Debug Logging enabled, natural-spawn diagnostics are limited to
fortress-table terrain: a generated fortress piece, or nether bricks beneath
the candidate inside the fortress's overall bounds. They include the chunk,
cycle, pack and attempt indexes; entry selection; environment, density and
entity-validity checks; and each mob accepted into the world. Biome attempts are
omitted to keep MultiMC logs manageable, and all diagnostics are skipped when
debugging is disabled.

Rejection diagnostics append `reason` and the world `tick` to environment and
entity checks. They observe which vanilla predicate is reached before a failure;
they never re-run random spawn predicates. `early-rejection` records missing
eligible players, the 24-block player/world-spawn exclusion, and candidate chunks
that are not ticking. Reasons distinguish table mismatches, terrain/fluid/border
restrictions, spawn-restriction predicates, bounding-box collisions, entity spawn
rules, entity space/fluid checks, and immediate-despawn distance. Density failures
are labeled `biome_spawn_density`. `rejection-details` adds supporting blocks,
light, bounds, fluid and block-collision observations, plus up to three overlapping
spawn-blocking entities. These are observations after the failure, not extra
spawn attempts or a re-evaluation of its random roll.

For loaded chunks referencing a fortress, `cap-summary` observes the original
monster-cap result before a cycle starts. It reports on first observation, cap
pass/fail transitions, and every 100 world ticks while observed. It includes the
monster count, calculated cap, spawning-chunk count, admitted/blocked calls since
the last report, initial-position skips (below world or solid), and the last
observed cycle. Initial-position counters are recorded after admission, so a
summary at the cap check describes prior cycles. `cycle=not_started` means this
cap check precedes cycle allocation; it does not increment the RNG counter.
These summaries use existing chunk structure references and do not locate or
generate a fortress. They do not run for chunks the vanilla scheduler never
visits, or when earlier spawn flags prevent the cap check; missing summaries
alone therefore do not prove a mob-cap rejection. All these diagnostics require
both RNG standardization and Seed Debug Logging to be enabled.

### Initial Fortress Opportunity Protection

`FortressSpawnProtectionState` reserves eight initial fortress-table **pack
selections**, shared by all referencing chunks of that fortress. Its key is the
generated structure-start chunk in the Nether world. State is saved in
`DIM-1/data/zsg_rooms_fortress_opportunities.dat`; ordinary unload/reload does
not replenish it. A fresh race/reset world starts with fresh state.

`SpawnHelperMixin` still evaluates the original cap predicate exactly once.
When it denies a Nether monster cycle, a generated fortress reference with
remaining allowance can admit that cycle. `method_29950`'s returned list is
observed to identify the actual vanilla fortress table, without replacing its
weights or rerolling selection. Selection consumes one opportunity before
validation, whether the cap originally allowed or denied the cycle. Repeated
candidate checks within a pack do not consume more opportunities. A protected
eighth pack can finish; a ninth pack cannot borrow its exception.

Only a pack selected from that protected fortress can use the cap exception.
`containsSpawnEntry` additionally rejects candidates that drift into a biome or
another fortress during a bypassed pack. Cap-admitted packs retain vanilla
cross-boundary behavior. All remaining predicates and the original population
runner stay in place, so successful mobs count toward the global population.
After eight selections, the exception ends, not natural fortress spawning.

For reproducible allowance allocation, `ServerChunkManagerFortressMixin`
snapshots the eligible, already-ticking fortress chunks after vanilla's shuffle.
`FortressSpawnOrder` dispatches their monster-spawn slots in chunk X/Z order.
It does not reorder the chunk-tick list, ordinary biome slots, other groups or
random block ticks, and never loads chunks or creates an additional spawn pass.
The mapping lasts until the end of that tick, including when the eighth
opportunity is consumed midway through it. Different eligible chunk sets and
player-dependent checks can still produce different outcomes.

`MAX_INITIAL_CYCLES = 4096` bounds unsuccessful searches per fortress as well as
the eight-selection budget. Each admitted referencing chunk evaluation consumes
one cycle, including initial-solid/below-world failures; several chunks can
consume several cycles in one tick. The final granted evaluation may finish.
The cycle budget is persisted too. Both constants are provisional playtest
values, not guarantees of a particular population or mob type.

With Seed Debug Logging on, `[ZSG-Rooms/FortressProtection]` reports activation,
`used=N/8`, `capBypassed`, exhaustion and the evaluation-limit cutoff. Existing
`cap-summary` counts still report the **original** cap decision, so a blocked
count can now coexist with protected evaluations. No numeric world seed is
included. These hooks and ordering are inactive when RNG standardization is off.

Gravel's Fortune-aware flint condition uses its own global, event-indexed
`gravel_flint` channel. The mixin applies only when the loot context contains a
gravel block and the table-bonus enchantment is Fortune. Minecraft still supplies
the chance for the active Fortune level, while the deterministic wrapper also
consumes the corresponding vanilla random float. Silk Touch and explosion paths
that never evaluate the flint condition do not advance the gravel sequence.

Player Unbreaking checks use per-item-type event counters under the `unbreaking`
channel. The redirect wraps only `UnbreakingEnchantment.shouldPreventDamage(...)`
inside `ItemStack.damage(...)`, allowing vanilla to retain its complete tool and
armor probability formulas. The wrapper consumes matching calls from the player's
original random stream. Equipment damage for non-player entities, unenchanted
items, and all ordinary durability application remain vanilla.

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

## Temporary Server Save Diagnostics

The `MinecraftServerSaveProbeMixin` and `ServerWorldSaveProbeMixin` instrument
integrated-server save entry and return points without changing save arguments,
flow, or results. They are diagnostic-only and run when **Seed Debug Logging**
is enabled in Room Settings. The probe records nested elapsed times, filtered
callers, room context, and the slowest dimension for saves taking at least
50 ms. Remove the probe, its tests, and both mixin registrations after the
gameplay-save trigger has been identified.
