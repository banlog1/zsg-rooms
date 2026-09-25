[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [ValidateSet('Development', 'Holdout')][string]$Samples = 'Development',
    [ValidateRange(10, 600)][int]$SecondsPerType = 120,
    [ValidateSet('All', 'temple', 'shipwreck', 'village', 'buried_treasure')][string]$Type = 'All',
    [ValidateRange(0, 281474976710655)][long]$Stream = 0,
    [string]$Java = 'java'
)

$ErrorActionPreference = 'Stop'
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Use a fresh policy measurement directory.' }
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
$OutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path
$search = Join-Path $PSScriptRoot 'Search-FilterCandidates.ps1'
$offsets = if ($Samples -eq 'Development') {
    @{temple=30000000L; shipwreck=150000000L; village=30000000L; buried_treasure=30000000L}
} else {
    @{temple=70000000L; shipwreck=250000000L; village=70000000L; buried_treasure=70000000L}
}
$types = @('temple', 'shipwreck', 'village', 'buried_treasure')
if ($Type -ne 'All') { $types = @($Type) }
$oldTune = $env:ZSG_MODEL_TUNE
$oldTrace = $env:ZSG_MODEL_TRACE
$env:ZSG_MODEL_TUNE = '1'
$env:ZSG_MODEL_TRACE = '0'
$summary = @()
try {
    for ($i=0; $i -lt $types.Count; $i++) {
        $type = $types[$i]
        $searchStream = if ($PSBoundParameters.ContainsKey('Stream')) { $Stream } else { $offsets[$type] }
        $bank = Join-Path $OutputDirectory "$type.jsonl"
        Write-Output "Measuring $type policies for up to $SecondsPerType seconds ($Samples)."
        $null = & $search -Type $type -Sisters 65536 -FamilyCap 4 -Target 1000000 -Families 1000000000 `
            -Seconds $SecondsPerType -Stream $searchStream -OutputFile $bank -Java $Java
        $report = Get-Content -LiteralPath ($bank + '.report.json') -Raw | ConvertFrom-Json
        $native = $report.results[0]
        if ($native.policyEstimates.Count -ne 18 -or $native.familyCap -ne 4) { throw 'Missing policy measurements.' }
        $rows = @(Get-Content -LiteralPath $bank | ForEach-Object { $_ | ConvertFrom-Json })
        $families = @($rows | Group-Object family)
        if ($rows.Count -ne $native.accepted -or $families.Count -ne $native.acceptedFamilies -or
            @($rows.seed | Sort-Object -Unique).Count -ne $rows.Count -or
            @($families | Where-Object { $_.Count -gt 4 }).Count -ne 0) { throw 'Invalid family/seed counts.' }
        foreach ($row in $rows) {
            if ([string]([long]$row.seed -band 281474976710655L) -cne $row.family) { throw 'Invalid private family identifier.' }
        }
        $estimates = @($native.policyEstimates | ForEach-Object {
            [pscustomobject]@{
                type = $type; sisters = $_.sisters; familyCap = $_.familyCap
                accepted = $_.accepted; productiveFamilies = $_.productiveFamilies
                estimatedMs = $_.estimatedMs
                estimatedSeedsPerMinute = if ($_.estimatedMs -gt 0) { 60000 * $_.accepted / $_.estimatedMs } else { 0 }
                estimatedFamiliesPerMinute = if ($_.estimatedMs -gt 0) { 60000 * $_.productiveFamilies / $_.estimatedMs } else { 0 }
            }
        })
        $summary += [pscustomobject]@{
            type = $type; samples = $Samples; streamOffset = $searchStream
            measuredMs = $native.elapsedMs; completedModelFamilies = $native.policyCompletedFamilies
            interruptedModelFamilies = $native.policyInterruptedFamilies; estimates = $estimates
        }
        $estimates | Sort-Object estimatedSeedsPerMinute -Descending |
            Format-Table sisters, familyCap, accepted, productiveFamilies,
                @{Label='Estimated seeds/min';Expression={'{0:N2}' -f $_.estimatedSeedsPerMinute}},
                @{Label='Estimated families/min';Expression={'{0:N2}' -f $_.estimatedFamiliesPerMinute}}
    }
} finally {
    $env:ZSG_MODEL_TUNE = $oldTune
    $env:ZSG_MODEL_TRACE = $oldTrace
    [IO.File]::WriteAllText((Join-Path (Resolve-Path -LiteralPath $OutputDirectory).Path 'summary.json'),
        (ConvertTo-Json -InputObject @($summary) -Depth 8), [Text.UTF8Encoding]::new($false))
}
