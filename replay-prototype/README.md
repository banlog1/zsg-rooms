# Replay Development Prototype

An isolated library project for [the replay plan](../docs/REPLAY_FOUNDATION.md).
It builds the isolated writer used by the root project's opt-in live recorder,
plus dependency and file-format probes. The root build includes this build to
package only our writer adapter and a pinned download manifest. External library
classes are not bundled in the mod. No command here installs ReplayMod.

## Purpose

Resolve ReplayStudio directly from its pinned upstream Maven publication:

```text
com.github.ReplayMod:ReplayStudio:06978a304fc023ed9cfe48ccc7c5f32d8d499460
```

Compile a small Java 8 fixture against that library, then load the fixture and
its dependencies through a dedicated `URLClassLoader`. The parent is Java's
platform/extension loader, not Minecraft's application loader. Only JDK types
cross the test boundary. No library relocation or ReplayMod internal calls are
needed for this experiment.

The ordinary test classpath deliberately excludes both the fixture and
ReplayStudio. Dependencies are copied to `build/replay-libraries/` as separate
JARs, with coordinates and SHA-256 hashes in `artifacts.json`. They are not
embedded in ZSG Rooms or registered as Fabric mods.

## Commands

From the repository root, using the same modern build JDK as the main project:

```powershell
.\gradlew.bat -p replay-prototype clean test
.\gradlew.bat -p replay-prototype coexistenceTest
```

The second command downloads the official ReplayMod 1.16.1-2.6.27 JAR to this
project's ignored `build/reference/` directory and verifies its pinned SHA-256.
It loads a library metadata class from that JAR in a competing class loader.
It does not initialize the ReplayMod mod, execute Fabric entrypoints, or install
anything in `run/mods/` or the user's MultiMC instance.

Dependencies use a Gradle lockfile and verification metadata. For an intentional
dependency update, review the new sources and artifacts before accepting new
checksums; checksum generation alone is not independent provenance verification:

```powershell
.\gradlew.bat -p replay-prototype test --write-locks --write-verification-metadata sha256
```

## Checks

- The application/test loader cannot resolve ReplayStudio or the fixture.
- ReplayStudio writes and reads an MCPR transport fixture without ReplayMod.
- Metadata identifies Minecraft 1.16.1, protocol 736, and MCPR format 14.
- The login transition precedes the test PLAY packets, identical timestamps
  retain packet order, and a 65-second gap is preserved.
- Packet payload bytes are unchanged and the output API releases their buffers.
- The reader intentionally removes keep-alives; distinct world-time updates are
  used to check payload fidelity and ordering rather than assuming every network
  packet is returned by the playback-oriented reader.
- Our fixture JAR contains Java 8 bytecode and no embedded ReplayStudio classes.
- An independently repackaged library JAR can replace the original without
  rebuilding the fixture. This modifies metadata only; it is not a test of every
  interface-compatible source modification or complete LGPL compliance.
- With ReplayMod's actual metadata class loaded through the ambient/context
  loader, our own library remains distinct and can still perform the round trip.

The fixture contains keep-alive and time-update packets, not chunks, a player,
or a JoinGame packet. It is intentionally **not a playable world replay**.

## Limits And Next Steps

Class-loader coexistence is not a Fabric/modpack compatibility test. Before using
this arrangement in the recorder, test initialization cost, library background
threads/context-loader behavior, lifetime/cleanup, and a proper game launch with
the complete modpack. Keep one appropriately managed library loader rather than
creating a new one on every packet or world reset.

The original transport fixture uses synchronous library calls on test threads.
The live recorder below has separate buffering tests. Neither set of unit tests
establishes gameplay overhead or robust recovery from a full disk/process crash.

## Live Recording

Recording is **OFF by default**. The root project now contains an opt-in
`ReplayPrototype`, two Minecraft mixins, a bounded `ReplayBuffer`, and a
development-only automated scenario. This is not yet automatic race recording.
It records any local integrated-server connection while enabled, not remote
multiplayer connections. Local controls are under **Room Settings > Replays**;
there is no synchronized room rule or run-history playback button yet.

Build and launch the separate development instance:

```powershell
.\gradlew.bat build
.\gradlew.bat runReplayPrototype
```

The second command opens Minecraft for manual testing in `run/replay-prototype/`.
It does not use the MultiMC instance or its saves. For the automated scenario:

```powershell
.\gradlew.bat runReplayPrototype -PreplaySmoke=true
```

This creates a uniquely named new world, changes a block, spawns an entity,
changes equipment, moves/swings the local player, visits the Nether, returns to
the Overworld, disconnects, waits for the writer, and exits. It requires Fabric's
development environment as well as the explicit smoke-test flag. Test worlds
are retained; it does not delete saves. This needs a graphical Minecraft client,
not a headless server. A timeout/failure causes the Gradle check to fail.
It configures recording through the saved-settings API (not the old recording
JVM switches), uses automatic instance-local library setup, and captures Room
Settings, both replay tabs, and the in-world recording HUD in `screenshots/`.
First use needs internet even when Gradle itself is running with `--offline`;
subsequent setup reuses verified downloaded JARs.

