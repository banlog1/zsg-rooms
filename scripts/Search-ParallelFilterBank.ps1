[CmdletBinding()]
param(
    [ValidateSet('temple','shipwreck','village','buried_treasure','ruined_portal')][string]$Type = 'temple',
    [ValidateRange(1,4)][int]$Workers = 2,
    [ValidateRange(4,1000000000)][long]$Families = 1000000000,
    [ValidateRange(1,600)][int]$Seconds = 60,
    [ValidateRange(1,65536)][int]$Sisters = 65536,
    [ValidateRange(1,4)][int]$FamilyCap = 1,
    [ValidateRange(0,281474976710655)][long]$StartOffset,
    [ValidateRange(2,8)][double]$ReserveGiB = 3,
    [ValidateRange(0.9,4)][double]$WorkerStartupGiB = 1.25,
    [switch]$CollectSamples,
    [string]$OutputDirectory,
    [string]$Java = 'java',
    [string]$Finder,
    [string]$ModelDirectory
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'FilterHost.ps1')
. (Join-Path $PSScriptRoot 'FilterBankState.ps1')
$root = Split-Path -Parent $PSScriptRoot
if (-not $PSBoundParameters.ContainsKey('Sisters')) {
    $Sisters = switch ($Type) { 'temple' {4096}; 'shipwreck' {16384}; 'village' {1024}; 'buried_treasure' {4096}; 'ruined_portal' {4096} }
}
if (-not $PSBoundParameters.ContainsKey('FamilyCap')) { $FamilyCap = if ($Type -in @('shipwreck','ruined_portal')) {4} else {2} }
$capacity = Get-FilterHostCapacity
if ($Workers -gt [Math]::Max(1,$capacity.physicalCores-2)) { throw 'Leave at least two physical cores for desktop applications.' }
if ($capacity.availableMemoryGiB -lt $ReserveGiB + $WorkerStartupGiB*$Workers) { throw 'Not enough free memory for the requested workers and desktop reserve.' }
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $root ('run/model-bank/parallel/' + [guid]::NewGuid().ToString()) }
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Use a fresh output directory.' }
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
$directory = (Resolve-Path -LiteralPath $OutputDirectory).Path

if (-not $PSBoundParameters.ContainsKey('StartOffset')) {
    $statePath = Join-Path $root 'run/model-bank/parallel-offset.json'
    $StartOffset = Reserve-FilterRange $Families $statePath
}
if ($StartOffset -gt 281474976710656L-$Families) { throw 'Search range would wrap and repeat families.' }
$manifest = [ordered]@{type=$Type;workers=$Workers;families=$Families;startOffset=$StartOffset;sisters=$Sisters;familyCap=$FamilyCap;seconds=$Seconds;state='running';hostBefore=$capacity;
    reserveGiB=$ReserveGiB;workerStartupGiB=$WorkerStartupGiB}
