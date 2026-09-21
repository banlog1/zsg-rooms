[CmdletBinding()]
param([string]$Java = 'java', [switch]$Integration, [ValidateRange(0,10000)][int]$BenchmarkBatches = 0)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'FilterBankState.ps1')
$root = Split-Path -Parent $PSScriptRoot
$directory = Join-Path $root ('run/filter-bench/overnight-tests-'+[guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $directory
function Assert($Condition,[string]$Message) { if (-not $Condition) { throw $Message } }
function Expect-Failure([scriptblock]$Action) {
    $failed=$false
    try { & $Action | Out-Null } catch { $failed=$true }
    Assert $failed 'Expected a guarded failure.'
}
$cursor = Join-Path $directory 'cursor.json'
$first = Reserve-FilterRange 7 $cursor
$second = Reserve-FilterRange 13 $cursor
Assert ($second -eq $first+7) 'Reservations overlap or lost their cursor.'
$cursorHash = (Get-FileHash $cursor).Hash
$lock = [IO.File]::Open($cursor+'.lock',[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
try { Expect-Failure { Reserve-FilterRange 4 $cursor } } finally { $lock.Dispose() }
Assert ((Get-FileHash $cursor).Hash -ceq $cursorHash) 'Contended reservation changed the cursor.'
Write-FilterAtomicJson $cursor @{version=1;nextOffset='281474976710655'}
Expect-Failure { Reserve-FilterRange 4 $cursor }
Write-FilterAtomicJson $cursor @{version=1;nextOffset='1000000000000.5'}
Expect-Failure { Reserve-FilterRange 4 $cursor }
[IO.File]::WriteAllText($cursor,'{broken')
Expect-Failure { Reserve-FilterRange 4 $cursor }
Assert ([IO.File]::ReadAllText($cursor) -ceq '{broken') 'Corrupt cursor was silently reset.'

$fixture = Join-Path $directory 'fixture'
$null = New-Item -ItemType Directory -Path (Join-Path $fixture 'jobs')
$null = New-Item -ItemType Directory -Path (Join-Path $fixture 'attempts')
$plan = [pscustomobject]@{policies=[pscustomobject]@{temple=[pscustomobject]@{sisters=4096;familyCap=2}}}
Add-Type -AssemblyName System.Numerics
function New-TestBatch([int]$Id,[long]$Offset,[bool]$Duplicate=$false) {
    $job = [pscustomobject]@{id=('{0:D8}' -f $Id);type='temple';startOffset=$Offset;families=4;state='complete';
        attempt=[guid]::NewGuid().ToString('N');attempts=1;failures=0;bankHash=$null;accepted=2;acceptedFamilies=1}
    $result = Get-OvernightResultDirectory $fixture $job
    $null = New-Item -ItemType Directory -Path $result -Force
    $lower = [long](([Numerics.BigInteger]$Offset*[Numerics.BigInteger]::Parse('11400714819323198485'))%[Numerics.BigInteger]::Pow(2,48))
    $lines = @(foreach ($i in 0..1) {
        $upper = if ($Duplicate) {0L} else {[long]$i}
        @{type='temple';profile='zsg-model-only-v5';status='MODEL_ACCEPTED';seed=($lower+$upper*281474976710656L).ToString();family=$lower.ToString()} | ConvertTo-Json -Compress
    })
    $bank = Join-Path $result 'bank.jsonl'
    [IO.File]::WriteAllLines($bank,[string[]]$lines,[Text.UTF8Encoding]::new($false))
    $job.bankHash=(Get-FileHash $bank).Hash
    Write-FilterAtomicJson (Join-Path $result 'manifest.json') @{state='complete';fixedWorkComplete=$true;workers=1;type='temple';
        startOffset=$Offset;families=4;completedFamilies=4;sisters=4096;familyCap=2;accepted=2;acceptedFamilies=1}
    Write-FilterAtomicJson (Join-Path $fixture ('jobs/'+$job.id+'.json')) $job
    return $job
}
$job0 = New-TestBatch 0 17
$job1 = New-TestBatch 1 21
$export = Export-OvernightBank $fixture $plan
Assert ($export.accepted -eq 4) 'Valid private fixture did not export.'
$bankHash = (Get-FileHash $export.bank).Hash
$null = Export-OvernightBank $fixture $plan
Assert ((Get-FileHash $export.bank).Hash -ceq $bankHash) 'Export is not idempotent.'
$source = Join-Path (Get-OvernightResultDirectory $fixture $job0) 'bank.jsonl'
$saved = [IO.File]::ReadAllText($source)
[IO.File]::AppendAllText($source,"`n")
Expect-Failure { Export-OvernightBank $fixture $plan }
Assert ((Get-FileHash $export.bank).Hash -ceq $bankHash) 'Failed export replaced a valid bank.'
[IO.File]::WriteAllText($source,$saved,[Text.UTF8Encoding]::new($false))
$job1.startOffset=18
Write-FilterAtomicJson (Join-Path $fixture 'jobs/00000001.json') $job1
Expect-Failure { Export-OvernightBank $fixture $plan }
$job1 = New-TestBatch 1 21 $true
Expect-Failure { Export-OvernightBank $fixture $plan }
$job1 = New-TestBatch 1 21
$manifestPath = Join-Path (Get-OvernightResultDirectory $fixture $job1) 'manifest.json'
$manifest = Read-FilterJson $manifestPath
$manifest.fixedWorkComplete=$false
Write-FilterAtomicJson $manifestPath $manifest
Expect-Failure { Export-OvernightBank $fixture $plan }
Write-Output 'Passed: cursor persistence/locking/exhaustion/corruption, idempotent export, committed hashes, overlap and duplicate rejection, partial-batch rejection, atomic publication.'

# Checkpoints are exercised on synthetic private fixtures, never the active bank.
$job1 = New-TestBatch 1 21
$baseline = Export-OvernightBank $fixture $plan
$baselineHash = (Get-FileHash $baseline.bank).Hash
$checkpoint = Read-OvernightCheckpoint $fixture $plan
Assert ($null -ne $checkpoint) 'Valid checkpoint could not be read.'
$fast = Export-OvernightBank $fixture $plan $checkpoint
Assert ($fast.reused -eq 2 -and $fast.verified -eq 0 -and $fast.accepted -eq 4) 'Unchanged history was revalidated.'
Assert ((Get-FileHash $fast.bank).Hash -ceq $baselineHash) 'Checkpoint changed the combined bank.'

$checkpoint = Read-OvernightCheckpoint $fixture $plan
$job2 = New-TestBatch 2 25
$delta = Export-OvernightBank $fixture $plan $checkpoint
Assert ($delta.reused -eq 2 -and $delta.verified -eq 1 -and $delta.accepted -eq 6) 'New completed batch was not verified exactly once.'
$deltaHash = (Get-FileHash $delta.bank).Hash
$full = Export-OvernightBank $fixture $plan
Assert ((Get-FileHash $full.bank).Hash -ceq $deltaHash) 'Incremental and full exports differ.'

$checkpoint = Read-OvernightCheckpoint $fixture $plan
$job1.failures=1
Write-FilterAtomicJson (Join-Path $fixture 'jobs/00000001.json') $job1
$changed = Export-OvernightBank $fixture $plan $checkpoint
Assert ($changed.reused -eq 2 -and $changed.verified -eq 1) 'Changed journal entry bypassed full validation.'

$checkpoint = Read-OvernightCheckpoint $fixture $plan
$job2.state='running'
$job2.bankHash=$null
Write-FilterAtomicJson (Join-Path $fixture 'jobs/00000002.json') $job2
$partial = Export-OvernightBank $fixture $plan $checkpoint
Assert ($partial.accepted -eq 4 -and $partial.reused -eq 2) 'Uncommitted attempt leaked into checkpoint bank.'
$checkpoint = Read-OvernightCheckpoint $fixture $plan
# The supervisor recovers this exact finished attempt, not a new family range.
$recovered = Read-OvernightBatch $fixture $job2 $plan.policies.temple
$job2.state='complete'
$job2.bankHash=$recovered.hash
Write-FilterAtomicJson (Join-Path $fixture 'jobs/00000002.json') $job2
$recoveredExport = Export-OvernightBank $fixture $plan $checkpoint
Assert ($recoveredExport.reused -eq 2 -and $recoveredExport.verified -eq 1 -and $recoveredExport.accepted -eq 6) 'Recovered attempt was lost or duplicated.'
Assert ((Get-FileHash $recoveredExport.bank).Hash -ceq $deltaHash) 'Recovery changed export ordering.'

$checkpointPath = Join-Path $fixture 'checkpoint.json'
$savedCheckpoint = [IO.File]::ReadAllText($checkpointPath)
[IO.File]::WriteAllText($checkpointPath,'{broken')
Assert ($null -eq (Read-OvernightCheckpoint $fixture $plan)) 'Corrupt checkpoint was trusted.'
[IO.File]::WriteAllText($checkpointPath,$savedCheckpoint,[Text.UTF8Encoding]::new($false))
$envelope = Read-FilterJson $checkpointPath
$envelope.hash='wrong'
Write-FilterAtomicJson $checkpointPath $envelope
Assert ($null -eq (Read-OvernightCheckpoint $fixture $plan)) 'Checkpoint checksum was ignored.'
[IO.File]::WriteAllText($checkpointPath,$savedCheckpoint,[Text.UTF8Encoding]::new($false))
$plan.policies.temple.familyCap=3
Assert ($null -eq (Read-OvernightCheckpoint $fixture $plan)) 'Different plan reused checkpoint.'
$plan.policies.temple.familyCap=2
[IO.File]::AppendAllText((Join-Path $fixture 'bank.jsonl'),"`n")
Assert ($null -eq (Read-OvernightCheckpoint $fixture $plan)) 'Bank/checkpoint publication mismatch was ignored.'
$rebuilt = Export-OvernightBank $fixture $plan
Assert ((Get-FileHash $rebuilt.bank).Hash -ceq $deltaHash) 'Full recovery did not repair combined bank.'

$checkpoint = Read-OvernightCheckpoint $fixture $plan
$job2.startOffset=18
Write-FilterAtomicJson (Join-Path $fixture 'jobs/00000002.json') $job2
Expect-Failure { Export-OvernightBank $fixture $plan $checkpoint }
$job2 = New-TestBatch 2 25
$checkpoint = Read-OvernightCheckpoint $fixture $plan
$jobPath=Join-Path $fixture 'jobs/00000001.json'
[IO.File]::Move($jobPath,$jobPath+'.held')
try { Expect-Failure { Export-OvernightBank $fixture $plan $checkpoint } }
finally { [IO.File]::Move($jobPath+'.held',$jobPath) }
$jobPath=Join-Path $fixture 'jobs/00000002.json'
[IO.File]::Move($jobPath,$jobPath+'.held')
try { Expect-Failure { Export-OvernightBank $fixture $plan $checkpoint } }
finally { [IO.File]::Move($jobPath+'.held',$jobPath) }

# Full verification still checks archived shard bytes even when a checkpoint exists.
$checkpoint = Read-OvernightCheckpoint $fixture $plan
[IO.File]::AppendAllText($source,"`n")
$cached = Export-OvernightBank $fixture $plan $checkpoint
Assert ($cached.accepted -eq 6) 'Validated snapshot should not depend on reopening archived shards.'
Expect-Failure { Export-OvernightBank $fixture $plan }
[IO.File]::WriteAllText($source,$saved,[Text.UTF8Encoding]::new($false))
Write-Output 'Passed: checkpoint reuse, delta-only validation, changed jobs, interrupted attempts, full-export parity, checksums, plan binding, crash recovery, range/gap guards and explicit archive verification.'

$empty = Join-Path $directory 'empty'
$null = New-Item -ItemType Directory -Path (Join-Path $empty 'jobs')
$emptyExport = Export-OvernightBank $empty $plan $null @()
$emptyCheckpoint = Read-OvernightCheckpoint $empty $plan
Assert ($null -ne $emptyCheckpoint -and $emptyExport.accepted -eq 0) 'Empty bank checkpoint failed.'
$emptyExport = Export-OvernightBank $empty $plan $emptyCheckpoint
Assert ($emptyExport.verified -eq 0 -and $emptyExport.accepted -eq 0) 'Empty checkpoint resume failed.'

if ($BenchmarkBatches -gt 0) {
    $fixture = Join-Path $directory 'benchmark'
    $null = New-Item -ItemType Directory -Path (Join-Path $fixture 'jobs')
    Write-Output "Creating isolated benchmark fixture with $BenchmarkBatches batches..."
    for ($i=0; $i -lt $BenchmarkBatches; $i++) { $null = New-TestBatch $i (17+4*$i) }
    $clock = [Diagnostics.Stopwatch]::StartNew()
    $full = Export-OvernightBank $fixture $plan
    $fullSeconds = $clock.Elapsed.TotalSeconds
    $expectedHash = (Get-FileHash $full.bank).Hash
    $clock.Restart()
    $checkpoint = Read-OvernightCheckpoint $fixture $plan
    Assert ($null -ne $checkpoint) 'Benchmark checkpoint failed to load.'
    $fast = Export-OvernightBank $fixture $plan $checkpoint
    $fastSeconds = $clock.Elapsed.TotalSeconds
    Assert ($fast.verified -eq 0 -and $fast.reused -eq $BenchmarkBatches) 'Benchmark revalidated cached shards.'
    Assert ((Get-FileHash $fast.bank).Hash -ceq $expectedHash) 'Benchmark export mismatch.'
    [pscustomobject]@{batches=$BenchmarkBatches;fullSeconds=[Math]::Round($fullSeconds,3);
        checkpointSeconds=[Math]::Round($fastSeconds,3);reused=$fast.reused;verified=$fast.verified} | ConvertTo-Json -Compress | Write-Output
}

if ($Integration) {
    $start = Join-Path $PSScriptRoot 'Start-OvernightFilterBank.ps1'
    $smoke = Join-Path $directory 'smoke'
    $null = & $start -Directory $smoke -Workers 2 -Minutes 2 -MaxCompletedBatches 3 -Java $Java -AllowSleep `
        -TempleFamilies 2000 -ShipwreckFamilies 2000 -VillageFamilies 2000 -BatchSeconds 15 -CollectSamples
    $samples = Read-FilterJson (Join-Path $smoke 'samples.json')
    Assert ($samples.Count -gt 0 -and $samples[0].availableGiB -gt 0) 'Memory telemetry was not recorded.'
    Assert (($samples | Measure-Object availableGiB -Minimum).Minimum -gt 0) 'Telemetry array could not be aggregated.'
    $before = @(Get-OvernightJobs $smoke)
    Assert ($before.Count -eq 3 -and @($before | Where-Object {$_.state -ne 'complete'}).Count -eq 0) 'Real queue smoke incomplete.'
    $attempts = ($before | ForEach-Object {$_.attempt}) -join ','
    # Simulate a crash after the worker committed its output but before the supervisor committed its journal.
    $before[0].state='running'
    $before[0].bankHash=$null
    Write-FilterAtomicJson (Join-Path $smoke 'jobs/00000000.json') $before[0]
    $null = & $start -Directory $smoke -Workers 2 -Minutes 2 -MaxCompletedBatches 3 -Java $Java -AllowSleep
    $after = @(Get-OvernightJobs $smoke)
    Assert (($after | ForEach-Object {$_.attempt}) -join ',' -ceq $attempts) 'Restart duplicated completed work.'
    Assert (@($after | Where-Object {$_.state -ne 'complete'}).Count -eq 0) 'Completed attempt was not recovered.'
    Expect-Failure { & $start -Directory $smoke -Workers 5 -MaxCompletedBatches 3 -Java $Java -AllowSleep }
    Expect-Failure { & $start -Directory $smoke -Workers 7 -AllowFullCpu -MaxCompletedBatches 3 -Java $Java -AllowSleep }
    if ([ZsgFilterHost]::PhysicalCores() -ge 6) {
        $null = & $start -Directory $smoke -Workers 6 -AllowFullCpu -MaxCompletedBatches 3 -Java $Java -AllowSleep
        Assert ((Read-FilterJson (Join-Path $smoke 'status.json')).maxWorkers -eq 6) 'Explicit six-worker allowance was rejected.'
    }
    Expect-Failure { & $start -Directory $smoke -Types temple -Java $Java -AllowSleep -Minutes 0.05 }
    Expect-Failure { & $start -Directory $smoke -TempleFamilies 4 -Java $Java -AllowSleep -Minutes 0.05 }
    $savedPlan = Read-FilterJson (Join-Path $smoke 'plan.json')
    $fingerprint = $savedPlan.fingerprint
    $savedPlan.fingerprint='wrong'
    Write-FilterAtomicJson (Join-Path $smoke 'plan.json') $savedPlan
    Expect-Failure { & $start -Directory $smoke -ExportOnly }
    $savedPlan.fingerprint=$fingerprint
    Write-FilterAtomicJson (Join-Path $smoke 'plan.json') $savedPlan
    $overnightLock = [IO.File]::Open((Join-Path $root 'run/model-bank/overnight.lock'),[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    try { Expect-Failure { & $start -Directory $smoke -Java $Java -AllowSleep -Minutes 0.05 } }
    finally { $overnightLock.Dispose() }
    $interrupted = Join-Path $directory 'interrupted'
    $null = & $start -Directory $interrupted -Types temple -TempleFamilies 1000000000 -Workers 1 -Minutes 0.1 -Java $Java -AllowSleep
    $pending = @(Get-OvernightJobs $interrupted)
    Assert ($pending.Count -eq 1 -and $pending[0].state -eq 'pending') 'Deadline did not preserve its interrupted assignment.'
    $offset = $pending[0].startOffset
    $attempt = $pending[0].attempt
    $null = & $start -Directory $interrupted -Workers 1 -Minutes 0.1 -Java $Java -AllowSleep
    $pending = @(Get-OvernightJobs $interrupted)
    Assert ($pending.Count -eq 1 -and $pending[0].startOffset -eq $offset -and $pending[0].attempt -cne $attempt) 'Interrupted assignment was lost instead of resumed.'
    $attempt = $pending[0].attempt
    $null = & $start -Directory $interrupted -ExportOnly
    Assert ((@(Get-OvernightJobs $interrupted))[0].attempt -ceq $attempt) 'Export-only started an interrupted job.'
    [IO.File]::WriteAllText((Join-Path $interrupted 'STOP'),'')
    $attempt = $pending[0].attempt
    $null = & $start -Directory $interrupted -Workers 1 -Minutes 0.1 -Java $Java -AllowSleep
    Assert ((@(Get-OvernightJobs $interrupted))[0].attempt -ceq $attempt) 'STOP launched more work.'
    Assert ((Read-FilterJson (Join-Path $interrupted 'status.json')).state -eq 'stop_requested') 'STOP status not persisted.'
    Write-Output 'Passed: native all-type queue, restart without replaying completed work, completed-attempt recovery, plan guard, singleton lock, hard deadline, interrupted-range resume and STOP drain.'
}
Write-Output "Test reports: $directory"
