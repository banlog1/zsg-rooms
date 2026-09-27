[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Candidates,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$Probe = 'run/filter-worker/aa-end-probe.exe'
)

$ErrorActionPreference = 'Stop'
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Choose a new diagnostic directory; existing results will not be overwritten.' }
$probePath = (Resolve-Path -LiteralPath $Probe).Path
$rows = @(Get-Content -LiteralPath $Candidates | Where-Object { $_.Trim() } | ForEach-Object { $_ | ConvertFrom-Json })
if (-not $rows.Count) { throw 'No candidates to measure.' }
$families = [Collections.Generic.HashSet[string]]::new()
foreach ($row in $rows) {
    if (-not $row.seed -or -not $row.family -or -not $families.Add([string]$row.family)) {
        throw 'Expected one sampled seed per distinct End family.'
    }
}
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null

function Get-DistanceSquared($A, $B) {
    $dx = [long]$A[0] - [long]$B[0]
    $dz = [long]$A[1] - [long]$B[1]
    return $dx * $dx + $dz * $dz
}

function Test-ConnectedShips($Model, [int]$FirstRadius, [int]$ConnectionRadius) {
    if ($FirstRadius + 2 * $ConnectionRadius -gt $Model.shipSearchRadius) {
        throw 'Probe envelope is too small for the requested connected-ship comparison.'
    }
    foreach ($first in $Model.ships) {
        if ((Get-DistanceSquared $first.ship $Model.outerGateway) -gt $FirstRadius * $FirstRadius) { continue }
        foreach ($second in $Model.ships) {
            if ((Get-DistanceSquared $first.city $second.city) -eq 0 -or
                (Get-DistanceSquared $first.ship $second.ship) -gt $ConnectionRadius * $ConnectionRadius) { continue }
            foreach ($third in $Model.ships) {
                if ((Get-DistanceSquared $third.city $first.city) -eq 0 -or
                    (Get-DistanceSquared $third.city $second.city) -eq 0) { continue }
                if ((Get-DistanceSquared $third.ship $first.ship) -le $ConnectionRadius * $ConnectionRadius -or
                    (Get-DistanceSquared $third.ship $second.ship) -le $ConnectionRadius * $ConnectionRadius) { return $true }
            }
        }
    }
    return $false
}

$measurements = @()
$raw = @()
$models = @()
foreach ($row in $rows) {
    $line = & $probePath ([string]$row.seed)
    if ($LASTEXITCODE -ne 0) { throw "End probe failed for candidate $($row.seed)." }
    $model = $line | ConvertFrom-Json
    if ([string]$model.seed -cne [string]$row.seed -or $model.currentPassed -ne $row.endPassed) {
        throw 'End probe disagrees with the sampled search verdict.'
    }
    $raw += $line
    $models += $model
    $anchors = @(
        foreach ($ship in $model.ships) {
            $others = @($model.ships | Where-Object {
                $_.city[0] -ne $ship.city[0] -or $_.city[1] -ne $ship.city[1]
            } | ForEach-Object { Get-DistanceSquared $_.ship $ship.ship } | Sort-Object)
            [pscustomobject]@{
                ship = $ship.ship
                gatewayDistanceSquared = Get-DistanceSquared $ship.ship $model.outerGateway
                companionDistanceSquared = if ($others.Count -ge 2) { $others[1] } else { $null }
            }
        }
    )
    $nearest = $anchors | Sort-Object gatewayDistanceSquared | Select-Object -First 1
    $measurement = [ordered]@{
        seed = [string]$row.seed; family = [string]$row.family; outerGateway = $model.outerGateway
        shipsFound = $anchors.Count; shipSearchRadius = $model.shipSearchRadius
        nearestShipDistance = if ($nearest) { [math]::Round([math]::Sqrt($nearest.gatewayDistanceSquared), 1) } else { $null }
        anchors = $anchors
    }
    foreach ($radius in @(384, 512, 768, 1024)) {
        $best = $anchors | Where-Object {
            $_.gatewayDistanceSquared -le $radius * $radius -and $null -ne $_.companionDistanceSquared
        } | Sort-Object companionDistanceSquared,gatewayDistanceSquared | Select-Object -First 1
        $measurement["minimumCompanionRadiusForFirst$radius"] = if ($best) {
            [math]::Ceiling([math]::Sqrt($best.companionDistanceSquared))
        } else { $null }
    }
    $measurements += [pscustomobject]$measurement
}

$comparisons = @(
    foreach ($first in @(384, 512, 768, 1024)) {
        foreach ($companion in @(512, 768, 1024, 1536, 2048)) {
            $passing = @($measurements | Where-Object {
                @($_.anchors | Where-Object {
                    $_.gatewayDistanceSquared -le $first * $first -and
                    $null -ne $_.companionDistanceSquared -and
                    $_.companionDistanceSquared -le $companion * $companion
                }).Count -gt 0
            })
            [pscustomobject]@{ firstRadius = $first; companionRadius = $companion; passingFamilies = $passing.Count; sampledFamilies = $rows.Count }
        }
    }
)
$connectedComparisons = @(
    foreach ($first in @(384, 512, 640, 768, 1024)) {
        foreach ($connection in @(512, 768, 1024)) {
            $passing = @($models | Where-Object { Test-ConnectedShips $_ $first $connection })
            [pscustomobject]@{ firstRadius = $first; connectionRadius = $connection; passingFamilies = $passing.Count; sampledFamilies = $rows.Count }
        }
    }
)
$report = [ordered]@{
    note = 'Counterfactual End checks on sampled pre-End candidates, not a new throughput benchmark. Bounded city search can miss unusually distant city starts.'
    comparisons = $comparisons
    connectedComparisons = $connectedComparisons
    candidates = $measurements
}
$utf8 = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllLines((Join-Path $OutputDirectory 'end-models.jsonl'), [string[]]$raw, $utf8)
[IO.File]::WriteAllText((Join-Path $OutputDirectory 'distances.json'), ($report | ConvertTo-Json -Depth 12), $utf8)
$measurements | Select-Object seed,family,shipsFound,nearestShipDistance,minimumCompanionRadiusForFirst384,
    minimumCompanionRadiusForFirst512,minimumCompanionRadiusForFirst768,minimumCompanionRadiusForFirst1024 |
    Export-Csv -LiteralPath (Join-Path $OutputDirectory 'distances.csv') -NoTypeInformation -Encoding UTF8
$comparisons | Format-Table -AutoSize
$connectedComparisons | Format-Table -AutoSize
Write-Output "Private End-distance diagnostics: $OutputDirectory"
