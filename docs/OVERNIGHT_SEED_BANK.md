# Overnight Seed Bank

The Windows supervisor rotates temple, shipwreck and village batches, with up to
four workers by default at below-normal priority. An explicit `-AllowFullCpu`
allows up to six workers, limited to the physical core count. It uses only the existing model-only v5
finder. No Minecraft generation or filter-criteria changes are involved.

## Start Or Resume

From the repository directory, run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Start-OvernightFilterBank.ps1 -Minutes 480
```

This starts an eight-hour session, or resumes the existing bank for another eight
hours. The bank is `run/model-bank/overnight/main`. Its current configuration uses
all three types, and the tested Java executable is remembered in `plan.json`.
An initial bounded trial has already populated this bank. No overnight session
was started as part of implementation.

On a new installation, build the standalone finder first using
[MODEL_SEED_FINDER.md](MODEL_SEED_FINDER.md). Supply `-Java` on the first launch
if a compatible Java is not on PATH. A new bank can be selected with `-Directory`.

Useful options:

| Option | Default | Meaning |
| --- | --- | --- |
| `-Minutes` | 480 | Session deadline, maximum 1440 minutes |
| `-Workers` | 4 | Maximum concurrent workers, not a promise to launch all four |
| `-AllowFullCpu` | off | Allow up to six workers, capped at physical core count; normally the count is limited to four and physical cores minus two. Memory guards remain active |
| `-CollectSamples` | off | Save five-second host CPU, available RAM, worker peak committed memory and progress samples to `samples.json` on shutdown |
| `-Types` | temple, shipwreck, village | Rotation for a new bank; an existing bank retains its saved selection |
| `-ReserveGiB` | 3 | Emergency available-RAM threshold |
| `-WorkerStartupGiB` | 0.9 | Admission estimate per newly starting worker, not a memory cap |
| `-BatchSeconds` | 300 | Native deadline per batch; incomplete batches are not committed |
| `-MaxRetries` | 2 | Two retries after a batch's first failure, then stop for inspection |
| `-MaxCompletedBatches` | 0 | Optional cumulative completed-batch target for this bank; zero means no target |
| `-AllowSleep` | off | Permit Windows idle sleep instead of holding a temporary system-awake request |
| `-ExportOnly` | off | Recover/export completed work without starting any searches |

The deadline stops search processes; validation/export and cleanup may finish
afterward. Duration, worker count and runtime limits may change between sessions.
Changing filter types or batch sizes requires a fresh bank directory. There are
no automatic reboots, scheduled tasks, cloud purchases or account operations.

`scripts/Benchmark-OvernightWorkers.ps1 -OutputDirectory <new-directory> -Java <java-path>`
runs four, five and six workers sequentially for ten minutes each. It stops the
comparison if a session stops early, including for memory pressure. Each test
uses fresh globally reserved ranges and retains its accepted seeds in its own
bank. Compare completed batches by type, not random seed yield. These are mixed
workload soak tests, not identical-input benchmarks; different ranges and warmup
can affect throughput. No temperature sensors are sampled.
Worker limits are not CPU affinity or utilization caps: the Java processes also
use helper threads. Below-normal priority and the RAM reserve still apply.
Use the benchmark's `-Resume` option to rebuild its summary and skip already
completed tests without repeating their searches. Incomplete tests are rejected,
not silently treated as full-duration measurements.

### Laptop Soak Comparison, 2026-09-14

Ten minutes each, in order 4/5/6, on the i7-8750H with 15.85 GiB RAM, Chrome
closed and AC power connected. Same default mixed-type batch policy and 3 GiB
reserve; fresh disjoint ranges for every test, not identical candidate inputs.

| Workers | Completed batches | Batches/min | Minimum free GiB | Peak worker committed GiB | Average host CPU |
| --- | --- | --- | --- | --- | --- |
| 4 | 103 | 10.3 | 6.68 | 3.54 | 50.5% |
| 5 | 115 | 11.5 | 6.33 | 4.39 | 63.1% |
| 6 | 123 | 12.3 | 6.12 | 5.23 | 73.1% |

Completed temple/shipwreck/village batch counts were 35/34/34, 39/39/37 and
42/41/40 respectively. All tests reached their deadlines without retry warnings
or memory stops. CPU averages exclude the first 30 seconds and include other
host activity. Memory/progress samples are taken every five seconds; the
emergency memory check still runs every supervisor iteration. Committed memory
is not the same as resident physical RAM.

Five delivered about 12% more batches than four; six added about 7% over five.
There was no large late-run throughput decline, but temperatures, power draw
and paging were not measured. This is not a twelve-hour stability guarantee or
an identical-input scaling benchmark. Five is the suggested first overnight
setting; six remains an explicit option. Four remains the default.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Start-OvernightFilterBank.ps1 -Minutes 720 -Workers 5 -AllowFullCpu -CollectSamples
```

Reports and retained private banks are under
`run/filter-bench/overnight-workers-20260914/workers-4`, `workers-5` and
`workers-6`; the parent contains `summary.json`. These are separate from the
main overnight bank. Timed-out in-flight batches remain pending in their own
test banks. No twelve-hour session was launched by this comparison.

