# Overnight Seed Bank

The Windows supervisor supports temple, AA temple, shipwreck, village, buried
treasure and ruined portal batches. The default rotation is temple, shipwreck
and village, with four workers requested at below-normal priority, subject to
the host limits. An explicit `-AllowFullCpu` allows up to six workers, limited
to the physical core count. It uses the existing model-only v5 finder.
No Minecraft generation or filter-criteria changes are involved.

## Start Or Resume

### Buried Treasure And Ruined Portal

From the repository root, use the `bt-rp` preset for alternating BT and RP batches:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Start-OvernightFilterBank.ps1 -Preset bt-rp -Minutes 720 -Workers 5 -AllowFullCpu -CollectSamples
```

This starts or resumes `run/model-bank/overnight/bt-rp`. It does not change any
existing three-type bank. To prepare its frozen runtime without starting workers,
use `-Preset bt-rp -ExportOnly`; that command does not start a search.

| Type | Families per batch | Sister attempts | Accepted seeds per family |
| --- | ---: | ---: | ---: |
| Buried treasure | 1,000,000 | 4,096 | 2 |
| Ruined portal | 1,000,000 | 4,096 | 4 |

BT retains `mapless-regular-v1`; RP requires `frame-completable-v3`, including
the golden axe OR pickaxe rule and existing ignition requirement. Each batch
must finish its assigned family range before commitment. Incomplete work is
retried on that same reservation, never silently counted as completed.

Results consolidate in `run/model-bank/overnight/bt-rp/bank.jsonl`, with
per-type counts in `status.json` and resource samples in `samples.json`.
Rerun the same command to extend the bank; create `STOP` in that directory to
drain it. The shared reservation cursor prevents overlap with earlier runs.
Rotation is by batch, not equal wall time or equal seed counts. Five workers
remain subject to the existing CPU and memory safeguards.

`-BuriedTreasureFamilies` and `-RuinedPortalFamilies` customize batch sizes only
when creating a fresh bank. `-Types` also accepts both names for custom mixed
rotations, but cannot be combined with `-Preset`. Existing saved plans retain
their original type selection, policies and runtime snapshots.

This setup only collects local files. It does not upload seeds or replace the
existing external RP Seedbank in Rooms. The hosted service and publication tools
support mixed BT/RP snapshots, including the RP v3 tool and ignition requirements.
Follow [production operations](../seed-service/PRODUCTION.md), using the latest
plan in the private operator journal. Preserve the pending tail and live slots.

### Standard And Custom Banks

Build the standalone finder as described in [Model Seed Finder](MODEL_SEED_FINDER.md).
Supply `-Java` on the first launch if a compatible Java is not on PATH.

The default command starts or resumes an eight-hour standard bank under
`run/model-bank/overnight/main`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Start-OvernightFilterBank.ps1 -Minutes 480
```

A fresh standard bank rotates temple, shipwreck and village. Existing directories
always retain their saved type selection, search policies and frozen runtime.
Their `plan.json`, not this guide or the directory name, identifies that state.
To adopt updated filtering logic, initialize a new directory; never replace
runtime files inside a saved bank.

