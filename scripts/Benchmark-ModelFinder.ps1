[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$BaselineFinder,
    [string]$CandidateFinder,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [ValidateSet('Development', 'Holdout')][string]$Samples = 'Development',
    [ValidateRange(1, 3)][int]$Repeats = 1,
    [string]$Java = 'java',
    [Parameter(Mandatory = $true)][datetime]$DeadlineUtc
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$search = Join-Path $PSScriptRoot 'Search-FilterCandidates.ps1'
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Use a new benchmark output directory.' }
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
$baseline = (Resolve-Path -LiteralPath $BaselineFinder).Path
$variants = [ordered]@{ baseline = $baseline }
if ($CandidateFinder) { $variants.candidate = (Resolve-Path -LiteralPath $CandidateFinder).Path }
$cases = @(
    @{ type = 'temple'; families = 300000; sisters = 65536; stream = 102269 },
    @{ type = 'shipwreck'; families = 40000000; sisters = 65536; stream = 0 },
    @{ type = 'village'; families = 1000000; sisters = 256; stream = 0 }
)
if ($Samples -eq 'Holdout') {
    $cases[0].stream = 2000000
    $cases[1].stream = 50000000
    $cases[2].stream = 3000000
}
$oldTrace = $env:ZSG_MODEL_TRACE
$env:ZSG_MODEL_TRACE = '1'
$summaries = @()
try {
    foreach ($repeat in 0..($Repeats - 1)) {
        foreach ($case in $cases) {
            $pair = @{}
            $order = @($variants.Keys)
            if ($repeat % 2) { [array]::Reverse($order) }
            foreach ($variant in $order) {
                $remaining = [Math]::Floor(($DeadlineUtc.ToUniversalTime() - [datetime]::UtcNow).TotalSeconds) - 35
                if ($remaining -lt 30) { throw 'Benchmark deadline reached; saved reports remain available.' }
                $seconds = [int][Math]::Min(300, $remaining)
                $bank = Join-Path $OutputDirectory "$($case.type)-$repeat-$variant.jsonl"
                Write-Output "Benchmark: $Samples / $($case.type) / $variant / repeat $repeat"
                $null = & $search -Type $case.type -Families $case.families -Sisters $case.sisters `
                    -Stream $case.stream -FamilyCap 1 -Target 1000000 -Seconds $seconds -OutputFile $bank -Java $Java -Finder $variants[$variant]
                $report = Get-Content -LiteralPath ($bank + '.report.json') -Raw | ConvertFrom-Json
                $native = $report.results[0]
                if ($native.stop -ne 'FAMILY_LIMIT' -or -not $native.traceEnabled) {
                    throw 'Fixed-work benchmark incomplete; do not compare time-limited results.'
                }
                $record = [pscustomobject]@{
                    samples = $Samples; type = $case.type; repeat = $repeat; variant = $variant
                    families = $native.families; sisters = $native.sisters; accepted = $native.accepted
                    searchMs = $native.elapsedMs; processMs = $report.processWallMs
                    decisionTrace = $native.decisionTrace; bankHash = (Get-FileHash -LiteralPath $bank).Hash
                    executableHash = (Get-FileHash -LiteralPath $variants[$variant]).Hash
                }
                $pair[$variant] = $record
                $summaries += $record
                Write-Output ("Completed: {0} accepted, {1:N2} seconds" -f $record.accepted, ($record.searchMs / 1000))
            }
            if ($pair.ContainsKey('candidate')) {
                foreach ($field in @('families', 'sisters', 'accepted', 'decisionTrace', 'bankHash')) {
                    if ($pair.baseline.$field -cne $pair.candidate.$field) { throw "Equivalence failed: $($case.type) / $field" }
                }
                Write-Output ("Equivalent decisions and bank. Speedup: {0:N3}x" -f ($pair.baseline.searchMs / $pair.candidate.searchMs))
            }
        }
    }
} finally {
    $env:ZSG_MODEL_TRACE = $oldTrace
    # Generated benchmark artifacts, never numeric candidate seeds.
    $json = ConvertTo-Json -InputObject @($summaries) -Depth 5
    [IO.File]::WriteAllText((Join-Path (Resolve-Path -LiteralPath $OutputDirectory).Path 'summary.json'), $json, [Text.UTF8Encoding]::new($false))
}
