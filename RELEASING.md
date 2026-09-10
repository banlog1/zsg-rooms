# Releasing ZSG Rooms

For local build, relay, and protocol-development instructions, see
[`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md).

The in-mod updater reads the latest public release from:

`https://api.github.com/repos/banlog1/zsg-rooms/releases/latest`

Releases are published by pushing a version tag that matches the final
`mod_version` in `gradle.properties`:

```powershell
.\gradlew.bat clean test build
git tag -a v1.0.12 -m "ZSG Rooms 1.0.12"
git push origin main
git push origin v1.0.12
```

The release workflow builds/tests the core, isolated recording adapter, and
optional replay viewer. One GitHub release publishes three JARs: the core mod,
the viewer mod, and the viewer's source archive, plus the core's SHA-256 file.
Existing installations discover the core update on the title screen. Updates
remain optional, and players may disable checks or skip a specific version.

## Two-Mod Packaging

The core version remains `mod_version` in `gradle.properties`. The companion's
independent version is `viewer_version` in `replay-viewer/gradle.properties`.
Bump the latter when shipping companion changes; its JAR name and mod metadata
are generated from this one value. Core tags remain `v<core version>`; do not
create viewer-only `v*` tags because the core updater reads this release feed.

The viewer requires ReplayMod **1.16.1-2.6.27**, Minecraft 1.16.1, and Fabric
Loader 0.19.3 or newer. ReplayMod and the viewer remain optional for core racing
and recording. ReplayMod is downloaded separately by players, not bundled in
either release JAR. The core updater neither installs nor updates playback mods.
SpeedRunIGT is optional for recorded timer data, not a viewer dependency.

For a full release check on JDK 25, run from the repository root:

```powershell
.\gradlew.bat -p replay-prototype clean build fetchReplayModReference
.\gradlew.bat clean test build
.\gradlew.bat -p replay-viewer clean build
.\gradlew.bat stageRelease
```

The first command downloads and checksum-verifies the pinned ReplayMod build
reference. A fresh checkout needs network access for dependencies; do not use
`--offline` after cleaning the reference directory. Never commit or publish that
reference JAR or the external recording-library directory.

`stageRelease` checks artifact IDs, embedded versions, dependency boundaries,
and license/source presence, then replaces `build/release` with the exact assets
and `release-details.md`. It fails if the viewer has not been built at the selected
version. Push CI exercises the same packaging check without publishing anything.
Tag CI passes the tag version into the core build and staging task.

The core retains CC0-1.0; the viewer includes its GPL-3.0-or-later license in its
binary and source JARs. Complete build scripts/source are also available from
the tagged repository archive. Keep those module boundaries explicit.

**Older updater compatibility:** attach only the core `.jar.sha256` file.
Older clients select any `.sha256` asset when GitHub's asset digest is missing.
The new parser matches the exact core filename, but publishing two checksum
files would still risk breaking old installations. Companion binary/source
checksums are included in the generated release details instead.

Release notes always append the generated download/compatibility instructions.
Players need only the core JAR for racing; custom playback needs the companion
and separately installed ReplayMod. The `-sources.jar` is not an installable mod.

## Notes And Tags

Write player-facing release notes in `docs/releases/<version>.md` before tagging.
For a stable release after experimental builds, cover all changes since the last
stable release, including the experimental builds' additions and any limitations.
The workflow publishes that file when present, otherwise it generates GitHub notes.

Experimental versions use a prerelease suffix and are marked as GitHub
prereleases automatically:

```powershell
git tag -a v1.0.25-experimental.1 -m "ZSG Rooms 1.0.25 experimental 1"
git push origin main
git push origin v1.0.25-experimental.1
```

GitHub excludes prereleases from the `/releases/latest` endpoint, so the normal
in-mod updater does not offer experimental builds to existing installations.

For a different repository, place its latest-release API URL in:

`config/zsg-rooms-update-url.txt`
