[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$CandidateFinder,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [string]$AaFixtureBank
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$candidate = (Resolve-Path -LiteralPath $CandidateFinder).Path
$baseline = Join-Path $root 'run/filter-worker/seed-finder.exe'
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Use a fresh test directory.' }
$null = New-Item -ItemType Directory -Path $OutputDirectory
$directory = (Resolve-Path -LiteralPath $OutputDirectory).Path
$oldTrace = $env:ZSG_MODEL_TRACE
$oldTune = $env:ZSG_MODEL_TUNE
$oldSamples = $env:ZSG_AA_END_SAMPLES
try {
    $env:ZSG_MODEL_TRACE = '1'
    $env:ZSG_MODEL_TUNE = '0'
    $env:ZSG_AA_END_SAMPLES = $null
    $pair = @()
    foreach ($finder in @($baseline, $candidate)) {
        $bank = Join-Path $directory "target-$($pair.Count).jsonl"
        $null = & (Join-Path $PSScriptRoot 'Search-FilterCandidates.ps1') -Type temple `
            -Finder $finder -Families 1000000 -Sisters 4096 -FamilyCap 2 -Target 1 `
            -Seconds 60 -Stream 102269 -OutputFile $bank
        $report = (Get-Content ($bank + '.report.json') -Raw | ConvertFrom-Json).results[0]
        if ($report.stop -ne 'TARGET' -or $report.accepted -ne 1) { throw 'Target stopping failed.' }
        $pair += @{ result = $report; hash = (Get-FileHash $bank).Hash }
    }
    foreach ($field in @('families','sisters','digest','decisionTrace')) {
        if ($pair[0].result.$field -cne $pair[1].result.$field) { throw "Target parity failed: $field" }
    }
    if ($pair[0].hash -cne $pair[1].hash) { throw 'Target bank mismatch.' }
    Write-Output 'PASS: target stopping and traced prefix match.'

    if ($AaFixtureBank) {
        $fixture = Get-Content -LiteralPath $AaFixtureBank -TotalCount 1 | ConvertFrom-Json
        if ($fixture.type -ne 'aa_temple' -or !$fixture.family) { throw 'Expected an accepted AA family fixture.' }
        Add-Type -AssemblyName System.Numerics
        $modulus = [System.Numerics.BigInteger]::Pow(2,48)
        # Invert the odd stream step modulo 2^48 to replay just this productive family.
        $inverse = [System.Numerics.BigInteger]::ModPow(
            [System.Numerics.BigInteger]::Parse('11400714819323198485'),
            [System.Numerics.BigInteger]::Pow(2,47)-1, $modulus)
        $stream = [long](([System.Numerics.BigInteger]::Parse($fixture.family)*$inverse)%$modulus)
        $pair = @()
        foreach ($finder in @($baseline, $candidate)) {
            $bank = Join-Path $directory "aa-accepted-$($pair.Count).jsonl"
            $null = & (Join-Path $PSScriptRoot 'Search-FilterCandidates.ps1') -Type aa_temple `
                -Finder $finder -Families 1 -Sisters 4096 -FamilyCap 2 -Target 1000000 `
                -Seconds 60 -Stream $stream -OutputFile $bank
            $r = (Get-Content ($bank + '.report.json') -Raw | ConvertFrom-Json).results[0]
            if ($r.stop -ne 'FAMILY_LIMIT' -or $r.accepted -lt 1) { throw 'AA fixture no longer accepted.' }
            $pair += @{ result = $r; hash = (Get-FileHash $bank).Hash }
        }
        foreach ($field in @('families','sisters','accepted','digest','decisionTrace')) {
            if ($pair[0].result.$field -cne $pair[1].result.$field) { throw "AA fixture parity failed: $field" }
        }
        if ($pair[0].hash -cne $pair[1].hash) { throw 'AA fixture bank mismatch.' }
        Write-Output 'PASS: productive AA family, all traced decisions and bank match.'
    }

    $env:ZSG_MODEL_TUNE = '1'
    $bank = Join-Path $directory 'tune.jsonl'
    $null = & (Join-Path $PSScriptRoot 'Search-FilterCandidates.ps1') -Type aa_temple `
        -Finder $candidate -Families 1000 -Sisters 65536 -FamilyCap 4 -Target 1000000 `
        -Seconds 60 -Stream 1537660560030 -OutputFile $bank
    $r = (Get-Content ($bank + '.report.json') -Raw | ConvertFrom-Json).results[0]
    if ($r.stop -ne 'FAMILY_LIMIT' -or $r.geometryTimingMode -ne 'exact' -or
        $r.geometryTimingSamples -ne $r.families -or $r.familyDeadlineInterval -ne 1) {
        throw 'Policy-tuning exact timing guard failed.'
    }
    Write-Output 'PASS: policy tuning retains exact timing and per-family deadline polling.'

    $env:ZSG_MODEL_TRACE = '0'
    $env:ZSG_MODEL_TUNE = '0'
    $bank = Join-Path $directory 'deadline.jsonl'
    $null = & (Join-Path $PSScriptRoot 'Search-FilterCandidates.ps1') -Type aa_temple `
        -Finder $candidate -Families 10000000000 -Sisters 4096 -FamilyCap 2 -Target 1000000 `
        -Seconds 1 -Stream 1537660560030 -OutputFile $bank
    $r = (Get-Content ($bank + '.report.json') -Raw | ConvertFrom-Json).results[0]
    if ($r.stop -ne 'TIME_LIMIT' -or $r.elapsedMs -lt 1000 -or $r.familyDeadlineInterval -ne 1024) {
        throw 'Deadline integration failed.'
    }
    Write-Output "PASS: one-second deadline ended at $($r.elapsedMs) ms. Whole model calls remain non-preemptible."
} finally {
    $env:ZSG_MODEL_TRACE = $oldTrace
    $env:ZSG_MODEL_TUNE = $oldTune
    $env:ZSG_AA_END_SAMPLES = $oldSamples
}
