[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [ValidateRange(0,10)][int]$SampleIndex = 0,
    [ValidateRange(1,4)][int]$MaxWorkers = 3,
    [ValidateRange(1,4)][int]$MinWorkers = 1,
    [ValidateRange(1,4)][int]$WorkMultiplier = 1,
    [ValidateRange(0.9,4)][double]$WorkerStartupGiB = 1.25,
    [string]$Java = 'java'
)
$ErrorActionPreference = 'Stop'
if ($MinWorkers -gt $MaxWorkers) { throw 'Minimum workers must not exceed maximum workers.' }
. (Join-Path $PSScriptRoot 'FilterHost.ps1')
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Use a fresh benchmark directory.' }
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
$search = Join-Path $PSScriptRoot 'Search-ParallelFilterBank.ps1'
$cases = @(@{type='temple';families=6000000},@{type='shipwreck';families=180000000},@{type='village';families=2000000})
$summary = @()
try {
    foreach ($case in $cases) {
        $expectedHash = $null
        $counts = @($MinWorkers..$MaxWorkers)
        if ($SampleIndex % 2) { [array]::Reverse($counts) }
        foreach ($workers in $counts) {
            $hostState = Get-FilterHostCapacity
            if ($hostState.availableMemoryGiB -lt 3+$WorkerStartupGiB*$workers -or $workers -gt [Math]::Max(1,$hostState.physicalCores-2)) {
                Write-Output "Skipping $workers workers: desktop headroom check."
                continue
            }
            $directory = Join-Path $OutputDirectory "$($case.type)-$workers"
            Write-Output "Testing $($case.type) with $workers workers over the same fixed family range."
            $families = $case.families*$WorkMultiplier
            $null = & $search -Type $case.type -Workers $workers -Families $families -Seconds ([Math]::Min(600,180*$WorkMultiplier)) `
                -WorkerStartupGiB $WorkerStartupGiB -CollectSamples `
                -StartOffset (3000000000L+$SampleIndex*10000000000L) -OutputDirectory $directory -Java $Java
            $r = Get-Content -LiteralPath (Join-Path $directory 'manifest.json') -Raw | ConvertFrom-Json
            if ($r.state -ne 'complete' -or -not $r.fixedWorkComplete -or $r.completedFamilies -ne $families) {
                throw 'Fixed-work benchmark incomplete; no scaling comparison is valid.'
            }
            $hash = (Get-FileHash -LiteralPath (Join-Path $directory 'bank.jsonl')).Hash
            if ($expectedHash -and $hash -cne $expectedHash) { throw 'Worker count changed the private seed bank.' }
            $expectedHash = $hash
            $summary += [pscustomobject]@{type=$case.type;workers=$workers;families=$families;startOffset=$r.startOffset;
                workerStartupGiB=$WorkerStartupGiB;elapsedMs=$r.elapsedMs;accepted=$r.accepted;
                acceptedFamilies=$r.acceptedFamilies;minimumAvailableGiB=$r.minimumAvailableGiB;
                peakWorkerCommittedGiB=$r.peakWorkerCommittedGiB;hostCpuPercent=$r.hostCpuPercent;bankHash=$hash}
            Write-Output ("{0:N2}s; {1} seeds / {2} families; free RAM min {3:N2} GiB; worker peak commit {4:N2} GiB." -f
                ($r.elapsedMs/1000),$r.accepted,$r.acceptedFamilies,$r.minimumAvailableGiB,$r.peakWorkerCommittedGiB)
        }
    }
} finally {
    [IO.File]::WriteAllText((Join-Path (Resolve-Path -LiteralPath $OutputDirectory).Path 'summary.json'),
        (ConvertTo-Json -InputObject @($summary) -Depth 6),[Text.UTF8Encoding]::new($false))
}