Recordings are written beneath the launched instance's game directory:

```text
replay_recordings/<random-id>.mcpr
```

Check the live smoke recording with the standalone streaming inspector:

```powershell
$replay = Get-ChildItem 'run/replay-prototype/replay_recordings/*.mcpr' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
.\gradlew.bat -p replay-prototype inspectRecording "-PreplayFile=$($replay.FullName)"
```

The inspector expects exactly the automated Overworld/Nether/Overworld scenario,
not an arbitrary run. It checks monotonic packet time, login/world initialization,
local-player spawn before movement after each transition, chunks, equipment,
animation, entity/block changes, and absence of custom payloads. It streams data
instead of retaining chunk payloads in a report.

New recordings use ReplayMod's default recording folder, so no manual copy is
needed for its Replay Viewer. Existing files in `zsgrooms/replays/prototype/`
are not moved. If ReplayMod has a custom `recordingPath`, this prototype does not
read or follow that override.

### In-Game Setup (No Java Arguments)

Use the newly built mod, then open **Room Settings > Replays**.

1. Enable **Record Local Worlds**. First use automatically downloads the pinned
   libraries into this instance's `zsgrooms/replay-libraries` (about 35 MiB).
   No folder selection or Java arguments are required.
2. Wait for setup to finish. **Waiting for next world** means recording is armed;
   the Setup tab also shows **Libraries ready**.
3. In the **Recording** tab, confirm **ReplayMod recorder disabled** if ReplayMod
   is installed, after disabling its Record Singleplayer, Record Server, and
   Automatic Recording settings. This checkbox is an acknowledgement, not an
   automatic modification of ReplayMod's configuration.
4. Enter a new local world. A red **REC** badge and elapsed recording time confirm
   capture. Enabling in an already loaded world takes effect on the next entry.
   Entering before setup finishes skips that recording; it never silently starts
   partway through the world. The HUD says **Replay: Not recording** in this case.
5. Room replays finalize automatically on victory, loss, draw, or forfeit when the
   result is displayed, and when returning to the room. Single-player replays
   finalize when quitting to title. **Stop Recording** is only a manual override.
   Atum resets (including Reset Run and New Seed) continue in the **same file**;
   loading gaps remain part of the recording timeline.
   **Open Recordings** opens the folder; use ReplayMod's Replay Viewer for playback.

The `?` tooltip gives scope/playback information. Status updates include installing,
recording, saving, saved, missing libraries, and ReplayMod confirmation. Setup controls are
locked while libraries are being checked or a recording is active/finalizing.

Choices persist in `config/zsg-rooms-replay.properties` inside the instance's game
directory. Setup's **Use Minecraft Folder & Set Up** restores automatic setup,
including when an earlier prototype saved a custom folder. Blank override means
automatic. **Browse...** and **Save & Check** remain for advanced use with a
separately prepared `live-libraries` folder, including interface-compatible library
replacements; custom folders are never downloaded into or checksum-repaired.
Keep library JARs out of `mods/`; the recorder loads them itself.

Default setup downloads from pinned HTTPS URLs on JitPack and Maven Central,
verifies SHA-256 before replacing a file, and reuses verified JARs without network
access. Work happens on the background setup thread. Failed partial downloads
are removed; existing files are not replaced with unverified data. Unknown JARs
in the managed folder are left untouched and excluded from the class loader.
The mod embeds only our CC0 adapter and the manifest, not ReplayStudio or its
dependencies. The build checks manifest hashes against the reviewed dependency
verification metadata, including when invoked as an included build.

The previous three `zsgrooms.replayPrototype` arguments can be removed. They are
only used as legacy defaults when no saved replay configuration exists. Once any
UI setting is saved, saved choices take precedence, including turning recording
off. With recording off, packet hooks do no capture and the tick callback returns
immediately when no session exists. Libraries load only when checked/enabled.

The library directory is separate from the replay output directory. For setup
failures, retry **Use Minecraft Folder & Set Up**, or check the supplied libraries
if using an override. Changing the recording folder does not repair missing
libraries. Other failures identify the stage (creating the
folder, opening the file, writing packets, or finalizing). Diagnostics do not
dump packet contents or arbitrary exception messages.

If ReplayMod is installed, recording is skipped by default to avoid dual
recorders. A deliberate coexistence test needs the in-game acknowledgement
**after manually disabling ReplayMod's own recorder**. ZSG does not inspect or
change ReplayMod settings.
Ordinary ReplayMod playback does not open a local integrated-server connection,
so it does not trigger this recorder. Full modpack coexistence remains untested.

