# Parallel Seed Bank Search

For queued, resumable overnight collection, use [Overnight Seed Bank](OVERNIGHT_SEED_BANK.md).
This page describes individual batches and the hardware benchmarks.

## Local Use

The Windows runner starts independent model-only search workers. It does not
launch Minecraft or change any acceptance criteria. Build the native finder and
standalone model coordinator using [MODEL_SEED_FINDER.md](MODEL_SEED_FINDER.md)
first. A compatible Java must be on PATH, or supplied with `-Java`.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Search-ParallelFilterBank.ps1 `
    -Type temple -Workers 2 -Seconds 60
```

Use `temple`, `shipwreck`, or `village`. The default worker count is two.
`-Seconds` bounds each native search, not the entire script including JVM startup
and merging. The supervisor has a further 120-second cleanup deadline.
The maximum batch is 600 seconds; run another batch for more work.

Each invocation creates a fresh directory under `run/model-bank/parallel`.
`bank.jsonl` is the completed private bank; `manifest.json` records counts,
timings and memory measurements without exact seed values. Worker banks contain
exact seeds and must remain private. Failed jobs never publish `bank.jsonl`.

Profile defaults remain temple 4096 sisters/cap 2, shipwreck 16384/cap 4 and
village 1024/cap 2. The village policy is still provisional. `-Families` is the
total lower48 family budget across all workers, not a per-worker budget.
This is a time/work-bounded batch tool, not a request for an exact accepted count.

### Desktop Safeguards

- All worker descendants run at below-normal priority in a Windows Job Object.
- Closing the job kills only its descendants, including their Java/native children.
- Startup requires 3 GiB free reserve plus 1.25 GiB per requested worker.
- Available RAM is sampled every 500 ms; falling below the reserve aborts the job.
- At most four workers are allowed, and no more than physical core count minus two
  (with a minimum allowance of one worker).

These are conservative admission rules, not CPU pinning or a hard memory cap.
JVMs can use multiple threads, allocation can jump between samples, and Chrome's
usage can grow. Each worker currently has two JVMs with up to 1 GiB heap each,
plus native/JVM overhead. Measured short-run memory is not a worst-case guarantee.
Use two workers for lighter browsing load. Three and four have now been compared
with Chrome open; see the four-worker trial below for the measured gains and its
explicit startup allowance. Plug the laptop in and watch heat/fan noise on longer runs.

### Nonoverlapping Work

Normal invocations exclusively reserve disjoint ordinal ranges through
`run/model-bank/parallel-offset.json`. The initial cursor is one trillion to stay
away from previous small manually chosen test ranges. The cursor advances by the
whole reserved budget, even on early termination or failure. This deliberately
skips unused work instead of risking silent repeats. Do not delete/reset this
file. Back it up alongside collected banks.

Each ordinal maps bijectively to a lower48 family using the native finder's odd
multiplier. Workers receive contiguous, nonoverlapping subranges. Merging checks
seed uniqueness, private family metadata, assigned range membership, family caps,
profile/type, model-only reports and accepted counts before publishing the bank.

`-StartOffset` bypasses the cursor for repeatable benchmarks. Do not use it for
routine collection or concatenate repeated benchmark banks. Old manual jobs and
separate machines are not coordinated by the local cursor. Before renting multiple
machines, assign disjoint ranges centrally; copying the cursor to every host would
duplicate work. The current runner is not an interruption-resumable Spot scheduler.

## Measured Laptop Trial: 2026-09-14

Intel i7-8750H, 6 physical cores / 12 threads, 15.85 GiB RAM. Chrome stayed open
with 16 processes and about 2.1 GiB summed working set (shared pages can be counted
more than once). No deliberate browser interaction or long thermal test was done.

Each worker count searched exactly the same fixed range for its profile:

| Profile | Families searched | Accepted seeds / families | 1 worker | 2 workers | 3 workers |
| --- | ---: | ---: | ---: | ---: | ---: |
| Temple | 6,000,000 | 11 / 8 | 42.19 s | 29.47 s | 22.92 s |
| Shipwreck | 180,000,000 | 43 / 13 | 48.75 s | 27.39 s | 24.47 s |
| Village | 2,000,000 | 2 / 1 | 73.02 s | 50.30 s | 45.86 s |

