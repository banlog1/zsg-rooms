# Replay Foundation Decision

Date: 2026-09-09

Status: foundation selected and opt-in local capture implemented. A development
client capture and streaming file inspection passed; visual ReplayMod playback,
full modpack compatibility, race integration, and performance remain unverified.
Local recording/setup controls are available in Room Settings > Replays; no
recording JVM arguments are required. Libraries remain separately prepared.

## Decision

Update 2026-09-10: the user approved a separate optional viewer companion and
manual file sharing first. The core recorder retains its file-only boundary.
ReplayMod-specific playback calls now live in `replay-viewer`, not in ZSG Rooms'
runtime. See [viewer controls](../replay-viewer/README.md) and
[multi-player coordination](REPLAY_COORDINATION.md). The original first-version
restrictions below describe the recorder, not this new companion module.

Use an MCPR-first hybrid:

- ReplayMod supplies playback, the recorded-player camera, freecam, and seeking.
- ReplayStudio is the preferred library for replay-file I/O and processing.
- ZSG Rooms owns race-scoped capture, bounded buffering, monotonic timing,
  metadata, and failure handling.
- Players may install ReplayMod separately for playback, as approved by the user.
  Do not bundle or fork the full ReplayMod into ZSG Rooms for the prototype.
- The playback boundary is file-only: ZSG produces MCPR files and the user opens
  them through ReplayMod's own viewer. Do not compile against, reflect into, or
  mix into ReplayMod internals for the first version.
- Use ordinary `.mcpr` files per continuous recording session, including local
  resets, and a small, versioned ZSG manifest to associate connection intervals,
  racers, race-time offsets, and results.
  Do not invent a new custom binary world-event format at this stage.

This deliberately does not select the unmodified ReplayMod recorder as our
production recorder. Use it as a correctness/performance reference while proving
the ZSG-controlled capture path. Reuse library functionality rather than copying
upstream recording classes wholesale.

## Verified Baseline

The local repository targets Minecraft 1.16.1, Yarn 1.16.1+build.21,
Fabric Loader 0.19.3, Fabric API 0.18.0+build.387-1.16.1, and Java 8 bytecode.
The local dependency folders contain Atum 2.7.2, FSG Mod 5.3.0,
SpeedrunAPI 2.2, and SpeedRunIGT 16.0.

