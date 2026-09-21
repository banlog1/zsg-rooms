# Optional Filter Gallery

The five 480x270 picker previews and eight 1280x720 loading PNGs in `filter-gallery`
are separate from the mod's resources and are never included in its JAR. The
supplied artwork keeps its original aspect ratio. Full-resolution originals are
not stored in this repository.

Build the optional Minecraft 1.16.1 resource pack:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Build-FilterGalleryPack.ps1
```

The output is `build/distributions/zsg-rooms-filter-gallery.zip`. Put that ZIP in
the instance's `.minecraft/resourcepacks` directory and enable it in Minecraft's
Resource Packs menu. The picker opens on ZSG Rooms and automatically chooses
Gallery with the pack enabled, or Compact without it. You can switch modes for
the current visit; opening the picker again restores the automatic choice. Compact mode does not
load these previews; Gallery falls back to Minecraft item icons for missing art.
Resource reloads pick up pack changes without restarting Minecraft.

Loading art lives in `assets/zsg-rooms/textures/gui/loading`. The files are `bt`,
`dt`, `dt2`, `rp`, `shipwreck1`, `shipwreck2`, `village` and `village2` (all `.png`).
Temple, shipwreck and village each have two variants. Selection is stable per seed
and does not consume gameplay RNG. A missing variant falls back to another image
in its category; a category with no image uses the normal loading screen.
You may omit the entire loading directory for a picker-only pack.

**Settings > Loading Screen > Loading Images** controls the artwork separately from the picker.
The same menu offers Center (default), Top Left, Top Right, Bottom Left and
Bottom Right presets for the loading square and percentage. Without active
artwork, vanilla and WorldPreview keep their own layouts.
Images fill the viewport without stretching, with centered cropping at other
aspect ratios. The vanilla progress square and percentage are drawn above them.
HUD item icons are vanilla assets and do not require this pack.

To replace the previews from five original 16:9 PNGs named `bt`, `dt`, `rp`,
`shipwreck` and `village`, pass `-ImageDirectory <folder>` to the same script.
The import performs a deterministic resize, without cropping or generating artwork.
Use `-LoadingImageDirectory <folder>` to import the eight loading originals at
1280x720. Originals are not modified. The combined ZIP is approximately 15.6 MiB.

Image ZIP distribution is optional and independent of the mod. No automatic
download, relay upload, additional mod dependency or server resource pack is used.

## Visual Test

After building the ZIP, run `./gradlew runFilterPickerTest -PfilterPickerTest`.
This opens an isolated Minecraft instance, checks selection, paging, button/text
bounds, two window sizes and unloading the optional pack, then exits. Screenshots
are written to `run/filter-picker-test/screenshots`. The test mod is not packaged
in the release JAR.

For HUD/loading checks, add `-PseedVisualSmoke`. Add `-PloadingWorldPreview` to
include the locally cached WorldPreview 6.3.1 and SpeedrunAPI JARs in that test
instance only. The test covers desktop/small layouts, missing/disabled artwork,
hidden input controls, resource reload and a real world-generation screen.

Use `-PlobbyRulesSmoke` instead for lobby host/guest permissions, live rule updates,
active-race edit rejection, compact rule tabs and the seed-header size slider.
