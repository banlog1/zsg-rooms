# Offline Structure Screening Prototype

The build now also produces the production `seed-finder.exe` for the
[model-only finder](../../docs/MODEL_SEED_FINDER.md). The comparison executables
and historical notes below are retained for offline calibration only.

The build also produces `spawn-model.exe`, a persistent Cubiomes 1.16.1 spawn
prediction process. See [model-first architecture and calibration](../../docs/FILTER_ARCHITECTURE.md).
Its private stdin accepts signed decimal seeds; stdout begins with the pinned
protocol/revision header and then returns only X/Z pairs. It is an approximate
spawn model, not a block generator. Neither executable ships in the mod.

This native development executable compares Cubiomes structure screening with
the existing Minecraft implementation. It is not bundled in the mod, does not
create worlds and does not supply race seeds. The offline bank currently uses
the persistent Java staged search, not this executable as its backend.

## Build And Compare

Requires GCC on PATH (tested with MSYS2 UCRT64), Git and the project Java setup.
Use the exact reviewed Cubiomes revision in an otherwise clean checkout:

```powershell
git clone https://github.com/Cubitect/cubiomes.git run/filter-reference/cubiomes
git -C run/filter-reference/cubiomes checkout --detach e61f90580cbdd883214a8054670dacae655e59c0
.\gradlew.bat benchmarkFilterStructures -PfilterProbe --offline
```

Skip the clone/checkout when the reviewed checkout already exists. The build
refuses a different revision or dirty source tree. It compiles without
`-ffast-math`, preserves wrapping arithmetic and copies the Cubiomes MIT license
beside the output. This repository does not vendor or redistribute that source.
The executable and reports remain under ignored `run/filter-worker`.

`-PfilterStructureSamples=100000` changes the per-mode corpus size (1-1,000,000).
Each of village, temple and shipwreck is checked with regular and OP distance
limits. The fixed corpus is the low 48 bits of index times an odd 64-bit constant.
The comparison includes rejection order and all accepted structure coordinates,
using matching counts and an ordered 64-bit digest. Both negative and positive
Nether regions are covered. No numeric seed values are printed or stored in the
public comparison report.

The native executable takes `typeIndex opFlag sampleCount` and emits one small
JSON summary. Protocol/revision/sample/digest/count mismatches fail the Gradle
task. This is a differential screening probe, not the future persistent private
candidate IPC protocol. It does not check shipwreck layout, biomes, loot, trees,
lava or complete filter acceptance.

`structure-comparison.json` records the Java warmed-pass wall time, native
reported CPU time and total native process wall time separately. Tiny timings
are noisy and include different runtime costs: do not convert these directly
into an end-to-end speedup claim. Before using a native production backend, add
batched private requests, error/timeout handling, full-seed biome comparisons
and an end-to-end throughput benchmark against the staged Java baseline.

Initial differential run on the development machine: 100,000 cases per mode,
six modes (600,000 total), all counts and coordinate digests matched. Java
warmed-pass wall times were approximately 46-95 ms per mode, native reported
clock times 19-23 ms, and native process wall times 66-230 ms. These single-pass
numbers show why batching/persistent workers matter; they do not measure biome,
loot, world generation or accepted seeds/hour.
