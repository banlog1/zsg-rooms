[CmdletBinding()]
param([string]$Java = 'java', [switch]$Village)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$directory = Join-Path $root ('run/model-finder-tests/' + [guid]::NewGuid().ToString())
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$finder = Join-Path $root 'run/filter-worker/seed-finder.exe'
$search = Join-Path $PSScriptRoot 'Search-FilterCandidates.ps1'
function Assert-ModelWater($rows) {
    foreach ($row in $rows) {
        if ($row.water.Count -ne 2) { throw 'Missing water coordinates' }
        $dx=$row.water[0]-$row.entry[0]
        $dz=$row.water[1]-$row.entry[1]
        if ($dx*$dx+$dz*$dz -gt 48*48) { throw 'Water exceeds the 48-block radius' }
    }
    $inputRows=$rows | ForEach-Object { '{0} {1} {2}' -f $_.seed,$_.entry[0],$_.entry[1] }
    $inspected=@($inputRows | & (Join-Path $root 'run/filter-worker/surface-query-test.exe') --nearby-water |
        ForEach-Object { $_ | ConvertFrom-Json })
    if ($LASTEXITCODE -ne 0 -or $inspected.Count -ne $rows.Count) { throw 'Water inspection failed' }
    for ($i=0;$i -lt $rows.Count;$i++) {
        if (-not $inspected[$i].found -or ($inspected[$i].water -join ',') -cne ($rows[$i].water -join ',')) {
            throw 'Bank water recheck failed'
        }
    }
}
$checks = 0
foreach ($type in @('temple', 'shipwreck')) {
    $paths = @()
    foreach ($repeat in @(0, 1)) {
        $file = Join-Path $directory "$type-$repeat.jsonl"
        $reportLines = & $search -Type $type -Target 2 -Seconds 120 -OutputFile $file -Java $Java
        $report = $reportLines | Where-Object { $_.StartsWith('{') } | Select-Object -First 1 | ConvertFrom-Json
        if ($report.accepted -ne 2 -or $report.minecraftWorlds -ne 0 -or $report.stop -cne 'TARGET') {
            throw "Expected two model-only $type acceptances within the smoke-test budget"
        }
        $rows = @(Get-Content -LiteralPath $file | ForEach-Object { $_ | ConvertFrom-Json })
        if ($rows.Count -ne 2) { throw 'Bank row count mismatch' }
        foreach ($row in $rows) {
            [long]$seed = 0
            if ($row.status -cne 'MODEL_ACCEPTED' -or $row.profile -cne 'zsg-model-only-v5' -or
                    $row.type -cne $type -or -not [long]::TryParse($row.seed, [ref]$seed)) { throw 'Invalid private bank row' }
            if ($row.netherObsidianScore -lt 20 -or $row.bastionType -notin @('STABLES', 'BRIDGE', 'HOUSING', 'TREASURE')) {
                throw 'Nether model checks not satisfied'
            }
            if ($reportLines -match [regex]::Escape([string]$row.seed)) { throw 'Seed leaked into public search output' }
        }
        $paths += $file
        $exposure = $report.checks.temple_exposure
        if ($type -eq 'temple') {
            if ($exposure.reached - $exposure.rejected -ne $report.checks.nearby_water.reached -or
                $report.checks.nearby_water.reached - $report.checks.nearby_water.rejected -ne $report.accepted) {
                throw 'Accepted temples did not all pass the exposure check'
            }
            $inputRows = $rows | ForEach-Object { '{0} {1} {2}' -f $_.seed, $_.structure[0], $_.structure[1] }
            $inspected = @($inputRows | & (Join-Path $root 'run/filter-worker/surface-query-test.exe') --temple-exposure |
                ForEach-Object { $_ | ConvertFrom-Json })
            if ($LASTEXITCODE -ne 0 -or $inspected.Count -ne $rows.Count -or ($inspected | Where-Object { -not $_.exposed })) {
                throw 'Bank exposure recheck failed'
            }
            $wooded = @($inputRows | & (Join-Path $root 'run/filter-worker/surface-query-test.exe') --temple-wood |
                ForEach-Object { $_ | ConvertFrom-Json })
            if ($LASTEXITCODE -ne 0 -or $wooded.Count -ne $rows.Count -or ($wooded | Where-Object { -not $_.wooded })) {
                throw 'Accepted temple lacks a tree-bearing biome near the temple'
            }
            foreach ($row in $rows) {
                if (($row.wood -join ',') -cne ($row.structure -join ',')) { throw 'Temple wood check anchored at the wrong location' }
            }
            Assert-ModelWater $rows
        } elseif ($exposure.reached -ne 0 -or $report.checks.nearby_water.reached -ne 0 -or ($rows | Where-Object { $null -ne $_.water })) {
            throw 'Temple exposure or water policy ran for shipwrecks'
        }
    }
    if ((Get-FileHash -LiteralPath $paths[0]).Hash -cne (Get-FileHash -LiteralPath $paths[1]).Hash) {
        throw 'Identical search inputs produced different private banks'
    }
    $before = (Get-FileHash -LiteralPath $paths[0]).Hash
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $oldPipe = $env:ZSG_MODEL_PIPE
        $env:ZSG_MODEL_PIPE = '1'
        try { $null = & $finder search $type 100 256 1 1 0 $paths[0] 2>&1 }
        finally { $env:ZSG_MODEL_PIPE = $oldPipe }
    } finally { $ErrorActionPreference = $previousPreference }
    if ($LASTEXITCODE -eq 0 -or (Get-FileHash -LiteralPath $paths[0]).Hash -cne $before) { throw 'Existing output was not protected' }
    $checks++
}
if ($Village) {
    $file = Join-Path $directory 'village.jsonl'
    $reportLines = & $search -Type village -Target 1 -Seconds 120 -OutputFile $file -Java $Java
    $report = $reportLines | Where-Object { $_.StartsWith('{') } | Select-Object -First 1 | ConvertFrom-Json
    if ($report.accepted -ne 1 -or $report.minecraftWorlds -ne 0) { throw 'Village model smoke test did not accept a seed' }
    $row = Get-Content -LiteralPath $file | ConvertFrom-Json
    $resources = $row.chestIron -ge 4 -or ($row.chestIron -ge 1 -and ($row.ironPickaxes -ge 1 -or $row.diamonds -ge 3))
    if (-not $resources -or $row.type -cne 'village' -or $row.villageResourceRule -cne 'pickaxe-credit-v1') { throw 'Village smith resources not satisfied' }
    if ($row.profile -cne 'zsg-model-only-v5' -or
        $report.checks.nearby_water.reached - $report.checks.nearby_water.rejected -ne $report.checks.smith_loot.reached) {
        throw 'Village water gate was not applied before smith loot'
    }
    Assert-ModelWater @($row)
    if ($reportLines -match [regex]::Escape([string]$row.seed)) { throw 'Seed leaked into public search output' }
    $checks++
}
Write-Output "Model-only search smoke tests passed for $checks profiles; no Minecraft worlds created."