SHA-256 bank hashes matched across all worker counts for each profile. Three
workers used at most 2.47 GiB peak job committed memory in this trial, while
available system RAM stayed at or above 5.77 GiB. This is committed memory, not
resident memory. Whole-host average CPU ranged from 42.6% to 53.6% with three
workers; that includes Chrome and other processes, not just the search.

Single samples, fixed order and uneven expensive-family distribution limit these
results. Three workers delivered 1.59-1.99x the one-worker throughput, not 3x.
Do not extrapolate the tiny village acceptance sample into a dependable bank rate.
Four workers were not tested in that initial trial. Reports and private comparison banks are under
`run/filter-bench/parallel-scaling-20260914`.

A separate normal-cursor two-worker temple batch accepted 20 seeds from 14
families in 60.907 seconds, visiting 19,067,007 families. Minimum available RAM
was 6.44 GiB, peak job commit 1.62 GiB. Private bank:
`run/model-bank/parallel-temple-20260914/bank.jsonl`. This was a fresh range,
not another copy of the benchmark bank. It is one throughput sample, not a
guaranteed 20-seed-per-minute rate.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Benchmark-ParallelFilterBank.ps1 `
    -OutputDirectory run/filter-bench/my-parallel-trial -SampleIndex 1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Test-ParallelFilterBank.ps1
```

Both accept `-Java`. The benchmark refuses incomplete work or different banks;
odd sample indexes reverse worker-count order and use a new range. Focused tests
cover job cleanup, failure publication, output collisions, wrap protection,
uneven partitions and persistent disjoint reservations.

### Four-Worker Follow-Up: 2026-09-14

Two fixed ranges per profile were compared with three and four workers, with
Chrome open and being used. The user reported normal Chrome responsiveness.
Each temple range covered 12 million families, shipwreck 360 million, and village
4 million. All twelve jobs completed their full budgets. All six paired bank
hashes matched, including the village pair with no acceptances.

| Profile | Range A: 3 / 4 workers | Range B: 3 / 4 workers | Mean: 3 / 4 workers | Combined throughput gain |
| --- | ---: | ---: | ---: | ---: |
| Temple | 44.526 / 35.425 s | 39.500 / 38.368 s | 42.013 / 36.897 s | 13.9% |
| Shipwreck | 44.797 / 36.789 s | 42.779 / 35.488 s | 43.788 / 36.139 s | 21.2% |
| Village | 65.826 / 63.726 s | 77.637 / 56.043 s | 71.732 / 59.885 s | 19.8% |

The combined gain is total three-worker time divided by total four-worker time,
minus one, not an arithmetic average of the two percentage gains. Range A accepted
23 temple seeds / 13 families, 90 shipwreck / 26 families, and no villages.
Range B accepted 28 / 17, 85 / 24 and 2 / 1 respectively. These are benchmark
banks, not additional independent banks for every worker count.

Four-worker observations:

- Peak job committed memory: 3.16-3.24 GiB.
- Minimum available system RAM across the trials: 4.96 GiB.
- Whole-host average CPU: 43.8-59.2%, including Chrome and other processes.
- No emergency memory-reserve abort or worker failure.
- CPU temperature, power and paging rates were not measured.

These twelve searches total about 9.7 minutes of worker-run wall time, spread
across short batches. This is not a continuous four-worker soak or proof of
overnight thermal/memory stability. Individual gains ranged from 3.0% to 38.5%.
Browser activity, static work distribution, startup/JIT and test order can all
affect the comparison; these two ranges are insufficient to separate their causes.

The default startup estimate remains 1.25 GiB per worker, requiring 8 GiB available
in total for four workers (5 GiB allowance plus the existing 3 GiB reserve). For this controlled trial we
explicitly used `-WorkerStartupGiB 0.9`, requiring 6.6 GiB available in total.
The **3 GiB runtime abort threshold was not lowered**, and JVM heaps were unchanged.
This allowance is an admission estimate, not a memory cap or a worst-case claim.
`-CollectSamples` writes seed-free `host-samples.csv` at approximately 500 ms
intervals, including available RAM, interval host CPU, active workers and cumulative
peak job commit. Sampling is optional on normal searches.

