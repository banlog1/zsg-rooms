[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [ValidateSet('Parity', 'Performance', 'Holdout')][string]$Phase = 'Parity',
    [ValidateSet('All', 'temple', 'aa_temple')][string]$Type = 'All',
    [ValidateRange(1, 3)][int]$Repeats = 2,
    [ValidateRange(1, 600)][int]$SecondsPerCase = 240,
    [string]$Java = 'java',
    [string]$Compiler = 'gcc',
    [string]$CandidateFinder
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'FilterBankState.ps1')
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Use a fresh benchmark directory.' }
$absolute = [IO.Path]::GetFullPath($OutputDirectory)
if (!$absolute.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Benchmark output must be inside the workspace.'
}
$null = New-Item -ItemType Directory -Path $absolute
$directory = (Resolve-Path -LiteralPath $absolute).Path
$source = Join-Path $root 'run/filter-reference/cubiomes'
if (!$CandidateFinder) {
    $revision = & git -C $source rev-parse HEAD
    if ($LASTEXITCODE -ne 0 -or $revision -cne 'e61f90580cbdd883214a8054670dacae655e59c0') {
        throw 'Expected the pinned Cubiomes revision.'
    }
    $dirty = & git -C $source status --porcelain --untracked-files=all
    if ($LASTEXITCODE -ne 0 -or $dirty) { throw 'Cubiomes checkout must be clean.' }
    $patched = Join-Path $directory 'cubiomes-patched'
    $null = New-Item -ItemType Directory -Path $patched
    Copy-Item -LiteralPath (Join-Path $source 'finders.c') -Destination $patched
    $relative = $patched.Substring($root.Length + 1).Replace('\', '/')
    & git -C $root apply --no-index --ignore-space-change "--directory=$relative" `
        (Join-Path $root 'tools/filter-worker/patches/cubiomes-end-city-1.16.1.patch')
    if ($LASTEXITCODE -ne 0) { throw 'End correction patch failed.' }
    $files = @('noise.c', 'biomes.c', 'layers.c', 'biomenoise.c', 'generator.c', 'finders.c', 'util.c', 'quadbase.c') |
        ForEach-Object { if ($_ -eq 'finders.c') { Join-Path $patched $_ } else { Join-Path $source $_ } }
    foreach ($unit in @('seed_finder', 'search_timing_test')) {
        & $Compiler '-O3' '-std=c99' '-Wall' '-Wextra' '-fwrapv' '-ffp-contract=off' `
            '-DZSG_EXPERIMENT_BATCH_TIMING=1' '-I' $source `
            (Join-Path $root "tools/filter-worker/$unit.c") @files '-lm' '-o' (Join-Path $directory "$unit.exe")
        if ($LASTEXITCODE -ne 0) { throw "Build failed: $unit" }
    }
    & (Join-Path $directory 'search_timing_test.exe')
    if ($LASTEXITCODE -ne 0) { throw 'Timing unit tests failed.' }
    $CandidateFinder = Join-Path $directory 'seed_finder.exe'
}
$variants = [ordered]@{
    baseline = Join-Path $directory 'baseline.exe'
    candidate = Join-Path $directory 'candidate.exe'
}
Copy-Item -LiteralPath (Join-Path $root 'run/filter-worker/seed-finder.exe') -Destination $variants.baseline
Copy-Item -LiteralPath (Resolve-Path -LiteralPath $CandidateFinder).Path -Destination $variants.candidate
$fingerprints = @{}
foreach ($variant in $variants.Keys) { $fingerprints[$variant] = Get-FilterModelFingerprint $root $Java $variants[$variant] }
$trace = $Phase -eq 'Parity'
$cases = if ($Phase -eq 'Parity') { @(
    @{ type = 'temple'; families = 1000000L; stream = 102269L; sisters = 4096; cap = 2 },
    @{ type = 'aa_temple'; families = 50000000L; stream = 1537660560030L; sisters = 4096; cap = 2 },
    @{ type = 'village'; families = 10000L; stream = 0L; sisters = 256; cap = 2 },
    @{ type = 'shipwreck'; families = 10000L; stream = 0L; sisters = 4096; cap = 4 },
    @{ type = 'buried_treasure'; families = 10000L; stream = 0L; sisters = 4096; cap = 4 },
    @{ type = 'ruined_portal'; families = 10000L; stream = 0L; sisters = 4096; cap = 4 }
) } elseif ($Phase -eq 'Holdout') { @(
    @{ type = 'temple'; families = 1000000L; stream = 2000000L; sisters = 4096; cap = 2 },
    @{ type = 'aa_temple'; families = 100000000L; stream = 1537910560030L; sisters = 4096; cap = 2 }
) } else { @(
    @{ type = 'temple'; families = 5000000L; stream = 102269L; sisters = 4096; cap = 2 },
    @{ type = 'aa_temple'; families = 250000000L; stream = 1537660560030L; sisters = 4096; cap = 2 }
) }
if ($Type -ne 'All') { $cases = @($cases | Where-Object { $_.type -eq $Type }) }
$inputs = @(Get-ChildItem (Join-Path $root 'tools/filter-worker') -File |
    Where-Object { $_.Extension -in @('.c','.h') } | Get-FileHash | Select-Object Path,Hash)
Write-FilterAtomicJson (Join-Path $directory 'runtime.json') @{
    phase = $Phase; startedUtc = [datetime]::UtcNow.ToString('o'); fingerprints = $fingerprints
    inputs = $inputs; cases = $cases; compiler = @(& $Compiler --version)
    repeats = $Repeats; trace = $trace; tune = $false; secondsPerCase = $SecondsPerCase
}
$oldTrace = $env:ZSG_MODEL_TRACE
$oldTune = $env:ZSG_MODEL_TUNE
$oldSamples = $env:ZSG_AA_END_SAMPLES
$records = @()
try {
    $env:ZSG_MODEL_TRACE = if ($trace) { '1' } else { '0' }
    $env:ZSG_MODEL_TUNE = '0'
    $env:ZSG_AA_END_SAMPLES = $null
    foreach ($repeat in 0..($Repeats - 1)) {
        foreach ($case in $cases) {
            $pair = @{}
            $order = @($variants.Keys)
            if (($repeat % 2 -eq 1) -xor ($Phase -eq 'Holdout')) { [array]::Reverse($order) }
            foreach ($variant in $order) {
                $bank = Join-Path $directory "$($case.type)-$repeat-$variant.jsonl"
                Write-Output "Starting $Phase $($case.type) $variant repeat $repeat"
                $null = & (Join-Path $PSScriptRoot 'Search-FilterCandidates.ps1') -Type $case.type `
                    -Families $case.families -Sisters $case.sisters -FamilyCap $case.cap -Target 1000000 `
                    -Seconds $SecondsPerCase -Stream $case.stream -OutputFile $bank -Java $Java -Finder $variants[$variant]
                $report = Get-Content -LiteralPath ($bank + '.report.json') -Raw | ConvertFrom-Json
                $native = $report.results[0]
                $record = [pscustomobject]@{
                    type = $case.type; repeat = $repeat; variant = $variant; stop = $native.stop
                    families = $native.families; sisters = $native.sisters; accepted = $native.accepted
                    productiveFamilies = $native.acceptedFamilies; digest = $native.digest
                    decisionTrace = $native.decisionTrace; traceEnabled = $native.traceEnabled
                    bankHash = (Get-FileHash -LiteralPath $bank).Hash; searchMs = $native.elapsedMs
                    processMs = $report.processWallMs; checks = $native.checks
                    geometryTimingMode = $native.geometryTimingMode; geometryTimingSamples = $native.geometryTimingSamples
                }
                $records += $record
                Write-FilterAtomicJson (Join-Path $directory 'summary.json') @{ phase = $Phase; records = $records }
                if ($record.stop -ne 'FAMILY_LIMIT' -or $record.traceEnabled -ne $trace) {
                    throw 'Incomplete fixed work or wrong tracing mode; do not compare timings.'
                }
                if ($variant -eq 'candidate' -and $record.geometryTimingMode -ne 'sampled_estimate') {
                    throw 'Candidate does not have experimental timing enabled.'
                }
                $pair[$variant] = $record
                Write-Output ("Finished: {0:N3}s, {1} accepted" -f ($record.searchMs / 1000), $record.accepted)
            }
            foreach ($field in @('families','sisters','accepted','productiveFamilies','digest','decisionTrace','bankHash')) {
                if ($pair.baseline.$field -cne $pair.candidate.$field) { throw "Parity failed: $($case.type) / $field" }
            }
            foreach ($stage in $pair.baseline.checks.PSObject.Properties.Name) {
                foreach ($field in @('reached','rejected')) {
                    if ($pair.baseline.checks.$stage.$field -ne $pair.candidate.checks.$stage.$field) {
                        throw "Stage parity failed: $stage / $field"
                    }
                }
            }
            Write-Output ("Equivalent results. Speedup: {0:N3}x" -f ($pair.baseline.searchMs / $pair.candidate.searchMs))
        }
    }
    foreach ($variant in $variants.Keys) {
        if ((Get-FilterModelFingerprint $root $Java $variants[$variant]) -cne $fingerprints[$variant]) {
            throw 'Model inputs changed during benchmark.'
        }
    }
    Write-Output 'PASS: fixed-work pairs match. Production finder and bank cursor untouched.'
} finally {
    $env:ZSG_MODEL_TRACE = $oldTrace
    $env:ZSG_MODEL_TUNE = $oldTune
    $env:ZSG_AA_END_SAMPLES = $oldSamples
}
