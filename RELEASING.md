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

The release workflow builds and tests the mod, publishes the versioned JAR, and
uploads its SHA-256 checksum. Existing installations discover the release on the
title screen. Updates remain optional, and players may disable checks or skip a
specific version.

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
