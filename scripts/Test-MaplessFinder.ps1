[CmdletBinding()]
param(
    [string]$Java = 'java',
    [string]$BaselineFinder,
    [ValidateRange(1,600)][int]$Seconds = 120
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$directory = Join-Path $root ('run/mapless-tests/' + [guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $directory
$search = Join-Path $PSScriptRoot 'Search-FilterCandidates.ps1'
$oldTrace = $env:ZSG_MODEL_TRACE
$oldTune = $env:ZSG_MODEL_TUNE
try {
    $env:ZSG_MODEL_TRACE = '1'
    $env:ZSG_MODEL_TUNE = '0'
    if ($BaselineFinder) {
        foreach ($type in @('temple','village','shipwreck')) {
            $reports = @()
            $paths = @()
            foreach ($finder in @($BaselineFinder,(Join-Path $root 'run/filter-worker/seed-finder.exe'))) {
                $path = Join-Path $directory ($type + '-' + $paths.Count + '.jsonl')
                $messages = @(& $search -Type $type -Java $Java -Finder $finder -Families 20000 -Sisters 256 -FamilyCap 2 -Target 1000000 -Seconds $Seconds -OutputFile $path)
                $report = $messages | Where-Object { $_.StartsWith('{') } | Select-Object -First 1 | ConvertFrom-Json
                if ($report.stop -cne 'FAMILY_LIMIT' -or $report.minecraftWorlds -ne 0) { throw 'Regression search did not finish its fixed work.' }
                $reports += $report
                $paths += $path
            }
            if ($reports[0].decisionTrace -cne $reports[1].decisionTrace -or $reports[0].sisters -ne $reports[1].sisters -or
                (Get-FileHash -LiteralPath $paths[0]).Hash -cne (Get-FileHash -LiteralPath $paths[1]).Hash) {
                throw "Existing $type decisions changed."
            }
            Write-Output "PASS: $type unchanged over 20,000 families and $($reports[1].sisters) sister checks."
        }
        # Shipwrecks are rare in a short fixed range; also compare a nonempty bank.
        $paths = @()
        foreach ($finder in @($BaselineFinder,(Join-Path $root 'run/filter-worker/seed-finder.exe'))) {
            $path = Join-Path $directory ('shipwreck-accepted-' + $paths.Count + '.jsonl')
            $messages = @(& $search -Type shipwreck -Java $Java -Finder $finder -Target 2 -Seconds $Seconds -OutputFile $path)
            $report = $messages | Where-Object { $_.StartsWith('{') } | Select-Object -First 1 | ConvertFrom-Json
            if ($report.stop -cne 'TARGET' -or $report.accepted -ne 2) { throw 'Shipwreck acceptance regression did not reach its target.' }
            $paths += $path
        }
        if ((Get-FileHash -LiteralPath $paths[0]).Hash -cne (Get-FileHash -LiteralPath $paths[1]).Hash) {
            throw 'Accepted shipwreck bank changed.'
        }
        Write-Output 'PASS: old/new finders produced identical nonempty shipwreck banks.'
    }
    $paths = @()
    foreach ($repeat in @(0,1)) {
        $path = Join-Path $directory ('buried-' + $repeat + '.jsonl')
        $messages = @(& $search -Type buried_treasure -Java $Java -Target 2 -FamilyCap 2 -Seconds $Seconds -OutputFile $path)
        $report = $messages | Where-Object { $_.StartsWith('{') } | Select-Object -First 1 | ConvertFrom-Json
        if ($report.stop -cne 'TARGET' -or $report.accepted -ne 2 -or $report.minecraftWorlds -ne 0) {
            throw 'Mapless sample did not reach two acceptances within the test budget.'
        }
        $rows = @(Get-Content -LiteralPath $path | ForEach-Object { $_ | ConvertFrom-Json })
        if ($rows.Count -ne 2) { throw 'Wrong sample row count.' }
        $inputRows = $rows | ForEach-Object {
            (@($_.seed) + @($_.structure) + @($_.entry) + @($_.wood) + @($_.spawn) + @($_.bastion) + @($_.fortress)) -join ' '
        }
        $checked = @($inputRows | & (Join-Path $root 'run/filter-worker/mapless-test.exe') --check-bank)
        if ($LASTEXITCODE -ne 0) { throw 'Mapless saved coordinates failed model recheck.' }
        foreach ($row in $rows) {
            if ($row.type -cne 'buried_treasure' -or $row.buriedTreasureRule -cne 'mapless-regular-v1' -or
                $row.status -cne 'MODEL_ACCEPTED' -or $row.netherObsidianScore -lt 20 -or $null -ne $row.water -or
                [Math]::Abs($row.spawn[0]-$row.structure[0]) -gt 32 -or [Math]::Abs($row.spawn[1]-$row.structure[1]) -gt 32 -or
                [Math]::Abs($row.entry[0]-$row.structure[0]) -gt 81 -or [Math]::Abs($row.entry[1]-$row.structure[1]) -gt 81) {
                throw 'Mapless bank criteria mismatch.'
            }
            if ($messages -match [regex]::Escape([string]$row.seed)) { throw 'Seed leaked into public output.' }
        }
        if ($report.checks.forest_size.reached-$report.checks.forest_size.rejected -ne 2 -or
            $report.checks.nearby_water.reached -ne 0 -or $report.checks.temple_exposure.reached -ne 0 -or $report.checks.smith_loot.reached -ne 0) {
            throw 'Wrong acceptance gates ran for mapless.'
        }
        $paths += $path
    }
    if ((Get-FileHash -LiteralPath $paths[0]).Hash -cne (Get-FileHash -LiteralPath $paths[1]).Hash) { throw 'Mapless repeat was not deterministic.' }
    Write-Output 'PASS: repeated mapless search produced identical model-only samples; no seeds printed.'
    Write-Output "Private test results: $directory"
} finally {
    $env:ZSG_MODEL_TRACE = $oldTrace
    $env:ZSG_MODEL_TUNE = $oldTune
}
