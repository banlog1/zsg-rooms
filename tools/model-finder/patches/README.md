# Village Model Corrections

`village-1.16.1-corrections.patch` applies to the clean VillageGenerator revision
`ee9e0c6c82aeac3fef9b1ffa3f452a2a1eae6339`. Gradle checks that revision, copies
its Java sources to `build/generated/sources/village`, then applies this patch
without modifying the checkout or Git index. A patch application failure fails
the build.

The patch contains only the two calibrated corrections:

- Invalidate chest-loot confidence only for feature pieces intersecting the
  current generation chunk. Preserve piece placement, iteration and RNG calls.
- Correct the plains `common/well_bottom` jigsaw from `(4, 3, 4), up_south` to
  Minecraft 1.16.1's `(3, 2, 0), up_north`.

The tests in `profotoce59.properties` guard both behaviors. See
`docs/MODEL_SEED_FINDER.md` in the repository root for the offline calibration
results. Do not replace this with per-candidate Minecraft verification.
