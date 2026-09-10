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

## Replays

ZSG Rooms can automatically record local worlds and races. Recording is optional
and off by default. **No Java arguments or folder selection are required.**

### Install For Playback

Add these to the same Minecraft instance's `mods` folder, then restart Minecraft:

| Mod | What it does |
| --- | --- |
| `zsg-rooms-<version>.jar` | Records and automatically saves replays. Use 1.0.26 or newer. |
| ReplayMod **1.16.1-2.6.27** | Plays the saved files through its Replay Viewer. |
| `zsg-replay-viewer-0.1.0.jar` | Optional companion: speedrunning playback controls, timers, milestones, and switching between racers. Requires the ReplayMod version above. |
| SpeedRunIGT | Optional; install when recording to capture RTA and IGT for playback. |

Get ZSG Rooms and the companion from
[GitHub Releases](https://github.com/banlog1/zsg-rooms/releases), and ReplayMod
from its [official downloads page](https://www.replaymod.com/download/).
Do not install the `-sources.jar` in `mods`. The companion is updated manually,
separately from ZSG Rooms' in-game updater.

ReplayMod and the companion are not needed just to record. They can be installed
later for viewing.

### Enable Recording

1. If ReplayMod is installed, open its settings and turn **Record Singleplayer**,
   **Record Server**, and **Automatic Recording** OFF. ZSG uses its own recorder;
   leave ReplayMod's recorder disabled to avoid duplicate recordings.
2. Open **ZSG Rooms > Settings** (the gear button) **> Replays > Recording**.
3. Enable **Record Local Worlds**. First setup downloads the recording libraries
   automatically into this instance's `.minecraft/zsgrooms/replay-libraries`.
   Internet access is needed for this initial setup. Do not move these libraries
   into `mods`.
4. If ReplayMod is installed, tick **ReplayMod recorder disabled**. This only
   confirms step 1; it does not change ReplayMod's settings for you.
5. Wait for **Libraries ready** in the Setup tab and **Waiting for next world**
   in Recording, then start a race or enter a local singleplayer world.
6. Check for the red **REC** badge and elapsed recording time. You can hide the
   badge with **Show Recording Indicator** without stopping the recording.

Set this up **before entering the world**. Enabling recording in an already
loaded world, or entering before setup finishes, does not start capture midway
through that world. Enter another world after setup is ready.

### Save And Watch

- Room recordings save automatically when a victory, loss, draw, or forfeit
  result is displayed, or when you return to the room.
- Ordinary singleplayer recordings save when you quit to title.
- **Resets continue in one replay file**, including Reset Run and New Seed.
  Loading gaps remain on the recording timeline.
- **Stop Recording** is a manual override, not a required step after a race.

Wait for saving to finish, then open **ReplayMod's Replay Viewer** from the title
screen and select the recording. Files are saved as `.mcpr` in this instance's
`.minecraft/replay_recordings`. ZSG's **Open Recordings** button opens that folder,
not the playback viewer. In MultiMC, use the tested instance's Minecraft folder,
not another installation's `.minecraft`.

With the optional ZSG Replay Viewer companion:

- Use the bottom bar for playback speed, seeking, and camera modes. It auto-hides
  while idle; release the mouse with ReplayMod's cursor key (normally **T**) and
  move it to reveal the controls.
- Press **3** to skip back five seconds or **4** to skip forward. Rebind these
  under Minecraft **Controls > Replay Mod**.
- Choose **Follow Player**, **Follow: Far**, or freecam. Click timeline milestone
  markers to jump to Nether, Bastion, Fortress, Stronghold, or End entries.
- The timer overlay shows recorded RTA/IGT when available. A pause icon identifies
  when the recorded player was paused. Chat is hidden by default during playback.

### Watch Other Racers

Each racer must enable recording on their own client. After the race, manually
share the saved `.mcpr` files. Open your replay, advance past the race start, then
select **Players > Import Replay...** in the companion to import another racer's
file. Select that racer to switch to the same elapsed race time.

Files must contain matching ZSG race metadata; the same seed alone does not group
them. Switching reloads the selected recording and can take time. Automatic
uploading and downloading are not implemented. Replays contain recorded world,
player, and chat data, so share them only with people you intend to receive it.

### Troubleshooting

- **Setup failed or an old custom folder is selected:** use **Replays > Setup >
  Use Minecraft Folder & Set Up**, then wait until ready. The optional folder
  override is for libraries, not where replay files are saved.
- **No replay appears:** check that REC was visible during the run and saving
  has finished. ZSG uses ReplayMod's default `replay_recordings` folder; it does
  not follow a custom ReplayMod recording path.
- **Timers show `--:--`:** record a new replay with SpeedRunIGT installed. Older
  recordings or gaps without timer data cannot display those values.
- **Playback stays on Loading terrain:** World Preview has interfered with
  playback in testing. Try disabling its preview behavior for replay viewing.

See the [companion guide](replay-viewer/README.md) for all controls and
[replay coordination](docs/REPLAY_COORDINATION.md) for solo testing and sharing
details.

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
  later outcomes within those channels. Eye of Ender break rolls use their own
  isolated deterministic sequence, as do gravel flint and player Unbreaking
  rolls. Nether natural
  monster spawning also uses isolated per-chunk cycle streams. Covering
  surrounding Nether chunks helps keep the populations feeding the mob cap more
  consistent. The first activated fortress's eight initial pack opportunities can
  be evaluated even at the global monster cap; failed packs count too. After
  that initial window, normal cap behavior resumes and later fortresses receive
  no protection. Tables, weights, eligibility,
  and spawn validation remain vanilla; this does not guarantee matching mobs.
- Room chat uses TLS in transit through the relay, but it is not end-to-end
  encrypted and should not be treated as private messaging.

## License

ZSG Rooms is available under the [CC0 1.0 license](LICENSE).
