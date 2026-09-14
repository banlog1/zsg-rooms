[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$FixtureBank,
    [string]$LegacyFinder,
    [string]$Java = 'java'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Numerics
$rows = @(Get-Content -LiteralPath $FixtureBank | ForEach-Object { $_ | ConvertFrom-Json })
$group = $rows | Group-Object family | Where-Object { $_.Count -eq 4 } | Select-Object -First 1
if (-not $group) { throw 'Use a private fixture bank containing four accepted sisters in one family.' }
$fixture = @($group.Group)
$type = $fixture[0].type
$modulus = [Numerics.BigInteger]::Pow(2, 48)
$step = [Numerics.BigInteger]::Parse('11400714819323198485')
$inverse = [Numerics.BigInteger]::ModPow($step, [Numerics.BigInteger]::Pow(2,47)-1, $modulus)
$stream = [long](([Numerics.BigInteger]::Parse($fixture[0].family) * $inverse) % $modulus)
$root = Split-Path -Parent $PSScriptRoot
$directory = Join-Path $root ('run/model-cap-tests/' + [guid]::NewGuid().ToString())
New-Item -ItemType Directory -Path $directory | Out-Null
$search = Join-Path $PSScriptRoot 'Search-FilterCandidates.ps1'
$oldTune = $env:ZSG_MODEL_TUNE
$env:ZSG_MODEL_TUNE = '0'
try {
    $paths = @{}
    foreach ($case in @(@{cap=1;target=1}, @{cap=1;target=4}, @{cap=2;target=2}, @{cap=2;target=4},
            @{cap=4;target=4}, @{cap=4;target=3}, @{cap=4;target=5})) {
        $key = "$($case.cap)-$($case.target)"
        $bank = Join-Path $directory "$key.jsonl"
        $public = & $search -Type $type -Families 1 -Sisters 65536 -FamilyCap $case.cap -Target $case.target `
            -Seconds 120 -Stream $stream -OutputFile $bank -Java $Java
        $report = (Get-Content -LiteralPath ($bank + '.report.json') -Raw | ConvertFrom-Json).results[0]
        $accepted = @(Get-Content -LiteralPath $bank | ForEach-Object { $_ | ConvertFrom-Json })
        $expectedCount = [Math]::Min($case.cap, $case.target)
        $expectedStop = if ($case.target -le $case.cap) { 'TARGET' } else { 'FAMILY_LIMIT' }
        if ($report.stop -cne $expectedStop -or $report.acceptedFamilies -ne 1 -or $report.familyCap -ne $case.cap -or
            $accepted.Count -ne $expectedCount) { throw 'Cap/target stopping failed.' }
        for ($i=0; $i -lt $accepted.Count; $i++) {
            if ($case.cap -eq 1) { $accepted[$i] | Add-Member -NotePropertyName family -NotePropertyValue $fixture[$i].family }
            if (($accepted[$i] | ConvertTo-Json -Compress) -cne ($fixture[$i] | ConvertTo-Json -Compress)) {
                throw 'Changing the family cap changed the accepted prefix or its metadata.'
            }
            if ($public -match [regex]::Escape([string]$accepted[$i].seed)) { throw 'Seed leaked into public output.' }
        }
        $paths[$key] = $bank
    }
    $repeat = Join-Path $directory 'repeat.jsonl'
    $null = & $search -Type $type -Families 1 -Sisters 65536 -FamilyCap 4 -Target 4 -Seconds 120 `
        -Stream $stream -OutputFile $repeat -Java $Java
    if ((Get-FileHash -LiteralPath $repeat).Hash -cne (Get-FileHash -LiteralPath $paths['4-4']).Hash) {
        throw 'Repeated search changed the private bank.'
    }
    if ($LegacyFinder) {
        $legacy = Join-Path $directory 'legacy.jsonl'
        $null = & $search -Type $type -Families 1 -Sisters 65536 -FamilyCap 1 -Target 1 -Seconds 120 `
            -Stream $stream -OutputFile $legacy -Java $Java -Finder $LegacyFinder
        if ((Get-FileHash -LiteralPath $legacy).Hash -cne (Get-FileHash -LiteralPath $paths['1-1']).Hash) {
            throw 'Cap one changed the legacy private bank.'
        }
    }
} finally {
    $env:ZSG_MODEL_TUNE = $oldTune
}
Write-Output 'Family caps, exact target stopping, deterministic prefixes, private metadata and repeatability passed.'
