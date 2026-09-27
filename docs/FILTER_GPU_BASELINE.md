# GPU Filtering Baseline

Follow-up: [isolated CPU timing experiment and A/B results](FILTER_TIMING_EXPERIMENT.md).

## Scope

September 27, 2026: CPU baseline and toolchain assessment before a GPU prototype.
No GPU kernel, production acceptance change, seed-bank import, range reservation,
driver change or toolkit installation was performed. These are short single-worker
screening measurements, not sustained overnight or multi-worker throughput claims.

Hardware: i7-8750H (6 cores / 12 threads), GTX 1050 Ti (4096 MiB VRAM).
About 6.8 GiB system RAM was free before the run. No game or seed-search workers
were running; the editor and normal background applications remained open.
The GPU initially reported 47 C and 0% utilization. CPU temperature and power
were not sampled, so these runs do not establish thermal behavior.

## Full Search

`Measure-FilterGpuBaseline.ps1` snapshots the installed finder and fingerprints
its model dependencies. Tracing and policy tuning are disabled; existing native
per-stage timers remain enabled. Each case uses 4096 sisters, family cap two,
and a deliberately unreachable acceptance target. A time limit is a safety guard:
only FAMILY_LIMIT completions qualify as comparable fixed work. Repeated ranges
are intentional and must not be merged into a production bank.

| Profile | Families per run | Sisters | Accepted / productive families | First search s | Repeat search s |
| --- | ---: | ---: | ---: | ---: | ---: |
| Temple | 1,000,000 | 95,301 | 3 / 2 | 8.456 | 11.869 |
| AA temple | 250,000,000 | 458,144 | 2 / 1 | 72.638 | 72.513 |

Order: temple, AA, AA, temple. Each search has a fresh coordinator JVM. Both
repeats matched family/sister counts, accepted and productive-family counts,
acceptance digest, and bank SHA-256. This does not compare a digest of every
rejection because tracing is off. There are only five unique accepted seeds
across these benchmark ranges, not ten new seeds from the repetitions.

| Recorded stage | Temple s, first / repeat | AA s, first / repeat |
| --- | ---: | ---: |
| Structure geometry | 0.131 / 0.145 | 28.245 / 28.285 |
| Biomes | 4.080 / 5.464 | 18.941 / 18.681 |
| Nether model, including IPC | 3.595 / 4.628 | 10.164 / 10.377 |

Geometry is about 39% of AA elapsed time but only 1-2% for temple in these
ranges. The short temple run varies materially; do not treat its accepted/minute
rate as a bank-fill estimate. The per-stage totals do not account for all loop,
deadline, timer and IPC bookkeeping.

Private reports: `run/filter-bench/gpu-baseline-20260927/`.
Frozen and production executable SHA-256 both:
`CA15D5F7E8718CDF17418C654DBC03D1B20720103BCBF3DCDBACE0D293EBE8C7`.

## Isolated Geometry

`geometry_benchmark.c` includes the current finder's helpers in a diagnostic-only
executable. It repeats the temple/AA initial geometry predicate: bastion within
96 per axis, fortress within 256 per axis, then origin-region temple within 320
per axis. Negative Nether regions use the original `nearby` helper and Cubiomes.
This is not an independent RNG implementation or a GPU equivalence test.

The timed variant emulates the outer deadline read and two geometry-stage clock
reads per family. The other variant measures only the whole batch. Both include
a matching digest of surviving seeds and selected structure coordinates.
Three repetitions alternate order; every pass checks ten million families.

- Batch-only times: 1050.512, 959.326, 953.999 ms; median 959.326 ms.
- Per-family timing: 1543.669, 1526.054, 1490.277 ms; median 1526.054 ms.
- Every pass found 266,658 geometry survivors, with digest `3d6be38ff4604efb`.
- Instrumented loop takes about 1.59x as long in this isolated test.

The diagnostic reuses helpers but has different compiler context and omits the
rest of the production loop. It is evidence to test sampled/batched bookkeeping,
not proof of a 37% whole-search improvement. Extrapolating its median difference
to 250 million families gives about 14 seconds, but only a full-search A/B test
can establish the realizable saving.

Private results: `run/filter-bench/gpu-geometry-20260927-b/`.
The first build directory is an unsuccessful build and contains no valid timings.

## Toolchain And Recommendation

NVIDIA driver utilities and runtime DLLs are present. `nvcc`, CUDA environment
variables and the standard CUDA toolkit directory are absent. MSVC toolsets
14.44 and 14.51 exist outside PATH under Visual Studio 2026; GCC is available.
Do not assume either MSVC toolset works with an older CUDA compiler without a
compile check. Pascal requires CUDA 12.x or earlier for native compilation;
CUDA 13 removed offline compilation for architectures before Turing.

Official references:
- [CUDA architecture support](https://developer.nvidia.com/blog/navigating-gpu-architecture-support-a-guide-for-nvidia-cuda-developers/)
- [CUDA 12.9 Windows installation and compiler requirements](https://docs.nvidia.com/cuda/archive/12.9.1/cuda-installation-guide-microsoft-windows/index.html)

Current bottleneck: AA spends substantial time screening lower48 families;
regular temple is dominated by biome/Nether modeling. Some AA overhead is
self-inflicted by per-family instrumentation, while required structure arithmetic
remains a genuine cost.

Best incremental test: batch/sample clock bookkeeping in a separate finder build,
with bounded deadline polling and unchanged acceptance/order. Use trace-enabled
parity runs separately from trace-disabled performance runs. The microbenchmark
suggests meaningful AA headroom; no full-search saving is established yet.

Best GPU experiment: an isolated, batched AA geometry screen, comparing every
decision and surviving coordinate to Cubiomes, including negative regions and
RNG edge cases. Keep all later checks on CPU initially. Measure transfers,
compaction and CPU consumption too. A geometry-only GPU path is unlikely to
substantially accelerate regular temple based on this sample.

Recommendation: test the cheap CPU bookkeeping alternative before a large
toolchain installation. If a GPU experiment follows, compare it against that
improved CPU baseline, not just the instrumented original. Require a sustained
end-to-end improvement before integrating a bounded queue into overnight search.
No estimate here is a measured GPU speedup.

## Repeat

Run serially, without other search workers, using fresh directories:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Measure-FilterGpuBaseline.ps1 `
    -OutputDirectory run/filter-bench/gpu-baseline-new -SecondsPerCase 240

powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Measure-FilterGeometry.ps1 `
    -OutputDirectory run/filter-bench/gpu-geometry-new -Families 10000000
```

Neither command modifies the production finder or overnight cursor. The geometry
build requires the pinned clean Cubiomes checkout and reuses no Minecraft world.
