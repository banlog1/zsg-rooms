[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [ValidateRange(0.05,30)][double]$MinutesPerTest = 10,
    [switch]$Resume,
    [string]$Java = 'java'
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'FilterBankState.ps1')
if (Test-Path -LiteralPath $OutputDirectory) {
    if (-not $Resume) { throw 'Use a fresh benchmark directory, or Resume completed tests.' }
} else { $null = New-Item -ItemType Directory -Path $OutputDirectory }
$OutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path
$results = [Collections.Generic.List[object]]::new()
foreach ($count in @(4,5,6)) {
    $directory = Join-Path $OutputDirectory ('workers-'+$count)
    if (Test-Path -LiteralPath $directory) {
        if (-not $Resume) { throw 'A benchmark case already exists.' }
        Write-Output "Reading completed $count-worker test without repeating its search."
    } else {
        Write-Output "Starting $count workers for $MinutesPerTest minutes."
        & (Join-Path $PSScriptRoot 'Start-OvernightFilterBank.ps1') -Directory $directory -Workers $count -AllowFullCpu -CollectSamples -Minutes $MinutesPerTest -Java $Java
    }
    $status = Read-FilterJson (Join-Path $directory 'status.json')
    $samples = Read-FilterJson (Join-Path $directory 'samples.json')
    if ($status.state -ne 'deadline' -or $status.maxWorkers -ne $count -or
        [Math]::Abs($samples[-1].seconds-$MinutesPerTest*60) -gt 10) { throw 'Test was incomplete or had a different duration; no scaling comparison is valid.' }
    $jobs = @(Get-OvernightJobs $directory)
    $complete = @($jobs | Where-Object state -eq 'complete')
    $byType = @{}
    foreach ($type in @('temple','shipwreck','village')) {
        $typed = @($complete | Where-Object type -eq $type)
        $byType[$type] = @{batches=$typed.Count;families=($typed | Measure-Object families -Sum).Sum;accepted=($typed | Measure-Object accepted -Sum).Sum}
    }
    $steady = @($samples | Where-Object seconds -ge 30)
    $results.Add([pscustomobject]@{workers=$count;minutes=$MinutesPerTest;state=$status.state;completed=$complete.Count;
        batchesPerMinute=$complete.Count/$MinutesPerTest;byType=$byType;
        minimumAvailableGiB=($samples | Measure-Object availableGiB -Minimum).Minimum;
        peakCommittedGiB=($samples | Measure-Object peakCommittedGiB -Maximum).Maximum;
        averageCpuPercent=($steady | Measure-Object hostCpuPercent -Average).Average;
        averageActiveWorkers=($steady | Measure-Object activeWorkers -Average).Average})
    Write-FilterAtomicJson (Join-Path $OutputDirectory 'summary.json') @($results.ToArray())
    Write-Output "$count workers: $($complete.Count) completed batches; stopped with $($status.state)."
    if ($status.state -ne 'deadline') { throw 'Test stopped early; higher worker counts will not be launched.' }
}