The official download page provides ReplayMod **1.16.1-2.6.27** for Fabric.
This is the candidate playback version, not a claim that every later version is
compatible. [Official downloads](https://www.replaymod.com/download/)

The official JAR was downloaded into memory for inspection, not installed or
executed:

- Size: 15,394,521 bytes.
- SHA-256: `92479881ab274e1f067171b1300b435b7b6831adf0fc86c806c915713c938f55`.
- Its Fabric metadata identifies version `1.16.1-2.6.27` and requires Loader
  `>=0.7.0` plus networking, key-binding, and resource-loader Fabric modules.
- Sampled `PacketListener`, `ReplayModReplay`, and `ZipReplayFile` classes have
  class-file major version 52, consistent with Java 8. This is not an exhaustive
  dependency or runtime compatibility test.

Source inspection was pinned to ReplayMod commit
`4361ec953a53d5bf7f1bf663f6a406273d44a146` (version.txt: 2.6.27).
Its ReplayStudio submodule is
`06978a304fc023ed9cfe48ccc7c5f32d8d499460`; that revision configures a Java 8
toolchain. Pin the actual dependency artifact and transitive dependencies during
the prototype rather than depending on a moving snapshot.
[ReplayStudio build](https://github.com/ReplayMod/ReplayStudio/blob/06978a304fc023ed9cfe48ccc7c5f32d8d499460/build.gradle.kts)

## Options Considered

| Approach | Main benefit | Main cost | Decision |
| --- | --- | --- | --- |
| Stock ReplayMod recorder and player | Fastest baseline; existing capture and viewer | Recording lifecycle, timeline, and queue do not match our requirements | Reference implementation only |
| ZSG capture, ReplayStudio files, ReplayMod player | Control race recording without rebuilding world playback | Capture correctness and file compatibility remain our responsibility | Selected prototype direction |
| ReplayStudio with a custom viewer | Existing file tooling | ReplayStudio is not a Minecraft viewer; reconstruction and camera integration still need implementation | Not selected |
| Entirely custom format and engine | Full control | Largest correctness, maintenance, and compatibility burden | Reconsider only after a concrete blocker |

ReplayStudio provides replay-file handling, crash recovery, filtering, and packet
stream processing. It does not replace ReplayMod's Minecraft playback layer.
[ReplayStudio overview](https://github.com/ReplayMod/ReplayStudio/tree/06978a304fc023ed9cfe48ccc7c5f32d8d499460)

## Why Stock Recording Is Not Sufficient

Confirmed structural differences in the inspected recorder:

- Packet timestamps use wall-clock milliseconds and remove integrated-server
  paused time. Our race clock includes elapsed pauses and reset/loading gaps.
- The writer uses `Executors.newSingleThreadExecutor()` without a bounded queue.
  An asynchronous writer alone does not guarantee bounded retained memory.
- Disconnect initiates finalization and schedules saving/rename screens. These
  need careful handling during rapid Atum resets.
- Integrated-server packet objects are explicitly encoded for recording; there
  is not an existing encoded network byte stream that we can capture for free.

These are implementation observations, not measured FPS or MSPT regressions.
[PacketListener source](https://github.com/ReplayMod/ReplayMod/blob/4361ec953a53d5bf7f1bf663f6a406273d44a146/src/main/java/com/replaymod/recording/packet/PacketListener.java)

Recording start/stop controls also use editing markers rather than simply
turning packet capture on/off. Do not use those buttons as our queue-safety or
race-only recording mechanism.
[Recording controls](https://github.com/ReplayMod/ReplayMod/blob/4361ec953a53d5bf7f1bf663f6a406273d44a146/src/main/java/com/replaymod/recording/gui/GuiRecordingControls.java)

## Integration Boundaries

ReplayMod explicitly does not promise an external API. Its current internals
expose recording initiation, access to the current recorder, marker insertion,
and replay opening, but public Java methods are not a compatibility guarantee.
Those methods are research findings, not planned integration points. The first
version will not call them. An absent playback dependency must not break
ordinary races or recording. One-click playback from ZSG would be a separate
future design and licensing review, not an implicit part of this decision.
[ReplayMod README](https://github.com/ReplayMod/ReplayMod/blob/4361ec953a53d5bf7f1bf663f6a406273d44a146/README.md),
[recording entry point](https://github.com/ReplayMod/ReplayMod/blob/4361ec953a53d5bf7f1bf663f6a406273d44a146/src/main/java/com/replaymod/recording/ReplayModRecording.java),
[playback entry point](https://github.com/ReplayMod/ReplayMod/blob/4361ec953a53d5bf7f1bf663f6a406273d44a146/src/main/java/com/replaymod/replay/ReplayModReplay.java)

ZSG responsibilities for the next stages:

1. Arm capture before the new world connection delivers initialization packets,
   not at the Start/GO message. `ZsgRoomsClient.beginSynchronizedStart` identifies
   the launch; the precise packet hook still needs mapping and ordering tests.
2. Publish the same monotonic start instant established by
   `EndExitTimeCapture.onResumedTick` through `IntegratedServerRaceClockMixin`.
   Retain recorder timing after the live race clock is cleared. Do not move or
   otherwise change the winner-timing boundary.
3. Give each MCPR segment nonnegative millisecond timestamps from its own origin.
   Store the race-zero offset, reset gaps, and precise finish elapsed duration
   separately. Replay timestamps are for viewing, never winner adjudication.
4. Atum resets, including `ZsgInGameActions.resetCurrentRun`, now retain one writer
   and monotonic clock in a single MCPR. Connection handoffs need explicit handling:
   omit subsequent login handshakes, preserve new JoinGame/entity IDs, refresh the
   playback player, and clear connection-level state. Independent connection streams
   cannot simply be concatenated without these steps. Race manifests remain planned.
5. Record the local player as replay-compatible synthetic entity packets. A custom
   player-frame record alone cannot be understood by an unmodified ReplayMod
   viewer. Verify spawn/profile, movement, equipment, pose, and dimension changes.
6. Prevent duplicate recording when ReplayMod is installed. Use its documented
   singleplayer-recording setting to disable the stock recorder explicitly in
   the test instance. For the first version, make that user-controlled setup a
   prerequisite for co-installation; do not confuse its start/stop controls with
   disabling capture, silently change settings, or patch its internals.
7. Enforce byte bounds before retaining packet data across either client-thread
   scheduling or writer scheduling. Queue saturation or write failure stops that
   recording, preserves the valid prefix, and never blocks the game.
8. Keep all file operations, including opening files and final packaging, off
   gameplay threads. Validate buffer ownership and release on every failure path.

The original MCPR remains independently viewable. The ZSG manifest adds race
organization; ordinary ReplayMod will not automatically implement our segment
switching, pause-gap presentation, or two-racer comparison UI.

## Verified ReplayStudio Packaging

The same official ReplayMod 1.16.1-2.6.27 JAR was inspected again; its SHA-256
matches the value above. ReplayStudio is merged into the outer JAR, including:

```text
com/replaymod/replaystudio/io/ReplayOutputStream.class
com/replaymod/replaystudio/replay/ReplayMetaData.class
com/replaymod/replaystudio/replay/ZipReplayFile.class
com/replaymod/replaystudio/studio/ReplayStudio.class
```

Its Fabric `jars` metadata lists four Fabric modules and MixinExtras, but no
separate ReplayStudio JAR. Bundled relocated dependencies also occupy namespaces
such as `com/replaymod/replaystudio/lib/guava` and
`com/replaymod/replaystudio/lib/viaversion`.

Consequences for our design, not a claim that class loading was tested:

- Do not source ZSG's library dependency from ReplayMod's installed classes.
  That would make the playback mod necessary for recording and couple us to
  its internal dependency version and packaging.
- Do not extract library classes from the ReplayMod distribution and assume
  that produces a complete, independently supported ReplayStudio artifact.
- Two independently supplied copies of the same class names may conflict.
  Prove collision-safe loading with ReplayMod present and absent before adopting
  a standalone ReplayStudio artifact. File-only interaction does not itself
  isolate classes when both mods run in the same JVM.
- Prefer an independently built, pinned, replaceable library artifact. Decide
  its class isolation or library-only relocation during the dependency spike.
  Preserve reproducible build/relocation instructions and replacement support;
  do not merge LGPL library classes into ZSG's own classes merely for convenience.

A plain Java library placed in `mods/` is not automatically a Fabric mod. Use
an explicit classpath arrangement or Fabric-compatible library packaging.
Loom's `include` can generate metadata for a nested non-mod library, but nesting
alone neither resolves duplicate classes nor proves LGPL compliance.
[Fabric Loader documentation](https://docs.fabricmc.net/develop/loader/)

## Licensing Boundary And Release Checklist

The repository currently uses CC0. ReplayMod declares GPL-3.0-or-later;
ReplayStudio declares LGPL-3.0-or-later. Keep independently written ZSG code
under CC0, with ReplayStudio and its dependencies retaining their own licenses.
Producing a compatible replay file is not copying ReplayMod's implementation.
The selected design excludes copied GPL recording/viewer code and direct
ReplayMod integration. This is an engineering compliance plan, not a legal
opinion about every possible future combination.
[ReplayMod license](https://github.com/ReplayMod/ReplayMod/blob/4361ec953a53d5bf7f1bf663f6a406273d44a146/LICENSE.md),
[ReplayStudio license](https://github.com/ReplayMod/ReplayStudio/blob/06978a304fc023ed9cfe48ccc7c5f32d8d499460/COPYING.LESSER)

Before distributing ReplayStudio with ZSG:

- Include attribution and complete GPLv3/LGPLv3 texts in the distributed
  artifacts, not just the repository. Add a third-party components notice.
- Identify the exact library revision, binary hash, build scripts, and patches.
  Audit transitive components too; not everything bundled has the same license.
- Provide corresponding source through a license-permitted distribution method.
  Prefer a source/build archive alongside our binary release; a generic upstream
  homepage link is not the whole compliance process.
- Document and test replacing the library with an interface-compatible modified
  version, or provide the required materials to recombine/relink it. Do not
  prohibit debugging modifications. Include installation information if required.
- Keep any library modifications separate, with applicable licensing and source
  availability, rather than presenting them as CC0 ZSG code.

LGPLv3 section 4 permits application terms of our choice subject to its notice,
license, modification, and relinking/replacement conditions. Packaging choices
must satisfy those conditions in practice.
[LGPLv3, section 4](https://github.com/ReplayMod/ReplayStudio/blob/06978a304fc023ed9cfe48ccc7c5f32d8d499460/COPYING.LESSER)

The isolated development probe downloads library artifacts for local tests;
none are installed into Minecraft or distributed in the main mod. Assemble the
actual third-party license files and release notices before production packaging,
so those notices describe what we really ship. The root `LICENSE` and ZSG's CC0
mod metadata remain unchanged.

## Dependency Probe Results

The separate [replay-prototype project](../replay-prototype/README.md) resolves
the selected ReplayStudio revision from JitPack without extracting ReplayMod
classes. Its standalone library JAR has SHA-256
`a49638a1800dc1a01e260f4d216e5178bcebd7f87ad92549a3066e739f94d456`.
The complete resolved set is locked and checksum-verified; an artifact inventory
is generated under that project's ignored build directory.

Five tests passed on the build JDK (Microsoft OpenJDK 25.0.2), including an
offline rerun with the locks and checksums enforced:

- The application classpath cannot resolve the fixture or ReplayStudio.
- A dedicated URLClassLoader with only a platform/extension parent can write
  and read an MCPR fixture, retaining world-time payloads and their timestamps.
- Our fixture is Java 8 bytecode and contains no merged library classes.
- A metadata-modified/repackaged library replaces the original without rebuilding
  the fixture and still reads/writes correctly.
- The actual ReplayMod JAR can supply its own competing metadata class through
  an ambient/context loader without replacing our isolated library's class.

This selects **separate JARs in an isolated library loader** as the next
packaging candidate, without relocating or rewriting the library. The tests do
not validate an actual Java 8 runtime, arbitrary library modifications, Fabric
startup, modpack behavior, or the complete source-distribution obligations.

One useful finding: ReplayStudio's reader intentionally filters keep-alives.
The transport fixture includes one, but validates ordering and a 65-second gap
using world-time updates instead. It has no JoinGame/chunk/player state and is
not a playable world recording. Upstream Netty also emits an `Unsafe` deprecation
warning on JDK 25; it did not fail these tests.

## Compatibility Gates Still To Run

- Start the candidate JAR with ZSG and the local dependency set on the supported
  runtime. Verify mixin application and dependency resolution.
- Repeat with the user's actual MultiMC modpack. Sodium, Lithium, WorldPreview,
  SeedQueue, and other historical pack entries are not all present locally.
  General upstream compatibility statements do not validate those exact builds.
- Test start-gate pauses, Nether/End transitions, death, local reset, seed change,
  exit-portal completion, return to Overworld, and rapid consecutive worlds.
- Verify stock and ZSG recorders never record the same connection concurrently
  and playback never starts a recording of itself.
- Verify recording without ReplayMod, library-class coexistence with ReplayMod,
  and the documented modified-library replacement/rebuild procedure.
- Verify playback cannot send relay messages, submit results, trigger live-world
  actions, or execute unsafe packet side effects.
- Prove the required recorded-player perspective. Spectating a replay entity is
  not a promise of exact original HUD, inventory screens, or every client effect.
- Compare capture ON/OFF frame-time percentiles, MSPT, allocations, retained bytes,
  disk throughput, and transition stalls before claiming negligible overhead.
- Test disk failure and queue saturation, truncated recovery, and opening a
  recording after its original world has been removed.

The first local capture smoke check now passes without ReplayMod installed.
Local controls are under **Room Settings > Replays**. Enabling recording now
automatically prepares pinned libraries in the Minecraft instance's
`zsgrooms/replay-libraries`; no Java arguments or folder selection are needed.
Setup retains an optional library override. The mod packages our writer adapter
and download manifest only, with external library downloads verified on a
background thread. An in-world REC badge shows active recording. Finalized
MCPR files go to `replay_recordings`, ReplayMod's default Replay Viewer folder.
This does not yet follow a custom ReplayMod recording path. The user has confirmed
ordinary playback; visual playback across resets still needs verification.
See [the prototype guide](../replay-prototype/README.md#live-recording) for exact
commands, packet counts, buffering tests, and failure behavior. Inspection of
actual world NBT exposed OpenNBT's `provided` Guava dependency; the isolated set
now explicitly supplies its requested Guava 21.0 and includes an NBT regression
test. This is additional to ReplayStudio's internally relocated Guava. Include
both in the eventual component/license and security review.

ReplayMod's documentation distinguishes recording with Sodium from its video
export compatibility requirements. Do not require a renderer replacement or
FFmpeg merely on the assumption that in-game playback is video export.
[Official compatibility documentation](https://www.replaymod.com/docs/)

## Next Deliverable

Verify continuous-reset playback and backward seeking in the selected viewer.
Ordinary playback has been confirmed by the user. Capture includes initial world
state, synthetic local-player state, entity/block changes and dimension changes.
Resets now continue in one file with explicit connection/world handoff packets;
the automated multi-world scenario and inspector are described in the prototype
guide. Then test paused time against the race clock. The opt-in prototype records
monotonic recording time, not race offsets. A new world opened while a genuinely
finished recording is still finalizing is skipped; resets do not finalize it.

If playback compatibility fails, investigate a different pinned ReplayMod build
or correct the MCPR output before considering a custom viewer. Direct ReplayMod
integration would require revisiting the file-only decision explicitly. If capture
cannot satisfy ordering, memory bounds, or race timing without invasive changes,
revisit the capture integration explicitly rather than weakening those guarantees.
