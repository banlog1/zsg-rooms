[CmdletBinding()]
param(
    [string]$Directory,
    [ValidateNotNullOrEmpty()][ValidateSet('temple','shipwreck','village','buried_treasure','ruined_portal')][string[]]$Types = @('temple','shipwreck','village'),
    [ValidateSet('standard','bt-rp')][string]$Preset = 'standard',
    [ValidateRange(1,6)][int]$Workers = 4,
    [switch]$AllowFullCpu,
    [switch]$CollectSamples,
    [ValidateRange(0.05,1440)][double]$Minutes = 480,
    [ValidateRange(1,600)][int]$BatchSeconds = 300,
    [ValidateRange(4,1000000000)][long]$TempleFamilies = 2000000,
    [ValidateRange(4,1000000000)][long]$ShipwreckFamilies = 60000000,
    [ValidateRange(4,1000000000)][long]$VillageFamilies = 500000,
    [ValidateRange(4,1000000000)][long]$BuriedTreasureFamilies = 1000000,
    [ValidateRange(4,1000000000)][long]$RuinedPortalFamilies = 1000000,
    [ValidateRange(0,1000000)][int]$MaxCompletedBatches = 0,
    [ValidateRange(0,5)][int]$MaxRetries = 2,
    [ValidateRange(2,8)][double]$ReserveGiB = 3,
    [ValidateRange(0.9,4)][double]$WorkerStartupGiB = 0.9,
    [switch]$AllowSleep,
    [switch]$ExportOnly,
    [switch]$FullVerify,
    [string]$Java = 'java'
)
$ErrorActionPreference = 'Stop'
if ($PSBoundParameters.ContainsKey('Preset')) {
    if ($PSBoundParameters.ContainsKey('Types')) { throw 'Choose either Preset or Types, not both.' }
    if ($Preset -eq 'bt-rp') { $Types = @('buried_treasure','ruined_portal') }
}
$endUtc = [datetime]::UtcNow.AddMinutes($Minutes)
. (Join-Path $PSScriptRoot 'FilterHost.ps1')
. (Join-Path $PSScriptRoot 'FilterBankState.ps1')
$root = Split-Path -Parent $PSScriptRoot
$bankRoot = Join-Path $root 'run/model-bank'
$null = New-Item -ItemType Directory -Force -Path $bankRoot
if (-not $Directory) { $Directory = Join-Path $bankRoot $(if ($Preset -eq 'bt-rp') {'overnight/bt-rp'} else {'overnight/main'}) }
$null = New-Item -ItemType Directory -Force -Path $Directory
$Directory = (Resolve-Path -LiteralPath $Directory).Path
# Only one overnight supervisor on this installation, even with different bank directories.
$lock = [IO.File]::Open((Join-Path $bankRoot 'overnight.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
$workerJob = $null
$awake = $false
$active = [Collections.Generic.List[object]]::new()
$jobs = [Collections.Generic.List[object]]::new()
$plan = $null
$ready = $false
$failure = $null
$reason = 'deadline'
$lastStatus = [datetime]::MinValue
$seen = [Collections.Generic.HashSet[string]]::new()
$pending = [Collections.Generic.Queue[object]]::new()
$totals = [pscustomobject]@{completed=0;counts=@{temple=0;shipwreck=0;village=0;buried_treasure=0;ruined_portal=0}}
$lastReservedEnd = 0L
$samples = [Collections.Generic.List[object]]::new()
$lastSample = [datetime]::MinValue
$checkpoint = $null
$lastCheckpointCount = 0
$previousCpu = [ZsgFilterHost]::CpuTimes()
$sampleClock = [Diagnostics.Stopwatch]::StartNew()

function Save-Job($Job) { Write-FilterAtomicJson (Join-Path $Directory ('jobs/'+$Job.id+'.json')) $Job }
function Complete-Job($Job) {
    $batch = Read-OvernightBatch $Directory $Job $plan.policies.($Job.type)
    # Persist the validated data before recording its completion in the journal.
    $resultDirectory = Get-OvernightResultDirectory $Directory $Job
    foreach ($name in @('bank.jsonl','manifest.json')) {
        $file = [IO.File]::Open((Join-Path $resultDirectory $name),[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::Read)
        try { $file.Flush($true) } finally { $file.Dispose() }
    }
    foreach ($row in $batch.rows) {
        if (-not $seen.Add($row.seed)) { throw 'Duplicate seed across overnight jobs.' }
    }
    $Job.state = 'complete'
    $Job.bankHash = $batch.hash
    $Job.accepted = $batch.accepted
    $Job.acceptedFamilies = $batch.acceptedFamilies
    Save-Job $Job
    $totals.completed++
    $totals.counts[$Job.type]+=$batch.accepted
}
function Has-CompleteAttempt($Job) {
    if (-not $Job.attempt) { return $false }
    $path = Join-Path (Get-OvernightResultDirectory $Directory $Job) 'manifest.json'
    if (-not [IO.File]::Exists($path)) { return $false }
    try { $manifest = Read-FilterJson $path } catch { return $false }
    return ($manifest.state -ceq 'complete' -and $manifest.fixedWorkComplete)
}
function Save-Status([string]$State) {
    Write-FilterAtomicJson (Join-Path $Directory 'status.json') @{state=$State;updatedUtc=[datetime]::UtcNow.ToString('o');
        deadlineUtc=$endUtc.ToString('o');completedBatches=$totals.completed;assignedBatches=$jobs.Count;activeWorkers=$active.Count;
        maxWorkers=$Workers;accepted=$totals.counts;availableGiB=[Math]::Round([ZsgFilterHost]::ReadMemory().availablePhysical/1GB,2);
        reserveGiB=$ReserveGiB;workerStartupGiB=$WorkerStartupGiB}
    $countsText = ($plan.types | ForEach-Object { "$_=$($totals.counts[$_])" }) -join ', '
    Write-Output ("{0}: {1} completed batches; seeds {2}; active workers={3}." -f
        $State,$totals.completed,$countsText,$active.Count)
}
try {
    Write-Output 'Preparing overnight bank: checking the saved runtime and plan...'
    $capacity = Get-FilterHostCapacity
    $workerLimit = if ($AllowFullCpu) { $capacity.physicalCores } else { [Math]::Max(1,$capacity.physicalCores-2) }
    if (-not $ExportOnly -and ($Workers -gt $workerLimit -or ($Workers -gt 4 -and -not $AllowFullCpu))) {
        throw 'Worker count exceeds the desktop allowance. Use AllowFullCpu to opt into up to six physical cores.'
    }
    $planPath = Join-Path $Directory 'plan.json'
    $runtime = Join-Path $Directory 'runtime'
    $finder = Join-Path $runtime 'seed-finder.exe'
    $models = Join-Path $runtime 'model-finder'
    $requestedPolicies = [ordered]@{
        temple=@{families=$TempleFamilies;sisters=4096;familyCap=2}
        shipwreck=@{families=$ShipwreckFamilies;sisters=16384;familyCap=4}
        village=@{families=$VillageFamilies;sisters=1024;familyCap=2}
        buried_treasure=@{families=$BuriedTreasureFamilies;sisters=4096;familyCap=2}
        ruined_portal=@{families=$RuinedPortalFamilies;sisters=4096;familyCap=4}
    }
    if ([IO.File]::Exists($planPath)) {
        $plan = Read-FilterJson $planPath
        if (-not $PSBoundParameters.ContainsKey('Java') -and $plan.java) { $Java=[string]$plan.java }
        if ($plan.version -ne 1 -or $plan.profile -cne 'zsg-model-only-v5' -or -not $plan.types.Count -or
            @($plan.types | Where-Object {$_ -notin $requestedPolicies.Keys}).Count -or
            (@($plan.types | Select-Object -Unique).Count -ne $plan.types.Count)) { throw 'Invalid overnight plan.' }
        if (($PSBoundParameters.ContainsKey('Types') -or $PSBoundParameters.ContainsKey('Preset')) -and ($Types -join ',') -cne ($plan.types -join ',')) { throw 'Use a new bank directory to change the selected types.' }
        $familyParameters = @{temple='TempleFamilies';shipwreck='ShipwreckFamilies';village='VillageFamilies';buried_treasure='BuriedTreasureFamilies';ruined_portal='RuinedPortalFamilies'}
        foreach ($type in $requestedPolicies.Keys) {
            $policy = $plan.policies.$type
            if (-not $policy) {
                if ($type -in $plan.types) { throw 'Missing policy for a selected type.' }
                continue # Older plans have only the original three policies.
            }
            if ($policy.families -lt 4 -or $policy.families -gt 1000000000 -or
                $policy.sisters -ne $requestedPolicies[$type].sisters -or $policy.familyCap -ne $requestedPolicies[$type].familyCap) {
                throw 'Invalid or incompatible overnight policy.'
            }
            if ($PSBoundParameters.ContainsKey($familyParameters[$type]) -and $policy.families -ne $requestedPolicies[$type].families) {
                throw 'Use a new bank directory to change batch sizes.'
            }
        }
        if ((Get-FilterModelFingerprint $root $Java $finder $models) -cne $plan.fingerprint) { throw 'Saved runtime or Java changed. Use the original runtime or a new bank directory.' }
        $plan | Add-Member -NotePropertyName java -NotePropertyValue (Get-Command $Java).Source -Force
        Write-FilterAtomicJson $planPath $plan
    } else {
        if (@(Get-ChildItem -LiteralPath $Directory -Force).Count) { throw 'New overnight banks require an empty directory; incomplete setup has not been overwritten.' }
        if ((@($Types | Select-Object -Unique).Count) -ne $Types.Count) { throw 'Select each type only once.' }
        $fingerprint = Get-FilterModelFingerprint $root $Java
        $null = New-Item -ItemType Directory -Path $runtime
        Copy-Item -LiteralPath (Join-Path $root 'run/filter-worker/seed-finder.exe') -Destination $finder
        Copy-Item -LiteralPath (Join-Path $root 'run/model-finder') -Destination $models -Recurse
        if ((Get-FilterModelFingerprint $root $Java $finder $models) -cne $fingerprint) { throw 'Runtime changed while creating its private snapshot.' }
        $null = New-Item -ItemType Directory -Path (Join-Path $Directory 'jobs')
        $null = New-Item -ItemType Directory -Path (Join-Path $Directory 'attempts')
        Write-FilterAtomicJson $planPath @{version=1;profile='zsg-model-only-v5';fingerprint=$fingerprint;java=(Get-Command $Java).Source;types=@($Types);policies=$requestedPolicies}
        $plan = Read-FilterJson $planPath
    }
    Write-Output 'Preparing overnight bank: loading checkpoint and job journal...'
    if (-not $FullVerify) { $checkpoint = Read-OvernightCheckpoint $Directory $plan }
    if (-not $checkpoint) { Write-Output 'Full verification required; this first pass can take several minutes for a large bank.' }
    foreach ($job in @(Get-OvernightJobs $Directory $checkpoint)) {
        if ($job.type -notin $plan.types -or $job.families -ne $plan.policies.($job.type).families) { throw 'Job does not match its saved plan.' }
        $jobs.Add($job)
    }
    foreach ($job in $jobs) {
        if ($job.state -ceq 'complete') { continue }
        if (Has-CompleteAttempt $job) { Complete-Job $job }
        else { $job.state='pending'; Save-Job $job }
    }
    # Committed shards are authoritative. A crash during an export cannot corrupt the journal.
    $export = Export-OvernightBank $Directory $plan $checkpoint $jobs.ToArray()
    $checkpoint = $export.checkpoint
    $seen = $export.seen
    Write-Output "Bank ready: $($export.reused) batches reused from checkpoint; $($export.verified) fully verified."
    $totals.completed=0
    $totals.counts=@{temple=0;shipwreck=0;village=0;buried_treasure=0;ruined_portal=0}
    foreach ($job in $jobs) {
        $lastReservedEnd=[Math]::Max($lastReservedEnd,[long]$job.startOffset+[long]$job.families)
        if ($job.state -ceq 'complete') { $totals.completed++; $totals.counts[$job.type]+=$job.accepted }
        else { $pending.Enqueue($job) }
    }
    $ready = $true
    $lastCheckpointCount = $totals.completed
    if ($ExportOnly) { $reason='export_only'; return }
    $endUtc = [datetime]::UtcNow.AddMinutes($Minutes)
    $sampleClock.Restart()
    $previousCpu = [ZsgFilterHost]::CpuTimes()
    if (-not $AllowSleep) { [ZsgFilterHost]::KeepAwake($true); $awake=$true }
    $workerJob = [ZsgFilterHost+WorkerJob]::new()
    Write-Output "Overnight bank: $Directory"
    Write-Output "Stop time (UTC): $($endUtc.ToString('u')). Create a file named STOP in the bank directory to drain the queue."
    while ([datetime]::UtcNow -lt $endUtc) {
        foreach ($entry in @($active.ToArray())) {
            if (-not $entry.process.HasExited -and ([datetime]::UtcNow-$entry.started).TotalSeconds -gt $BatchSeconds+120) {
                $entry.process.Kill()
                $entry.process.WaitForExit()
            }
            if (-not $entry.process.HasExited) { continue }
            $entry.process.WaitForExit()
            if ($entry.process.ExitCode -eq 0 -and (Has-CompleteAttempt $entry.job)) { Complete-Job $entry.job }
            else {
                $entry.job.state='pending'
                $entry.job.failures++
                Save-Job $entry.job
                $pending.Enqueue($entry.job)
                Write-Output "Batch $($entry.job.id) ($($entry.job.type)) failed or exceeded its work deadline; failures=$($entry.job.failures)."
            }
            $entry.process.Dispose()
            $null = $active.Remove($entry)
            if ($entry.job.failures -gt $MaxRetries) { throw 'A batch exceeded its retry allowance; stopping rather than looping overnight.' }
        }
        $available = [ZsgFilterHost]::ReadMemory().availablePhysical/1GB
        if ($CollectSamples -and ([datetime]::UtcNow-$lastSample).TotalSeconds -ge 5) {
            $cpu = [ZsgFilterHost]::CpuTimes()
            $total = [double]($cpu[1]-$previousCpu[1])
            $busy = if ($total -gt 0) { 100*(1-([double]($cpu[0]-$previousCpu[0])/$total)) } else { 0 }
            $samples.Add([pscustomobject]@{seconds=$sampleClock.Elapsed.TotalSeconds;availableGiB=$available;
                peakCommittedGiB=$workerJob.PeakCommittedBytes()/1GB;hostCpuPercent=$busy;
                activeWorkers=$active.Count;completedBatches=$totals.completed})
            $previousCpu=$cpu
            $lastSample=[datetime]::UtcNow
        }
        if ($available -lt $ReserveGiB) { $reason='memory_reserve'; break }
        if ($totals.completed-$lastCheckpointCount -ge 500) {
            Write-Output 'Saving incremental bank checkpoint...'
            $export = Export-OvernightBank $Directory $plan $checkpoint
            $checkpoint = $export.checkpoint
            $lastCheckpointCount = $totals.completed
        }
        if ([datetime]::UtcNow -ge $endUtc) { break }
        $stopping = [IO.File]::Exists((Join-Path $Directory 'STOP'))
        $completeCount = $totals.completed
        if ($MaxCompletedBatches -gt 0 -and $completeCount -ge $MaxCompletedBatches) { $reason='batch_target'; break }
        if ($stopping -and -not $active.Count) { $reason='stop_requested'; break }
        # Newly launched processes may not have allocated their heaps yet; reserve their startup allowance.
        $starting = @($active | Where-Object {([datetime]::UtcNow-$_.started).TotalSeconds -lt 10}).Count
        if (-not $stopping -and $active.Count -lt $Workers -and $available-$starting*$WorkerStartupGiB -ge $ReserveGiB+$WorkerStartupGiB -and
            ($MaxCompletedBatches -eq 0 -or $completeCount+$active.Count -lt $MaxCompletedBatches)) {
            $job = if ($pending.Count) { $pending.Dequeue() } else { $null }
            if (-not $job) {
                $type = $plan.types[$jobs.Count % $plan.types.Count]
                $count = [long]$plan.policies.$type.families
                $offset = Reserve-FilterRange $count (Join-Path $bankRoot 'parallel-offset.json')
                if ($offset -lt $lastReservedEnd) { throw 'Global cursor moved backwards into this bank. No duplicate work launched.' }
                $job = [pscustomobject]@{id=('{0:D8}' -f $jobs.Count);type=$type;startOffset=$offset;families=$count;state='pending';
                    attempt=$null;attempts=0;failures=0;bankHash=$null;accepted=0;acceptedFamilies=0}
                Save-Job $job
                $jobs.Add($job)
                $lastReservedEnd=$offset+$count
            }
            if ($job.failures -gt $MaxRetries) { throw 'Pending batch needs attention before it can be retried.' }
            $job.attempt=[guid]::NewGuid().ToString('N')
            $job.attempts++
            $job.state='running'
            Save-Job $job
            $attempt = Join-Path $Directory ('attempts/'+$job.attempt)
            $null = New-Item -ItemType Directory -Path $attempt
            $configPath = Join-Path $attempt 'config.json'
            $policy = $plan.policies.($job.type)
            Write-FilterAtomicJson $configPath @{type=$job.type;families=$job.families;startOffset=$job.startOffset;seconds=$BatchSeconds;
                sisters=$policy.sisters;familyCap=$policy.familyCap;java=$Java;finder=$finder;modelDirectory=$models;
                reserveGiB=$ReserveGiB;workerStartupGiB=$WorkerStartupGiB;output=(Join-Path $attempt 'result')}
            $script = Join-Path $PSScriptRoot 'FilterOvernightWorker.ps1'
            $process = Start-Process -FilePath (Join-Path $PSHOME 'powershell.exe') -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"{0}"' -f $script),'-ConfigPath',('"{0}"' -f $configPath)) `
                -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $attempt 'worker.log') -RedirectStandardError (Join-Path $attempt 'worker.err')
            $active.Add([pscustomobject]@{job=$job;process=$process;started=[datetime]::UtcNow})
            $workerJob.Assign($process)
            [IO.File]::WriteAllText($configPath+'.ready','ready')
        }
        if (([datetime]::UtcNow-$lastStatus).TotalSeconds -ge 30) {
            Save-Status $(if ($stopping) {'draining'} elseif ($active.Count) {'running'} else {'waiting_for_memory'})
            $lastStatus=[datetime]::UtcNow
        }
        Start-Sleep -Milliseconds 500
    }
} catch { $failure=$_; $reason='error' }
finally {
    try {
        if ($workerJob) { $workerJob.Dispose() }
        foreach ($entry in $active) {
            try {
                if (-not $entry.process.HasExited) { $entry.process.Kill() }
                $entry.process.WaitForExit()
                if ($failure) { continue }
                if (Has-CompleteAttempt $entry.job) { Complete-Job $entry.job }
                else { $entry.job.state='pending'; Save-Job $entry.job }
            } finally { $entry.process.Dispose() }
        }
        $active.Clear()
        if ($ready) {
            $export = Export-OvernightBank $Directory $plan $checkpoint
            Save-Status $reason
            if ($CollectSamples -and $samples.Count) { Write-FilterAtomicJson (Join-Path $Directory 'samples.json') @($samples.ToArray()) }
            Write-Output "Private combined bank: $($export.bank)"
        }
    } finally {
        try { if ($awake) { [ZsgFilterHost]::KeepAwake($false) } }
        finally { $lock.Dispose() }
    }
}
if ($failure) { throw $failure }
