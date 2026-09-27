[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [ValidateRange(1, 3)][int]$Repeats = 2,
    [ValidateRange(1, 600)][int]$SecondsPerCase = 180,
    [long]$TempleFamilies = 1000000,
    [long]$AaFamilies = 250000000,
    [string]$Java = 'java'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'FilterBankState.ps1')
if ($TempleFamilies -lt 1 -or $AaFamilies -lt 1) { throw 'Family counts must be positive.' }
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Use a fresh benchmark directory.' }
$null = New-Item -ItemType Directory -Path $OutputDirectory
$directory = (Resolve-Path -LiteralPath $OutputDirectory).Path
$finder = Join-Path $directory 'baseline-finder.exe'
Copy-Item -LiteralPath (Join-Path $root 'run/filter-worker/seed-finder.exe') -Destination $finder
$fingerprint = Get-FilterModelFingerprint $root $Java $finder
$cases = @(
    @{ type = 'temple'; families = $TempleFamilies; stream = 102269L },
    @{ type = 'aa_temple'; families = $AaFamilies; stream = 1537660560030L }
)
# Deliberately replay benchmark ranges; these files are never imported into a bank.
$records = @()
$expected = @{}
$oldTrace = $env:ZSG_MODEL_TRACE
$oldTune = $env:ZSG_MODEL_TUNE
try {
    $env:ZSG_MODEL_TRACE = '0'
    $env:ZSG_MODEL_TUNE = '0'
    Write-FilterAtomicJson (Join-Path $directory 'runtime.json') @{
        startedUtc = [datetime]::UtcNow.ToString('o'); fingerprint = $fingerprint
        finderHash = (Get-FileHash -LiteralPath $finder).Hash
        java = (Get-Command $Java).Source; trace = $false; tune = $false
        cases = $cases; repeats = $Repeats; secondsPerCase = $SecondsPerCase
        sisters = 4096; familyCap = 2; target = 1000000
        scope = 'Single-worker fixed-work baseline, not an overnight throughput estimate'
    }
    foreach ($repeat in 0..($Repeats - 1)) {
        $order = @($cases)
        if ($repeat % 2) { [array]::Reverse($order) }
        foreach ($case in $order) {
            if ((Get-FilterModelFingerprint $root $Java $finder) -cne $fingerprint) {
                throw 'Model inputs changed during benchmark.'
            }
            $bank = Join-Path $directory "$($case.type)-$repeat.jsonl"
            Write-Output "Starting $($case.type), repeat $repeat, $($case.families) families"
            $null = & (Join-Path $PSScriptRoot 'Search-FilterCandidates.ps1') -Type $case.type `
                -Families $case.families -Sisters 4096 -FamilyCap 2 -Target 1000000 `
                -Seconds $SecondsPerCase -Stream $case.stream -OutputFile $bank -Java $Java -Finder $finder
            $report = Get-Content -LiteralPath ($bank + '.report.json') -Raw | ConvertFrom-Json
            $native = $report.results[0]
            $record = [pscustomobject]@{
                type = $case.type; repeat = $repeat; complete = ($native.stop -eq 'FAMILY_LIMIT')
                stop = $native.stop; families = $native.families; sisters = $native.sisters
                accepted = $native.accepted; productiveFamilies = $native.acceptedFamilies
                searchMs = $native.elapsedMs; processMs = $report.processWallMs
                bankHash = (Get-FileHash -LiteralPath $bank).Hash; digest = $native.digest
                checks = $native.checks
            }
            $records += $record
            Write-FilterAtomicJson (Join-Path $directory 'summary.json') @{
                fingerprint = $fingerprint; records = $records
            }
            if (!$record.complete -or $native.traceEnabled) {
                throw 'Fixed work did not complete or tracing was enabled; reports retained, timings not comparable.'
            }
            if ($expected.ContainsKey($case.type)) {
                foreach ($field in @('families', 'sisters', 'accepted', 'productiveFamilies', 'bankHash', 'digest')) {
                    if ($expected[$case.type].$field -cne $record.$field) {
                        throw "Repeated baseline differs: $($case.type) / $field"
                    }
                }
            } else { $expected[$case.type] = $record }
            Write-Output ("Finished {0}: {1:N2}s, {2} accepted, {3} productive families" -f `
                $case.type, ($record.searchMs / 1000), $record.accepted, $record.productiveFamilies)
        }
    }
    if ((Get-FilterModelFingerprint $root $Java $finder) -cne $fingerprint) {
        throw 'Model inputs changed during benchmark.'
    }
    Write-Output 'PASS: repeated fixed work and accepted banks match; no production bank or cursor changed.'
} finally {
    $env:ZSG_MODEL_TRACE = $oldTrace
    $env:ZSG_MODEL_TUNE = $oldTune
}