### Capture And Failure Handling

- Capture attaches before local login/world initialization. Login success and
  vanilla PLAY packets are serialized before their mutable data can be reused.
- Timestamps and ordering are committed through the client task queue. A barrier
  between incoming JoinGame/Respawn and client world application suppresses
  synthetic local-player frames from the previous world.
- Local-player position, head rotation, velocity, copied metadata, changed
  equipment, and swing animations supplement the server stream. Metadata copying
  does not call vanilla constructors that clear live tracker dirty flags.
- Packet encoding still happens on the receiving/client thread. Disk I/O,
  ReplayStudio initialization, packet writes, and ZIP finalization happen on one
  daemon writer thread. This is not a claim of zero capture overhead.
- The per-recording buffer accounts for pending client tasks, queued records,
  and the active write: 32 MiB of payload/accounting allowance and 8,192 records.
  An encoded packet is capped at 4 MiB. Object/encoding/library memory is additional;
  32 MiB is not a bound on all recorder heap usage.
- Saturation or capture errors stop recording without waiting for disk. A
  successfully finalized prefix is named `.incomplete.mcpr`. Writer/disk failures
  may instead leave temporary files; crash recovery is not implemented.
- One writer may be active at a time. A world opened before the previous writer
  finishes after an actual session end is skipped and logged. Accepted Atum resets
  instead hand the replacement connection to the existing writer and clock.
- A client disconnect hook closes the buffer before Minecraft cancels queued
  tasks; finalization does not depend on a queued network-close callback surviving
  that cancellation. Repeated result/room-return/disconnect signals are harmless.
  Results delayed until the Overworld also delay recording finalization until
  that return. A dimension change by itself does not stop recording.
- An accepted Atum `scheduleReset` preserves the recording through teardown and
  loading; `stopRunning` cancels that expectation. Abandoning a reset after
  teardown finalizes the recording. Attachment identity rejects late old-world
  packets and disconnects. Pending reservations are released before vanilla
  discards the client tasks; committed packets still drain normally.
- Only the first login success is written. Each replacement world contributes
  its actual JoinGame and new entity IDs. Replay-only player-list/boss-bar removals
  and a sound stop clear connection-level state. A same-dimension Respawn after
  replacement JoinGame recreates the playback player/camera in the fresh world.
  These synthetic packets go to the file, never to the live game or relay.
- Ordinary disconnect drains committed packets. Client shutdown waits at most
  two seconds for finalization; abrupt exit or slow disk can leave unfinished data.
- Custom mod payloads, resource-pack downloads, and authentication exchanges are
  excluded, as are disconnect packets that would prematurely close playback.
  Replays still contain ordinary world/player data and vanilla chat;
  treat them as private files, not sanitized public uploads.

### Verified And Still Pending

Automatic-finish smoke modes are available with
`runReplayPrototype -PreplaySmoke=true -PreplayEnd=quit` (the default), or
`victory`, `loss`, `draw`, `forfeit`, and `room`. Result modes invoke the same local
result handler used by the transports, and assert saving completes while the
world is still open. They do not simulate relay arbitration. Quit mode verifies
the client-disconnect hook; room mode returns to a local fixture lobby without
opening a relay connection. None uses the manual Stop Recording control.

Add `-PreplayResets=2` to record three worlds in one file: first a same-seed
reset, then a different seed. Add `-PreplayAtum=true` to load the local optional
mods and exercise `ZsgSeedBridge.launchSeedWithAtum` and the actual Atum hook;
without it the smoke harness drives the same handoff explicitly. The test asserts
the recording session stays identical across worlds. Pass `-PreplayResets=2`
to `inspectRecording` as well. It requires one login, three world initializations,
nine local-player spawns and the expected respawns, with monotonic timestamps.

The continuous Atum smoke passed locally: two resets produced one 73,137 ms file,
with one login, three JoinGame packets, nine local-player spawns and 687 local
movement packets. Streaming inspection passed. Unit tests cover repeated handoffs,
stale attachment rejection, reset cancellation, forced stops, pending-task budget
reclamation, and player-list/boss-bar cleanup. These are capture checks, not a
claim that visual playback or seeking across resets has been verified.

On the local JDK 25 development client, the smoke run finalized 2,130 packets
(6,456,958 payload bytes) over 12,330 ms. The inspector found 211 chunk packets,
three local-player spawns, 229 local movement packets, and both dimension changes
in the expected order. These counts describe one test, not a performance target.
The main build passes seven new buffer tests and a default-off hook test; the standalone suite has nine
passing tests including the live writer and isolated NBT dependencies.

