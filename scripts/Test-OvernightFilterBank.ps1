[CmdletBinding()]
param([string]$Java = 'java', [switch]$Integration)
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