For a custom temple/village bank, initialize without starting workers. The
`-Command` form passes the PowerShell type array correctly:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -Command "& './scripts/Start-OvernightFilterBank.ps1' -Directory 'run/model-bank/overnight/custom-temple-village' -Types temple,village -ExportOnly"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Start-OvernightFilterBank.ps1 -Directory run/model-bank/overnight/custom-temple-village -Minutes 720 -Workers 5 -AllowFullCpu -CollectSamples
```

Use a fresh directory name if that example already exists with another runtime.
Check and stop each bank in its own directory. All banks on this installation
share the reservation cursor and overnight supervisor lock; do not reset either.
For AA-only settings and requirements, see [AA Temple Filter](AA_TEMPLE_FILTER.md).

### Options

| Option | Default | Meaning |
| --- | --- | --- |
| `-Minutes` | 480 | Session deadline, maximum 1440 minutes |
| `-Workers` | 4 | Maximum concurrent workers, not a promise to launch all four |
| `-AllowFullCpu` | off | Allow up to six workers, capped at physical core count; normally the count is limited to four and physical cores minus two. Memory guards remain active |
| `-CollectSamples` | off | Save five-second host CPU, available RAM, worker peak committed memory and progress samples to `samples.json` on shutdown |
| `-Types` | temple, shipwreck, village | Rotation for a new bank; an existing bank retains its saved selection |
| `-Preset` | standard | `bt-rp` selects BT/RP and defaults to their separate bank directory; mutually exclusive with `-Types` |
| `-BuriedTreasureFamilies` / `-RuinedPortalFamilies` | 1000000 each | Fixed family work per new BT/RP batch; changing a saved batch policy requires a fresh directory |
| `-ReserveGiB` | 3 | Emergency available-RAM threshold |
| `-WorkerStartupGiB` | 0.9 | Admission estimate per newly starting worker, not a memory cap |
| `-BatchSeconds` | 300 | Native deadline per batch; incomplete batches are not committed |
| `-MaxRetries` | 2 | Two retries after a batch's first failure, then stop for inspection |
| `-MaxCompletedBatches` | 0 | Optional cumulative completed-batch target for this bank; zero means no target |
| `-AllowSleep` | off | Permit Windows idle sleep instead of holding a temporary system-awake request |
| `-ExportOnly` | off | Recover/export completed work without starting any searches |
| `-FullVerify` | off | Revalidate all completed batch files instead of reusing a checkpoint |

The deadline stops search processes; validation/export and cleanup may finish
afterward. Duration, worker count and runtime limits may change between sessions.
Changing filter types or batch sizes requires a fresh bank directory. There are
no automatic reboots, scheduled tasks, cloud purchases or account operations.

### Checkpointed Resume

Resumes use a checksum-protected `checkpoint.json` paired with `bank.jsonl`.
An existing bank without a checkpoint must verify its journal once to create it.
Subsequent starts load the validated combined bank, enumerate the job journal,
and fully validate only new or changed completed entries. Pending attempts still
follow normal recovery: reuse a completed attempt or retry its reserved range.
Runtime fingerprints, range overlap, missing assignments and duplicate-seed
guards remain in place. Startup reports its recovery stages and progress;
the requested `-Minutes` search duration starts after startup recovery finishes.

Checkpoints are refreshed every 500 newly completed batches and on shutdown.
The two files are published individually using atomic replacements; if a crash
leaves them mismatched, the next start performs full verification. Missing,
damaged or wrong-plan checkpoints also take that full path. Original per-batch
files remain available for recovery. A checksum is an accidental-corruption
guard, not protection against deliberate edits to both data and checksums.

The fast path treats checkpointed completed shards as immutable archives. It
checks journal file length and modification time, but does not reopen or hash
every historical shard on every launch. To audit the archived shard contents,
force full verification. With the supervisor stopped, this also rebuilds the
checkpoint without starting any search workers:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Start-OvernightFilterBank.ps1 -Directory run/model-bank/overnight/main -ExportOnly -FullVerify
```

Keep the entire bank directory, including its journal, attempts and checkpoint.
Do not edit or delete history to speed up a resume, or change supervisor scripts
while a session is active.

The isolated checkpoint tests cover cache reuse, delta validation, corruption,
changed plans/jobs, completed-attempt recovery, missing/overlapping assignments,
empty banks, and byte-for-byte equality with full export. An optional synthetic
benchmark runs without accessing the live bank or reserving search ranges:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Test-OvernightFilterBank.ps1 -BenchmarkBatches 1000
```

A historical 1,000-batch synthetic test measured 5.78 seconds for full export
and 0.82 seconds for checkpoint load/export. This is not a fixed startup-time
guarantee; disk cache, bank size and other applications still matter.

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
lid. There is no CPU-temperature or battery monitor; short worker comparisons
are not a guarantee of overnight thermal or memory stability.

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
   An exclusive sibling `.lock` file protects atomic cursor replacement.
   Never run legacy scripts that bypass this locking protocol alongside it.
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
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Test-OvernightFilterBank.ps1 -Integration
```

Use a compatible Java on PATH, or supply its local executable with `-Java`.
Coverage includes cursor contention/exhaustion/corruption, immutable batch hashes,
duplicate/overlap/partial rejection, atomic export and idempotence, real all-type
queue execution, completed-attempt recovery without rerunning it, session locking,
plan compatibility, deadline interruption, same-range resume, export-only and STOP.
Fixtures and partial failure outputs are isolated under `run/filter-bench`.
