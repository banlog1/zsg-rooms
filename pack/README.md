# Easy-Install Suite

`mods.lock.json` describes the 24 third-party JARs used by the tested Rooms
instance. The release builder adds the freshly built core and viewer for a
26-mod installation.

The standard release archives (`zsg-rooms-multimc.zip` and `.mrpack`) use the Modrinth
pack format. MultiMC recognizes the manifest in either extension through
**Add Instance > Import from ZIP**. The archives contain download metadata
and minimal defaults, not redistributed third-party JARs. The launcher
downloads the exact original files and verifies their size and SHA-512 hash.

Download sources are version-pinned Modrinth files, immutable commits of the
Minecraft Java Edition Speedrunning mod catalog, the official ReplayMod
download, and the matching ZSG GitHub release. Filenames, IDs, versions, size,
SHA-1 and SHA-512 were taken from the installed files and checked against the
public downloads. Update a pin deliberately, then run the download verifier;
never silently substitute a newer mod because a URL fails.

Build from the repository root after building both ZSG mods:

```powershell
node --test scripts/Build-EasyInstallPack.test.mjs scripts/Build-ToolscreenPack.test.mjs
node scripts/Build-EasyInstallPack.mjs --verify-downloads
.\gradlew.bat stageRelease
```

Only an explicit allowlist enters the ZIP. The builder never reads an installed
MultiMC instance, account, options file, world, recording, or personal config.
Recording remains opt-in, and ReplayMod automatic recording starts disabled.
Fresh installs include a generated one-setting `options.txt` with music at 0%.
Other sound volumes and game options retain their defaults. Players can change
music normally in Options > Music & Sounds; mod updates do not reset it.
The launcher retains the user's Java, memory and launch-command settings.
Java 17 is recommended for the complete suite. The gallery resource pack stays optional.

After publication, extract the released ZIP to a temporary directory and run:

```powershell
node scripts/Build-EasyInstallPack.mjs --verify-release path/to/modrinth.index.json
```

Use the published manifest rather than locally built hashes when CI produced
the release. No custom installer, pre-launch executable or background updater
is installed by the standard pack. Pack import creates a fresh instance; the Rooms updater only
updates the ZSG mods, not the other pinned components.

## Optional Windows + ToolScreen Archive

`zsg-rooms-multimc-toolscreen.zip` is a separate **MultiMC on Windows** variant.
It uses the same 26 download entries and defaults, plus the official ToolScreen
installer at the archive root, its MIT license, provenance metadata and
`TOOLSCREEN-SETUP.txt`. It does not contain personal configs or an `instance.cfg`.
MultiMC preserves root files when importing the Modrinth manifest and moves
only `overrides` into `.minecraft`. Adding `instance.cfg` would select the native
importer instead and prevent the mod downloads. Do not add it or attempt a
`../` download path. Other launchers may discard nonstandard root files, so do
not advertise this variant as a general-purpose `.mrpack`.

The user imports the ZIP, leaves Minecraft closed, opens **Instance Folder**,
and runs **Toolscreen-1.5.2-double-click-me.exe** once. The upstream installer
handles its launch hook after user interaction. Java 17 is recommended for
the suite. In fullscreen, **Ctrl+I** opens ToolScreen settings. Run the installer
again with Minecraft closed and choose **Uninstall** to remove it. Importing
alone never executes the installer, adds a launch command or changes antivirus
settings. Users may cancel setup and use Rooms without it. See the main README
and generated release installation notes for the complete user instructions.

`toolscreen.lock.json` pins the official release URL, version, source commit,
size and GitHub release-asset SHA-256. `Toolscreen-LICENSE.txt` is the upstream
MIT license from that commit. The initial Windows executable's Authenticode
signature was also checked locally. A signature/hash validates provenance, not
compatibility with every machine; never claim this guarantees crash-free use.
Review the upstream license/signature again when deliberately changing the pin.
The unmodified installer is redistributed only in the optional ZIP; it is
downloaded to ignored `build/` storage, never committed to the repository.

```powershell
.\gradlew.bat easyInstallToolscreenPack
```

The first build downloads the pinned installer. Subsequent builds verify and
reuse `build/toolscreen-download/`; `--offline` is supported once cached. Bad
size/hash, failed downloads and missing offline caches fail closed. No installer
is executed during builds or tests. `stageRelease` builds and stages all three
packs; CI publishes the optional ZIP alongside the unchanged standard downloads.
The ZIP task independently validates its exact file allowlist, embedded installer
hash and unchanged mod manifest/defaults. The Rooms updater does not update
ToolScreen; any subsequent ToolScreen updates are managed by that tool itself.

References: [MultiMC import](https://github.com/MultiMC/Launcher/wiki/Import-Instance),
[MultiMC import implementation](https://github.com/MultiMC/Launcher/blob/develop/launcher/InstanceImportTask.cpp),
[MCSR catalog](https://github.com/Minecraft-Java-Edition-Speedrunning/legal-mods),
[ToolScreen release](https://github.com/jojoe77777/Toolscreen/releases/tag/v1.5.2),
[ToolScreen license](https://github.com/jojoe77777/Toolscreen/blob/4989a74d0143456c4f628a500ed182389fb388b6/LICENSE).
