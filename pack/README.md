# Easy-Install Suite

`mods.lock.json` describes the 24 third-party JARs used by the tested Rooms
instance. The release builder adds the freshly built core and viewer for a
26-mod installation.

Both release archives (`zsg-rooms-multimc.zip` and `.mrpack`) use the Modrinth
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
node --test scripts/Build-EasyInstallPack.test.mjs
node scripts/Build-EasyInstallPack.mjs --verify-downloads
.\gradlew.bat stageRelease
```

Only an explicit allowlist enters the ZIP. The builder never reads an installed
MultiMC instance, account, options file, world, recording, or personal config.
Recording remains opt-in, and ReplayMod automatic recording starts disabled.
The launcher retains the user's Java, memory and launch-command settings.
Use Java 17 for the complete suite. The gallery resource pack stays optional.

After publication, extract the released ZIP to a temporary directory and run:

```powershell
node scripts/Build-EasyInstallPack.mjs --verify-release path/to/modrinth.index.json
```

Use the published manifest rather than locally built hashes when CI produced
the release. No custom installer, pre-launch executable or background updater
is installed. Pack import creates a fresh instance; the Rooms updater only
updates the ZSG mods, not the other pinned components.

References: [MultiMC import](https://github.com/MultiMC/Launcher/wiki/Import-Instance),
[MultiMC import implementation](https://github.com/MultiMC/Launcher/blob/develop/launcher/InstanceImportTask.cpp),
[MCSR catalog](https://github.com/Minecraft-Java-Edition-Speedrunning/legal-mods).