## Stop And Monitor

`status.json` in the bank directory updates approximately every 30 seconds with
counts, active workers, available RAM and the UTC deadline. It contains no exact
seed values. Console progress is also seed-free.

To stop gracefully, create an empty file named `STOP` inside the bank directory:

```powershell
New-Item -ItemType File -Path run/model-bank/overnight/main/STOP
```

No new batches will launch. Active batches finish within their deadlines, and
then the bank is exported. The `STOP` file is deliberately left in place; remove
that file before resuming. Ctrl+C or closing the supervisor interrupts active
batches instead. They can be retried next time.

If available RAM drops below the reserve, the supervisor stops its workers and
preserves their assignments for the next session. It does not keep restarting
under memory pressure. When admission headroom is limited, it waits or runs fewer
workers; startup reservations account for children that have not allocated yet.

The awake request prevents ordinary idle sleep while the supervisor runs and is
released on exit. It does not keep the display on or override lid-close actions.
Keep the laptop plugged in, ventilated, and avoid sleep/hibernation or closing the
lid. There is no CPU-temperature or battery monitor. Four-worker short trials
were promising, but an overnight thermal/memory soak has not been completed.

## Where The Seeds Go

- `jobs/`: durable assignments and completion records.
- `attempts/`: private per-attempt banks, reports and worker logs.
- `runtime/`: private snapshot of the native finder and compiled Java models.
- `plan.json`: fixed profile, policies, runtime fingerprint and Java path.
- `bank.jsonl`: consolidated private bank, with the seed type in each record.
- `status.json`: seed-free progress and stop reason.

Each completed batch is committed individually. The consolidated bank is rebuilt
on startup and clean shutdown; it is **not a live append file during the run**.
After a crash, completed shards remain authoritative even if that snapshot is old.
To recover and export without launching searches:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Start-OvernightFilterBank.ps1 -ExportOnly
```

Use `bank.jsonl` as the collection output. Do not concatenate it with its own
attempt files or previous snapshots: those are the same seeds, not extra banks.
Keep all exact-seed files private and out of public commits/logs.

## Avoiding Duplicate Work

1. Every new batch reserves a disjoint lower48 ordinal range through the same
   `run/model-bank/parallel-offset.json` cursor used by ordinary parallel searches.
   An exclusive sibling `.lock` file protects atomic cursor replacement. Stop any
   older pre-update search scripts before using this new locking protocol.
2. The supervisor saves the assignment before launching its worker. Each retry
   uses a fresh attempt directory but the same assignment, not a new range.
3. Completed work is never intentionally rerun. If a worker finished before the
   supervisor crashed, its valid completed output is recovered on restart.
4. A batch is committed only after its complete fixed family budget was searched.
   Time-limited partial batches are retried, not treated as fully searched ranges.
5. Export verifies completion, recorded hashes, profile, type, range membership,
   family caps and unique exact seeds. Duplicate or corrupted data fails closed,
   preserving the last valid exported bank instead of silently dropping entries.

Exactly-once computation across power loss is not promised: an interrupted batch
can replay its unfinished work. Only its successful completed attempt enters the
bank. A crash after global reservation but before assignment can leave a small
unused gap; it cannot cause the cursor to reuse that range.

Default batch sizes are 2 million temple families, 60 million shipwreck families,
and 500,000 village families. Policies remain 4096 sisters/cap 2, 16384/cap 4,
and 1024/cap 2 respectively. Rotation balances batches, not accepted seed counts.
There is no fixed-sized wave: each free worker receives the next batch immediately
when admission permits. Java processes currently restart per batch; keeping them
alive between batches is a separate optimisation, not implemented here.

The compiled runtime is copied and fingerprinted on first setup. Resume verifies
that saved runtime and Java, so rebuilding the normal finder does not silently
change this bank. To adopt new filter logic, create a new bank. Do not edit the
snapshot or supervisor scripts while a session is running.

Back up the entire bank directory **and the shared cursor while all searches are
stopped**. Do not reset, restore an older cursor, remove job records or run clones
of the cursor on different machines. The cursor coordinates this installation,
not manual `-StartOffset` benchmarks or future cloud hosts. Those need separately
allocated ranges. The installation-wide overnight lock prevents accidentally
starting two overnight supervisors. Normal parallel jobs share the cursor but
should not run alongside the overnight supervisor if desktop headroom matters.

## Verification

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Test-OvernightFilterBank.ps1 -Integration -Java "C:/Program Files/Microsoft/jdk-25.0.2.10-hotspot/bin/java.exe"
```

Coverage includes cursor contention/exhaustion/corruption, immutable batch hashes,
duplicate/overlap/partial rejection, atomic export and idempotence, real all-type
queue execution, completed-attempt recovery without rerunning it, session locking,
plan compatibility, deadline interruption, same-range resume, export-only and STOP.
Fixtures and partial failure outputs are isolated under `run/filter-bench`.

The initial normal-sized trial committed six batches with up to four workers:
8 temple and 10 shipwreck seeds, no villages in that short sample. All 18 seeds
were validated on consolidation. This is a functional trial, not a forecast of
overnight yield or a multi-hour reliability test.
