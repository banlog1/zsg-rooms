param(
    [Parameter(Mandatory = $true)][string[]]$Directory,
    [switch]$SkipFirstTrial
)

$ErrorActionPreference = 'Stop'

function Get-Median([double[]]$Values) {
    $sorted = @($Values | Sort-Object)
    if ($sorted.Count -eq 0) { return 0 }
    $middle = [int][Math]::Floor($sorted.Count / 2)
    if ($sorted.Count % 2) { return $sorted[$middle] }
    return ($sorted[$middle - 1] + $sorted[$middle]) / 2
}

$all = @()
$summary = foreach ($path in $Directory) {
    $rows = @(Get-Content -LiteralPath (Join-Path $path 'results.jsonl') | ForEach-Object { ConvertFrom-Json $_ })
    foreach ($row in $rows) {
        if ($row.chargeTicksMs.Count -ne 80 -or $row.chargeWallMs -lt 3800) {
            throw "Invalid charge timing in $path trial $($row.trial)"
        }
    }
    $all += $rows
    if ($SkipFirstTrial) { $rows = @($rows | Where-Object { $_.trial -ne 0 }) }
    $ticks = @($rows | ForEach-Object { $_.chargeTicksMs } | Sort-Object)
    [pscustomobject]@{
        Directory = $path
        Trials = $rows.Count
        TerrainReady = @($rows | Where-Object { $_.terrainTick -ge 0 }).Count
        FullReady = @($rows | Where-Object { $_.fullTick -ge 0 }).Count
        TransferMedianMs = [Math]::Round((Get-Median @($rows.transferMs)), 2)
        TransferMaxMs = [Math]::Round(($rows.transferMs | Measure-Object -Maximum).Maximum, 2)
        ChargeTickP95Ms = [Math]::Round($ticks[[Math]::Ceiling($ticks.Count * 0.95) - 1], 2)
        ChargeTickMaxMs = [Math]::Round($ticks[-1], 2)
        ChargeTicksOver50Ms = @($ticks | Where-Object { $_ -gt 50 }).Count
        ChargeCpuMedianMs = [Math]::Round((Get-Median @($rows.chargeCpuMs)), 2)
        ChargeAndTransferCpuMedianMs = [Math]::Round((Get-Median @($rows.chargeAndTransferCpuMs)), 2)
        GcMedianMs = Get-Median @($rows.gcMs)
    }
}

foreach ($group in ($all | Group-Object seed, center)) {
    $destinations = @($group.Group | ForEach-Object { "$($_.arrival)|$($_.portalAxis)" } | Select-Object -Unique)
    if ($destinations.Count -ne 1) { throw "Arrival/axis mismatch for $($group.Name): $destinations" }
}
$summary | Format-List
Write-Output 'All compared arrival positions and portal axes match.'
