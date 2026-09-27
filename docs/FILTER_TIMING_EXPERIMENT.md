# Filter Timing Experiment

## Scope

September 27, 2026. Follow-up to [the GPU baseline](FILTER_GPU_BASELINE.md).
The production finder and overnight configuration remain unchanged. No seed-bank
import, GPU code, driver or toolkit installation was performed.

The existing search has three clock reads around nearly every cheap lower48
geometry rejection. This overhead is avoidable; moving it to a GPU would not be
the simplest fix. The experiment is compiled only with
`-DZSG_EXPERIMENT_BATCH_TIMING=1`, which the normal build script does not set.

- Sample geometry time once per 1,024 families, without consuming search RNG.
- Keep every stage's reach/reject count exact.
- Poll the outer deadline every 1,024 cheap rejections, and force a check on the
  next outer iteration after any geometry pass / potentially expensive family.
- Keep sister-seed deadline polling unchanged.
- Disable batching for policy-tuning runs, preserving their exact timestamps.
- Label extrapolated geometry timings `geometryTimingMode: sampled_estimate`,
  include the sample count and deadline interval. These estimates may have sampling
  bias and are not used to decide performance gains; compare full search wall time.

The new deadline behavior permits up to 1,023 additional cheap family checks
between clock polls. That is a work bound, not a guaranteed millisecond bound.
Already-started model calls remain non-preemptible, as before. Time-limited runs
can therefore finish at a different prefix; family/target stopping, acceptance
criteria, seed order and family caps are unchanged.

## Comparisons

Single worker, fresh JVMs, same frozen executable per variant, same model
dependencies, no competing game/filter worker. The editor and normal background
applications remained open. No compilation overlapped the measured runs.
All banks are private benchmark artifacts, not additions to the production bank.

First, trace-enabled fixed-work parity checks compared all-decision digests,
accepted bank hashes, family/sister counts and every stage's reach/reject count.
All six profiles passed. Temple used one million families / 95,301 sisters with
three accepted seeds; AA used 50 million / 104,259 sisters with no acceptances.
Village, shipwreck, buried treasure and ruined portal were small 10,000-family,
zero-sister rejection-only smoke tests, not full acceptance coverage.

Then tracing and tuning were disabled for two larger fixed-work repeats. Variant
order was baseline/candidate, then candidate/baseline. Every pair matched stage
counts and accepted files; rejection-by-rejection tracing is deliberately absent
from these performance measurements.

| Profile | Families each | Original seconds | Candidate seconds | Throughput change |
| --- | ---: | ---: | ---: | ---: |
| Temple, repeat 1 | 5,000,000 | 34.616 | 35.991 | -3.8% |
| Temple, repeat 2 | 5,000,000 | 35.911 | 34.689 | +3.5% |
| AA, repeat 1 | 250,000,000 | 71.989 | 60.664 | +18.7% |
| AA, repeat 2 | 250,000,000 | 71.161 | 61.615 | +15.5% |

Combined: AA **17.1% higher throughput / 14.6% less elapsed time**. Temple is
effectively unchanged (-0.2% combined throughput). Each temple run accepted four
seeds in three families; each AA run accepted two seeds from one family. Repeats
visit the same seeds, so do not add these counts together as new bank entries.

These are bounded single-worker measurements, not a confidence interval or a
prediction of sustained five-worker overnight performance. The GPU baseline's
shorter temple timings are not comparable to this five-million-family workload.

## Holdout And Guards

Separate ranges: temple starts at stream 2,000,000 (one million families); AA
starts at 1,537,910,560,030 (100 million families). Tracing remains off and all
stage counts and accepted files match. The holdout AA range accepted zero seeds,
so it validates fixed-work performance/counts, not rare-event yield.

| Holdout pair | Order | Original seconds | Candidate seconds |
| --- | --- | ---: | ---: |
| Temple | candidate first | 10.046 | 8.024 |
| AA, initial | candidate first | 27.245 | 28.712 |
| AA, repeat 1 | candidate first | 29.160 | 23.075 |
| AA, repeat 2 | baseline first | 29.558 | 23.907 |

The initial AA holdout did not improve (about 5% lower throughput). It also
showed slower unchanged biome and Nether stages. This motivated repetitions;
the observation is retained, not discarded as an invalid run. The two additional
AA pairs improved 23.6-26.4%, about 25% combined. Including all three holdout pairs
gives about 13.6% combined improvement. This variability argues for sustained
multi-worker measurements before forecasting overnight gains. The isolated
temple holdout improvement is not enough to override its flat main comparison.

Additional tests passed:

- Unit checks for first/batch-boundary deadline polls, at most 1,023 deferred
  cheap checks, forced refresh after expensive work, exact mode and sample counts.
- Target=1 stops on the identical family/sister prefix and produces the same file.
- A productive AA family is replayed with tracing on: 1,629 sisters, two accepted
  seeds, identical all-decision digest and bank in both builds. This supplements
  the rejection-only AA trace range.
- Policy tuning selects exact timing (one sample and deadline check per family).
- A one-second integration run stops with TIME_LIMIT at 1,033.503 ms. This is
  consistent with a non-preemptible model call, not a hard 1,000 ms guarantee.
- PowerShell syntax checks pass. The production executable hash remains unchanged.

## Artifacts

- Parity/build/unit tests: `run/filter-bench/timing-parity-20260927/`
- Performance: `run/filter-bench/timing-performance-20260927/`
- Holdout: `run/filter-bench/timing-holdout-20260927/`
- Repeated AA holdout: `run/filter-bench/timing-holdout-repeat-20260927/`
- Target/tuning/deadline and productive AA tests: `run/filter-bench/timing-guards-20260927/`
- Production SHA-256: `CA15D5F7E8718CDF17418C654DBC03D1B20720103BCBF3DCDBACE0D293EBE8C7`
- Candidate SHA-256: `DED895631793E19553744D9D220824C8B0894F9104DF23583291C77F34277BB2`

Runtime manifests include input hashes and model fingerprints. Benchmark scripts
refuse incomplete fixed work, mismatched results and reused output directories.
The production executable is never overwritten.

## Repeat

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Benchmark-FilterTiming.ps1 `
    -OutputDirectory run/filter-bench/timing-parity-new -Phase Parity -Repeats 1

powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Benchmark-FilterTiming.ps1 `
    -OutputDirectory run/filter-bench/timing-performance-new -Phase Performance -Repeats 2 `
    -CandidateFinder run/filter-bench/timing-parity-new/candidate.exe

powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Benchmark-FilterTiming.ps1 `
    -OutputDirectory run/filter-bench/timing-holdout-new -Phase Holdout -Repeats 1 `
    -CandidateFinder run/filter-bench/timing-parity-new/candidate.exe

powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Test-FilterTiming.ps1 `
    -OutputDirectory run/filter-bench/timing-guards-new `
    -CandidateFinder run/filter-bench/timing-parity-new/candidate.exe `
    -AaFixtureBank run/filter-bench/timing-performance-new/aa_temple-0-baseline.jsonl
```

Recommendation: retain this isolated CPU candidate as the comparison point for
further GPU investigation. Promoting it to production should retain an explicit
exact-profiling mode and sampled-timing metadata, then remeasure realistic
multi-worker loads. A GPU geometry prototype is still primarily an AA experiment;
these results give no reason to expect it to fix regular temple's biome/Nether
bottlenecks.