Four ran first for temple and shipwreck in A. Three ran first in B. The four-worker
village A and shipwreck B starts were deferred by the admission guard as Chrome's
memory changed, then retried separately after headroom recovered. Their complete
manifests and bank hashes were checked against the matching three-worker cases;
the original sweep `summary.json` files omit those deferred cases. Use their
individual `manifest.json` files for the full comparison.

Artifacts: `run/filter-bench/four-workers-20260914-a` and
`run/filter-bench/four-workers-20260914-b`. Example reproduction:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Benchmark-ParallelFilterBank.ps1 `
    -OutputDirectory run/filter-bench/my-four-worker-trial -SampleIndex 1 `
    -MinWorkers 3 -MaxWorkers 4 -WorkMultiplier 2 -WorkerStartupGiB 0.9
```

For an explicitly monitored normal batch, with a fresh automatically reserved range:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Search-ParallelFilterBank.ps1 `
    -Type temple -Workers 4 -Seconds 600 -WorkerStartupGiB 0.9 -CollectSamples
```

Both commands accept `-Java`. No runner defaults were changed to four workers.
Four is a reasonable next controlled-batch setting on this laptop. A longer
soak remains untested; the resumable supervisor is now documented separately above. Focused tests now
also cover the explicit allowance, unchanged emergency reserve and telemetry fields.

## Rental Shortlist: 2026-09-14

Budget ceiling: EUR 30, preferably much less. Start with a short hourly trial,
not a monthly server. No resources have been purchased or provisioned.

| Vendor / plan | Published base price | Trial fit |
| --- | --- | --- |
| Hetzner CCX33, Germany/Finland | EUR 0.2219/hour, excluding VAT and IPv4 | First choice to check, subject to capacity |
| DigitalOcean CPU-Optimized, regular, 8 vCPU / 16 GiB | USD 0.25/hour | Hourly alternative; start below eight workers to leave memory headroom |
| DigitalOcean General Purpose, regular, 8 vCPU / 32 GiB | USD 0.375/hour | More memory headroom |

Hetzner lists CCX33 as 8 dedicated vCPUs / 32 GB. Its extracted product page also
shows an unavailable notice; actual regional capacity must be checked in the
console before planning a run. Ten hours is EUR 2.219 base compute, not an all-in
quote. Sources: [CCX specifications](https://www.hetzner.com/cloud/general-purpose/),
[current Germany/Finland rates](https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/).

DigitalOcean's examples are USD, not EUR; account taxes and conversion can add
cost. Ten hours is USD 2.50 or USD 3.75 base compute respectively.
Source: [official Droplet prices](https://www.digitalocean.com/pricing/droplets).

OVHcloud dedicated Rise machines were also considered, but monthly rental and
possible installation fees are a poor first fit for this budget. Recheck its
[current offers](https://eco.ovhcloud.com/en/rise/) if sustained monthly generation
becomes useful. AWS compute/Spot remains another later option, but no region-specific
price or interruption-resilient deployment has been validated here.

### Before Spending

1. Package the native C finder, pinned model dependencies and worker supervisor for
   x86-64 Linux. The new runner uses Windows APIs and is not a Linux deployment.
2. Check Linux banks against the same fixed-work Windows fixtures. A Java-only
   port is insufficient because the Cubiomes executable must also be rebuilt.
3. Rent only after explicit approval, initially for 1-2 hours. Benchmark 2/4/6/8
   workers within measured CPU/RAM limits, with identical fixed work.
4. Compare accepted distinct families per euro as well as seeds per euro. Measure
   actual speedup rather than assuming vCPU counts translate directly into cores
   or linear throughput. This finder has no GPU implementation to benefit from
   a GPU rental.
5. Keep the first trial below EUR 5 including extras, with an external expiry and
   cleanup mechanism. Export and verify private banks before deleting resources.
   A usage alert is not a spending cap, and stopping compute may not stop billing.

For Hetzner specifically, servers remain billable until deleted, even when powered
off. Remove any separately billable resources too after exporting the results.
Source: [billing FAQ](https://docs.hetzner.com/cloud/billing/faq/).
