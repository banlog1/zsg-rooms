# Releasing ZSG Rooms

The in-mod updater reads the latest public release from:

`https://api.github.com/repos/banlog1/zsg-rooms/releases/latest`

Create a public GitHub repository named `zsg-rooms` under `banlog1`, push this
project, and publish releases by pushing a version tag:

```powershell
git tag v1.0.1
git push origin v1.0.1
```

The release workflow builds and tests the mod, publishes the versioned JAR, and
uploads its SHA-256 checksum. Existing installations discover the release on the
title screen. Updates remain optional, and players may disable checks or skip a
specific version.

For a different repository, place its latest-release API URL in:

`config/zsg-rooms-update-url.txt`