$manifestPath = Join-Path $directory 'manifest.json'
function Save-Manifest {
    [IO.File]::WriteAllText($manifestPath,($manifest | ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
}
Save-Manifest
$job = $null
$processes = @()
$configs = @()
$watch = [Diagnostics.Stopwatch]::StartNew()
$cpuBefore = [ZsgFilterHost]::CpuTimes()
$previousCpu = $cpuBefore
$samples = [Collections.Generic.List[object]]::new()
$minimumAvailable = $capacity.availableMemoryGiB
try {
    $job = [ZsgFilterHost+WorkerJob]::new()
    $offset = $StartOffset
    for ($i=0;$i -lt $Workers;$i++) {
        $count = [long][Math]::Floor($Families / $Workers)
        if ($i -lt ($Families % $Workers)) { $count++ }
        $config = [ordered]@{type=$Type;families=$count;startOffset=$offset;sisters=$Sisters;familyCap=$FamilyCap;seconds=$Seconds;
            java=$Java;finder=$Finder;modelDirectory=$ModelDirectory;bank=(Join-Path $directory "worker-$i.jsonl")}
        $offset += $count
        $configPath = Join-Path $directory "worker-$i.config.json"
        [IO.File]::WriteAllText($configPath,($config | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
        $workerScript = Join-Path $PSScriptRoot 'FilterParallelWorker.ps1'
        $p = Start-Process -FilePath (Join-Path $PSHOME 'powershell.exe') -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"{0}"' -f $workerScript),'-ConfigPath',('"{0}"' -f $configPath)) `
            -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $directory "worker-$i.log") `
            -RedirectStandardError (Join-Path $directory "worker-$i.err")
        $processes += $p
        $configs += $config
        $job.Assign($p)
    }
    for ($i=0;$i -lt $Workers;$i++) { [IO.File]::WriteAllText((Join-Path $directory "worker-$i.config.json.ready"),'ready') }
    while (@($processes | Where-Object { -not $_.HasExited }).Count) {
        $available = [ZsgFilterHost]::ReadMemory().availablePhysical / 1GB
        $minimumAvailable = [Math]::Min($minimumAvailable,$available)
        if ($CollectSamples) {
            $cpuNow = [ZsgFilterHost]::CpuTimes()
            $totalDelta = $cpuNow[1]-$previousCpu[1]
            $cpuPercent = if ($totalDelta -gt 0) { [Math]::Round(100*(1-($cpuNow[0]-$previousCpu[0])/$totalDelta),1) } else { $null }
            $samples.Add([pscustomobject]@{elapsedMs=$watch.ElapsedMilliseconds;availableGiB=[Math]::Round($available,3);
                hostCpuPercent=$cpuPercent;activeWorkers=@($processes | Where-Object {-not $_.HasExited}).Count;
                peakWorkerCommittedGiB=[Math]::Round($job.PeakCommittedBytes()/1GB,3)})
            $previousCpu = $cpuNow
        }
        if ($available -lt $ReserveGiB) { throw 'Desktop memory reserve reached; stopping workers.' }
        if ($watch.Elapsed.TotalSeconds -gt $Seconds+120) { throw 'Parallel worker deadline exceeded.' }
        foreach ($p in $processes) { if ($p.HasExited -and $p.ExitCode -ne 0) { throw 'A worker failed; keeping partial banks separate.' } }
        Start-Sleep -Milliseconds 500
    }
    foreach ($p in $processes) { $p.WaitForExit(); if ($p.ExitCode -ne 0) { throw 'A worker failed.' } }
    $cpuAfter = [ZsgFilterHost]::CpuTimes()
    $manifest.elapsedMs = $watch.ElapsedMilliseconds
    $manifest.minimumAvailableGiB = [Math]::Round($minimumAvailable,2)
    $manifest.peakWorkerCommittedGiB = [Math]::Round($job.PeakCommittedBytes()/1GB,2)
    $manifest.hostCpuPercent = [Math]::Round(100*(1-($cpuAfter[0]-$cpuBefore[0])/($cpuAfter[1]-$cpuBefore[1])),1)
    $reports = @($configs | ForEach-Object { Get-Content -LiteralPath ($_.bank+'.report.json') -Raw | ConvertFrom-Json })
    $seen = [Collections.Generic.HashSet[string]]::new()
    $familyCounts = @{}
    $combined = Join-Path $directory 'bank.jsonl'
    $pending = Join-Path $directory 'bank.pending'
    Add-Type -AssemblyName System.Numerics
    $modulus = [Numerics.BigInteger]::Pow(2,48)
    $inverse = [Numerics.BigInteger]::ModPow([Numerics.BigInteger]::Parse('11400714819323198485'),[Numerics.BigInteger]::Pow(2,47)-1,$modulus)
    $writer = [IO.StreamWriter]::new([IO.File]::Open($pending,[IO.FileMode]::CreateNew),[Text.UTF8Encoding]::new($false))
    try {
        for ($i=0;$i -lt $Workers;$i++) {
            $native = $reports[$i].results[0]
            if ($native.profile -cne 'zsg-model-only-v5' -or $native.minecraftWorlds -ne 0 -or $native.familyCap -ne $FamilyCap -or
                $native.type -cne $Type -or $reports[$i].streamOffset -ne $configs[$i].startOffset -or
                $native.families -gt $configs[$i].families) { throw 'Invalid worker report.' }
            foreach ($line in [IO.File]::ReadLines($configs[$i].bank)) {
                $row = $line | ConvertFrom-Json
                Assert-FilterBankTypeRules $row
                if ($row.type -cne $Type -or $row.status -cne 'MODEL_ACCEPTED' -or $row.profile -cne 'zsg-model-only-v5' -or
                    -not $seen.Add([string]$row.seed)) { throw 'Invalid or duplicate accepted seed.' }
                $family = ([long]$row.seed -band 281474976710655L).ToString()
                $ordinal = [long](([Numerics.BigInteger]::Parse($family)*$inverse)%$modulus)
                if ($ordinal -lt $configs[$i].startOffset -or $ordinal -ge $configs[$i].startOffset+$native.families) {
                    throw 'Accepted family lies outside its assigned range.'
                }
                if ($FamilyCap -gt 1 -and $row.family -cne $family) { throw 'Invalid private family metadata.' }
                $familyCounts[$family]++
                if ($familyCounts[$family] -gt $FamilyCap) { throw 'Merged bank exceeded its family cap.' }
                $writer.WriteLine($line)
            }
        }
    } finally { $writer.Dispose() }
    if ($seen.Count -ne ($reports | ForEach-Object {$_.results[0].accepted} | Measure-Object -Sum).Sum) { throw 'Accepted count mismatch.' }
    Move-Item -LiteralPath $pending -Destination $combined
    $manifest.accepted = $seen.Count
    $manifest.acceptedFamilies = $familyCounts.Count
    $manifest.completedFamilies = ($reports | ForEach-Object {$_.results[0].families} | Measure-Object -Sum).Sum
    $manifest.fixedWorkComplete = @($reports | Where-Object {$_.results[0].stop -cne 'FAMILY_LIMIT'}).Count -eq 0
    $manifest.state = 'complete'
} catch {
    $manifest.state = 'failed'
    throw
} finally {
    try {
        if ($job) { $job.Dispose() }
        foreach ($p in $processes) {
            try {
                if (-not $p.HasExited) { $p.Kill() }
                $p.WaitForExit()
            } catch { if (-not $p.HasExited) { throw } }
            finally { $p.Dispose() }
        }
    } finally {
        Save-Manifest
        if ($CollectSamples) { $samples | Export-Csv -LiteralPath (Join-Path $directory 'host-samples.csv') -NoTypeInformation }
    }
}
[pscustomobject]$manifest | ConvertTo-Json -Depth 8
Write-Output "Private combined bank: $combined"