Real world inspection exposed OpenNBT's `provided` dependency on Guava 21.0.
It is now supplied explicitly inside the isolated loader. The small time-packet
fixture did not exercise that dependency; a dedicated NBT regression test does.
ReplayStudio's reader also intentionally complements the JoinGame entity ID for
its spectator camera and filters keep-alives. The inspector accounts for this;
the recorded raw vanilla JoinGame is not rewritten by ZSG.

The user has confirmed ordinary playback works in their ReplayMod instance.
**Continuous-reset visual playback and seeking still need viewer verification.**
Open the multi-world MCPR and check player/camera state and chunks on both sides
of each reset, including when seeking backward across it. Full MultiMC modpack,
pauses, death, rapid resets, and race start/finish coverage remains incomplete.
The prototype currently uses elapsed monotonic recording time, including loading
and pause gaps. It is not IGT, does not write race offsets/manifests, and does not
capture ZSG's client-only HUD/chat overlays or exact inventory-screen interaction.
No ON/OFF frame-time or server-tick benchmark has been done.

## Investigating Playback Loading

Work on a copy of the MCPR, not the user's original recording. The streaming
diagnostic reports packet types, counts, and initialization timestamps without
printing chat, player names, coordinates, or seeds:

```powershell
.\gradlew.bat -p replay-prototype inspectRecording '-PreplayFile=<absolute copy path>' -PreplayDiagnose=true --offline
```

An optional development-only viewer driver opens that copy in ReplayMod 2.6.27.
Fetch the pinned reference first with the standalone `fetchReplayModReference`
task. From the root project:

```powershell
.\gradlew.bat runReplayPlaybackTest '-PreplayPlaybackFile=<absolute copy path>' -PreplayPlaybackEnd=118000 --offline
```

`replayPlaybackEnd` is the replay timestamp in milliseconds at which to seek back
to 1.5 seconds; choose a value within the recording. The default is 118000.
The driver logs screen transitions, takes an initial screenshot, then closes the
viewer five seconds after seeking. Inspect `[ReplayPlaybackTest]` messages in
`run/replay-playback-test/logs/latest.log`; reaching the end of Gradle alone is
not proof of successful playback. Unlike the packet summary, Minecraft's normal
playback log can contain recorded chat. Do not publish it without reviewing it.

The test source set and ReplayMod runtime dependency exist only when the playback
property is supplied. Neither is included in the normal release JAR. Optional
`-PreplayPlaybackMods=true` loads copied JARs from
`run/replay-playback-test/reference-mods`; omit ZSG and ReplayMod duplicates.
`-PreplayPlaybackExclude=<glob,glob>` can exclude copied mods for diagnosis.

A supplied 118.8-second recording had its first position-initialization packet at
676 ms and local-player spawn at 1165 ms. In baseline playback the initial loading
screen cleared around 2.1 seconds, before the first recorded interaction, and
the subsequent dimension changes and reset did not leave it stuck. This does
not establish that playback works with the user's complete modpack. That copied
pack failed before playback in this development launcher: SeedQueue and then
FastReset reported an unregistered MixinSquared handler selector. This is a test
environment limitation, not evidence that either mod caused the reported bug.
StateOutput also failed at the title screen with a missing `State` class. With
those three copied mods excluded, opening the same replay cleared loading at
about 1.9 seconds; seeking from 5 seconds back to 1.5 seconds also cleared it and
the driver completed normally. A baseline seek backward across the reset returned
without a terrain screen as well. These checks have not reproduced the reported
stuck-on-open behavior. User confirmation places the issue on initial opening,
not after a seek or reset; a playback-time MultiMC log is still needed.

Follow-up: the user identified World Preview attempting to preview the replay
in their instance. No recorder/terrain-screen change was made for that report.

## Third-Party Components

The probe's independently written source is covered by the repository's CC0
license. ReplayStudio is LGPL-3.0-or-later and is downloaded, not copied from
ReplayMod. ReplayMod is an optional GPL-3.0-or-later test reference, not a runtime
dependency of ZSG's recording design.

- [Exact ReplayStudio source](https://github.com/ReplayMod/ReplayStudio/tree/06978a304fc023ed9cfe48ccc7c5f32d8d499460)
- [ReplayStudio license](https://github.com/ReplayMod/ReplayStudio/blob/06978a304fc023ed9cfe48ccc7c5f32d8d499460/COPYING.LESSER)
- [ReplayMod source](https://github.com/ReplayMod/ReplayMod/tree/4361ec953a53d5bf7f1bf663f6a406273d44a146)
- [Official ReplayMod downloads](https://www.replaymod.com/download/)

Do not publish `build/replay-libraries/` or the reference JAR as a release bundle.
The source/build archives, full license texts, transitive license inventory,
and replacement instructions required by the production packaging plan have not
yet been assembled. The main mod now contains our adapter and download manifest
only; this is not a bundled third-party-library distribution.
