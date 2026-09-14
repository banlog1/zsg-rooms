[CmdletBinding()]
param([string]$Java = 'java')
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'FilterHost.ps1')
$root = Split-Path -Parent $PSScriptRoot
$directory = Join-Path $root ('run/filter-bench/parallel-tests-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $directory | Out-Null
$search = Join-Path $PSScriptRoot 'Search-ParallelFilterBank.ps1'
function Expect-Failure([scriptblock]$Action, [string]$Message) {
    $failed = $false
    try { & $Action | Out-Null } catch {
        if ($_.Exception.Message -notlike "*$Message*") { throw }
        $failed = $true
    }
    if (-not $failed) { throw "Expected failure: $Message" }
}

# The job must terminate only its own child, even if the worker never reaches Java.
$job = [ZsgFilterHost+WorkerJob]::new()
$child = $null
try {
    $child = Start-Process (Join-Path $PSHOME 'powershell.exe') -WindowStyle Hidden -PassThru `
        -ArgumentList @('-NoProfile','-Command','Start-Sleep -Seconds 60')
    $job.Assign($child)
    $job.Dispose()
    if (-not $child.WaitForExit(10000)) { throw 'Job close left its child running.' }
} finally {
    $job.Dispose()
    if ($child) {
        if (-not $child.HasExited) { $child.Kill(); $child.WaitForExit() }
        $child.Dispose()
    }
}

$failedDirectory = Join-Path $directory 'failed-worker'
Expect-Failure {
    & $search -Workers 2 -Families 5 -Seconds 1 -StartOffset 8000000000 `
        -OutputDirectory $failedDirectory -Java (Join-Path $directory 'missing-java.exe')
} 'worker failed'
$failed = Get-Content (Join-Path $failedDirectory 'manifest.json') -Raw | ConvertFrom-Json
if ($failed.state -ne 'failed' -or (Test-Path (Join-Path $failedDirectory 'bank.jsonl'))) {
    throw 'Failed work was published as a finished bank.'
}
Expect-Failure { & $search -OutputDirectory $failedDirectory } 'fresh output directory'
Expect-Failure {
    & $search -Workers 1 -Families 5 -StartOffset 281474976710654 -OutputDirectory (Join-Path $directory 'wrap')
} 'wrap and repeat'

# Real cursor reservations must be disjoint; do not reset the persistent cursor after testing.
$previous = $null
foreach ($index in 0..1) {
    $output = Join-Path $directory "reservation-$index"
    $null = & $search -Workers 2 -Families 5 -Seconds 15 -Java $Java -OutputDirectory $output `
        -WorkerStartupGiB 0.9 -CollectSamples
    $result = Get-Content (Join-Path $output 'manifest.json') -Raw | ConvertFrom-Json
    $first = Get-Content (Join-Path $output 'worker-0.config.json') -Raw | ConvertFrom-Json
    $second = Get-Content (Join-Path $output 'worker-1.config.json') -Raw | ConvertFrom-Json
    if ($result.state -ne 'complete' -or -not $result.fixedWorkComplete -or $result.completedFamilies -ne 5 -or
        $first.families -ne 3 -or $second.families -ne 2 -or $second.startOffset -ne $first.startOffset+3) {
        throw 'Uneven range partition lost or repeated work.'
    }
    if ($previous -and $result.startOffset -lt $previous.startOffset+$previous.families) {
        throw 'Persistent reservations overlap.'
    }
    $samples = @(Import-Csv -LiteralPath (Join-Path $output 'host-samples.csv'))
    if ($result.reserveGiB -ne 3 -or $result.workerStartupGiB -ne 0.9 -or -not $samples.Count) {
        throw 'Benchmark allowance or telemetry missing; emergency reserve must remain unchanged.'
    }
    foreach ($sample in $samples) {
        if ([double]$sample.availableGiB -le 0 -or [double]$sample.hostCpuPercent -lt 0 -or
            [double]$sample.hostCpuPercent -gt 100 -or [int]$sample.activeWorkers -gt 2 -or
            [double]$sample.peakWorkerCommittedGiB -le 0) { throw 'Invalid host telemetry sample.' }
    }
    $previous = $result
}
Write-Output 'Passed: job cleanup, failed-worker isolation, unpublished failures, output collision, wrap protection, uneven partition, persistent disjoint reservations and guarded benchmark telemetry.'
Write-Output "Test reports: $directory"
