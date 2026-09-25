[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [ValidateRange(10, 300)][int]$SecondsPerCase = 60,
    [ValidateRange(0, 100)][int]$SampleIndex = 0,
    [ValidateSet('All', 'temple', 'shipwreck', 'village', 'ruined_portal', 'buried_treasure')][string]$Type = 'All',
    [ValidateRange(0, 281474976710655)][long]$Stream = 0,
    [string]$Java = 'java'
)

$ErrorActionPreference = 'Stop'
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Use a fresh benchmark directory.' }
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
$OutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path
$search = Join-Path $PSScriptRoot 'Search-FilterCandidates.ps1'
$cases = @(
    @{type='temple'; sisters=65536; cap=1},
    @{type='temple'; sisters=4096; cap=2},
    @{type='temple'; sisters=4096; cap=4},
    @{type='shipwreck'; sisters=65536; cap=1},
    @{type='shipwreck'; sisters=16384; cap=4},
    @{type='shipwreck'; sisters=16384; cap=2},
    @{type='village'; sisters=256; cap=1},
    @{type='village'; sisters=1024; cap=2},
    @{type='village'; sisters=4096; cap=4},
    @{type='ruined_portal'; sisters=4096; cap=2},
    @{type='ruined_portal'; sisters=4096; cap=4},
    @{type='buried_treasure'; sisters=65536; cap=2},
    @{type='buried_treasure'; sisters=16384; cap=2},
    @{type='buried_treasure'; sisters=4096; cap=2},
    @{type='buried_treasure'; sisters=1024; cap=2}
)
if ($Type -ne 'All') { $cases = @($cases | Where-Object { $_.type -eq $Type }) }
if ($SampleIndex % 2) { [array]::Reverse($cases) }
$oldTune = $env:ZSG_MODEL_TUNE
$oldTrace = $env:ZSG_MODEL_TRACE
$env:ZSG_MODEL_TUNE = '0'
$env:ZSG_MODEL_TRACE = '0'
$summary = @()
try {
    foreach ($case in $cases) {
        $searchStream = if ($PSBoundParameters.ContainsKey('Stream')) { $Stream } else { 400000000L + $SampleIndex * 1000000000L }
        $bank = Join-Path $OutputDirectory "$($case.type)-$($case.sisters)-$($case.cap).jsonl"
        Write-Output "Testing $($case.type): $($case.sisters) attempts, cap $($case.cap), $SecondsPerCase seconds."
        $null = & $search -Type $case.type -Sisters $case.sisters -FamilyCap $case.cap -Target 1000000 `
            -Families 1000000000 -Seconds $SecondsPerCase -Stream $searchStream -OutputFile $bank -Java $Java
        $report = Get-Content -LiteralPath ($bank + '.report.json') -Raw | ConvertFrom-Json
        $native = $report.results[0]
        if ($native.policyEstimates -or $native.traceEnabled -or $native.minecraftWorlds -ne 0) { throw 'Invalid benchmark configuration.' }
        $record = [pscustomobject]@{
            type=$case.type; sisters=$case.sisters; familyCap=$case.cap; streamOffset=$searchStream
            accepted=$native.accepted; productiveFamilies=$native.acceptedFamilies
            elapsedMs=$native.elapsedMs; processMs=$report.processWallMs
            seedsPerMinute=60000*$native.accepted/$native.elapsedMs
            familiesPerMinute=60000*$native.acceptedFamilies/$native.elapsedMs
        }
        $summary += $record
        Write-Output ("Accepted {0} from {1} families: {2:N2} seeds/min, {3:N2} families/min." -f
            $record.accepted, $record.productiveFamilies, $record.seedsPerMinute, $record.familiesPerMinute)
    }
} finally {
    $env:ZSG_MODEL_TUNE = $oldTune
    $env:ZSG_MODEL_TRACE = $oldTrace
    [IO.File]::WriteAllText((Join-Path (Resolve-Path -LiteralPath $OutputDirectory).Path 'summary.json'),
        (ConvertTo-Json -InputObject @($summary) -Depth 5), [Text.UTF8Encoding]::new($false))
}
